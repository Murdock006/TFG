package com.example.tfg.service

import android.content.Context
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
}
