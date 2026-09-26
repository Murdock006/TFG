package es.sintaxys.teamtask.vista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import es.sintaxys.teamtask.databinding.FragmentTareasPendientesBinding
import es.sintaxys.teamtask.modelo.Tarea
import es.sintaxys.teamtask.viewmodel.TareasViewModel
import es.sintaxys.teamtask.service.LocalizadorServicios
import es.sintaxys.teamtask.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FragmentTareasPendientes : Fragment() {

    private var _binding: FragmentTareasPendientesBinding? = null
    private val binding get() = _binding!!
    private val parejaVM: es.sintaxys.teamtask.viewmodel.ParejaViewModel by activityViewModels()
    private val tareasVM: TareasViewModel by activityViewModels()

    // Adaptador construido por vista: se crea en onViewCreated contra la vista actual y se destruye
    // en onDestroyView, de modo que cada recreación parte de un scope vivo.
    private var adapter: TareasHomeAdapter? = null

    // Job para la suscripción a tareas, se cancela y reinicia cuando cambia el grupo
    private var tareasJob: Job? = null
    private var grupoActualId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTareasPendientesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvTareasPendientes.layoutManager = LinearLayoutManager(requireContext())
        adapter = TareasHomeAdapter(this, parejaVM, tareasVM) { id ->
            findNavController().navigate(
                R.id.fragment_Tareas,
                FragmentTareasArgs(taskId = id, modo = null, categoria = null).toBundle()
            )
        }
        binding.rvTareasPendientes.adapter = adapter

        // Pull-to-refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val list = LocalizadorServicios.repositorioTarea.obtenerTareas().getOrNull() ?: emptyList()
                actualizarListado(list)
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Resultado de completar/confirmar tarea disparado desde el adaptador de esta pantalla.
        // No se navega: solo se informa y se resetea el estado.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tareasVM.marcarCompletadaState.collect { result ->
                    result?.let {
                        if (it.isSuccess) {
                            android.widget.Toast.makeText(requireContext(), getString(R.string.tarea_marcar_completada), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(requireContext(), it.exceptionOrNull()?.message ?: "Error", android.widget.Toast.LENGTH_LONG).show()
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
                            android.widget.Toast.makeText(requireContext(), getString(R.string.tarea_confirmada), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(requireContext(), it.exceptionOrNull()?.message ?: "Error", android.widget.Toast.LENGTH_LONG).show()
                        }
                        tareasVM.resetConfirmarTareaState()
                    }
                }
            }
        }

        // botones para cambiar vista
        binding.btnPendientes.setOnClickListener { mostrarPendientes() }
        binding.btnAsignadas.setOnClickListener { mostrarAsignadas() }
        binding.btnHistorial.setOnClickListener { mostrarHistorial() }

        // estado inicial
        aplicarEstadoBotones(selected = "pendientes")

        // observar usuarios para mostrar nombres correctamente
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                        adapter?.updateUsuarios(lista)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FragmentTareasPendientes", "Error observando usuarios: ${e.message}")
                }
            }
        }

        // observar el grupo: mostrar/ocultar UI y recargar tareas cuando cambie
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collectLatest { grupo ->
                val nuevoGrupoId = grupo?.id
                if (nuevoGrupoId != grupoActualId) {
                    grupoActualId = nuevoGrupoId
                    // reiniciar la suscripción a tareas con el nuevo grupo
                    tareasJob?.cancel()
                    adapter?.updateItems(emptyList())
                }

                if (grupo == null) {
                    // Sin grupo: ocultar tabs y lista, mostrar mensaje
                    binding.layoutBotonesTabs.visibility = View.GONE
                    binding.rvTareasPendientes.visibility = View.GONE
                    binding.layoutSinGrupo.visibility = View.VISIBLE
                    binding.swipeRefresh.isEnabled = false
                } else {
                    // Con grupo: mostrar tabs y lista
                    binding.layoutBotonesTabs.visibility = View.VISIBLE
                    binding.rvTareasPendientes.visibility = View.VISIBLE
                    binding.layoutSinGrupo.visibility = View.GONE
                    binding.swipeRefresh.isEnabled = true

                    //                     suscribir tareas del grupo si no hay suscripción activa
                    if (tareasJob == null || tareasJob?.isActive == false) {
                        suscribirTareas()
                    }
                }
                }
            }
        }
    }

    private fun suscribirTareas() {
        tareasJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LocalizadorServicios.repositorioTarea.observarTareas().collect { list ->
                    actualizarListado(list)
                }
            }
        }
    }

    private var modoHistorial: Boolean = false
    private var modoAsignadas: Boolean = false

    private fun aplicarEstadoBotones(selected: String) {
        val blanco = android.graphics.Color.WHITE
        val negro  = 0xFF000000.toInt()

        // inactivo: borde gris, texto negro
        binding.btnPendientes.setBackgroundResource(R.drawable.tab_inactivo)
        binding.btnPendientes.setTextColor(negro)
        binding.btnAsignadas.setBackgroundResource(R.drawable.tab_inactivo)
        binding.btnAsignadas.setTextColor(negro)
        binding.btnHistorial.setBackgroundResource(R.drawable.tab_inactivo)
        binding.btnHistorial.setTextColor(negro)

        // activo: fondo negro, texto blanco
        when (selected) {
            "pendientes" -> { binding.btnPendientes.setBackgroundResource(R.drawable.tab_activo); binding.btnPendientes.setTextColor(blanco) }
            "asignadas"  -> { binding.btnAsignadas.setBackgroundResource(R.drawable.tab_activo);  binding.btnAsignadas.setTextColor(blanco) }
            "historial"  -> { binding.btnHistorial.setBackgroundResource(R.drawable.tab_activo);  binding.btnHistorial.setTextColor(blanco) }
        }
    }

    private fun mostrarPendientes() {
        modoHistorial = false
        modoAsignadas = false
        aplicarEstadoBotones(selected = "pendientes")
        viewLifecycleOwner.lifecycleScope.launch {
            val list = LocalizadorServicios.repositorioTarea.obtenerTareas().getOrNull() ?: emptyList()
            actualizarListado(list)
        }
    }

    private fun mostrarAsignadas() {
        modoHistorial = false
        modoAsignadas = true
        aplicarEstadoBotones(selected = "asignadas")
        viewLifecycleOwner.lifecycleScope.launch {
            val list = LocalizadorServicios.repositorioTarea.obtenerTareas().getOrNull() ?: emptyList()
            actualizarListado(list)
        }
    }

    private fun mostrarHistorial() {
        modoHistorial = true
        modoAsignadas = false
        aplicarEstadoBotones(selected = "historial")
        viewLifecycleOwner.lifecycleScope.launch {
            val list = LocalizadorServicios.repositorioTarea.obtenerTareas().getOrNull() ?: emptyList()
            actualizarListado(list)
        }
    }

    private fun actualizarListado(list: List<Tarea>) {
        val uid = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        val grupoId = grupoActualId

        // Si no hay grupo, no mostrar nada (la UI ya muestra el mensaje de sin grupo)
        if (grupoId.isNullOrBlank()) {
            adapter?.updateItems(emptyList())
            return
        }

        // Filtrar solo tareas que pertenezcan al grupo actual
        val tareasDelGrupo = list.filter { it.grupoId == grupoId }

        val filtrado = when {
            modoAsignadas -> {
                // Tareas creadas/asignadas por el usuario dentro de su grupo
                tareasDelGrupo.filter { it.creadoPor == uid }
            }
            modoHistorial -> {
                // Historial: tareas completadas, confirmadas o reclamadas en el grupo
                tareasDelGrupo.filter {
                    (it.creadoPor == uid || it.asignadoA == uid) &&
                    (it.estado == "completada" || it.estado == "confirmada" || it.estado == "reclamada")
                }
            }
            else -> {
                // Pendientes: tareas asignadas a mi (pendiente o esperando confirmación), o creadas por mi pendientes de confirmación
                tareasDelGrupo.filter {
                    (it.asignadoA == uid && it.estado == "pendiente") ||
                    (it.asignadoA == uid && it.estado == "pendiente_confirmacion") ||
                    (it.creadoPor == uid && it.estado == "pendiente_confirmacion")
                }
            }
        }
        adapter?.updateItems(filtrado)

        // Empty state
        if (filtrado.isEmpty()) {
            binding.rvTareasPendientes.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = when {
                modoAsignadas -> getString(R.string.empty_assigned_tasks)
                modoHistorial -> getString(R.string.empty_history)
                else -> getString(R.string.empty_pending_tasks)
            }
        } else {
            binding.rvTareasPendientes.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tareasJob?.cancel()
        adapter?.destroy()
        adapter = null
        _binding = null
    }
}

