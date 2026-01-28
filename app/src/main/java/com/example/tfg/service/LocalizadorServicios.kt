package com.example.tfg.service

import android.util.Log
import com.example.tfg.data.firebase.AuthRepositorioFirebase
import com.example.tfg.data.firebase.TareaRepositorioFirebase
import com.example.tfg.data.inmemory.AuthRepositorioInMemory
import com.example.tfg.data.inmemory.GrupoRepositorioInMemory
import com.example.tfg.data.inmemory.TareaRepositorioInMemory
import com.example.tfg.repositorio.AuthRepositorio
import com.example.tfg.repositorio.GrupoRepositorio
import com.example.tfg.repositorio.TareaRepositorio
import com.google.firebase.FirebaseApp

object LocalizadorServicios {
    // Cambia esta flag para usar Firebase en lugar de la implementación in-memory
    // Para pruebas reales con Firestore la dejamos activada por defecto.
    private const val USAR_FIREBASE = true

    init {
        Log.d("LocalizadorServicios", "USAR_FIREBASE = $USAR_FIREBASE")
        if (USAR_FIREBASE) {
            try {
                val app = FirebaseApp.getInstance()
                val opts = app.options
                Log.d("LocalizadorServicios", "Firebase projectId=${opts.projectId} applicationId=${opts.applicationId} databaseUrl=${opts.databaseUrl}")
            } catch (e: Exception) {
                Log.e("LocalizadorServicios", "FirebaseApp not initialized or error reading options", e)
            }
        }
    }

    val repositorioAuth: AuthRepositorio by lazy {
        if (USAR_FIREBASE) AuthRepositorioFirebase() else AuthRepositorioInMemory()
    }

    val repositorioGrupo: GrupoRepositorio by lazy {
        if (USAR_FIREBASE) com.example.tfg.repositorio.RepositorioPareja() else GrupoRepositorioInMemory()
    }

    val repositorioTarea: TareaRepositorio by lazy {
        if (USAR_FIREBASE) TareaRepositorioFirebase() else TareaRepositorioInMemory()
    }
}
