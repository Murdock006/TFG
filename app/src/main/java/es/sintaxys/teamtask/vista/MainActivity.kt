package es.sintaxys.teamtask.vista

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import es.sintaxys.teamtask.R
import es.sintaxys.teamtask.databinding.ActivityMainBinding
import es.sintaxys.teamtask.service.NotificationScheduler
import es.sintaxys.teamtask.service.LocalizadorServicios
import es.sintaxys.teamtask.service.firebase.FirebaseComposition
import es.sintaxys.teamtask.util.Constants
import es.sintaxys.teamtask.viewmodel.ParejaViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val parejaVM: ParejaViewModel by viewModels()
    private lateinit var navigationView: NavigationView
    private lateinit var navigationViewFooter: NavigationView
    private val helpDialogTag = "help_dialog"

    // NavController guardado para uso en callbacks
    private lateinit var navController: androidx.navigation.NavController

    // control doble retroceso
    private var ultimoRetrocesoMs: Long = 0L
    private var notificacionesJob: Job? = null
    private var notificacionesUidObservado: String? = null
    private var avatarDrawerJob: Job? = null

    // Listener one-shot de auto-login, guardado para poder quitarlo en onDestroy (y tras disparar)
    // y para no registrar dos en una misma instancia de Activity.
    private var autoLoginListener: androidx.navigation.NavController.OnDestinationChangedListener? = null
    
    // Launcher para pedir permiso de notificaciones
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.notificaciones_activadas), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.notificaciones_desactivadas), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TFG)
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate iniciado")

        // Dejar que el sistema gestione los insets y el redimensionado por el teclado (adjustResize)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        android.util.Log.d("MainActivity", "Binding inflado")
        setContentView(binding.root)
        android.util.Log.d("MainActivity", "Content view establecido")

        val navHostFragment = supportFragmentManager.findFragmentById(es.sintaxys.teamtask.R.id.nav_host_fragment) as? NavHostFragment
        navController = navHostFragment?.navController ?: run {
            val found = try {
                Navigation.findNavController(this, es.sintaxys.teamtask.R.id.nav_host_fragment)
            } catch (e: IllegalStateException) {
                null
            }
            found ?: throw IllegalStateException("No se encontró NavHostFragment o NavController con id nav_host_fragment. Revisa activity_main.xml y que el id coincida.")
        }
        android.util.Log.d("MainActivity", "NavController inicializado")

        // AUTO-LOGIN: verificar si hay sesión activa de Firebase
        verificarSesionActiva()
        
        // Pedir permiso de notificaciones (Android 13+)
        solicitarPermisoNotificaciones()

        // Configurar BottomNavigation
        // Configuración manual de la barra inferior: NavigationUI.setupWithNavController usa
        // saveState/restoreState por pestaña, y eso restauraba pilas "envenenadas" (Inicio
        // apilado encima de Tareas) impidiendo volver a entrar a una pestaña. Aquí navegamos
        // sin guardar/restaurar estado por pestaña.
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val opciones = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(false)
                .setPopUpTo(
                    navController.graph.startDestinationId,
                    inclusive = false,
                    saveState = false
                )
                .build()
            navController.navigate(item.itemId, null, opciones)
            true
        }
        // Sincronizar el resaltado de la barra con el destino actual (lo que hacía
        // NavigationUI internamente al configurar la barra).
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.menu.findItem(destination.id)?.let { item ->
                if (!item.isChecked) item.isChecked = true
            }
        }

        navigationView = binding.navigationView
        navigationViewFooter = binding.navigationViewFooter
        setupDrawer()
        observarUsuarioDrawerHeader()
        refrescarAvatarToolbar()

        // Ajustar insets: padding fijo superior moderado + padding inferior dinámico para navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            // Solo manejar navigation bar (barra inferior) dinámicamente
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Aplicar padding superior FIJO (16dp) y padding inferior dinámico
            val paddingTopDp = 16 // padding fijo en dp
            val paddingTopPx = (paddingTopDp * resources.displayMetrics.density).toInt()
            
            v.updatePadding(
                top = paddingTopPx,
                bottom = navInsets.bottom
            )
            
            // El navHostFragment también necesita estos paddings
            binding.navHostFragment?.updatePadding(
                top = paddingTopPx,
                bottom = navInsets.bottom
            )
            
            insets
        }

        iniciarObservacionNotificacionesParaSesionActual()

        // manejar si la activity fue lanzada con openTaskId
        intent?.getStringExtra("openTaskId")?.let { tid ->
            handleOpenTaskId(tid)
        }

        // Ocultar la barra inferior en pantallas de presentación y login
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                es.sintaxys.teamtask.R.id.fragment_Presentacion,
                es.sintaxys.teamtask.R.id.fragment_Login,
                es.sintaxys.teamtask.R.id.fragment_Registro -> {
                    binding.bottomNavigation.visibility = View.GONE
                    binding.topAppBar.visibility = View.GONE
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
                else -> {
                    binding.bottomNavigation.visibility = View.VISIBLE
                    binding.topAppBar.visibility = View.VISIBLE
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
            }
        }

        // manejar doble retroceso: si estamos en el fragment principal, pedir confirmación con Toast
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    if (binding.drawerLayout.isDrawerOpen(Gravity.START)) {
                        binding.drawerLayout.closeDrawer(Gravity.START)
                        return
                    }
                    val destId = navController.currentDestination?.id
                    // considerar fragment_PgPrincipal como la pantalla principal
                    if (destId == es.sintaxys.teamtask.R.id.fragment_PgPrincipal) {
                        val ahora = System.currentTimeMillis()
                        if (ahora - ultimoRetrocesoMs <= Constants.DOUBLE_BACK_TIMEOUT_MS) {
                            // salir de la app
                            finish()
                        } else {
                            ultimoRetrocesoMs = ahora
                            Toast.makeText(this@MainActivity, getString(R.string.doble_back), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Si estamos en un fragment secundario, volver a Inicio
                        val destinosSecundarios = setOf(
                            es.sintaxys.teamtask.R.id.fragment_Calendario,
                            es.sintaxys.teamtask.R.id.fragment_Recompensas,
                            es.sintaxys.teamtask.R.id.fragment_Pareja,
                            es.sintaxys.teamtask.R.id.fragment_Tareas,
                            es.sintaxys.teamtask.R.id.fragment_TareasPendientes,
                            es.sintaxys.teamtask.R.id.fragment_Perfil,
                            es.sintaxys.teamtask.R.id.fragment_Registro
                        )
                        if (destId in destinosSecundarios) {
                            // Cerrar el destino secundario actual (inclusive) antes de navegar a
                            // Inicio: evita apilar un PgPrincipal duplicado y deja limpia la pila.
                            val opciones = androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(destId!!, true)
                                .setLaunchSingleTop(true)
                                .build()
                            navController.navigate(
                                es.sintaxys.teamtask.R.id.fragment_PgPrincipal, null, opciones
                            )
                        } else {
                            if (!navController.popBackStack()) finish()
                        }
                    }
                } catch (e: Exception) {
                    // fallback default
                    if (!navController.popBackStack()) finish()
                }
            }
        })
    }

    /** Abre el drawer lateral desde fragments */
    fun openDrawer() {
        binding.drawerLayout.openDrawer(Gravity.START)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("openTaskId")?.let { tid ->
            if (::navController.isInitialized) handleOpenTaskId(tid)
        }
    }

    override fun onResume() {
        super.onResume()
        iniciarObservacionNotificacionesParaSesionActual()
    }

    override fun onDestroy() {
        super.onDestroy()
        notificacionesJob?.cancel()
        notificacionesJob = null
        notificacionesUidObservado = null
        // Acotar la vida del listener one-shot de auto-login a esta Activity
        autoLoginListener?.let {
            if (::navController.isInitialized) navController.removeOnDestinationChangedListener(it)
        }
        autoLoginListener = null
        // Cancelar la carga de avatar para que no corra contra una Activity destruida
        avatarDrawerJob?.cancel()
        avatarDrawerJob = null
    }

    private fun handleOpenTaskId(taskId: String) {
        try {
            val args = FragmentTareasArgs(taskId = taskId, modo = null, categoria = null).toBundle()
            if (navController.currentDestination?.id == es.sintaxys.teamtask.R.id.fragment_Tareas) {
                // Ya estamos en Tareas: pop inclusive de la entrada actual y push de una nueva con el
                // taskId entrante, evitando apilar un fragment_Tareas duplicado.
                val opciones = androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(es.sintaxys.teamtask.R.id.fragment_Tareas, true)
                    .build()
                navController.navigate(es.sintaxys.teamtask.R.id.fragment_Tareas, args, opciones)
            } else {
                navController.navigate(es.sintaxys.teamtask.R.id.fragment_Tareas, args)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error navegando a tarea $taskId", e)
        }
    }

    private fun verificarSesionActiva() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("MainActivity", "verificarSesionActiva: iniciando...")
                // Verificar Firebase Auth
                val firebaseUser = FirebaseComposition.auth().currentUser
                android.util.Log.d("MainActivity", "Firebase user: ${firebaseUser?.uid}")
                
                if (firebaseUser != null) {
                    // Verificar que el email esté verificado (obligatorio)
                    if (!firebaseUser.isEmailVerified) {
                        // Si NO está verificado, forzar logout y mostrar mensaje
                        android.util.Log.d("MainActivity", "Email no verificado, logout forzado")
                        FirebaseComposition.auth().signOut()
                        Toast.makeText(this@MainActivity, getString(R.string.verificar_email), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    
                    // Usuario autenticado y verificado: ESPERAR a que el grupo se cargue completamente
                    // antes de navegar (evita crash por acceso a grupo null)
                    android.util.Log.d("MainActivity", "Cargando grupo para usuario ${firebaseUser.uid}")
                    parejaVM.cargarGrupoPorUsuario(firebaseUser.uid)
                    android.util.Log.d("MainActivity", "Grupo cargado, procediendo con navegación")
                    
                    // Ahora que el grupo está cargado, esperar a que el NavController esté listo y navegar a PgPrincipal.
                    // Un único coordinator (navegarDePresentacionALogin) es dueño de la transición
                    // Presentación → Login. Se quita el listener previo antes de añadir el nuevo para que
                    // una misma instancia de Activity nunca registre dos.
                    val listener = object : androidx.navigation.NavController.OnDestinationChangedListener {
                        override fun onDestinationChanged(
                            controller: androidx.navigation.NavController,
                            destination: androidx.navigation.NavDestination,
                            arguments: android.os.Bundle?
                        ) {
                            // Solo navegar cuando llegamos a Presentacion (inicio)
                            if (destination.id == es.sintaxys.teamtask.R.id.fragment_Presentacion) {
                                navegarDePresentacionALogin()
                                // Esperar un frame para que Login se monte. La continuación
                                // Login → PgPrincipal queda ligada a la sesión verificada (esta ruta),
                                // no a la señal de mensajes de Presentación.
                                binding.root.post {
                                    if (controller.currentDestination?.id == es.sintaxys.teamtask.R.id.fragment_Login) {
                                        android.util.Log.d("MainActivity", "En Login, navegando a PgPrincipal")
                                        controller.navigate(es.sintaxys.teamtask.R.id.action_fragment_Login_to_fragment_PgPrincipal)
                                    }
                                }
                                controller.removeOnDestinationChangedListener(this)
                                autoLoginListener = null
                            }
                        }
                    }
                    autoLoginListener?.let { navController.removeOnDestinationChangedListener(it) }
                    autoLoginListener = listener
                    navController.addOnDestinationChangedListener(listener)
                } else {
                    // No hay sesión: flujo normal de presentación → login
                    android.util.Log.d("MainActivity", "No hay sesión activa")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error verificando sesión activa", e)
            }
        }
    }

    /**
     * Único punto que dispara la transición Presentación → Login. Es idempotente por el guard de
     * destino vivo: tras la primera navegación `currentDestination` es Login (actualizado de forma
     * síncrona), por lo que cualquier señal duplicada se vuelve un no-op.
     */
    private fun navegarDePresentacionALogin() {
        if (navController.currentDestination?.id != es.sintaxys.teamtask.R.id.fragment_Presentacion) return
        android.util.Log.d("MainActivity", "En Presentacion, navegando a Login")
        navController.navigate(es.sintaxys.teamtask.R.id.action_fragment_Presentacion_to_fragment_Login)
    }

    /**
     * Señal que FragmentPresentacion emite tras sus tres mensajes. Delega en el coordinator único; la
     * continuación Login → PgPrincipal queda reservada a la ruta de sesión verificada (el listener de
     * destino), de modo que un usuario sin sesión permanece en Login.
     */
    fun onPresentacionMensajesCompletados() {
        navegarDePresentacionALogin()
    }

    private fun iniciarObservacionNotificacionesParaSesionActual() {
        val uid = es.sintaxys.teamtask.service.LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        if (uid.isNullOrBlank()) {
            notificacionesJob?.cancel()
            notificacionesJob = null
            notificacionesUidObservado = null
            android.util.Log.d("MainActivity", "No hay usuario actual, observación de notificaciones detenida")
            return
        }

        if (notificacionesJob?.isActive == true && notificacionesUidObservado == uid) {
            return
        }

        notificacionesJob?.cancel()
        notificacionesUidObservado = uid
        val repoNot = es.sintaxys.teamtask.repositorio.RepositorioNotificaciones()

        notificacionesJob = lifecycleScope.launch {
            try {
                android.util.Log.d("MainActivity", "Iniciando observación de notificaciones para uid=$uid")
                repoNot.observarNotificaciones(uid).collect { lista ->
                    android.util.Log.d("MainActivity", "Notificaciones recibidas: ${lista.size} total, ${lista.filter { !it.visto }.size} sin ver")
                    if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@collect
                    lista.filter { !it.visto }.forEach { not ->
                        try {
                            android.util.Log.d("MainActivity", "Procesando notificación: id=${not.id}, tipo=${not.tipo}, contenido=${not.contenido}")
                            val title = when (not.tipo) { "asignacion" -> "Tarea asignada"; else -> "Notificación" }
                            val message = (not.contenido["titulo"] as? String) ?: (not.contenido["texto"] as? String) ?: "Tienes una notificación"
                            val tareaId = (not.contenido["tareaId"] as? String)
                            android.util.Log.d("MainActivity", "Mostrando notificación sistema: title=$title, message=$message, tareaId=$tareaId")
                            NotificationScheduler.showImmediateNotification(this@MainActivity, not.id.hashCode(), title, message, tareaId)
                            repoNot.marcarNotificacionVista(not.id)
                            android.util.Log.d("MainActivity", "Notificación marcada como vista: ${not.id}")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error procesando notificación ${not.id}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error en observarNotificaciones", e)
            }
        }
    }

    /** Cambia el título de la toolbar desde fragments */
    fun setToolbarTitle(title: String) {
        binding.topAppBar.title = title
    }

    private fun solicitarPermisoNotificaciones() {
        // Solo necesario en Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Ya tenemos permiso
                    android.util.Log.d("MainActivity", "Permiso de notificaciones ya concedido")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // El usuario rechazó antes, mostrar explicación
                    Toast.makeText(
                        this,
                        getString(R.string.contrasena_requerida),
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    // Pedir permiso
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun setupDrawer() {
        binding.topAppBar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(Gravity.START)
        }

        // El avatar de la toolbar abre el drawer (mismo gesto que la barra superior).
        binding.toolbarAvatarContainer.setOnClickListener {
            binding.drawerLayout.openDrawer(Gravity.START)
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                refrescarHeaderDrawer()
            }
        })

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                es.sintaxys.teamtask.R.id.menuCuentaSeguridad -> {
                    if (navController.currentDestination?.id != es.sintaxys.teamtask.R.id.fragment_Perfil) {
                        navController.navigate(es.sintaxys.teamtask.R.id.fragment_Perfil)
                    }
                    binding.drawerLayout.closeDrawer(Gravity.START)
                    true
                }
                es.sintaxys.teamtask.R.id.menuCambiarContrasena -> {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                    enviarResetContrasena()
                    true
                }
                es.sintaxys.teamtask.R.id.menuAyuda -> {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                    mostrarAyuda()
                    true
                }
                es.sintaxys.teamtask.R.id.menuContactarSoporte -> {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                    contactarSoporte()
                    true
                }
                es.sintaxys.teamtask.R.id.menuComprarPuntos -> {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                    Toast.makeText(this, getString(es.sintaxys.teamtask.R.string.proximamente), Toast.LENGTH_SHORT).show()
                    true
                }
                es.sintaxys.teamtask.R.id.menuSuscripcion -> {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                    Toast.makeText(this, getString(es.sintaxys.teamtask.R.string.proximamente), Toast.LENGTH_SHORT).show()
                    true
                }
                es.sintaxys.teamtask.R.id.menuPoliticaPrivacidad -> {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                    abrirPoliticaPrivacidad()
                    true
                }
                else -> false
            }
        }

        navigationViewFooter.setNavigationItemSelectedListener { item ->
            return@setNavigationItemSelectedListener when (item.itemId) {
                es.sintaxys.teamtask.R.id.menuCerrarSesion -> {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                    mostrarDialogoCerrarSesion()
                    true
                }
                else -> false
            }
        }
    }

private fun observarUsuarioDrawerHeader() {
        // Solo observar si hay usuario logueado - evita crash al inicio sin sesión
        val usuario = LocalizadorServicios.repositorioAuth.usuarioActual()
        if (usuario == null) {
            android.util.Log.d("MainActivity", "Sin usuario, no se observa header drawer")
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                try {
                    LocalizadorServicios.repositorioAuth.observarUsuarios().collect {
                        refrescarHeaderDrawer()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error observando usuarios para drawer", e)
                }
            }
        }
    }

    private fun refrescarHeaderDrawer() {
        try {
            val header = navigationView.getHeaderView(0) ?: run {
                android.util.Log.w("MainActivity", "Header drawer no disponible")
                return
            }
            val tvNombre = header.findViewById<TextView>(es.sintaxys.teamtask.R.id.tvDrawerNombre)
            val tvEmail = header.findViewById<TextView>(es.sintaxys.teamtask.R.id.tvDrawerEmail)
            val tvGrupo = header.findViewById<TextView>(es.sintaxys.teamtask.R.id.tvDrawerGrupo)
            val tvRacha = header.findViewById<TextView>(es.sintaxys.teamtask.R.id.tvDrawerRacha)
            val ivAvatar = header.findViewById<ImageView>(es.sintaxys.teamtask.R.id.ivDrawerAvatar)
            
            if (tvNombre == null || tvEmail == null || tvGrupo == null || tvRacha == null) {
                android.util.Log.w("MainActivity", "TextViews del header no encontrados")
                return
            }
            
            val usuario = LocalizadorServicios.repositorioAuth.usuarioActual()
            if (usuario != null) {
                tvNombre.text = usuario.nombre.ifBlank { getString(es.sintaxys.teamtask.R.string.no_hay_usuario) }
                tvEmail.text = usuario.email
                tvRacha.text = getString(es.sintaxys.teamtask.R.string.drawer_racha_format, usuario.rachaDias)
                val grupo = parejaVM.grupo.value
                if (grupo != null) {
                    tvGrupo.text = getString(
                        es.sintaxys.teamtask.R.string.drawer_pareja_format,
                        grupo.emoji,
                        grupo.nombre
                    )
                } else {
                    tvGrupo.text = getString(es.sintaxys.teamtask.R.string.drawer_pareja_sin_grupo)
                }
                
                // Resolver el avatar a través del repositorio canónico (cualquier usuario)
                cargarAvatarEnVistas(usuario.id, usuario.avatarUpdatedAt, ivAvatar)
            } else {
                tvNombre.text = getString(es.sintaxys.teamtask.R.string.no_hay_usuario)
                tvEmail.text = ""
                tvGrupo.text = getString(es.sintaxys.teamtask.R.string.drawer_pareja_sin_grupo)
                tvRacha.text = ""
                pintarAvatarGenerico(ivAvatar)
                pintarAvatarGenerico(binding.ivToolbarAvatar)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error en refrescarHeaderDrawer", e)
        }
    }

    /**
     * Carga el avatar de [uid] en el header del drawer y en el avatar de la toolbar,
     * reutilizando un único job para cancelar cargas anteriores.
     */
    private fun cargarAvatarEnVistas(uid: String, avatarUpdatedAt: com.google.firebase.Timestamp?, ivHeader: ImageView) {
        avatarDrawerJob?.cancel()
        avatarDrawerJob = lifecycleScope.launch {
            val bitmap = LocalizadorServicios.repositorioAvatar.obtenerAvatar(uid, avatarUpdatedAt)
            val destinos = listOfNotNull(ivHeader, binding.ivToolbarAvatar)
            if (bitmap != null) {
                destinos.forEach { vista ->
                    Glide.with(this@MainActivity)
                        .load(bitmap)
                        .circleCrop()
                        .placeholder(es.sintaxys.teamtask.R.drawable.perfil)
                        .into(vista)
                }
            } else {
                destinos.forEach { pintarAvatarGenerico(it) }
            }
        }
    }

    private fun pintarAvatarGenerico(vista: ImageView?) {
        vista ?: return
        Glide.with(this).clear(vista)
        vista.setImageResource(es.sintaxys.teamtask.R.drawable.perfil)
    }

    /**
     * Carga el avatar de la toolbar de forma independiente al drawer, para que
     * esté visible aunque el menú lateral nunca se haya abierto.
     */
    private fun refrescarAvatarToolbar() {
        val usuario = LocalizadorServicios.repositorioAuth.usuarioActual() ?: run {
            pintarAvatarGenerico(binding.ivToolbarAvatar)
            return
        }
        avatarDrawerJob?.cancel()
        avatarDrawerJob = lifecycleScope.launch {
            val bitmap = LocalizadorServicios.repositorioAvatar
                .obtenerAvatar(usuario.id, usuario.avatarUpdatedAt)
            val vista = binding.ivToolbarAvatar
            if (bitmap != null) {
                Glide.with(this@MainActivity)
                    .load(bitmap)
                    .circleCrop()
                    .placeholder(es.sintaxys.teamtask.R.drawable.perfil)
                    .into(vista)
            } else {
                pintarAvatarGenerico(vista)
            }
        }
    }

    private fun enviarResetContrasena() {
        val auth = FirebaseComposition.auth()
        val email = auth.currentUser?.email
        if (email.isNullOrBlank()) {
            Toast.makeText(this, getString(es.sintaxys.teamtask.R.string.no_hay_usuario), Toast.LENGTH_LONG).show()
            return
        }
        auth
            .sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, getString(es.sintaxys.teamtask.R.string.reset_password_enviado), Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(es.sintaxys.teamtask.R.string.reset_password_error), Toast.LENGTH_LONG).show()
            }
    }

    private fun mostrarAyuda() {
        if (supportFragmentManager.findFragmentByTag(helpDialogTag) != null) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(es.sintaxys.teamtask.R.string.ayuda_titulo))
            .setMessage(getString(es.sintaxys.teamtask.R.string.ayuda_texto))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(getString(es.sintaxys.teamtask.R.string.ayuda_info_eliminacion)) { _, _ ->
                abrirInfoEliminacionCuenta()
            }
            .show()
    }

    private fun abrirInfoEliminacionCuenta() {
        val url = getString(es.sintaxys.teamtask.R.string.info_eliminacion_url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(es.sintaxys.teamtask.R.string.ayuda_error_navegador), Toast.LENGTH_LONG).show()
        }
    }

    private fun contactarSoporte() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:hello@sintaxys.es")
            putExtra(Intent.EXTRA_SUBJECT, getString(es.sintaxys.teamtask.R.string.soporte_asunto))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(es.sintaxys.teamtask.R.string.reset_password_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun abrirPoliticaPrivacidad() {
        val url = getString(es.sintaxys.teamtask.R.string.politica_privacidad_url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(es.sintaxys.teamtask.R.string.ayuda_error_navegador), Toast.LENGTH_LONG).show()
        }
    }

    private fun mostrarDialogoCerrarSesion() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(es.sintaxys.teamtask.R.string.logout_confirm_titulo))
            .setMessage(getString(es.sintaxys.teamtask.R.string.logout_confirm_mensaje))
            .setNegativeButton(getString(es.sintaxys.teamtask.R.string.cancelar), null)
            .setPositiveButton(getString(es.sintaxys.teamtask.R.string.cerrar_sesion)) { _, _ ->
                cerrarSesionYVolverALogin()
            }
            .show()
    }

    private fun cerrarSesionYVolverALogin() {
        lifecycleScope.launch {
            try {
                LocalizadorServicios.repositorioAuth.logout()
            } catch (_: Exception) {
            }

            try {
                getSharedPreferences("tfg_prefs", MODE_PRIVATE).edit().remove("grupoId").apply()
            } catch (_: Exception) {
            }

            notificacionesJob?.cancel()
            notificacionesJob = null
            notificacionesUidObservado = null

            try {
                val opciones = androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(es.sintaxys.teamtask.R.id.fragment_Presentacion, true)
                    .build()
                navController.navigate(es.sintaxys.teamtask.R.id.fragment_Login, null, opciones)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error navegando a login tras logout", e)
            }
        }
    }
}
