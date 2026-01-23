package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Tarea(
    val id: String = "",
    val titulo: String = "",
    val descripcion: String? = null,
    val categoria: String? = null,
    val dificultad: Int = 1,
    val puntos: Int = 0,
    val asignadoA: String? = null, // uid
    val creadoPor: String? = null,
    val grupoId: String? = null,
    val estado: String = "pendiente", // pendiente | completada | confirmada | reclamada
    val requiereConfirmacion: Boolean = true,
    val fechaCreada: Timestamp? = null,
    val fechaProgramada: Timestamp? = null,
    val fechaReclamada: Timestamp? = null,
    val reclamadoPor: String? = null,
    val motivoReclamo: String? = null
)
