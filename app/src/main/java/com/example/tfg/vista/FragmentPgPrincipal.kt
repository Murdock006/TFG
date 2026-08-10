package com.example.tfg.vista

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
import com.example.tfg.R
import com.example.tfg.databinding.FragmentPgPrincipalBinding
import com.example.tfg.viewmodel.VistaModeloPrincipal
import com.example.tfg.viewmodel.ParejaViewModel
import com.example.tfg.viewmodel.TareasViewModel
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.modelo.Tarea
import com.example.tfg.modelo.Usuario
import com.example.tfg.modelo.Notificacion
import com.example.tfg.repositorio.CategoriasRepositorio
import com.example.tfg.repositorio.RepositorioNotificaciones
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File

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
            val bundle = Bundle().apply { putString("categoria", "cocina") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaLimpieza.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "limpieza") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaRopa.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "ropa") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaMascotas.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "mascotas") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaRecados.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "recados") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaPersonalizado.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "personalizado") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
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
                        binding.puntosReservados.text = getString(com.example.tfg.R.string.reservados_format, yo?.puntosReservados ?: 0)
                    } else {
                        binding.puntosUsuario.text = "0"
                        binding.puntosReservados.text = getString(com.example.tfg.R.string.reservados_format, 0)
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
                        binding.nombreGrupo.text = grupo.nombre ?: getString(com.example.tfg.R.string.guion)
                        binding.miembrosCount.text = getString(com.example.tfg.R.string.miembros_format, grupo.miembros.size)

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
                    if (binding.rvTareasHome.adapter is TareasHomeAdapter) {
                        (binding.rvTareasHome.adapter as TareasHomeAdapter).updateUsuarios(listaUsuarios)
                    }
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

        // Tareas recientes
        binding.rvTareasHome.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvTareasHome.isNestedScrollingEnabled = false

        val tareaAdapter = TareasHomeAdapter(this, parejaVM, tareasVM, viewLifecycleOwner.lifecycleScope)
        binding.rvTareasHome.adapter = tareaAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collectLatest { grupo ->
                    val nuevoGrupoId = grupo?.id
                    tareasHomeJob?.cancel()
                    tareasHomeJob = null
                    tareaAdapter.updateItems(emptyList())
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
                                    tareaAdapter.updateItems(recientes)
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
                            if (opcionesMiembros.isEmpty()) { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.no_hay_miembros), Toast.LENGTH_SHORT).show(); return@launch }
                            val nombres = opcionesMiembros.map { it.first }.toTypedArray()
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle(getString(com.example.tfg.R.string.selecciona_miembro))
                                .setItems(nombres) { _, mIdx ->
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        val elegidoUid = opcionesMiembros[mIdx].second
                                        if (!uidActual.isNullOrBlank() && elegidoUid == uidActual) {
                                            Toast.makeText(requireContext(), getString(com.example.tfg.R.string.no_autoasignar), Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        val tarea = Tarea(titulo = sel.titulo, descripcion = sel.descripcion, categoria = categoriaId, dificultad = if (sel.dificultad.uppercase()=="FACIL") 1 else if (sel.dificultad.uppercase()=="MEDIA") 2 else 3, puntos = sel.puntos, creadoPor = LocalizadorServicios.repositorioAuth.usuarioActual()?.id, asignadoA = elegidoUid, grupoId = parejaVM.grupo.value?.id)
                                        val res = LocalizadorServicios.repositorioTarea.crearTarea(tarea)
                                        if (res.isSuccess) {
                                            android.util.Log.d("FragmentPgPrincipal", "Tarea asignada desde home: id=${res.getOrNull()?.id}, destinatario=$elegidoUid")
                                            Toast.makeText(requireContext(), getString(com.example.tfg.R.string.tarea_asignada_ok, opcionesMiembros[mIdx].first), Toast.LENGTH_SHORT).show()
                                            // Notificar al asignado vía Firebase
                                            if (!elegidoUid.isNullOrBlank()) {
                                                android.util.Log.d("FragmentPgPrincipal", "Enviando notificación Firebase (home): tipo=asignacion, destinatario=$elegidoUid, tareaId=${res.getOrNull()?.id}")
                                                val repoNot = RepositorioNotificaciones()
                                                val notifResult = repoNot.enviarNotificacion(
                                                    Notificacion(
                                                        id = "",
                                                        tipo = "asignacion",
                                                        contenido = mapOf(
                                                            "tareaId" to (res.getOrNull()?.id ?: ""),
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
                                .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null).show()
                        }
                    }
                    .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null)
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
                    val bundle = Bundle().apply { putString("modo", "crear"); putString("categoria", "Personalizada") }
                    findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
                } else {
                    val bundle = Bundle().apply { putString("categoria", categoriaId) }
                    findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
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

        // Botón comprar puntos → abre el drawer
        try {
            binding.btnComprarPuntos.setOnClickListener {
                val drawer = requireActivity().findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
                drawer.openDrawer(android.view.Gravity.START)
            }
        } catch (e: Exception) {
            android.util.Log.w("FragmentPgPrincipal", "Error configurando botón comprar puntos: ${e.message}")
        }
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

            // Cargar avatar si existe
            val prefs = requireContext().getSharedPreferences("avatar_prefs", android.content.Context.MODE_PRIVATE)
            val avatarPath = prefs.getString("avatar_${u.id}", null)
            if (avatarPath != null) {
                val file = File(avatarPath)
                if (file.exists()) {
                    Glide.with(requireContext()).load(file).circleCrop().into(holder.ivAvatar)
                } else {
                    holder.ivAvatar.setImageResource(R.drawable.perfil)
                }
            } else {
                holder.ivAvatar.setImageResource(R.drawable.perfil)
            }
        }
        override fun getItemCount(): Int = items.size
    }

}
