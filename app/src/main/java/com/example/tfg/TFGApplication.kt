package com.example.tfg

import android.app.Application
import com.google.firebase.FirebaseApp

class TFGApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializa Firebase explícitamente si no se inicializa automáticamente
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Ignorar: si ya está inicializado no pasa nada
        }
    }
}
