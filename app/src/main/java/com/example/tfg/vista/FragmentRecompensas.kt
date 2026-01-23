package com.example.tfg.vista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.databinding.FragmentRecompensasListBinding
import com.example.tfg.modelo.Recompensa
import com.example.tfg.modelo.Notificacion
import com.example.tfg.repositorio.RepositorioRecompensas
import com.example.tfg.repositorio.RepositorioNotificaciones
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.viewmodel.ParejaViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

class FragmentRecompensas : Fragment() {

    private lateinit var binding: FragmentRecompensasListBinding
    private val repo = RepositorioRecompensas()
    private val repoNot = RepositorioNotificaciones()
    private val parejaVM: ParejaViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRecompensasListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvRecompensas.layoutManager = LinearLayoutManager(requireContext())
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            private var items: List<Recompensa> = emptyList()
            fun setItems(list: List<Recompensa>) { items = list; notifyDataSetChanged() }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val context = parent.context
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val pad = (12 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                val tv = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    textSize = 16f
                }
                val btn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = "Canjear"
                }
                container.addView(tv)
                container.addView(btn)
                return object : RecyclerView.ViewHolder(container) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val r = items[position]
                val container = holder.itemView as LinearLayout
                val tv = container.getChildAt(0) as TextView
                val btn = container.getChildAt(1) as Button
                tv.text = "${r.titulo} - ${r.coste} pts"

                // Habilitar botón según puntos actuales del usuario
                val usuarioPts = LocalizadorServicios.repositorioAuth.usuarioActual()?.puntos ?: 0
                btn.isEnabled = usuarioPts >= r.coste

                btn.setOnClickListener {
                    val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
                    if (usuarioId.isBlank()) {
                        Toast.makeText(requireContext(), "Inicia sesión para canjear", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    lifecycleScope.launch {
                        val res = repo.canjearRecompensa(r.id, usuarioId)
                        if (res.isSuccess) {
                            Toast.makeText(requireContext(), "Canje realizado", Toast.LENGTH_SHORT).show()
                            // enviar notificación a los demás miembros del grupo (si existe)
                            val grupo = parejaVM.grupo.value
                            if (grupo != null) {
                                val nombreUsuario = LocalizadorServicios.repositorioAuth.usuarioActual()?.nombre ?: "Alguien"
                                grupo.miembros.keys.filter { it != usuarioId }.forEach { miembroUid ->
                                    val contenido = mapOf("tipo" to "canje", "recompensaId" to r.id, "usuario" to usuarioId, "texto" to "${nombreUsuario} ha canjeado ${r.titulo}")
                                    val not = Notificacion(id = "", tipo = "canje", contenido = contenido, destinatario = miembroUid, visto = false, fecha = Timestamp.now())
                                    repoNot.enviarNotificacion(not)
                                }
                            }
                            // refrescar lista de recompensas y UI
                            val lista = repo.listarRecompensas(null).getOrNull() ?: emptyList()
                            setItems(lista)
                        } else {
                            Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // click en el item abre detalles (placeholder)
                container.setOnClickListener {
                    Toast.makeText(requireContext(), "${r.titulo}: ${r.descripcion ?: ""}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun getItemCount(): Int = items.size
        }
        binding.rvRecompensas.adapter = adapter

        lifecycleScope.launch {
            val lista = repo.listarRecompensas(null).getOrNull() ?: emptyList()
            adapter.setItems(lista)
        }

        // Observador: si el usuario cambia, refrescar lista de recompensas para habilitar botones
        lifecycleScope.launch {
            LocalizadorServicios.repositorioAuth.observarUsuarios().collect { usuarios ->
                val lista = repo.listarRecompensas(null).getOrNull() ?: emptyList()
                adapter.setItems(lista)
            }
        }
    }
}
