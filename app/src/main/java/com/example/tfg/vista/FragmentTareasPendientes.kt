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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FragmentTareasPendientes : Fragment() {

    private var _binding: FragmentTareasPendientesBinding? = null
    private val binding get() = _binding!!
    private val parejaVM: com.example.tfg.viewmodel.ParejaViewModel by activityViewModels()

    private val adapter by lazy { TareasHomeAdapter(this, parejaVM, viewLifecycleOwner.lifecycleScope) }

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
        binding.btnHistorial.setOnClickListener { mostrarHistorial() }

        // observar usuarios para mostrar nombres correctamente
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                    adapter.updateUsuarios(lista)
                }
            } catch (e: Exception) { /* ignore */ }
        }

        // observar tareas y actualizar vista según modo actual
        viewLifecycleOwner.lifecycleScope.launch {
            LocalizadorServicios.repositorioTarea.observarTareas().collect { list ->
                // inicialmente mostrar pendientes
                actualizarListado(list)
            }
        }
    }

    private var modoHistorial: Boolean = false

    private fun mostrarPendientes() {
        modoHistorial = false
        // cambiar estado visual de botones
        binding.btnPendientes.isEnabled = false
        binding.btnHistorial.isEnabled = true
        // forzar reconsulta (obs ya activa) -> simplemente filtrar la última lista
        viewLifecycleOwner.lifecycleScope.launch {
            val list = LocalizadorServicios.repositorioTarea.obtenerTareas().getOrNull() ?: emptyList()
            actualizarListado(list)
        }
    }

    private fun mostrarHistorial() {
        modoHistorial = true
        binding.btnPendientes.isEnabled = true
        binding.btnHistorial.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val list = LocalizadorServicios.repositorioTarea.obtenerTareas().getOrNull() ?: emptyList()
            actualizarListado(list)
        }
    }

    private fun actualizarListado(list: List<Tarea>) {
        val uid = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        val filtrado = if (modoHistorial) {
            // historial: tareas completadas, confirmadas o reclamadas donde soy creador o asignado
            list.filter { (it.creadoPor == uid || it.asignadoA == uid) && (it.estado == "completada" || it.estado == "confirmada" || it.estado == "reclamada") }
        } else {
            // pendientes: tareas asignadas a mi y en estado pendiente
            list.filter { it.asignadoA == uid && it.estado == "pendiente" }
        }
        adapter.updateItems(filtrado)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
