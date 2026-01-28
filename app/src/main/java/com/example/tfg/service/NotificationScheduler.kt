package com.example.tfg.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    fun scheduleReminder(context: Context, tareaId: String, title: String, message: String, triggerAtMillis: Long) {
        val delay = triggerAtMillis - System.currentTimeMillis()
        if (delay <= 0) return
        val data = Data.Builder()
            .putString(NotificationWorker.KEY_TAREA_ID, tareaId)
            .putString(NotificationWorker.KEY_TITLE, title)
            .putString(NotificationWorker.KEY_MESSAGE, message)
            .build()

        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork("reminder_$tareaId", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelReminder(context: Context, tareaId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$tareaId")
    }

    // Mostrar notificación inmediata local
    fun showImmediateNotification(context: Context, notifId: Int, title: String, message: String) {
        createChannelIfNeeded(context)
        val builder = NotificationCompat.Builder(context, NotificationWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(notifId, builder.build())
        }
    }

    // Mostrar notificación inmediata local (opcionalmente abre una tarea al pulsarla)
    fun showImmediateNotification(context: Context, notifId: Int, title: String, message: String, openTaskId: String? = null) {
        createChannelIfNeeded(context)
        val builder = NotificationCompat.Builder(context, NotificationWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (!openTaskId.isNullOrBlank()) {
            val intent = android.content.Intent(context, com.example.tfg.vista.MainActivity::class.java).apply {
                putExtra("openTaskId", openTaskId)
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pending = android.app.PendingIntent.getActivity(context, notifId, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            builder.setContentIntent(pending)
        }

        with(NotificationManagerCompat.from(context)) {
            notify(notifId, builder.build())
        }
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de TFG"
            val descriptionText = "Notificaciones para tareas programadas y asignaciones"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NotificationWorker.CHANNEL_ID, name, importance)
            channel.description = descriptionText
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
