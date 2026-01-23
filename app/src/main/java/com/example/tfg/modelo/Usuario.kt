package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Usuario(
    val id: String = "",
    val nombre: String = "",
    val edad: Int? = null,
    val ciudad: String? = null,
    val email: String = "",
    val puntos: Int = 0,                 // puntos disponibles
    val puntosReservados: Int = 0,      // puntos bloqueados para tareas asignadas
    val rachaDias: Int = 0,             // racha de completados consecutivos
    val grupoId: String? = null,
    val fechaCreacion: Timestamp? = null
)
