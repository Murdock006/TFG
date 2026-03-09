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
    val motivoReclamo: String? = null,
    // --- Prioridad / emergencia ---
    val esEmergencia: Boolean = false,          // activa multiplicador temporal
    val multiplicadorPuntos: Double = 1.0,      // ej: 1.5 = +50% puntos en emergencia
    // --- Recurrencia ---
    val esRecurrente: Boolean = false,
    val tipoRecurrencia: String? = null,        // "diaria" | "semanal" | "mensual"
    val rotarMiembros: Boolean = false,         // si true, la siguiente vez se asigna al otro miembro
    // --- Recordatorios ---
    val minutosAntes: Int = 30,                 // minutos de antelación para el recordatorio (10, 30, 60)
    // --- Marcado importante ---
    val esImportante: Boolean = false
)
