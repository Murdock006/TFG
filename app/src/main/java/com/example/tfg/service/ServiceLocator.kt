package com.example.tfg.service

import com.example.tfg.data.firebase.AuthRepositorioFirebase
import com.example.tfg.data.inmemory.AuthRepositorioInMemory
import com.example.tfg.data.inmemory.GrupoRepositorioInMemory
import com.example.tfg.data.inmemory.TareaRepositorioInMemory
import com.example.tfg.repositorio.AuthRepositorio
import com.example.tfg.repositorio.GrupoRepositorio
import com.example.tfg.repositorio.TareaRepositorio

object ServiceLocator {
    // Cambia esta flag para usar Firebase en lugar de la implementación in-memory
    private const val USE_FIREBASE = true

    val authRepositorio: AuthRepositorio by lazy {
        if (USE_FIREBASE) AuthRepositorioFirebase() else AuthRepositorioInMemory()
    }

    val grupoRepositorio: GrupoRepositorio by lazy { GrupoRepositorioInMemory() }
    val tareaRepositorio: TareaRepositorio by lazy { TareaRepositorioInMemory() }
}
