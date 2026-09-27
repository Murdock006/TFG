package es.sintaxys.teamtask.util

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import com.google.firebase.Timestamp
import java.util.Calendar

/**
 * Selector reutilizable de fecha + hora.
 *
 * Encadena un DatePickerDialog y un TimePickerDialog. Solo se invoca [onResult]
 * cuando el usuario completa AMBOS pasos; si cancela cualquiera de los dos,
 * no se entrega ningún valor (el llamador decide qué hacer, típicamente abortar).
 */
object SelectorFechaHora {

    /**
     * Muestra el selector de fecha y, si se confirma, el de hora.
     *
     * @param fechaInicial fecha/hora que se muestra preseleccionada; si es null se usa "ahora".
     * @param onResult se invoca con el Timestamp elegido al completar ambos pasos.
     */
    fun elegir(context: Context, fechaInicial: Timestamp? = null, onResult: (Timestamp) -> Unit) {
        val base = Calendar.getInstance().apply {
            fechaInicial?.let { time = it.toDate() }
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, h, min ->
                        val cal = Calendar.getInstance()
                        cal.set(year, month, day, h, min, 0)
                        onResult(Timestamp(cal.time))
                    },
                    base.get(Calendar.HOUR_OF_DAY),
                    base.get(Calendar.MINUTE),
                    true
                ).show()
            },
            base.get(Calendar.YEAR),
            base.get(Calendar.MONTH),
            base.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** Formatea un Timestamp como "DD/MM/AAAA HH:MM". */
    fun formatear(ts: Timestamp): String {
        val cal = Calendar.getInstance().apply { time = ts.toDate() }
        return "%02d/%02d/%04d %02d:%02d".format(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }
}
