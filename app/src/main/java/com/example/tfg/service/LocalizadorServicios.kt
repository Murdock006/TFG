package com.example.tfg.service

import com.example.tfg.data.firebase.AuthRepositorioFirebase
import com.example.tfg.data.inmemory.AuthRepositorioInMemory
import com.example.tfg.data.inmemory.GrupoRepositorioInMemory
import com.example.tfg.data.inmemory.TareaRepositorioInMemory
import com.example.tfg.repositorio.AuthRepositorio
import com.example.tfg.repositorio.GrupoRepositorio
import com.example.tfg.repositorio.TareaRepositorio

object LocalizadorServicios {
    // Cambia esta flag para usar Firebase en lugar de la implementación in-memory
    private const val USAR_FIREBASE = true

    val repositorioAuth: AuthRepositorio by lazy {
        if (USAR_FIREBASE) AuthRepositorioFirebase() else AuthRepositorioInMemory()
    }

    val repositorioGrupo: GrupoRepositorio by lazy { GrupoRepositorioInMemory() }
    val repositorioTarea: TareaRepositorio by lazy { TareaRepositorioInMemory() }
}
