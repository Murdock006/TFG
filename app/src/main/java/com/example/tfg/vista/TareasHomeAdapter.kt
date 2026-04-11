package com.example.tfg.vista

import android.app.DatePickerDialog
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.R
import com.example.tfg.modelo.Tarea
import com.example.tfg.modelo.Usuario
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.viewmodel.ParejaViewModel
import com.example.tfg.viewmodel.TareasViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

// Adaptador reutilizable para la lista "Tareas recientes" usando ListAdapter + DiffUtil
class TareasHomeAdapter(
    private val fragment: Fragment,
    private val parejaVM: ParejaViewModel,
    private val tareasVM: TareasViewModel,
    private val scope: CoroutineScope
) : ListAdapter<Tarea, TareasHomeAdapter.VH>(TareaDiffCallback()) {

    private val TAG = "TareasHomeAdapter"
    private var usuarios: List<Usuario> = emptyList()

    fun updateItems(list: List<Tarea>) { submitList(null); submitList(list) }
    fun updateUsuarios(list: List<Usuario>) { usuarios = list; notifyDataSetChanged() }

    class TareaDiffCallback : DiffUtil.ItemCallback<Tarea>() {
        override fun areItemsTheSame(oldItem: Tarea, newItem: Tarea): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Tarea, newItem: Tarea): Boolean = oldItem == newItem
    }

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val tvTitulo: TextView = root.findViewById(R.id.tvTituloTarea)
        val tvDificultad: TextView? = root.findViewById(R.id.tvDificultad)
        val tvMeta: TextView = root.findViewById(R.id.tvMetaTarea)
        val tvAsignado: TextView = root.findViewById(R.id.tvAsignado)
        val btnAccion: Button = root.findViewById(R.id.btnAccionTarea)
        val vIndicator: View? = root.findViewById(R.id.vIndicator)
    }

    private suspend fun obtenerNombreUsuario(uid: String?): String {
        if (uid.isNullOrBlank()) return "Desconocido"
        val u = usuarios.find { it.id == uid }
        if (u != null) return if (u.nombre.isNotBlank()) u.nombre else (if (u.email.isNotBlank()) u.email else uid)
        return try {
            val doc = Firebase.firestore.collection("usuarios").document(uid).get().await()
            doc.getString("nombre") ?: doc.getString("email") ?: uid
        } catch (e: Exception) {
            Log.w(TAG, "obtenerNombreUsuario fallo: ${e.message}")
            uid
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tarea, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = getItem(position)
        holder.btnAccion.visibility = View.GONE
        holder.btnAccion.isEnabled = false
        holder.tvAsignado.visibility = View.GONE

        val estadoLegible = when (t.estado.lowercase()) {
            "pendiente" -> "pendiente"
            "pendiente_confirmacion" -> "pendiente de confirmación"
            "completada" -> "completada"
            "confirmada" -> "confirmada"
            "en_disputa", "disputa" -> "en disputa"
            else -> t.estado
        }

        val difTxt = when (t.dificultad) { 1 -> "Fácil"; 2 -> "Media"; else -> "Difícil" }
        holder.tvTitulo.text = t.titulo
        holder.tvDificultad?.text = difTxt
        holder.tvMeta.text = "${t.puntos} pts · ${estadoLegible.replaceFirstChar { it.uppercase() }}"

        when (t.dificultad) {
            1 -> holder.vIndicator?.setBackgroundColor(Color.parseColor("#A5D6A7"))
            2 -> holder.vIndicator?.setBackgroundColor(Color.parseColor("#FFF59D"))
            else -> holder.vIndicator?.setBackgroundColor(Color.parseColor("#FFCDD2"))
        }

        val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""

        if (!t.asignadoA.isNullOrBlank()) {
            holder.tvAsignado.visibility = View.VISIBLE
            // Intentar resolver el nombre de forma síncrona desde la caché primero
            val cacheCreador = if (usuarioId == t.asignadoA) usuarios.find { it.id == t.creadoPor } else null
            val cacheAsignado = if (usuarioId != t.asignadoA) usuarios.find { it.id == t.asignadoA } else null

            fun nombreDesdeCache(u: com.example.tfg.modelo.Usuario?): String? =
                u?.let { if (it.nombre.isNotBlank()) it.nombre else if (it.email.isNotBlank()) it.email else null }

            if (usuarioId == t.asignadoA && cacheCreador != null) {
                holder.tvAsignado.text = "Te la asignó: ${nombreDesdeCache(cacheCreador) ?: cacheCreador.id}"
            } else if (usuarioId != t.asignadoA && cacheAsignado != null) {
                holder.tvAsignado.text = "Asignado a: ${nombreDesdeCache(cacheAsignado) ?: cacheAsignado.id}"
            } else {
                // Solo ir a Firestore si no está en caché — guardar posición para evitar race condition
                holder.tvAsignado.text = "Cargando..."
                val posicionActual = holder.bindingAdapterPosition
                scope.launch {
                    val nombre = if (usuarioId == t.asignadoA) {
                        "Te la asignó: ${obtenerNombreUsuario(t.creadoPor)}"
                    } else {
                        "Asignado a: ${obtenerNombreUsuario(t.asignadoA)}"
                    }
                    if (holder.bindingAdapterPosition == posicionActual) {
                        holder.tvAsignado.text = nombre
                    }
                }
            }
        }

        // Diferentes acciones según rol y estado
        when (t.estado) {
            "pendiente" -> {
                // Si soy el asignado -> completar
                if (!usuarioId.isBlank() && usuarioId == t.asignadoA) {
                    holder.btnAccion.visibility = View.VISIBLE
                    holder.btnAccion.isEnabled = true
                    holder.btnAccion.text = "Completar"
                    holder.btnAccion.setOnClickListener {
                        holder.btnAccion.isEnabled = false
                        tareasVM.marcarCompletada(t.id, usuarioId)
                        // El observer del Fragment maneja el resultado y muestra Toast
                    }
                } else if (!usuarioId.isBlank() && usuarioId == t.creadoPor && t.asignadoA.isNullOrBlank()) {
                    // Si soy el creador Y la tarea AÚN NO tiene asignado → permitir asignar
                    holder.btnAccion.visibility = View.VISIBLE
                    holder.btnAccion.isEnabled = true
                    holder.btnAccion.text = "Asignar"
                    holder.btnAccion.setOnClickListener {
                        // abrir selector de miembros
                        scope.launch {
                            val grupo = parejaVM.grupo.value
                            val usuariosList = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<Usuario>() }
                            val opciones = mutableListOf<Pair<String,String>>()
                            if (grupo != null) {
                                val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                                grupo.miembros.keys.forEach { uid ->
                                    if (!uidActual.isNullOrBlank() && uid == uidActual) return@forEach
                                    val u2 = usuariosList.find { it.id == uid }
                                    val display = when {
                                        u2 != null && u2.nombre.isNotBlank() -> if (u2.email.isNotBlank()) "${u2.nombre} (${u2.email})" else u2.nombre
                                        u2 != null && u2.email.isNotBlank() -> u2.email
                                        else -> "Usuario"
                                    }
                                    opciones.add(Pair(display, uid))
                                }
                            }
                            if (opciones.isEmpty()) android.widget.Toast.makeText(fragment.requireContext(), fragment.getString(R.string.no_hay_miembros), android.widget.Toast.LENGTH_SHORT).show() else {
                                val names = opciones.map { it.first }.toTypedArray()
                                androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext()).setTitle("Selecciona miembro").setItems(names) { _, idx ->
                                    scope.launch {
                                        val elegido = opciones[idx].second
                                        if (!usuarioId.isBlank() && elegido == usuarioId) {
                                            android.widget.Toast.makeText(fragment.requireContext(), "No podés autoasignarte tareas", android.widget.Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        val nueva = t.copy(asignadoA = elegido, grupoId = parejaVM.grupo.value?.id)
                                        val res2 = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                        if (res2.isSuccess) {
                                            android.widget.Toast.makeText(fragment.requireContext(), "Tarea asignada", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(fragment.requireContext(), res2.exceptionOrNull()?.message ?: "Error", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.setNegativeButton("Cancelar", null).show()
                            }
                        }
                    }
                } else {
                    // no mostrar acción para otros roles en la vista principal
                    holder.btnAccion.visibility = View.GONE
                }
            }
            "pendiente_confirmacion" -> {
                // El asignado no puede volver a completar; el creador puede confirmar o reclamar
                if (!usuarioId.isBlank() && usuarioId == t.creadoPor) {
                    holder.btnAccion.visibility = View.VISIBLE
                    holder.btnAccion.isEnabled = true
                    holder.btnAccion.text = "Acciones"
                    holder.btnAccion.setOnClickListener {
                        val opciones = arrayOf("Confirmar","Reclamar")
                        androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                            .setTitle("Elige acción")
                            .setItems(opciones) { _, idx ->
                                when (idx) {
                                    0 -> {
                                        holder.btnAccion.isEnabled = false
                                        tareasVM.confirmarTarea(t.id, usuarioId)
                                        // El observer del Fragment maneja el resultado y muestra Toast
                                    }
                                    1 -> android.widget.Toast.makeText(fragment.requireContext(), "Abre la tarea y usa 'Reclamar' para adjuntar evidencia", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }.setNegativeButton("Cancelar", null).show()
                    }
                }
            }
            "completada", "confirmada", "reclamada" -> {
                // estados finales: ocultar acciones
                holder.btnAccion.visibility = View.GONE
            }
            else -> { holder.btnAccion.visibility = View.GONE }
        }

        holder.root.setOnClickListener {
            try {
                val bundle = android.os.Bundle().apply { putString("taskId", t.id) }
                fragment.findNavController().navigate(R.id.fragment_Tareas, bundle)
            } catch (_: Exception) { }
        }
    }

    override fun getItemCount(): Int = currentList.size
}
