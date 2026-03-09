package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Canje(
    val id: String = "",
    val recompensaId: String = "",
    val tituloRecompensa: String = "",
    val coste: Int = 0,
    val usuarioUid: String = "",
    val nombreUsuario: String = "",
    val grupoId: String? = null,
    val fecha: Timestamp? = null,
    val estado: String = "pendiente"  // pendiente | aceptado | rechazado
)

