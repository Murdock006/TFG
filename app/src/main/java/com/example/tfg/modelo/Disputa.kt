package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Disputa(
    val id: String = "",
    val tareaId: String = "",
    val iniciador: String = "",
    val estado: String = "abierta", // abierta | en_progreso | cerrada
    val pruebas: List<String> = emptyList(), // urls a fotos
    val fechaCreacion: Timestamp? = null
)
