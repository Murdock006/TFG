package es.sintaxys.teamtask.vista

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import es.sintaxys.teamtask.BuildConfig
import es.sintaxys.teamtask.R
import es.sintaxys.teamtask.databinding.FragmentPgPrincipalBinding
import es.sintaxys.teamtask.viewmodel.VistaModeloPrincipal
import es.sintaxys.teamtask.viewmodel.ParejaViewModel
import es.sintaxys.teamtask.viewmodel.TareasViewModel
import es.sintaxys.teamtask.service.LocalizadorServicios
import es.sintaxys.teamtask.service.NotificationScheduler
import es.sintaxys.teamtask.modelo.Tarea
import es.sintaxys.teamtask.modelo.Usuario
import es.sintaxys.teamtask.modelo.Notificacion
import es.sintaxys.teamtask.util.Constants
import es.sintaxys.teamtask.util.SelectorFechaHora
import es.sintaxys.teamtask.repositorio.CategoriasRepositorio
import es.sintaxys.teamtask.repositorio.RepositorioNotificaciones
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FragmentPgPrincipal : Fragment() {

    private lateinit var binding: FragmentPgPrincipalBinding
    private val vistaModelo: VistaModeloPrincipal by viewModels()
    private val parejaVM: ParejaViewModel by activityViewModels()
    private val tareasVM: TareasViewModel by activityViewModels()
    // cache de usuarios para resolver nombres en adapters
    private var usuariosCache: List<Usuario> = emptyList()
    // control de suscripción de tareas recientes por grupo
    private var tareasHomeJob: Job? = null
    private var grupoIdActual: String? = null
    // adapter horizontal de miembros
    private lateinit var miembrosAdapterHorizontal: MiembrosHorizontalAdapter
    // adapter de tareas recientes, construido por vista para poder destruirlo en onDestroyView
    private var tareaAdapter: TareasHomeAdapter? = null

    // Anuncio recompensado (AdMob). Se carga y se recicla siguiendo el ciclo de la vista.
    private var rewardedAd: RewardedAd? = null
    private var cargandoAnuncio = false

    companion object {
        private const val PREFS_NAME = "tfg_prefs"
        private const val MAX_ANUNCIOS_DIA = 3
        private const val PUNTOS_POR_ANUNCIO = 20
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPgPrincipalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pull-to-refresh
        binding.swipeRefresh.setOnRefreshListener {
            vistaModelo.actualizarTexto()
            binding.swipeRefresh.isRefreshing = false
        }

        // Show loading initially
        binding.progressBar.visibility = View.VISIBLE

        vistaModelo.textoLiveData.observe(viewLifecycleOwner) { valor ->
            binding.textViewResultados.text = valor
            binding.progressBar.visibility = View.GONE
        }

        vistaModelo.actualizarTexto()

        // Conectar botones de UI a acciones de navegación
        binding.categoriaCocina.setOnClickListener {
            findNavController().navigate(
                es.sintaxys.teamtask.R.id.fragment_Tareas,
                FragmentTareasArgs(taskId = null, modo = null, categoria = "cocina").toBundle()
            )
        }
        binding.categoriaLimpieza.setOnClickListener {
            findNavController().navigate(
                es.sintaxys.teamtask.R.id.fragment_Tareas,
                FragmentTareasArgs(taskId = null, modo = null, categoria = "limpieza").toBundle()
            )
        }
        binding.categoriaRopa.setOnClickListener {
            findNavController().navigate(
                es.sintaxys.teamtask.R.id.fragment_Tareas,
                FragmentTareasArgs(taskId = null, modo = null, categoria = "ropa").toBundle()
            )
        }
        binding.categoriaMascotas.setOnClickListener {
            findNavController().navigate(
                es.sintaxys.teamtask.R.id.fragment_Tareas,
                FragmentTareasArgs(taskId = null, modo = null, categoria = "mascotas").toBundle()
            )
        }
        binding.categoriaRecados.setOnClickListener {
            findNavController().navigate(
                es.sintaxys.teamtask.R.id.fragment_Tareas,
                FragmentTareasArgs(taskId = null, modo = null, categoria = "recados").toBundle()
            )
        }
        binding.categoriaPersonalizado.setOnClickListener {
            findNavController().navigate(
                es.sintaxys.teamtask.R.id.fragment_Tareas,
                FragmentTareasArgs(taskId = null, modo = null, categoria = "personalizado").toBundle()
            )
        }

        // Configurar RecyclerView horizontal de miembros
        miembrosAdapterHorizontal = MiembrosHorizontalAdapter()
        binding.rvMiembrosHorizontal.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMiembrosHorizontal.adapter = miembrosAdapterHorizontal

        // Observadores para puntos en tiempo real y cache de usuarios
        val authRepo = LocalizadorServicios.repositorioAuth
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepo.observarUsuarios().collect { listaUsuarios ->
                    usuariosCache = listaUsuarios
                    val myId = authRepo.usuarioActual()?.id
                    if (myId != null) {
                        val yo = listaUsuarios.find { it.id == myId }
                        binding.puntosUsuario.text = (yo?.puntos ?: 0).toString()
                        binding.puntosReservados.text = getString(es.sintaxys.teamtask.R.string.reservados_format, yo?.puntosReservados ?: 0)
                    } else {
                        binding.puntosUsuario.text = "0"
                        binding.puntosReservados.text = getString(es.sintaxys.teamtask.R.string.reservados_format, 0)
                    }

                    // Actualizar miembros horizontales
                    val grupo = parejaVM.grupo.value
                    if (grupo == null) {
                        // Sin grupo: mostrar mensaje "vincula pareja"
                        miembrosAdapterHorizontal.setItems(emptyList())
                        binding.indicadorScrollMiembros.visibility = View.GONE
                        binding.tvSinGrupo.visibility = View.VISIBLE
                        binding.infoGrupo.visibility = View.GONE
                    } else {
                        // Con grupo: mostrar tarjetas de miembros, ocultar "vincula pareja"
                        binding.tvSinGrupo.visibility = View.GONE
                        binding.infoGrupo.visibility = View.VISIBLE
                        binding.nombreGrupo.text = grupo.nombre ?: getString(es.sintaxys.teamtask.R.string.guion)
                        binding.miembrosCount.text = getString(es.sintaxys.teamtask.R.string.miembros_format, grupo.miembros.size)

                        val miembrosList = grupo.miembros.entries.map { (uid, rol) ->
                            val usuario = listaUsuarios.find { it.id == uid }
                                ?: Usuario(id = uid, nombre = uid, email = "", puntos = 0)
                            Pair(usuario, rol)
                        }
                        miembrosAdapterHorizontal.setItems(miembrosList)

                        // Mostrar indicador de scroll solo si hay más de 2 miembros
                        binding.indicadorScrollMiembros.visibility =
                            if (miembrosList.size > 2) View.VISIBLE else View.GONE
                    }

                    // actualizar adaptador de tareas
                    tareaAdapter?.updateUsuarios(listaUsuarios)
                }
            }
        }

        // Actualizar cuando cambie el grupo
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collect { g ->
                    if (g == null) {
                        binding.tvSinGrupo.visibility = View.VISIBLE
                        binding.infoGrupo.visibility = View.GONE
                        miembrosAdapterHorizontal.setItems(emptyList())
                        binding.indicadorScrollMiembros.visibility = View.GONE
                    }
                }
            }
        }

        // Resultado de completar/confirmar tarea disparado desde el adaptador de Inicio.
        // En Inicio no se navega: solo se informa y se resetea el estado.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tareasVM.marcarCompletadaState.collect { result ->
                    result?.let {
                        if (it.isSuccess) {
                            Toast.makeText(requireContext(), getString(R.string.tarea_marcar_completada), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), it.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                        tareasVM.resetMarcarCompletadaState()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tareasVM.confirmarTareaState.collect { result ->
                    result?.let {
                        if (it.isSuccess) {
                            Toast.makeText(requireContext(), getString(R.string.tarea_confirmada), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), it.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                        tareasVM.resetConfirmarTareaState()
                    }
                }
            }
        }

        // Tareas recientes
        binding.rvTareasHome.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvTareasHome.isNestedScrollingEnabled = false

        tareaAdapter = TareasHomeAdapter(this, parejaVM, tareasVM) { id ->
            findNavController().navigate(
                es.sintaxys.teamtask.R.id.fragment_Tareas,
                FragmentTareasArgs(taskId = id, modo = null, categoria = null).toBundle()
            )
        }
        binding.rvTareasHome.adapter = tareaAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collectLatest { grupo ->
                    val nuevoGrupoId = grupo?.id
                    tareasHomeJob?.cancel()
                    tareasHomeJob = null
                    tareaAdapter?.updateItems(emptyList())
                    grupoIdActual = nuevoGrupoId

                    if (grupo == null) {
                        binding.rvTareasHome.visibility = View.GONE
                        binding.tvSinTareasRecientes.visibility = View.GONE
                        binding.emptyState.visibility = View.VISIBLE
                    } else {
                        binding.tvSinTareasRecientes.visibility = View.GONE
                        binding.emptyState.visibility = View.GONE
                        binding.rvTareasHome.visibility = View.VISIBLE
                        tareasHomeJob = viewLifecycleOwner.lifecycleScope.launch {
                            LocalizadorServicios.repositorioTarea
                                .observarTareasPorGrupo(nuevoGrupoId!!)
                                .collect { list ->
                                    val recientes = list
                                        .sortedByDescending { it.fechaCreada?.seconds ?: 0L }
                                        .take(5)
                                    tareaAdapter?.updateItems(recientes)
                                    if (recientes.isEmpty()) {
                                        binding.rvTareasHome.visibility = View.GONE
                                        binding.tvSinTareasRecientes.visibility = View.GONE
                                        binding.emptyState.visibility = View.VISIBLE
                                    } else {
                                        binding.rvTareasHome.visibility = View.VISIBLE
                                        binding.tvSinTareasRecientes.visibility = View.GONE
                                        binding.emptyState.visibility = View.GONE
                                    }
                                }
                        }
                    }
                }
            }
        }

        // helper: lanzar flujo de asignación
        val lanzarAsignacion: (String) -> Unit = { categoriaId ->
            viewLifecycleOwner.lifecycleScope.launch {
                val repoCat = CategoriasRepositorio(requireContext())
                val cats = try { repoCat.cargarCategoriasDesdeRaw() } catch (_: Exception) { emptyList() }
                val cat = cats.find { it.nombre.equals(categoriaId, true) || it.id.equals(categoriaId, true) }
                val sugeridas = cat?.tareas ?: emptyList()
                if (sugeridas.isEmpty()) { Toast.makeText(requireContext(), "No hay sugerencias para $categoriaId", Toast.LENGTH_SHORT).show(); return@launch }
                val titulos = sugeridas.map { "${it.titulo} — ${it.puntos} pts" }.toTypedArray()
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Sugerencias: $categoriaId")
                    .setItems(titulos) { _, idx ->
                        val sel = sugeridas[idx]
                        viewLifecycleOwner.lifecycleScope.launch {
                            val grupo = parejaVM.grupo.value
                            val usuariosCache = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<Usuario>() }
                            val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                            val opcionesMiembros = mutableListOf<Pair<String,String>>()
                            if (grupo != null) {
                                grupo.miembros.keys.forEach { uid ->
                                    if (!uidActual.isNullOrBlank() && uid == uidActual) return@forEach
                                    val usuario = usuariosCache.find { it.id == uid }
                                    val display = when {
                                        usuario?.nombre?.isNotBlank() == true -> {
                                            val mailOrId = if (usuario.email.isNotBlank()) usuario.email else usuario.id
                                            "${usuario.nombre} (${mailOrId})"
                                        }
                                        usuario?.email?.isNotBlank() == true -> usuario.email
                                        else -> uid
                                    }
                                    opcionesMiembros.add(Pair(display, uid))
                                }
                            }
                            if (opcionesMiembros.isEmpty()) { Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.no_hay_miembros), Toast.LENGTH_SHORT).show(); return@launch }
                            val nombres = opcionesMiembros.map { it.first }.toTypedArray()
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle(getString(es.sintaxys.teamtask.R.string.selecciona_miembro))
                                .setItems(nombres) { _, mIdx ->
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        val elegidoUid = opcionesMiembros[mIdx].second
                                        if (!uidActual.isNullOrBlank() && elegidoUid == uidActual) {
                                            Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.no_autoasignar), Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        // Fecha/hora OBLIGATORIA: si el usuario cancela el picker, no se crea la tarea.
                                        SelectorFechaHora.elegir(requireContext()) { fechaElegida ->
                                            viewLifecycleOwner.lifecycleScope.launch {
                                            val tarea = Tarea(titulo = sel.titulo, descripcion = sel.descripcion, categoria = categoriaId, dificultad = if (sel.dificultad.uppercase()=="FACIL") 1 else if (sel.dificultad.uppercase()=="MEDIA") 2 else 3, puntos = sel.puntos, creadoPor = LocalizadorServicios.repositorioAuth.usuarioActual()?.id, asignadoA = elegidoUid, grupoId = parejaVM.grupo.value?.id, fechaProgramada = fechaElegida)
                                            val res = LocalizadorServicios.repositorioTarea.crearTarea(tarea)
                                            if (res.isSuccess) {
                                                val creada = res.getOrNull()
                                                android.util.Log.d("FragmentPgPrincipal", "Tarea asignada desde home: id=${creada?.id}, destinatario=$elegidoUid")
                                                if (creada != null && creada.fechaProgramada != null) {
                                                    val trigger = creada.fechaProgramada!!.toDate().time - creada.minutosAntes * Constants.SECONDS_PER_MINUTE * Constants.MILLIS_PER_SECOND
                                                    NotificationScheduler.scheduleReminder(requireContext(), creada.id, getString(es.sintaxys.teamtask.R.string.recordatorio_tarea_title, creada.titulo), getString(es.sintaxys.teamtask.R.string.recordatorio_tarea_msg, SelectorFechaHora.formatear(creada.fechaProgramada!!)), trigger)
                                                }
                                                Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.tarea_asignada_ok, opcionesMiembros[mIdx].first), Toast.LENGTH_SHORT).show()
                                                // Notificar al asignado vía Firebase
                                                if (!elegidoUid.isNullOrBlank()) {
                                                    android.util.Log.d("FragmentPgPrincipal", "Enviando notificación Firebase (home): tipo=asignacion, destinatario=$elegidoUid, tareaId=${creada?.id}")
                                                    val repoNot = RepositorioNotificaciones()
                                                    val notifResult = repoNot.enviarNotificacion(
                                                        Notificacion(
                                                            id = "",
                                                            tipo = "asignacion",
                                                            contenido = mapOf(
                                                                "tareaId" to (creada?.id ?: ""),
                                                                "titulo" to tarea.titulo,
                                                                "desde" to (uidActual ?: "")
                                                            ),
                                                            destinatario = elegidoUid,
                                                            visto = false,
                                                            fecha = Timestamp.now()
                                                        )
                                                    )
                                                    if (notifResult.isSuccess) {
                                                        android.util.Log.d("FragmentPgPrincipal", "Notificación enviada OK, id=${notifResult.getOrNull()}")
                                                    } else {
                                                        android.util.Log.e("FragmentPgPrincipal", "Error enviando notificación: ${notifResult.exceptionOrNull()?.message}")
                                                    }
                                                }
                                            } else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton(getString(es.sintaxys.teamtask.R.string.cancelar), null).show()
                        }
                    }
                    .setNegativeButton(getString(es.sintaxys.teamtask.R.string.cancelar), null)
                    .show()
            }
        }

        fun setupCategoriaAsignacion(button: View, categoriaId: String) {
            button.setOnLongClickListener {
                lanzarAsignacion(categoriaId)
                true
            }
            button.setOnClickListener {
                if (categoriaId.equals("personalizado", ignoreCase = true)) {
                    findNavController().navigate(
                        es.sintaxys.teamtask.R.id.fragment_Tareas,
                        FragmentTareasArgs(taskId = null, modo = "crear", categoria = "Personalizada").toBundle()
                    )
                } else {
                    findNavController().navigate(
                        es.sintaxys.teamtask.R.id.fragment_Tareas,
                        FragmentTareasArgs(taskId = null, modo = null, categoria = categoriaId).toBundle()
                    )
                }
            }
        }

        setupCategoriaAsignacion(binding.categoriaCocina, "cocina")
        setupCategoriaAsignacion(binding.categoriaLimpieza, "limpieza")
        setupCategoriaAsignacion(binding.categoriaRopa, "ropa")
        setupCategoriaAsignacion(binding.categoriaMascotas, "mascotas")
        setupCategoriaAsignacion(binding.categoriaRecados, "recados")
        setupCategoriaAsignacion(binding.categoriaPersonalizado, "personalizado")

        try {
            binding.textViewResultados.visibility = View.GONE
            binding.btnCuentaSeguridad.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.w("FragmentPgPrincipal", "Error ocultando views: ${e.message}")
        }

        try {
            binding.btnAsignarCocina.visibility = View.GONE
            binding.btnAsignarLimpieza.visibility = View.GONE
            binding.btnAsignarRopa.visibility = View.GONE
            binding.btnAsignarMascotas.visibility = View.GONE
            binding.btnAsignarRecados.visibility = View.GONE
            binding.btnAsignarPersonalizado.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.w("FragmentPgPrincipal", "Error ocultando botones asignar: ${e.message}")
        }

        // Botón de anuncio recompensado → límite diario, mostrar y recargar
        try {
            binding.btnVerAnuncio.setOnClickListener { alPulsarVerAnuncio() }
            actualizarTextoBotonAnuncio()
            cargarAnuncioRecompensado()
        } catch (e: Exception) {
            android.util.Log.w("FragmentPgPrincipal", "Error configurando botón de anuncio: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        // El contador puede haber cambiado de día; refresca el texto y asegura un anuncio cargado.
        actualizarTextoBotonAnuncio()
        cargarAnuncioRecompensado()
    }

    // --- Anuncio recompensado (AdMob) ---

    /**
     * Carga un rewarded si no hay uno listo.
     * El ad unit ID sale de BuildConfig: en debug/emulator usa el ID de TEST de Google y en
     * release el ID REAL (ver buildConfigField AD_REWARDED_UNIT_ID en app/build.gradle.kts).
     */
    private fun cargarAnuncioRecompensado() {
        if (rewardedAd != null || cargandoAnuncio) return
        cargandoAnuncio = true
        RewardedAd.load(
            requireContext(),
            BuildConfig.AD_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    cargandoAnuncio = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    cargandoAnuncio = false
                }
            }
        )
    }

    private fun alPulsarVerAnuncio() {
        // Verificación del límite ANTES de mostrar.
        if (anunciosRestantesHoy() <= 0) {
            Toast.makeText(requireContext(), R.string.anuncio_limite_alcanzado, Toast.LENGTH_SHORT).show()
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            Toast.makeText(requireContext(), R.string.anuncio_no_disponible, Toast.LENGTH_SHORT).show()
            cargarAnuncioRecompensado()
            return
        }
        // Evita un segundo show si el usuario pulsa dos veces seguidas.
        rewardedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Reciclar: soltar el anuncio consumido y pedir otro para la próxima vez.
                rewardedAd = null
                cargarAnuncioRecompensado()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                cargarAnuncioRecompensado()
            }
        }
        ad.show(requireActivity()) { onUsuarioRecompensado() }
    }

    private fun onUsuarioRecompensado() {
        // Verificación del límite DESPUÉS de la recompensa: nunca superar el máximo diario.
        if (anunciosRestantesHoy() <= 0) {
            Toast.makeText(requireContext(), R.string.anuncio_limite_alcanzado, Toast.LENGTH_SHORT).show()
            actualizarTextoBotonAnuncio()
            return
        }
        val uid = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        if (uid == null) {
            Toast.makeText(requireContext(), R.string.anuncio_sin_sesion, Toast.LENGTH_SHORT).show()
            return
        }
        // Consumir el cupo del día solo cuando hay sesión (no gastar cupo sin recompensa).
        registrarAnuncioVisto()
        actualizarTextoBotonAnuncio()
        viewLifecycleOwner.lifecycleScope.launch {
            val res = LocalizadorServicios.repositorioAuth.sumarPuntos(uid, PUNTOS_POR_ANUNCIO)
            val ctx = context ?: return@launch
            Toast.makeText(
                ctx,
                if (res.isSuccess) getString(R.string.anuncio_recompensa_obtenida, PUNTOS_POR_ANUNCIO)
                else getString(R.string.anuncio_error_recompensa),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // --- Límite diario persistido en SharedPreferences ("tfg_prefs") ---

    private fun claveAnunciosHoy(): String {
        val fecha = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "ad_rewards_$fecha"
    }

    private fun anunciosVistosHoy(): Int =
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(claveAnunciosHoy(), 0)

    private fun anunciosRestantesHoy(): Int =
        (MAX_ANUNCIOS_DIA - anunciosVistosHoy()).coerceAtLeast(0)

    private fun registrarAnuncioVisto() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val vistosHoy = prefs.getInt(claveAnunciosHoy(), 0)
        prefs.edit().putInt(claveAnunciosHoy(), vistosHoy + 1).apply()
    }

    private fun actualizarTextoBotonAnuncio() {
        val restantes = anunciosRestantesHoy()
        binding.tvVerAnuncio.text = if (restantes > 0) {
            getString(R.string.ver_anuncio_restantes, PUNTOS_POR_ANUNCIO, restantes)
        } else {
            getString(R.string.anuncio_limite_alcanzado)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        // Ningún job con alcance de vista debe sobrevivir a la vista.
        tareasHomeJob?.cancel()
        tareasHomeJob = null
        tareaAdapter?.destroy()
        tareaAdapter = null
        // Soltar la referencia al anuncio al destruir la vista.
        rewardedAd = null
        cargandoAnuncio = false
    }

    private inner class MiembrosHorizontalAdapter : RecyclerView.Adapter<MiembrosHorizontalAdapter.MV>() {
        private var items: List<Pair<Usuario, String>> = emptyList() // usuario, rol
        fun setItems(list: List<Pair<Usuario, String>>) { items = list; notifyDataSetChanged() }
        inner class MV(val root: View) : RecyclerView.ViewHolder(root) {
            val ivAvatar: ImageView = root.findViewById(R.id.ivMiembroAvatar)
            val tvNombre: TextView = root.findViewById(R.id.tvMiembroNombre)
            val tvPuntos: TextView = root.findViewById(R.id.tvMiembroPuntos)
            val tvRol: TextView = root.findViewById(R.id.tvMiembroRol)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MV {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_miembro_card, parent, false)
            return MV(v)
        }
        override fun onBindViewHolder(holder: MV, position: Int) {
            val (u, rol) = items[position]
            holder.tvNombre.text = if (u.nombre.isNotBlank()) u.nombre else u.email.ifBlank { u.id }
            holder.tvPuntos.text = u.puntos.toString()
            holder.tvRol.text = rol

            // Resolver el avatar a través del repositorio canónico, con guarda de reciclado
            holder.ivAvatar.tag = u.id
            holder.ivAvatar.setImageResource(R.drawable.perfil)
            viewLifecycleOwner.lifecycleScope.launch {
                val bmp = LocalizadorServicios.repositorioAvatar.obtenerAvatar(u.id, u.avatarUpdatedAt)
                if (holder.ivAvatar.tag == u.id && bmp != null) {
                    Glide.with(holder.itemView.context).load(bmp).circleCrop().into(holder.ivAvatar)
                }
            }
        }
        override fun getItemCount(): Int = items.size
    }

}
