package es.sintaxys.teamtask.util

import com.google.firebase.Timestamp
import es.sintaxys.teamtask.R
import java.util.Calendar

/**
 * Fuentes únicas de verdad para la presentación de tarjetas de tarea.
 *
 * Centraliza el formato de fecha/hora, la etiqueta y los colores del chip de estado
 * para que todos los adaptadores de tarjetas (TareasHomeAdapter, TareasAdapter y
 * SugeridasAdapter) muestren la misma información de la misma forma.
 */
object TareaUi {

    /**
     * Formatea un Timestamp como fecha/hora corta y legible, p. ej. "27/09 · 15:30".
     * Si es null devuelve "Sin fecha".
     *
     * Nota: [SelectorFechaHora.formatear] usa el formato largo "DD/MM/AAAA HH:MM"
     * para formularios y recordatorios. Este método usa el formato corto porque la
     * tarjeta necesita un texto compacto; se conserva la misma lógica (Calendar).
     */
    fun formatearFechaHora(ts: Timestamp?): String {
        if (ts == null) return "Sin fecha"
        val cal = Calendar.getInstance().apply { time = ts.toDate() }
        return "%02d/%02d · %02d:%02d".format(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    /**
     * Etiqueta legible del estado para el chip.
     * Los estados desconocidos se devuelven tal cual llegaron.
     */
    fun etiquetaEstado(estado: String): String = when (estado.lowercase()) {
        "pendiente" -> "Pendiente"
        "pendiente_confirmacion" -> "Pendiente de aprobar"
        "reclamada", "en_disputa", "disputa" -> "En disputa"
        "confirmada" -> "Confirmada"
        "completada" -> "Completada"
        "eliminada" -> "Eliminada"
        else -> estado
    }

    /**
     * Color de fondo (res id) del chip por estado.
     * Usa los colores semánticos existentes del proyecto:
     * naranja (pendiente de aprobar), rojo (en disputa), verde (confirmada) y
     * gris neutro para el resto.
     */
    fun colorEstado(estado: String): Int = when (estado.lowercase()) {
        "pendiente_confirmacion" -> R.color.naranja
        "reclamada", "en_disputa", "disputa" -> R.color.rojo
        "confirmada", "completada" -> R.color.verde
        else -> R.color.gris
    }

    /**
     * Color del texto del chip.
     *
     * Los colores semánticos del proyecto para estado (verde `#A7F3D0`,
     * naranja `#FCD34D`, rojo `#FCA5A5`) son tonos CLAROS, por lo que el texto
     * oscuro ([R.color.texto_principal]) es el único legible sobre ellos.
     * El fondo gris neutro ([R.color.gris]) usa texto blanco.
     */
    fun colorTextoEstado(estado: String): Int = when (estado.lowercase()) {
        "pendiente_confirmacion", "reclamada", "en_disputa", "disputa", "confirmada", "completada" ->
            R.color.texto_principal
        else -> R.color.white
    }
}
