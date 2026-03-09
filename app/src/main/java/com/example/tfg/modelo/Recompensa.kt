package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Recompensa(
    val id: String = "",
    val titulo: String = "",
    val descripcion: String? = null,
    val coste: Int = 0,
    val creadoPor: String? = null,
    val grupoId: String? = null,
    val fechaCreacion: Timestamp? = null,
    val esPredefinida: Boolean = false,   // recompensa estándar del sistema
    val esPersonalizada: Boolean = false  // creada por los usuarios del grupo
)
