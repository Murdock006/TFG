package com.example.tfg.vista

import android.app.DatePickerDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.R
import com.example.tfg.modelo.Tarea
import com.example.tfg.modelo.Usuario
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.viewmodel.ParejaViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.firebase.Timestamp
import java.util.Calendar
import androidx.navigation.fragment.findNavController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import androidx.cardview.widget.CardView
import android.graphics.Color

// Adaptador reutilizable para la lista "Tareas recientes" usando ListAdapter + DiffUtil
class TareasHomeAdapter(
    private val fragment: Fragment,
    private val parejaVM: ParejaViewModel,
    private val scope: CoroutineScope
) : ListAdapter<Tarea, TareasHomeAdapter.VH>(TareaDiffCallback()) {

    private val TAG = "TareasHomeAdapter"
    private var usuarios: List<Usuario> = emptyList()

    fun updateItems(list: List<Tarea>) { submitList(list) }
    fun updateUsuarios(list: List<Usuario>) {
        Log.d(TAG, "updateUsuarios: recibidos ${list.size} usuarios")
        // calcular diffs para rebind selectivo: si algún usuario cambió, solo notificar posiciones afectadas
        val viejoMap = usuarios.associateBy { it.id }
        val nuevoMap = list.associateBy { it.id }
        usuarios = list
        // si no hay lista de items aún, hacemos refresh completo
        val current = currentList
        if (current.isEmpty()) return
        val posicionesAfectadas = mutableSetOf<Int>()
        for ((idx, tarea) in current.withIndex()) {
            val creadorId = tarea.creadoPor
            val asignadoId = tarea.asignadoA
            var marcado = false
            if (creadorId != null) {
                val old = viejoMap[creadorId]
                val nw = nuevoMap[creadorId]
                if (old != nw) marcado = true
            }
            if (!marcado && asignadoId != null) {
                val old = viejoMap[asignadoId]
                val nw = nuevoMap[asignadoId]
                if (old != nw) marcado = true
            }
            if (marcado) posicionesAfectadas.add(idx)
        }
        // notificar solo las posiciones afectadas para rebind parcial
        for (p in posicionesAfectadas) notifyItemChanged(p)
    }

    class TareaDiffCallback : DiffUtil.ItemCallback<Tarea>() {
        override fun areItemsTheSame(oldItem: Tarea, newItem: Tarea): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Tarea, newItem: Tarea): Boolean = oldItem == newItem
    }

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val cardRoot: CardView? = root.findViewById(R.id.cardRoot)
        val tvTitulo: TextView = root.findViewById(R.id.tvTituloTarea)
        val tvMeta: TextView = root.findViewById(R.id.tvMetaTarea)
        val tvAsignado: TextView = root.findViewById(R.id.tvAsignado)
        val btnAccion: Button = root.findViewById(R.id.btnAccionTarea)
        val vIndicator: View? = root.findViewById(R.id.vIndicator)
        val tvDif: TextView = root.findViewById(R.id.tvDificultad)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tarea, parent, false)
        return VH(v)
    }

    private suspend fun obtenerNombreUsuario(uid: String?): String {
        if (uid.isNullOrBlank()) return "Desconocido"
        // primero intentar resolver desde cache local
        val u = usuarios.find { it.id == uid }
        if (u != null) return if (u.nombre.isNotBlank()) u.nombre else (if (u.email.isNotBlank()) u.email else uid)
        // fallback: leer documento concreto desde Firestore
        return try {
            val doc = Firebase.firestore.collection("usuarios").document(uid).get().await()
            val nombre = doc.getString("nombre")
            val email = doc.getString("email")
            val res = nombre ?: email ?: uid
            Log.d(TAG, "obtenerNombreUsuario: obtenido de Firestore para $uid -> $res")
            res
        } catch (e: Exception) {
            Log.w(TAG, "obtenerNombreUsuario: fallo al leer $uid: ${e.message}")
            uid
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = getItem(position)
        // RESET: evitar artefactos por reciclado
        holder.btnAccion.visibility = View.GONE
        holder.btnAccion.text = ""
        holder.btnAccion.isEnabled = false
        holder.tvAsignado.visibility = View.GONE
        holder.tvAsignado.text = ""

        // mapear estado a texto legible
        val estadoLegible = when (t.estado.lowercase()) {
            "pendiente" -> "pendiente"
            "completada" -> "completada"
            "confirmada" -> "confirmada"
            "en_disputa", "disputa" -> "en disputa"
            else -> t.estado
        }

        holder.tvTitulo.text = t.titulo
        // mostrar estado junto a puntos y dificultad
        val difTxt = when (t.dificultad) { 1 -> "Fácil"; 2 -> "Media"; else -> "Difícil" }
        holder.tvMeta.text = "${t.puntos} pts · $difTxt · ${estadoLegible.replaceFirstChar { it.uppercase() }}"
        holder.tvDif.text = difTxt
        when (t.dificultad) {
            1 -> holder.vIndicator?.setBackgroundColor(Color.parseColor("#A5D6A7"))
            2 -> holder.vIndicator?.setBackgroundColor(Color.parseColor("#FFF59D"))
            else -> holder.vIndicator?.setBackgroundColor(Color.parseColor("#FFCDD2"))
        }

        val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""

        // Establecer color de la tarjeta según rol/estado
        try {
            val targetColor = when {
                !t.asignadoA.isNullOrBlank() && usuarioId == t.asignadoA && t.estado == "pendiente" -> Color.parseColor("#FFF9C4") // light yellow
                t.estado == "completada" && usuarioId == t.creadoPor -> Color.parseColor("#FFE0B2") // light orange
                t.estado == "confirmada" -> Color.parseColor("#E8F5E9")
                else -> Color.WHITE
            }
            // aplicar color objetivo directamente (sin animación) para reducir carga UI
            holder.cardRoot?.setCardBackgroundColor(targetColor)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo aplicar color a card: ${e.message}")
        }

        // Mostrar quien asignó / a quién está asignada (usar nombre o email si está en usuarios)
        holder.tvAsignado.visibility = View.GONE
        if (!t.asignadoA.isNullOrBlank()) {
            if (usuarioId == t.asignadoA) {
                // Soy el asignado -> mostrar quién me asignó
                holder.tvAsignado.visibility = View.VISIBLE
                holder.tvAsignado.text = fragment.getString(R.string.cargando)
                scope.launch {
                    val nombreCreador = obtenerNombreUsuario(t.creadoPor)
                    holder.tvAsignado.text = fragment.getString(R.string.asignado_por, nombreCreador)
                }
            } else {
                holder.tvAsignado.visibility = View.VISIBLE
                holder.tvAsignado.text = fragment.getString(R.string.cargando)
                scope.launch {
                    val nombreAsignado = obtenerNombreUsuario(t.asignadoA)
                    holder.tvAsignado.text = fragment.getString(R.string.asignado_a, nombreAsignado)
                }
            }
        }

        holder.btnAccion.setOnClickListener(null)

        // Mostrar botón Asignar solo si soy el creador y aún no está asignada
        if (!usuarioId.isBlank() && usuarioId == t.creadoPor && t.asignadoA.isNullOrBlank()) {
            holder.btnAccion.visibility = View.VISIBLE
            holder.btnAccion.text = fragment.getString(R.string.asignar)
            holder.btnAccion.isEnabled = true
            holder.btnAccion.setOnClickListener {
                // seleccionar miembro del grupo
                scope.launch {
                    val grupo = parejaVM.grupo.value
                    val usuariosCache = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (e: Exception) { emptyList<Usuario>() }
                    val opcionesMiembros = mutableListOf<Pair<String,String>>()
                    if (grupo != null) {
                        grupo.miembros.keys.forEach { uid -> val nombre = usuariosCache.find { it.id == uid }?.nombre ?: uid; opcionesMiembros.add(Pair(nombre, uid)) }
                    }
                    if (opcionesMiembros.isEmpty()) {
                        android.widget.Toast.makeText(fragment.requireContext(), fragment.getString(R.string.no_hay_miembros), android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val nombres = opcionesMiembros.map { it.first }.toTypedArray()
                    androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                        .setTitle(fragment.getString(R.string.selecciona_miembro))
                        .setItems(nombres) { _, mIdx ->
                            // Mostrar DatePicker para elegir fecha programada
                            val hoy = Calendar.getInstance()
                            val dp = DatePickerDialog(fragment.requireContext(), { _, year, month, dayOfMonth ->
                                val cal = Calendar.getInstance()
                                cal.set(year, month, dayOfMonth, 12, 0, 0)
                                val ts = Timestamp(cal.time)
                                scope.launch {
                                    val elegidoUid = opcionesMiembros[mIdx].second
                                    val nueva = t.copy(asignadoA = elegidoUid, grupoId = parejaVM.grupo.value?.id, fechaProgramada = ts)
                                    val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                    if (res.isSuccess) android.widget.Toast.makeText(fragment.requireContext(), fragment.getString(R.string.tarea_asignada_ok, opcionesMiembros[mIdx].first), android.widget.Toast.LENGTH_SHORT).show() else android.widget.Toast.makeText(fragment.requireContext(), res.exceptionOrNull()?.message ?: "Error", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH))
                            dp.setTitle(fragment.getString(R.string.selecciona_fecha))
                            dp.show()
                        }
                        .setNegativeButton(fragment.getString(R.string.cancelar), null)
                        .show()
                }
            }
        } else if (!usuarioId.isBlank() && usuarioId == t.asignadoA) {
            // soy el asignado -> puedo completar
            holder.btnAccion.visibility = View.VISIBLE
            holder.btnAccion.text = fragment.getString(R.string.completar)
            holder.btnAccion.isEnabled = true
            holder.btnAccion.setOnClickListener {
                scope.launch {
                    val nueva = t.copy(estado = if (t.requiereConfirmacion) "completada" else "confirmada")
                    val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                    if (res.isSuccess) android.widget.Toast.makeText(fragment.requireContext(), fragment.getString(R.string.tarea_completada_ok), android.widget.Toast.LENGTH_SHORT).show() else android.widget.Toast.makeText(fragment.requireContext(), res.exceptionOrNull()?.message ?: "Error", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            holder.btnAccion.visibility = View.GONE
        }

        holder.root.setOnClickListener {
            try {
                val bundle = android.os.Bundle().apply { putString("taskId", t.id) }
                fragment.findNavController().navigate(R.id.fragment_Tareas, bundle)
            } catch (_: Exception) {}
        }
    }

}
