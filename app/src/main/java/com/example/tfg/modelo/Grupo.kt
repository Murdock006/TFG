package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Grupo(
    val id: String = "",
    val nombre: String = "",
    val miembros: Map<String, String> = emptyMap(), // uid -> rol
    val puntos: Int = 0,
    val fechaCreacion: Timestamp? = null
)
