package com.example.tfg.vista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.tfg.R
import com.example.tfg.databinding.FragmentPgPrincipalBinding
import com.example.tfg.viewmodel.VistaModeloPrincipal
import com.example.tfg.viewmodel.ParejaViewModel
import com.example.tfg.viewmodel.TareasViewModel
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.modelo.Tarea
import com.example.tfg.modelo.Usuario
import com.example.tfg.repositorio.CategoriasRepositorio
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPgPrincipalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vistaModelo.textoLiveData.observe(viewLifecycleOwner) { valor ->
            binding.textViewResultados.text = valor
        }

        vistaModelo.actualizarTexto()

        // Conectar botones de UI a acciones de navegación (usar id de categoría en minúsculas)
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

        // El acceso a Perfil de pareja se realiza desde la BottomNavigation ahora.

        // configurar reciclerview de miembros (usar findViewById si binding no contiene la vista todavía)
        val rvMiembros = try {
            binding.root.findViewById<RecyclerView>(com.example.tfg.R.id.rvMiembros)
        } catch (_: Exception) { null }
        rvMiembros?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        rvMiembros?.isNestedScrollingEnabled = false
        rvMiembros?.adapter = MiembrosAdapter()

        // helper para actualizar items del recycler (si binding no expone la vista)
        fun actualizarMiembros(miembros: List<Usuario>) {
            val adapter = rvMiembros?.adapter
            if (adapter is MiembrosAdapter) adapter.setItems(miembros)
        }

        // Observadores para puntos en tiempo real y cache de usuarios
        val authRepo = LocalizadorServicios.repositorioAuth
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observador de lista de usuarios para obtener puntos actualizados
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

                    // Si hay grupo, mostrar puntos del primer compañero distinto
                    val grupo = parejaVM.grupo.value
                    if (grupo == null) {
                        binding.puntosCompanero.text = "-"
                        binding.nombreGrupo.text = getString(com.example.tfg.R.string.guion)
                        binding.miembrosCount.text = getString(com.example.tfg.R.string.miembros_format, 0)
                    } else {
                        binding.nombreGrupo.text = grupo.nombre ?: getString(com.example.tfg.R.string.guion)
                        binding.miembrosCount.text = getString(com.example.tfg.R.string.miembros_format, grupo.miembros.size)
                        val otroUid = grupo.miembros.keys.firstOrNull { it != myId }
                        if (otroUid != null) {
                            val otro = listaUsuarios.find { it.id == otroUid }
                            binding.puntosCompanero.text = (otro?.puntos ?: 0).toString()
                        } else {
                            binding.puntosCompanero.text = "-"
                        }
                        // poblar recycler miembros
                        val miembrosList = grupo.miembros.keys.map { uid -> listaUsuarios.find { it.id == uid } ?: Usuario(id = uid, nombre = uid, email = "", puntos = 0) }
                        actualizarMiembros(miembrosList)
                    }

                    // actualizar adaptador si existe
                    if (binding.rvTareasHome.adapter is TareasHomeAdapter) {
                        (binding.rvTareasHome.adapter as TareasHomeAdapter).updateUsuarios(listaUsuarios)
                    }
                }
            }
        }

        // También actualizar cuando cambie el grupo (por ejemplo, al aceptar invitación)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collect { g ->
                    // la actualización real llega por el observador anterior; aquí podemos cambiar texto si grupo es null
                    if (g == null) binding.puntosCompanero.text = "-"
                }
            }
        }

        // Añadir soporte para tareas recientes: usar lista vertical (una tarea por línea)
        binding.rvTareasHome.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvTareasHome.isNestedScrollingEnabled = false

        val tareaAdapter = TareasHomeAdapter(this, parejaVM, tareasVM, viewLifecycleOwner.lifecycleScope)
        binding.rvTareasHome.adapter = tareaAdapter

        // observar el grupo y gestionar tareas recientes — re-suscribir siempre que cambie el grupo
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collectLatest { grupo ->
                    val nuevoGrupoId = grupo?.id

                    // Siempre cancelar la suscripción anterior al cambiar de grupo
                    tareasHomeJob?.cancel()
                    tareasHomeJob = null
                    tareaAdapter.updateItems(emptyList())
                    grupoIdActual = nuevoGrupoId

                    if (grupo == null) {
                        binding.rvTareasHome.visibility = View.GONE
                        binding.tvSinTareasRecientes.visibility = View.VISIBLE
                        binding.tvSinTareasRecientes.text = "Únete a un grupo para ver tareas recientes"
                    } else {
                        binding.tvSinTareasRecientes.visibility = View.GONE
                        binding.rvTareasHome.visibility = View.VISIBLE
                        // Nueva suscripción con el grupoId ya conocido — evita que el repositorio
                        // tenga que ir a Firestore a buscarlo y no lo encuentre a tiempo
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
                                        binding.tvSinTareasRecientes.visibility = View.VISIBLE
                                        binding.tvSinTareasRecientes.text = "No hay tareas recientes en este grupo"
                                    } else {
                                        binding.rvTareasHome.visibility = View.VISIBLE
                                        binding.tvSinTareasRecientes.visibility = View.GONE
                                    }
                                }
                        }
                    }
                }
            }
        }

        // helper: lanzar flujo de asignación para una categoría (muestra sugerencias y permite seleccionar miembro)
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
                        // elegir miembro del grupo
                        viewLifecycleOwner.lifecycleScope.launch {
                            val grupo = parejaVM.grupo.value
                            val usuariosCache = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<Usuario>() }
                            val opcionesMiembros = mutableListOf<Pair<String,String>>()
                            if (grupo != null) {
                                grupo.miembros.keys.forEach { uid ->
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
                                        val tarea = Tarea(titulo = sel.titulo, descripcion = sel.descripcion, categoria = categoriaId, dificultad = if (sel.dificultad.uppercase()=="FACIL") 1 else if (sel.dificultad.uppercase()=="MEDIA") 2 else 3, puntos = sel.puntos, creadoPor = LocalizadorServicios.repositorioAuth.usuarioActual()?.id, asignadoA = elegidoUid, grupoId = parejaVM.grupo.value?.id)
                                        val res = LocalizadorServicios.repositorioTarea.crearTarea(tarea)
                                        if (res.isSuccess) Toast.makeText(requireContext(), getString(com.example.tfg.R.string.tarea_asignada_ok, opcionesMiembros[mIdx].first), Toast.LENGTH_SHORT).show() else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null).show()
                        }
                    }
                    .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null)
                    .show()
            }
        }

        // Convertir click largo en asignación directa (mostrar sugeridas y seleccionar miembro)
        fun setupCategoriaAsignacion(button: View, categoriaId: String) {
            button.setOnLongClickListener {
                lanzarAsignacion(categoriaId)
                true
            }
            // On short click: for "personalizado" open the crear mode, otherwise show the list
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

        // ocultar el texto del repositorio ya que no lo queremos mostrar
        try {
            binding.textViewResultados.visibility = View.GONE
            binding.btnCuentaSeguridad.visibility = View.GONE
        } catch (_: Exception) { }

        // Conectar botones 'Asignar' para abrir directamente el flujo de asignación
        // Se eliminan/ocultan: mover la lógica de asignación al fragment de categoría
        try {
            binding.btnAsignarCocina.visibility = View.GONE
            binding.btnAsignarLimpieza.visibility = View.GONE
            binding.btnAsignarRopa.visibility = View.GONE
            binding.btnAsignarMascotas.visibility = View.GONE
            binding.btnAsignarRecados.visibility = View.GONE
            binding.btnAsignarPersonalizado.visibility = View.GONE
        } catch (_: Exception) { }

        // ...existing code...
    }

    // adaptador simple para mostrar miembros (nombre y puntos)
    private inner class MiembrosAdapter : RecyclerView.Adapter<MiembrosAdapter.MV>() {
        private var items: List<Usuario> = emptyList()
        fun setItems(list: List<Usuario>) { items = list; notifyDataSetChanged() }
        inner class MV(val root: View) : RecyclerView.ViewHolder(root) {
            val tvNombre: TextView = root.findViewById(com.example.tfg.R.id.tvMiembroNombre)
            val tvPuntos: TextView = root.findViewById(com.example.tfg.R.id.tvMiembroPuntos)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MV {
            val v = LayoutInflater.from(parent.context).inflate(com.example.tfg.R.layout.item_miembro, parent, false)
            return MV(v)
        }
        override fun onBindViewHolder(holder: MV, position: Int) {
            val u = items[position]
            holder.tvNombre.text = if (u.nombre.isNotBlank()) u.nombre else u.email.ifBlank { u.id }
            holder.tvPuntos.text = (u.puntos).toString()
        }
        override fun getItemCount(): Int = items.size
    }

}
