package com.example.tfg.vista

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.tfg.databinding.FragmentCalendarioBinding
import com.example.tfg.modelo.Tarea
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.service.NotificationScheduler
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.util.*

class FragmentCalendario : Fragment() {

    private var binding: FragmentCalendarioBinding? = null
    private var fechaSeleccionada: Calendar = Calendar.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCalendarioBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.let { b ->
            b.rvTareasDia.layoutManager = LinearLayoutManager(requireContext())
            val adapter = object : RecyclerView.Adapter<ViewHolder>() {
                private var items: List<Tarea> = emptyList()
                fun setItems(list: List<Tarea>) { items = list; notifyDataSetChanged() }
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                    val tv = TextView(parent.context)
                    val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    tv.layoutParams = lp
                    val pad = (12 * resources.displayMetrics.density).toInt()
                    tv.setPadding(pad,pad,pad,pad)
                    // asegurar color de texto legible
                    tv.setTextColor(parent.context.resources.getColor(com.example.tfg.R.color.texto_principal, parent.context.theme))
                    tv.textSize = 14f
                    return object : ViewHolder(tv) {}
                }
                override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                    val tarea = items[position]
                    val fechaTexto = tarea.fechaProgramada?.toDate()?.let { d ->
                        val cal = Calendar.getInstance().apply { time = d }
                        "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH)+1}/${cal.get(Calendar.YEAR)}"
                    } ?: "sin fecha"
                    (holder.itemView as TextView).text = "${tarea.titulo} — ${tarea.puntos} pts — ${tarea.estado} — $fechaTexto"
                    holder.itemView.setOnClickListener {
                        // permitir reprogramar
                        elegirFechaParaTarea(tarea)
                    }
                }
                override fun getItemCount(): Int = items.size
            }
            b.rvTareasDia.adapter = adapter

            // ItemTouchHelper: swipe left/right -> solo informar al usuario (no reprogramar automáticamente)
            val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean = false
                override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
                    val pos = viewHolder.bindingAdapterPosition
                    // restaurar el item (no se borra) y avisar al usuario
                    (b.rvTareasDia.adapter as RecyclerView.Adapter<ViewHolder>).notifyItemChanged(pos)
                    Toast.makeText(requireContext(), "Usa tocar para reprogramar (drag&drop no activo)", Toast.LENGTH_SHORT).show()
                }
            })
            touchHelper.attachToRecyclerView(b.rvTareasDia)

            // Observamos todas las tareas y filtramos por semana seleccionada
            viewLifecycleOwner.lifecycleScope.launch {
                LocalizadorServicios.repositorioTarea.observarTareas().collect { list ->
                    val inicio = semanaInicio(fechaSeleccionada)
                    val fin = semanaFin(fechaSeleccionada)
                    val filtradas = list.filter { t ->
                        val ts = t.fechaProgramada
                        if (ts == null) false else (ts.toDate().time >= inicio.timeInMillis && ts.toDate().time <= fin.timeInMillis)
                    }
                    adapter.setItems(filtradas)
                }
            }

            // actualizar texto fecha
            b.tvFechaSeleccionada.text = getString(com.example.tfg.R.string.semana_rango, formatoFecha(semanaInicio(fechaSeleccionada)), formatoFecha(semanaFin(fechaSeleccionada)))
            b.btnElegirDia.setOnClickListener {
                val hoy = fechaSeleccionada
                val dp = DatePickerDialog(requireContext(), { _, year, month, day ->
                    fechaSeleccionada.set(year, month, day, 12, 0, 0)
                    b.tvFechaSeleccionada.text = getString(com.example.tfg.R.string.semana_rango, formatoFecha(semanaInicio(fechaSeleccionada)), formatoFecha(semanaFin(fechaSeleccionada)))
                }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH))
                dp.show()
            }

            b.btnPrevWeek.setOnClickListener {
                fechaSeleccionada.add(Calendar.WEEK_OF_YEAR, -1)
                b.tvFechaSeleccionada.text = getString(com.example.tfg.R.string.semana_rango, formatoFecha(semanaInicio(fechaSeleccionada)), formatoFecha(semanaFin(fechaSeleccionada)))
            }
            b.btnNextWeek.setOnClickListener {
                fechaSeleccionada.add(Calendar.WEEK_OF_YEAR, 1)
                b.tvFechaSeleccionada.text = getString(com.example.tfg.R.string.semana_rango, formatoFecha(semanaInicio(fechaSeleccionada)), formatoFecha(semanaFin(fechaSeleccionada)))
            }
        }
    }

    private fun elegirFechaParaTarea(tarea: Tarea) {
        val hoy = Calendar.getInstance()
        val dp = DatePickerDialog(requireContext(), { _, year, month, day ->
            val cal = Calendar.getInstance()
            cal.set(year, month, day, 12,0,0)
            val nueva = tarea.copy(fechaProgramada = com.google.firebase.Timestamp(cal.time))
            viewLifecycleOwner.lifecycleScope.launch {
                val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                if (res.isSuccess) {
                    Toast.makeText(requireContext(), "Tarea reprogramada", Toast.LENGTH_SHORT).show()
                    // cancelar recordatorio anterior y programar uno nuevo 30 minutos antes
                    try {
                        NotificationScheduler.cancelReminder(requireContext(), tarea.id)
                        val trigger = cal.time.time - 30 * 60 * 1000L
                        NotificationScheduler.scheduleReminder(requireContext(), tarea.id, "Tarea: ${tarea.titulo}", "Tarea programada para ${formatoFecha(cal)}", trigger)
                    } catch (e: Exception) {
                        // ignore
                    }
                } else {
                    Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                }
            }
        }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH))
        dp.show()
    }

    private fun formatoFecha(cal: Calendar): String {
        return "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH)+1}/${cal.get(Calendar.YEAR)}"
    }

    private fun semanaInicio(cal: Calendar): Calendar {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0)
        return c
    }

    private fun semanaFin(cal: Calendar): Calendar {
        val c = semanaInicio(cal)
        c.add(Calendar.DAY_OF_MONTH, 6)
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE,59); c.set(Calendar.SECOND,59); c.set(Calendar.MILLISECOND,999)
        return c
    }

}
