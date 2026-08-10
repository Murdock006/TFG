package com.example.tfg.service

import com.example.tfg.data.firebase.AuthRepositorioFirebase
import com.example.tfg.data.firebase.TareaRepositorioFirebase
import com.example.tfg.data.inmemory.AuthRepositorioInMemory
import com.example.tfg.data.inmemory.GrupoRepositorioInMemory
import com.example.tfg.data.inmemory.TareaRepositorioInMemory
import com.example.tfg.repositorio.AuthRepositorio
import com.example.tfg.repositorio.GrupoRepositorio
import com.example.tfg.repositorio.TareaRepositorio
import com.example.tfg.service.firebase.FirebaseComposition

object LocalizadorServicios {
    val repositorioAuth: AuthRepositorio by lazy {
        FirebaseComposition.requireContext()
        AuthRepositorioFirebase(FirebaseComposition.auth(), FirebaseComposition.firestore(), FirebaseComposition.storage())
    }

    val repositorioGrupo: GrupoRepositorio by lazy {
        FirebaseComposition.requireContext()
        com.example.tfg.repositorio.RepositorioPareja(FirebaseComposition.firestore())
    }

    val repositorioTarea: TareaRepositorio by lazy {
        FirebaseComposition.requireContext()
        TareaRepositorioFirebase(FirebaseComposition.firestore())
    }
}
