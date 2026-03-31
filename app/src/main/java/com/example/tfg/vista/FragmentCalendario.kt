package com.example.tfg.vista

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.R
import com.example.tfg.databinding.FragmentCalendarioBinding
import com.example.tfg.modelo.Tarea
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.service.NotificationScheduler
import com.example.tfg.viewmodel.ParejaViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class FragmentCalendario : Fragment() {

    private var binding: FragmentCalendarioBinding? = null
    private val parejaVM: ParejaViewModel by activityViewModels()
    private var fechaSeleccionada: Calendar = Calendar.getInstance()
    private var tareasJob: Job? = null
    private var grupoIdActual: String? = null
    private var todasLasTareas: List<Tarea> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCalendarioBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        b.rvTareasDia.layoutManager = LinearLayoutManager(requireContext())
        val adapter = TareasCalendarioAdapter()
        b.rvTareasDia.adapter = adapter

        actualizarCabecera(b)

        b.btnPrevWeek.setOnClickListener {
            fechaSeleccionada.add(Calendar.DAY_OF_MONTH, -1)
            actualizarCabecera(b)
            filtrarYMostrar(adapter, b)
        }
        b.btnNextWeek.setOnClickListener {
            fechaSeleccionada.add(Calendar.DAY_OF_MONTH, 1)
            actualizarCabecera(b)
            filtrarYMostrar(adapter, b)
        }
        b.btnElegirDia.setOnClickListener {
            val hoy = fechaSeleccionada
            DatePickerDialog(requireContext(), { _, y, m, d ->
                fechaSeleccionada.set(y, m, d, 12, 0, 0)
                actualizarCabecera(b)
                filtrarYMostrar(adapter, b)
            }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Observar grupo y suscribir tareas
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collectLatest { grupo ->
                    val nuevoId = grupo?.id
                    if (nuevoId != grupoIdActual) {
                        grupoIdActual = nuevoId
                        tareasJob?.cancel()
                        tareasJob = null
                        todasLasTareas = emptyList()
                        adapter.setItems(emptyList())
                        actualizarResumen(b, emptyList())
                    }
                    if (grupo != null && (tareasJob == null || tareasJob?.isActive == false)) {
                        tareasJob = viewLifecycleOwner.lifecycleScope.launch {
                            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                LocalizadorServicios.repositorioTarea.observarTareas().collect { lista ->
                                    todasLasTareas = lista.filter { it.grupoId == grupoIdActual }
                                    filtrarYMostrar(adapter, b)
                                }
                            }
                        }
                    }
                    if (grupo == null) {
                        b.tvFechaSeleccionada.text = "Sin grupo activo"
                        b.tvResumenDia.text = ""
                        adapter.setItems(emptyList())
                    }
                }
            }
        }
    }

    private fun filtrarYMostrar(adapter: TareasCalendarioAdapter, b: FragmentCalendarioBinding) {
        val inicio = inicioDia(fechaSeleccionada)
        val fin = finDia(fechaSeleccionada)
        val del_dia = todasLasTareas.filter { t ->
            val ts = t.fechaProgramada ?: return@filter false
            ts.toDate().time in inicio.timeInMillis..fin.timeInMillis
        }.sortedWith(compareByDescending<Tarea> { it.esImportante }.thenBy { it.fechaProgramada?.seconds ?: 0L })
        adapter.setItems(del_dia)
        actualizarResumen(b, del_dia)
    }

    private fun actualizarCabecera(b: FragmentCalendarioBinding) {
        b.tvFechaSeleccionada.text = formatoFechaLargo(fechaSeleccionada)
    }

    private fun actualizarResumen(b: FragmentCalendarioBinding, tareas: List<Tarea>) {
        val puntosDisponibles = tareas.filter { it.estado == "pendiente" }.sumOf { it.puntos }
        val importantesCount = tareas.count { it.esImportante }
        b.tvResumenDia.text = buildString {
            append("${tareas.size} tarea(s) · $puntosDisponibles pts disponibles")
            if (importantesCount > 0) append(" · ⭐ $importantesCount importantes")
        }
    }

    // Adapter interno para el calendario
    private inner class TareasCalendarioAdapter : RecyclerView.Adapter<TareasCalendarioAdapter.VH>() {
        private var items: List<Tarea> = emptyList()
        fun setItems(list: List<Tarea>) { items = list; notifyDataSetChanged() }

        inner class VH(val card: CardView, val tvTitulo: TextView, val tvInfo: TextView, val tvHora: TextView, val ivImportante: ImageView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = CardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { (it as RecyclerView.LayoutParams).setMargins(0,0,0,12) }
                radius = 12f
                cardElevation = 4f
                setCardBackgroundColor(parent.context.getColor(R.color.fondo))
            }
            val ll = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
            }
            val rowTop = LinearLayout(parent.context).apply { orientation = LinearLayout.HORIZONTAL }
            val tvTitulo = TextView(parent.context).apply {
                textSize = 15f; setTextColor(parent.context.getColor(R.color.texto_principal))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val ivImportante = ImageView(parent.context).apply {
                setImageResource(android.R.drawable.btn_star_big_on)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(48, 48)
            }
            val tvHora = TextView(parent.context).apply {
                textSize = 12f; setTextColor(parent.context.getColor(R.color.texto_secundario))
            }
            val tvInfo = TextView(parent.context).apply {
                textSize = 13f; setTextColor(parent.context.getColor(R.color.texto_secundario))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = 4 }
            }
            rowTop.addView(tvTitulo); rowTop.addView(ivImportante)
            ll.addView(rowTop); ll.addView(tvHora); ll.addView(tvInfo)
            card.addView(ll)
            return VH(card, tvTitulo, tvInfo, tvHora, ivImportante)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val t = items[position]
            holder.tvTitulo.text = t.titulo
            val hora = t.fechaProgramada?.toDate()?.let { d ->
                val c = Calendar.getInstance().apply { time = d }
                "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            } ?: ""
            holder.tvHora.text = hora
            val estadoTexto = when (t.estado) {
                "pendiente" -> "⏳ Pendiente"
                "pendiente_confirmacion" -> "🔍 Pendiente confirmación"
                "confirmada" -> "✅ Confirmada"
                else -> t.estado
            }
            val badge = buildString {
                if (t.esEmergencia) append(" 🚨 Emergencia x${t.multiplicadorPuntos}")
                if (t.esRecurrente) append(" 🔁 ${t.tipoRecurrencia ?: ""}")
            }
            holder.tvInfo.text = "${t.puntos} pts · $estadoTexto$badge"
            holder.ivImportante.visibility = if (t.esImportante) View.VISIBLE else View.GONE

            // Color de fondo por importancia
            holder.card.setCardBackgroundColor(if (t.esImportante) 0xFFFFFDE7.toInt() else requireContext().getColor(R.color.fondo))

            holder.card.setOnClickListener { mostrarOpcionesTarea(t) }
        }

        override fun getItemCount() = items.size
    }

    private fun mostrarOpcionesTarea(tarea: Tarea) {
        val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
        val esCreadoPor = usuarioId == tarea.creadoPor

        // Construir opciones según rol: solo el creador puede gestionar la emergencia
        data class Opcion(val texto: String, val accion: () -> Unit)
        val opciones = mutableListOf<Opcion>()

        opciones.add(Opcion("Reprogramar fecha y hora") { elegirFechaHoraParaTarea(tarea) })
        opciones.add(Opcion(if (tarea.esImportante) "Quitar importante" else "Marcar como importante") {
            actualizarCampo(tarea.copy(esImportante = !tarea.esImportante))
        })
        // Solo el creador/asignador puede activar o desactivar emergencia
        if (esCreadoPor) {
            opciones.add(Opcion(if (tarea.esEmergencia) "Desactivar emergencia" else "🚨 Activar emergencia (×1.5 pts)") {
                val nuevo = if (tarea.esEmergencia) tarea.copy(esEmergencia = false, multiplicadorPuntos = 1.0)
                            else tarea.copy(esEmergencia = true, multiplicadorPuntos = 1.5)
                actualizarCampo(nuevo)
            })
        }
        opciones.add(Opcion("Cambiar recordatorio (${tarea.minutosAntes} min)") { elegirMinutosRecordatorio(tarea) })

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(tarea.titulo)
            .setItems(opciones.map { it.texto }.toTypedArray()) { _, idx -> opciones[idx].accion() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun elegirMinutosRecordatorio(tarea: Tarea) {
        val opciones = arrayOf("10 minutos antes", "30 minutos antes", "60 minutos antes")
        val valores = intArrayOf(10, 30, 60)
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Recordatorio")
            .setItems(opciones) { _, idx ->
                val nueva = tarea.copy(minutosAntes = valores[idx])
                actualizarCampo(nueva)
                // programar recordatorio si hay fecha
                tarea.fechaProgramada?.let { ts ->
                    val trigger = ts.toDate().time - (valores[idx] * 60 * 1000L)
                    if (trigger > System.currentTimeMillis()) {
                        NotificationScheduler.cancelReminder(requireContext(), tarea.id)
                        NotificationScheduler.scheduleReminder(requireContext(), tarea.id,
                            "Recordatorio: ${tarea.titulo}",
                            "Tarea en ${valores[idx]} min",
                            trigger)
                    }
                }
                Toast.makeText(requireContext(), "Recordatorio: ${valores[idx]} min antes", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun elegirFechaHoraParaTarea(tarea: Tarea) {
        val hoy = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            TimePickerDialog(requireContext(), { _, h, min ->
                val cal = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                val nueva = tarea.copy(fechaProgramada = Timestamp(cal.time))
                actualizarCampo(nueva)
                // programar recordatorio
                val trigger = cal.time.time - (tarea.minutosAntes * 60 * 1000L)
                if (trigger > System.currentTimeMillis()) {
                    NotificationScheduler.cancelReminder(requireContext(), tarea.id)
                    NotificationScheduler.scheduleReminder(requireContext(), tarea.id,
                        "Recordatorio: ${tarea.titulo}",
                        "Tarea en ${tarea.minutosAntes} min",
                        trigger)
                }
                Toast.makeText(requireContext(), "Reprogramada: ${formatoFechaLargo(cal)} ${"${"%02d".format(h)}:${"%02d".format(min)}"}", Toast.LENGTH_SHORT).show()
            }, hoy.get(Calendar.HOUR_OF_DAY), hoy.get(Calendar.MINUTE), true).show()
        }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun actualizarCampo(tarea: Tarea) {
        viewLifecycleOwner.lifecycleScope.launch {
            val res = LocalizadorServicios.repositorioTarea.actualizarTarea(tarea)
            if (res.isFailure) Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatoFechaLargo(cal: Calendar): String {
        val dias = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        val meses = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        val diaSemana = dias[cal.get(Calendar.DAY_OF_WEEK) - 1]
        return "$diaSemana ${cal.get(Calendar.DAY_OF_MONTH)} ${meses[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }

    private fun inicioDia(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun finDia(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tareasJob?.cancel()
        binding = null
    }
}
