package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.databinding.FragmentParejaBinding
import com.example.tfg.modelo.Usuario
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.viewmodel.ParejaViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FragmentPareja : Fragment() {

    private lateinit var binding: FragmentParejaBinding
    private val parejaVM: ParejaViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentParejaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtener usuario actual desde el Localizador de servicios
        val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id

        // Setup RecyclerView
        binding.rvMiembros.layoutManager = LinearLayoutManager(requireContext())
        val adapter = MiembrosAdapter()
        binding.rvMiembros.adapter = adapter

        // Observar usuarios y grupo para mapear uid -> nombre
        var usuariosCache: List<Usuario> = emptyList()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LocalizadorServicios.repositorioAuth.observarUsuarios().collect { list ->
                    usuariosCache = list
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collect { g ->
                    if (g == null) {
                        binding.tvCodigo.text = "No perteneces a ningún grupo"
                        adapter.setItems(emptyList())
                    } else {
                        binding.tvCodigo.text = "Grupo: ${g.nombre} (id=${g.id})"
                        val miembrosList = g.miembros.map { (uid, rol) ->
                            val nombre = usuariosCache.find { it.id == uid }?.nombre ?: uid
                            Pair(nombre, rol)
                        }
                        adapter.setItems(miembrosList)
                    }
                }
            }
        }

        binding.btnCrearGrupo.setOnClickListener {
            if (usuarioId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Inicia sesión para crear un grupo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parejaVM.crearGrupo("Mi pareja", usuarioId)
            Toast.makeText(requireContext(), "Creando grupo...", Toast.LENGTH_SHORT).show()
        }

        binding.btnGenerarInvitacion.setOnClickListener {
            val grupoId = parejaVM.grupo.value?.id
            if (grupoId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Primero crea o únete a un grupo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (usuarioId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Inicia sesión para generar invitaciones", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parejaVM.crearInvitacion(grupoId, usuarioId) { res ->
                if (res.isSuccess) {
                    val codigo = res.getOrNull()
                    binding.tvCodigo.text = "Código: ${codigo ?: "-"}"
                } else {
                    Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error creando invitación", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnAceptarInvitacion.setOnClickListener {
            val codigo = binding.etCodigoAceptar.text.toString().trim()
            if (codigo.isEmpty()) return@setOnClickListener
            if (usuarioId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Inicia sesión para aceptar invitaciones", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parejaVM.aceptarInvitacion(codigo, usuarioId) { res ->
                if (res.isSuccess) {
                    Toast.makeText(requireContext(), "Invitación aceptada", Toast.LENGTH_SHORT).show()
                    // navegar a principal
                    findNavController().navigate(com.example.tfg.R.id.fragment_PgPrincipal)
                } else {
                    Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error aceptando invitación", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class MiembrosAdapter : RecyclerView.Adapter<MiembrosAdapter.VH>() {
        private var items: List<Pair<String,String>> = emptyList()
        fun setItems(list: List<Pair<String,String>>) { items = list; notifyDataSetChanged() }
        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context)
            val pad = (8 * resources.displayMetrics.density).toInt()
            tv.setPadding(pad,pad,pad,pad)
            return VH(tv)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (nombre, rol) = items[position]
            holder.tv.text = "$nombre — $rol"
        }
        override fun getItemCount(): Int = items.size
    }
}
