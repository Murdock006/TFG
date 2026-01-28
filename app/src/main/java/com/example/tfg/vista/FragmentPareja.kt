package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.databinding.FragmentParejaBinding
import com.example.tfg.modelo.Usuario
import com.example.tfg.service.LocalizadorServicios
import android.util.Log
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import com.example.tfg.viewmodel.ParejaViewModel
import com.example.tfg.data.inmemory.GrupoRepositorioInMemory
import com.example.tfg.modelo.Grupo
import com.google.firebase.Timestamp
import java.util.UUID
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FragmentPareja : Fragment() {

    private lateinit var binding: FragmentParejaBinding
    private val parejaVM: ParejaViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentParejaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        binding.rvMiembros.layoutManager = LinearLayoutManager(requireContext())
        val adapter = MiembrosAdapter()
        binding.rvMiembros.adapter = adapter

        // Observar usuarios y grupo para mapear uid -> nombre
        var usuariosCache = emptyList<Usuario>()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                        usuariosCache = lista
                        // actualizar miembros si hay grupo
                        val g = parejaVM.grupo.value
                        if (g != null) {
                            val items = g.miembros.map { (uid, rol) ->
                                val nombre = usuariosCache.find { it.id == uid }?.nombre ?: uid
                                nombre to rol
                            }
                            adapter.setItems(items)
                        }
                    }
                }
                launch {
                    parejaVM.grupo.collect { g ->
                        if (g != null) {
                            // actualizar barra inferior
                            binding.bottomBar.visibility = View.VISIBLE
                            binding.tvGroupName.text = g.nombre
                            binding.tvGroupMembers.text = "Miembros: ${g.miembros.size}"
                        } else {
                            // ocultar barra si no hay grupo
                            binding.bottomBar.visibility = View.GONE
                            binding.tvGroupName.text = "-"
                            binding.tvGroupMembers.text = "Miembros: 0"
                            adapter.setItems(emptyList())
                        }
                    }
                }
            }
        }

        binding.btnCrearGrupo.setOnClickListener {
            // pedir nombre y crear grupo
            val et = EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle(getString(com.example.tfg.R.string.crear_grupo_title))
                .setView(et)
                .setPositiveButton(getString(com.example.tfg.R.string.crear)) { _, _ ->
                    val nombre = et.text.toString().trim().ifEmpty { "Mi grupo" }
                    val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setPositiveButton
                    binding.btnCrearGrupo.isEnabled = false
                    parejaVM.crearGrupo(nombre, usuarioId) { res ->
                        binding.btnCrearGrupo.isEnabled = true
                        if (res.isSuccess) {
                            Toast.makeText(requireContext(), "Grupo creado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null)
                .show()
        }

        binding.btnGenerarInvitacion.setOnClickListener {
            val grupo = parejaVM.grupo.value
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setOnClickListener
            if (grupo == null) { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            parejaVM.crearInvitacion(grupo.id, usuarioId, null) { invRes ->
                if (invRes.isSuccess) {
                    val codigo = invRes.getOrNull()
                    binding.tvCodigo.text = getString(com.example.tfg.R.string.grupo_creado_codigo, codigo ?: grupo.id)
                    Toast.makeText(requireContext(), getString(com.example.tfg.R.string.grupo_creado_codigo, codigo ?: grupo.id), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), invRes.exceptionOrNull()?.message ?: "Error creando invitación", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnAceptarInvitacion.setOnClickListener {
            val codigo = binding.etCodigoAceptar.text.toString().trim()
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setOnClickListener
            if (codigo.isEmpty()) { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.introduce_codigo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            parejaVM.aceptarInvitacionPorCodigo(codigo, usuarioId) { res ->
                if (res.isSuccess) {
                    Toast.makeText(requireContext(), "Invitación aceptada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "No se pudo aceptar la invitación: código no encontrado o error.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Abrir detalles del grupo
        binding.btnAbrirGrupo.setOnClickListener {
            // abrir diálogo con info del grupo y lista de miembros (usar coroutine para llamar a .first())
            val g = parejaVM.grupo.value
            if (g == null) { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            viewLifecycleOwner.lifecycleScope.launch {
                val usuariosCacheLocal = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<Usuario>() }
                val miembrosTexto = g.miembros.map { (uid, rol) ->
                    val nombre = usuariosCacheLocal.find { it.id == uid }?.nombre ?: uid
                    "- $nombre ($rol)"
                }.joinToString("\n")
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(com.example.tfg.R.string.miembros_del_grupo))
                    .setMessage("Nombre: ${g.nombre}\nMiembros (${g.miembros.size}):\n$miembrosTexto")
                    .setPositiveButton(getString(com.example.tfg.R.string.aceptar), null)
                    .show()
            }
        }

        // Salir del grupo
        binding.btnSalirGrupo.setOnClickListener {
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Salir del grupo")
                .setMessage("¿Estás seguro de que quieres salir del grupo?")
                .setPositiveButton(getString(com.example.tfg.R.string.aceptar)) { _, _ ->
                    parejaVM.salirGrupo(usuarioId) { res ->
                        if (res.isSuccess) {
                            Toast.makeText(requireContext(), "Has salido del grupo", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error saliendo del grupo", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null)
                .show()
        }

    }

    private inner class MiembrosAdapter : RecyclerView.Adapter<MiembrosAdapter.VH>() {
        private var items: List<Pair<String,String>> = emptyList()
        fun setItems(list: List<Pair<String,String>>) { items = list; notifyDataSetChanged() }
        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context)
            tv.setPadding(16,16,16,16)
            return VH(tv)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (nombre, rol) = items[position]
            holder.tv.text = "$nombre — $rol"
        }
        override fun getItemCount(): Int = items.size
    }
}
