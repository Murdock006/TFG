package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Invitacion(
    val codigo: String = "",
    val creadoPor: String = "",
    val grupoId: String = "",
    val correoDestino: String? = null,
    val estado: String = "pendiente", // pendiente | aceptada | caducada
    val fechaCreacion: Timestamp? = null,
    val expiracion: Timestamp? = null
)
