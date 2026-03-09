package com.example.tfg.vista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tfg.databinding.FragmentTareasPendientesBinding
import com.example.tfg.modelo.Tarea
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FragmentTareasPendientes : Fragment() {

    private var _binding: FragmentTareasPendientesBinding? = null
    private val binding get() = _binding!!
    private val parejaVM: com.example.tfg.viewmodel.ParejaViewModel by activityViewModels()

    private val adapter by lazy { TareasHomeAdapter(this, parejaVM, viewLifecycleOwner.lifecycleScope) }

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
        binding.rvTareasPendientes.adapter = adapter

        // botones para cambiar vista
        binding.btnPendientes.setOnClickListener { mostrarPendientes() }
        binding.btnAsignadas.setOnClickListener { mostrarAsignadas() }
        binding.btnHistorial.setOnClickListener { mostrarHistorial() }

        // estado inicial
        aplicarEstadoBotones(selected = "pendientes")

        // observar usuarios para mostrar nombres correctamente
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                    adapter.updateUsuarios(lista)
                }
            } catch (_: Exception) { }
        }

        // observar el grupo: mostrar/ocultar UI y recargar tareas cuando cambie
        viewLifecycleOwner.lifecycleScope.launch {
            parejaVM.grupo.collectLatest { grupo ->
                val nuevoGrupoId = grupo?.id
                if (nuevoGrupoId != grupoActualId) {
                    grupoActualId = nuevoGrupoId
                    // reiniciar la suscripción a tareas con el nuevo grupo
                    tareasJob?.cancel()
                    adapter.updateItems(emptyList())
                }

                if (grupo == null) {
                    // Sin grupo: ocultar tabs y lista, mostrar mensaje
                    binding.layoutBotonesTabs.visibility = View.GONE
                    binding.rvTareasPendientes.visibility = View.GONE
                    binding.layoutSinGrupo.visibility = View.VISIBLE
                } else {
                    // Con grupo: mostrar tabs y lista
                    binding.layoutBotonesTabs.visibility = View.VISIBLE
                    binding.rvTareasPendientes.visibility = View.VISIBLE
                    binding.layoutSinGrupo.visibility = View.GONE

                    // suscribir tareas del grupo si no hay suscripción activa
                    if (tareasJob == null || tareasJob?.isActive == false) {
                        suscribirTareas()
                    }
                }
            }
        }
    }

    private fun suscribirTareas() {
        tareasJob = viewLifecycleOwner.lifecycleScope.launch {
            LocalizadorServicios.repositorioTarea.observarTareas().collect { list ->
                actualizarListado(list)
            }
        }
    }

    private var modoHistorial: Boolean = false
    private var modoAsignadas: Boolean = false

    private fun aplicarEstadoBotones(selected: String) {
        val normalBg = resources.getDrawable(R.drawable.btn_outline_black, requireContext().theme)
        binding.btnPendientes.background = normalBg
        binding.btnAsignadas.background = normalBg
        binding.btnHistorial.background = normalBg

        binding.btnPendientes.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))
        binding.btnAsignadas.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))
        binding.btnHistorial.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))

        when (selected) {
            "pendientes" -> {
                binding.btnPendientes.backgroundTintList = null
                binding.btnPendientes.setBackgroundResource(R.drawable.btn_tab_selected)
                binding.btnPendientes.setTextColor(resources.getColor(android.R.color.white, requireContext().theme))
                binding.btnAsignadas.backgroundTintList = null
                binding.btnAsignadas.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))
                binding.btnHistorial.backgroundTintList = null
                binding.btnHistorial.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))
            }
            "asignadas" -> {
                binding.btnAsignadas.backgroundTintList = null
                binding.btnAsignadas.setBackgroundResource(R.drawable.btn_tab_selected)
                binding.btnAsignadas.setTextColor(resources.getColor(android.R.color.white, requireContext().theme))
                binding.btnPendientes.backgroundTintList = null
                binding.btnPendientes.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))
                binding.btnHistorial.backgroundTintList = null
                binding.btnHistorial.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))
            }
            "historial" -> {
                binding.btnHistorial.backgroundTintList = null
                binding.btnHistorial.setBackgroundResource(R.drawable.btn_tab_selected)
                binding.btnHistorial.setTextColor(resources.getColor(android.R.color.white, requireContext().theme))
                binding.btnPendientes.backgroundTintList = null
                binding.btnPendientes.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))
                binding.btnAsignadas.backgroundTintList = null
                binding.btnAsignadas.setTextColor(resources.getColor(R.color.texto_principal, requireContext().theme))
            }
        }
        if (selected != "pendientes") {
            binding.btnPendientes.backgroundTintList = null
            binding.btnPendientes.setBackgroundResource(R.drawable.btn_outline_black)
        }
        if (selected != "asignadas") {
            binding.btnAsignadas.backgroundTintList = null
            binding.btnAsignadas.setBackgroundResource(R.drawable.btn_outline_black)
        }
        if (selected != "historial") {
            binding.btnHistorial.backgroundTintList = null
            binding.btnHistorial.setBackgroundResource(R.drawable.btn_outline_black)
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
            adapter.updateItems(emptyList())
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
                // Pendientes: tareas asignadas a mi en estado pendiente, o creadas por mi pendientes de confirmación
                tareasDelGrupo.filter {
                    (it.asignadoA == uid && it.estado == "pendiente") ||
                    (it.creadoPor == uid && it.estado == "pendiente_confirmacion")
                }
            }
        }
        adapter.updateItems(filtrado)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tareasJob?.cancel()
        _binding = null
    }
}

