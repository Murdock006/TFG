package com.example.tfg.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker

class NotificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TAREA_ID = "tarea_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val CHANNEL_ID = "tfg_reminder_channel"
    }

    override suspend fun doWork(): Result {
        val data: Data = inputData
        val title = data.getString(KEY_TITLE) ?: "Recordatorio"
        val message = data.getString(KEY_MESSAGE) ?: "Tienes una tarea pendiente"
        val notificationId = data.getString(KEY_TAREA_ID)?.hashCode() ?: System.currentTimeMillis().toInt()

        createChannelIfNeeded(applicationContext)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(notificationId, builder.build())
        }

        return ListenableWorker.Result.success()
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de TFG"
            val descriptionText = "Notificaciones para tareas programadas"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = descriptionText
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
