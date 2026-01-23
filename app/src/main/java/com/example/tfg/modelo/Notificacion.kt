package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Notificacion(
    val id: String = "",
    val tipo: String = "",
    val contenido: Map<String, Any> = emptyMap(),
    val destinatario: String = "",
    val visto: Boolean = false,
    val fecha: Timestamp? = null
)
