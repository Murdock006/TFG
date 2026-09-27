package es.sintaxys.teamtask.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import es.sintaxys.teamtask.R

class NotificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TAREA_ID = "tarea_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val CHANNEL_ID = "tfg_reminder_channel"
    }

    override suspend fun doWork(): Result {
        // Verificar permiso antes de mostrar notificación
        if (!hasNotificationPermission(applicationContext)) {
            android.util.Log.w("NotificationWorker", "No hay permiso de notificaciones. No se puede mostrar notificación.")
            return Result.success()
        }
        
        val data: Data = inputData
        val title = data.getString(KEY_TITLE) ?: "Recordatorio"
        val message = data.getString(KEY_MESSAGE) ?: "Tienes una tarea pendiente"
        val notificationId = data.getString(KEY_TAREA_ID)?.hashCode() ?: System.currentTimeMillis().toInt()

        // Canal creado por el único dueño (NotificationScheduler) para mantener una sola descripción.
        NotificationScheduler.ensureChannel(applicationContext)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(applicationContext, R.color.primario))
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(notificationId, builder.build())
        }

        return ListenableWorker.Result.success()
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
