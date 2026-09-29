package es.sintaxys.teamtask.vista

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.card.MaterialCardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import es.sintaxys.teamtask.R
import es.sintaxys.teamtask.databinding.FragmentCalendarioBinding
import es.sintaxys.teamtask.modelo.Tarea
import es.sintaxys.teamtask.service.LocalizadorServicios
import es.sintaxys.teamtask.service.NotificationScheduler
import es.sintaxys.teamtask.util.TareaUi
import es.sintaxys.teamtask.viewmodel.ParejaViewModel
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
            DatePickerDialog(
                requireContext(),
                android.R.style.Theme_Material_Light_Dialog,
                { _, y, m, d ->
                    fechaSeleccionada.set(y, m, d, 12, 0, 0)
                    actualizarCabecera(b)
                    filtrarYMostrar(adapter, b)
                },
                hoy.get(Calendar.YEAR),
                hoy.get(Calendar.MONTH),
                hoy.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        b.btnExportarCalendario.setOnClickListener {
            val tareasDelDia = obtenerTareasDelDia()
            if (tareasDelDia.isEmpty()) {
                Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.exportar_calendario_sin_fecha), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.exportar_calendario_ok), Toast.LENGTH_SHORT).show()
                es.sintaxys.teamtask.service.IcsExporter.exportarTareas(requireContext(), tareasDelDia)
            }
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
                                    // Excluir tareas eliminadas (soft delete) del calendario.
                                    todasLasTareas = lista.filter { it.grupoId == grupoIdActual && it.estado != "eliminada" }
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
        val tareasDelDia = obtenerTareasDelDia()
        adapter.setItems(tareasDelDia)
        actualizarResumen(b, tareasDelDia)

        // Empty state
        if (tareasDelDia.isEmpty()) {
            b.rvTareasDia.visibility = View.GONE
            b.emptyState.visibility = View.VISIBLE
        } else {
            b.rvTareasDia.visibility = View.VISIBLE
            b.emptyState.visibility = View.GONE
        }
    }

    private fun obtenerTareasDelDia(): List<Tarea> {
        val inicio = inicioDia(fechaSeleccionada)
        val fin = finDia(fechaSeleccionada)
        return todasLasTareas.filter { t ->
            val ts = t.fechaProgramada ?: return@filter false
            ts.toDate().time in inicio.timeInMillis..fin.timeInMillis
        }.sortedWith(compareByDescending<Tarea> { it.esImportante }.thenBy { it.fechaProgramada?.seconds ?: 0L })
    }

    private fun actualizarCabecera(b: FragmentCalendarioBinding) {
        b.tvFechaSeleccionada.text = formatoFechaLargo(fechaSeleccionada)
    }

    private fun actualizarResumen(b: FragmentCalendarioBinding, tareas: List<Tarea>) {
        // En emergencia se suman los puntos efectivos (puntos × multiplicador), no los base.
        val puntosDisponibles = tareas.filter { it.estado == "pendiente" }
            .sumOf { (it.puntos * it.multiplicadorPuntos.coerceAtLeast(1.0)).toInt() }
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

        inner class VH(val card: MaterialCardView, val tvTitulo: TextView, val tvInfo: TextView, val tvHora: TextView, val ivImportante: ImageView, val vIndicator: View, val tvEstadoChip: TextView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val dm = ctx.resources.displayMetrics
            fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm)
            fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)
            val card = MaterialCardView(ctx).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { (it as RecyclerView.LayoutParams).setMargins(0, 0, 0, dp(12f).toInt()) }
                radius = dp(12f)
                cardElevation = dp(4f)
                setCardBackgroundColor(ctx.getColor(R.color.fondo))
                strokeWidth = 0
            }
            // Contenedor horizontal: barra de dificultad (izquierda) + contenido de texto (peso 1).
            // El padding izquierdo del contenido se reduce para que, sumado a la barra, el texto
            // quede alineado como antes.
            val fila = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val vIndicator = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(4f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT).also { it.marginEnd = dp(10f).toInt() }
                minimumHeight = dp(48f).toInt()
                contentDescription = ctx.getString(R.string.cd_indicador_dificultad)
            }
            val ll = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20f).toInt(), dp(24f).toInt(), dp(32f).toInt(), dp(24f).toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val rowTop = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val tvTitulo = TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(15f)); setTextColor(ctx.getColor(R.color.texto_principal))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val ivImportante = ImageView(ctx).apply {
                setImageResource(android.R.drawable.btn_star_big_on)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt())
            }
            val tvHora = TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(12f)); setTextColor(ctx.getColor(R.color.texto_secundario))
            }
            val tvInfo = TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(13f)); setTextColor(ctx.getColor(R.color.texto_secundario))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4f).toInt() }
            }
            // Chip de estado: mismo estilo que las tarjetas XML (bg_estado_chip), tintado en el bind.
            val tvEstadoChip = TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(12f))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ctx.getColor(R.color.white))
                setBackgroundResource(R.drawable.bg_estado_chip)
                setPadding(dp(10f).toInt(), dp(3f).toInt(), dp(10f).toInt(), dp(3f).toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6f).toInt() }
            }
            rowTop.addView(tvTitulo); rowTop.addView(ivImportante)
            ll.addView(rowTop); ll.addView(tvHora); ll.addView(tvInfo); ll.addView(tvEstadoChip)
            fila.addView(vIndicator); fila.addView(ll)
            card.addView(fila)
            return VH(card, tvTitulo, tvInfo, tvHora, ivImportante, vIndicator, tvEstadoChip)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val t = items[position]
            holder.tvTitulo.text = t.titulo
            val hora = t.fechaProgramada?.toDate()?.let { d ->
                val c = Calendar.getInstance().apply { time = d }
                "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            } ?: ""
            holder.tvHora.text = hora
            val badge = buildString {
                if (t.esEmergencia) append(" 🚨 Emergencia x${t.multiplicadorPuntos}")
                if (t.esRecurrente) append(" 🔄 ${t.tipoRecurrencia ?: ""}")
            }
            // En emergencia se muestra el valor efectivo (puntos × multiplicador), no el base.
            val puntosMostrados = (t.puntos * t.multiplicadorPuntos.coerceAtLeast(1.0)).toInt()
            // tvInfo conserva los puntos y las marcas (emergencia/recurrencia); el estado vive en el chip.
            holder.tvInfo.text = "$puntosMostrados pts$badge"
            holder.ivImportante.visibility = if (t.esImportante) View.VISIBLE else View.GONE

            // Chip de estado: etiqueta y colores centralizados en TareaUi (mismo patrón que las tarjetas XML).
            holder.tvEstadoChip.text = TareaUi.etiquetaEstado(t.estado)
            holder.tvEstadoChip.backgroundTintList = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(TareaUi.colorEstado(t.estado))
            )
            holder.tvEstadoChip.setTextColor(requireContext().getColor(TareaUi.colorTextoEstado(t.estado)))

            // Barra de dificultad: se re-tinta en cada bind porque el holder se recicla.
            val colorDificultad = when (t.dificultad) {
                1 -> R.color.verde
                2 -> R.color.naranja
                else -> R.color.rojo
            }
            holder.vIndicator.background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.v_indicador_dificultad)?.apply {
                setTint(requireContext().getColor(colorDificultad))
            }

            // Color de fondo por importancia (token adaptativo: claro en modo claro, ámbar oscuro en modo oscuro)
            holder.card.setCardBackgroundColor(
                if (t.esImportante) requireContext().getColor(R.color.importante_bg)
                else requireContext().getColor(R.color.fondo)
            )

            // Marca visual de emergencia (borde rojo) y recurrencia (borde violeta). Se resetea en cada
            // bind porque el holder se recicla. Si es ambas, el rojo de emergencia tiene prioridad.
            if (t.esEmergencia) {
                holder.card.strokeColor = requireContext().getColor(R.color.emergencia)
                holder.card.strokeWidth = requireContext().resources.getDimensionPixelSize(R.dimen.stroke_thick)
            } else if (t.esRecurrente) {
                holder.card.strokeColor = requireContext().getColor(R.color.recurrente)
                holder.card.strokeWidth = requireContext().resources.getDimensionPixelSize(R.dimen.stroke_thick)
            } else {
                holder.card.strokeWidth = 0
            }

            holder.card.setOnClickListener { mostrarOpcionesTarea(t) }
        }

        override fun getItemCount() = items.size
    }

    private fun mostrarOpcionesTarea(tarea: Tarea) {
        // Gestionar opciones de la tarea según el rol del usuario actual:
        //   - Creador: reprogramar, marcar/quitar importante y cambiar recordatorio.
        //   - Asignado (no creador): solo cambiar recordatorio (es su propia notificación).
        //   - Tercero (ni creador ni asignado): solo lectura.
        // Las acciones mutantes requieren estado "pendiente"; exportar está siempre disponible.
        // La emergencia (×1.5) se gestiona en el formulario de creación, no desde el calendario.
        data class Opcion(val texto: String, val accion: () -> Unit)
        val opciones = mutableListOf<Opcion>()

        val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        val soyCreador = !uidActual.isNullOrBlank() && tarea.creadoPor == uidActual
        val soyAsignado = !uidActual.isNullOrBlank() && tarea.asignadoA == uidActual
        // Las acciones que mutan la tarea solo se ofrecen mientras está "pendiente". En estados
        // finales (confirmada, completada, etc.) no se reabre una tarea ya cerrada.
        val puedeMutar = tarea.estado == "pendiente"

        if (puedeMutar && soyCreador) {
            opciones.add(Opcion("Reprogramar fecha y hora") { elegirFechaHoraParaTarea(tarea) })
            opciones.add(Opcion(if (tarea.esImportante) "Quitar importante" else "Marcar como importante") {
                actualizarCampo { LocalizadorServicios.repositorioTarea.actualizarImportante(tarea.id, !tarea.esImportante) }
            })
        }
        // El recordatorio es una notificación propia: lo pueden cambiar el creador y el asignado.
        if (puedeMutar && (soyCreador || soyAsignado)) {
            opciones.add(Opcion("Cambiar recordatorio (${tarea.minutosAntes} min)") { elegirMinutosRecordatorio(tarea) })
        }
        // Exportar es una acción de solo lectura: disponible para todos, en cualquier estado.
        opciones.add(Opcion("📤 Añadir al calendario") {
            es.sintaxys.teamtask.service.IcsExporter.exportarTarea(requireContext(), tarea)
        })

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
                actualizarCampo { LocalizadorServicios.repositorioTarea.actualizarRecordatorio(tarea.id, valores[idx]) }
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
        DatePickerDialog(
            requireContext(),
            android.R.style.Theme_Material_Light_Dialog,
            { _, y, m, d ->
                TimePickerDialog(requireContext(), { _, h, min ->
                    val cal = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                    actualizarCampo { LocalizadorServicios.repositorioTarea.reprogramarTarea(tarea.id, Timestamp(cal.time)) }
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
            },
            hoy.get(Calendar.YEAR),
            hoy.get(Calendar.MONTH),
            hoy.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun actualizarCampo(accion: suspend () -> Result<Unit>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val res = accion()
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
