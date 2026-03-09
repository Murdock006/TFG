package com.example.tfg

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp

class TFGApplication : Application() {
    companion object {
        var appContext: Context? = null
            private set
    }
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // Inicializa Firebase explícitamente si no se inicializa automáticamente
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Ignorar: si ya está inicializado no pasa nada
        }
    }
}
