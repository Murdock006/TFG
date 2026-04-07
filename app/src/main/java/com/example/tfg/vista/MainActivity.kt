package com.example.tfg.vista

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.Navigation
import com.example.tfg.databinding.ActivityMainBinding
import com.example.tfg.service.NotificationScheduler
import com.example.tfg.viewmodel.ParejaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val parejaVM: ParejaViewModel by viewModels()

    // NavController guardado para uso en callbacks
    private lateinit var navController: androidx.navigation.NavController

    // control doble retroceso
    private var ultimoRetrocesoMs: Long = 0L
    
    // Launcher para pedir permiso de notificaciones
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notificaciones activadas ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No recibirás notificaciones de tareas. Puedes activarlas desde ajustes.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate iniciado")

        // Dejar que el sistema gestione los insets y el redimensionado por el teclado (adjustResize)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        android.util.Log.d("MainActivity", "Binding inflado")
        setContentView(binding.root)
        android.util.Log.d("MainActivity", "Content view establecido")

        val navHostFragment = supportFragmentManager.findFragmentById(com.example.tfg.R.id.nav_host_fragment) as? NavHostFragment
        navController = navHostFragment?.navController ?: run {
            val found = try {
                Navigation.findNavController(this, com.example.tfg.R.id.nav_host_fragment)
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
        binding.bottomNavigation.setupWithNavController(navController)

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

        // Observar notificaciones para el usuario actual y mostrar locales
        CoroutineScope(Dispatchers.Main).launch {
            try {
                android.util.Log.d("MainActivity", "Iniciando observación de notificaciones...")
                val uid = com.example.tfg.service.LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                android.util.Log.d("MainActivity", "UID actual: $uid")
                if (!uid.isNullOrBlank()) {
                    val repoNot = com.example.tfg.repositorio.RepositorioNotificaciones()
                    // usar flow observer
                    repoNot.observarNotificaciones(uid).collect { lista ->
                        android.util.Log.d("MainActivity", "Recibidas ${lista.size} notificaciones")
                        lista.filter { !it.visto }.forEach { not ->
                            try {
                                // mostrar notificación local
                                val title = when (not.tipo) { "asignacion" -> "Tarea asignada"; else -> "Notificación" }
                                val message = (not.contenido["titulo"] as? String) ?: (not.contenido["texto"] as? String) ?: "Tienes una notificación"
                                val tareaId = (not.contenido["tareaId"] as? String)
                                NotificationScheduler.showImmediateNotification(this@MainActivity, (not.id.hashCode()), title, message, tareaId)
                                // marcar como vista
                                CoroutineScope(Dispatchers.IO).launch {
                                    repoNot.marcarNotificacionVista(not.id)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error procesando notificación ${not.id}", e)
                            }
                        }
                    }
                } else {
                    android.util.Log.d("MainActivity", "No hay usuario actual, saltando observación de notificaciones")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error en observarNotificaciones", e)
            }
        }

        // manejar si la activity fue lanzada con openTaskId
        intent?.getStringExtra("openTaskId")?.let { tid ->
            handleOpenTaskId(tid)
        }

        // Ocultar la barra inferior en pantallas de presentación y login
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                com.example.tfg.R.id.fragment_Presentacion,
                com.example.tfg.R.id.fragment_Login,
                com.example.tfg.R.id.fragment_Registro -> binding.bottomNavigation.visibility = View.GONE
                else -> binding.bottomNavigation.visibility = View.VISIBLE
            }
        }

        // manejar doble retroceso: si estamos en el fragment principal, pedir confirmación con Toast
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    val destId = navController.currentDestination?.id
                    // considerar fragment_PgPrincipal como la pantalla principal
                    if (destId == com.example.tfg.R.id.fragment_PgPrincipal) {
                        val ahora = System.currentTimeMillis()
                        if (ahora - ultimoRetrocesoMs <= 2000L) {
                            // salir de la app
                            finish()
                        } else {
                            ultimoRetrocesoMs = ahora
                            Toast.makeText(this@MainActivity, "Pulsa de nuevo retroceder para salir", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // dejar que NavController intente popBackStack, si no, terminar
                        if (!navController.popBackStack()) finish()
                    }
                } catch (e: Exception) {
                    // fallback default
                    if (!navController.popBackStack()) finish()
                }
            }
        })
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("openTaskId")?.let { tid ->
            if (::navController.isInitialized) handleOpenTaskId(tid)
        }
    }

    private fun handleOpenTaskId(taskId: String) {
        try {
            val bundle = android.os.Bundle().apply { putString("taskId", taskId) }
            navController.navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        } catch (e: Exception) {
            // ignore navigation errors
        }
    }

    private fun verificarSesionActiva() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                android.util.Log.d("MainActivity", "verificarSesionActiva: iniciando...")
                // Verificar Firebase Auth
                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                android.util.Log.d("MainActivity", "Firebase user: ${firebaseUser?.uid}")
                
                if (firebaseUser != null) {
                    // Verificar que el email esté verificado (obligatorio)
                    if (!firebaseUser.isEmailVerified) {
                        // Si NO está verificado, forzar logout y mostrar mensaje
                        android.util.Log.d("MainActivity", "Email no verificado, logout forzado")
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        Toast.makeText(this@MainActivity, "Debes verificar tu correo electrónico antes de continuar. Revisa tu bandeja de entrada.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    
                    // Usuario autenticado y verificado: ESPERAR a que el grupo se cargue completamente
                    // antes de navegar (evita crash por acceso a grupo null)
                    android.util.Log.d("MainActivity", "Cargando grupo para usuario ${firebaseUser.uid}")
                    parejaVM.cargarGrupoPorUsuario(firebaseUser.uid)
                    android.util.Log.d("MainActivity", "Grupo cargado, procediendo con navegación")
                    
                    // Ahora que el grupo está cargado, esperar a que el NavController esté listo y navegar a PgPrincipal
                    navController.addOnDestinationChangedListener(object : androidx.navigation.NavController.OnDestinationChangedListener {
                        override fun onDestinationChanged(
                            controller: androidx.navigation.NavController,
                            destination: androidx.navigation.NavDestination,
                            arguments: android.os.Bundle?
                        ) {
                            // Solo navegar cuando llegamos a Presentacion (inicio)
                            if (destination.id == com.example.tfg.R.id.fragment_Presentacion) {
                                android.util.Log.d("MainActivity", "En Presentacion, navegando a Login")
                                controller.navigate(com.example.tfg.R.id.action_fragment_Presentacion_to_fragment_Login)
                                // Esperar un frame para que Login se monte
                                binding.root.post {
                                    if (controller.currentDestination?.id == com.example.tfg.R.id.fragment_Login) {
                                        android.util.Log.d("MainActivity", "En Login, navegando a PgPrincipal")
                                        controller.navigate(com.example.tfg.R.id.action_fragment_Login_to_fragment_PgPrincipal)
                                    }
                                }
                                controller.removeOnDestinationChangedListener(this)
                            }
                        }
                    })
                } else {
                    // No hay sesión: flujo normal de presentación → login
                    android.util.Log.d("MainActivity", "No hay sesión activa")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error verificando sesión activa", e)
            }
        }
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
                        "Las notificaciones te ayudan a recordar tus tareas. Actívalas desde ajustes si quieres recibirlas.",
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
}
