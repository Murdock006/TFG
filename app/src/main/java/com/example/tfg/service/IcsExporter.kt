package com.example.tfg.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.tfg.R
import com.example.tfg.modelo.Tarea
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Genera archivos .ics (iCalendar) a partir de tareas para exportar al calendario del dispositivo.
 * Compatible con Google Calendar, Samsung Calendar, Outlook y cualquier app que soporte iCalendar.
 */
object IcsExporter {

    private const val MIME_TYPE = "text/calendar"
    private const val FILE_PREFIX = "teamtask_"
    private const val FILE_EXTENSION = ".ics"

    /**
     * Genera un archivo .ics con las tareas proporcionadas y lanza un intent para compartirlo.
     * El usuario puede elegir con qué app de calendario abrirlo.
     */
    fun exportarTareas(context: Context, tareas: List<Tarea>) {
        if (tareas.isEmpty()) return

        val contenido = generarIcs(tareas)
        val archivo = guardarArchivo(context, contenido)
        compartirArchivo(context, archivo)
    }

    /**
     * Genera un archivo .ics para una sola tarea.
     */
    fun exportarTarea(context: Context, tarea: Tarea) {
        exportarTareas(context, listOf(tarea))
    }

    private fun generarIcs(tareas: List<Tarea>): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//TeamTask//ES")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")

        val formatter = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")

        for (tarea in tareas) {
            // Solo exportar tareas con fecha programada
            val fecha = tarea.fechaProgramada ?: continue

            val fechaInicio = fecha.toDate()
            val duracionMinutos = 60 // eventos de 1 hora por defecto
            val calFin = Calendar.getInstance().apply { time = fechaInicio; add(Calendar.MINUTE, duracionMinutos) }

            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:teamtask-${tarea.id}@teamtask.app")
            sb.appendLine("DTSTAMP:${formatter.format(Calendar.getInstance().time)}")
            sb.appendLine("DTSTART:${formatter.format(fechaInicio)}")
            sb.appendLine("DTEND:${formatter.format(calFin.time)}")
            sb.appendLine("SUMMARY:${escapeIcs(tarea.titulo)}")

            // Descripcion con detalles de la tarea
            val descripcion = buildString {
                append("Tarea TeamTask\\n")
                if (!tarea.descripcion.isNullOrBlank()) {
                    append("Descripcion: ${escapeIcs(tarea.descripcion)}\\n")
                }
                append("Puntos: ${tarea.puntos}\\n")
                append("Estado: ${tarea.estado}\\n")
                if (tarea.esImportante) append("⭐ Importante\\n")
                if (tarea.esEmergencia) append("🚨 Emergencia (x${tarea.multiplicadorPuntos})\\n")
                if (tarea.esRecurrente) append("🔁 Recurrente: ${tarea.tipoRecurrencia}\\n")
                if (!tarea.categoria.isNullOrBlank()) append("Categoria: ${escapeIcs(tarea.categoria)}\\n")
            }
            sb.appendLine("DESCRIPTION:$descripcion")

            // Recordatorio (alarma) basado en minutosAntes
            if (tarea.minutosAntes > 0) {
                sb.appendLine("BEGIN:VALARM")
                sb.appendLine("TRIGGER:-PT${tarea.minutosAntes}M")
                sb.appendLine("ACTION:DISPLAY")
                sb.appendLine("DESCRIPTION:Recordatorio: ${escapeIcs(tarea.titulo)}")
                sb.appendLine("END:VALARM")
            }

            sb.appendLine("END:VEVENT")
        }

        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun guardarArchivo(context: Context, contenido: String): File {
        val directorio = File(context.cacheDir, "calendar_exports")
        if (!directorio.exists()) directorio.mkdirs()

        val nombre = "$FILE_PREFIX${System.currentTimeMillis()}$FILE_EXTENSION"
        val archivo = File(directorio, nombre)
        archivo.writeText(contenido, Charsets.UTF_8)
        return archivo
    }

    private fun compartirArchivo(context: Context, archivo: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archivo
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Intentar abrir directamente con app de calendario
        val resolved = context.packageManager.queryIntentActivities(intent, 0)
        if (resolved.isNotEmpty()) {
            context.startActivity(intent)
        } else {
            // Fallback: compartir como archivo
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.exportar_calendario_compartir)))
        }
    }

    /**
     * Escapa caracteres especiales para el formato iCalendar.
     * Segun RFC 5545, se deben escapar: coma, punto y coma, backslash y saltos de linea.
     */
    private fun escapeIcs(texto: String): String {
        return texto
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }
}
