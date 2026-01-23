package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Recompensa(
    val id: String = "",
    val titulo: String = "",
    val descripcion: String? = null,
    val coste: Int = 0,
    val creadoPor: String? = null,
    val grupoId: String? = null,
    val fechaCreacion: Timestamp? = null
)
