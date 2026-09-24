package com.example.tfg.modelo

import com.google.firebase.Timestamp

data class Usuario(
    val id: String = "",
    val nombre: String = "",
    val fechaNacimiento: String? = null,
    val sexo: String? = null,
    val pais: String? = null,
    val ciudad: String? = null,
    val email: String = "",
    val puntos: Int = 0,                    // puntos de actividad (se ganan y gastan en tareas)
    val puntosReservados: Int = 0,          // puntos bloqueados para tareas asignadas
    val puntosRecompensa: Int = 0,          // puntos exclusivos para canjear recompensas (10% de cada tarea)
    val rachaDias: Int = 0,                 // racha de completados consecutivos
    val grupoId: String? = null,
    val avatarUrl: String? = null,          // Legacy: no producer/reader after teamtask-avatar-authority
    val avatarUpdatedAt: Timestamp? = null, // Avatar hint: existence flag + cache version key
    val fechaCreacion: Timestamp? = null
)
