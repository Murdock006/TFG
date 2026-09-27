package es.sintaxys.teamtask.modelo

import com.google.firebase.Timestamp

data class Disputa(
    val id: String = "",
    val tareaId: String = "",
    val iniciador: String = "",
    val estado: String = "abierta", // abierta | en_progreso | cerrada
    val pruebas: List<String> = emptyList(), // urls a fotos
    val fechaCreacion: Timestamp? = null,
    // --- Respuesta del asignado (flujo bidireccional) ---
    // Nullable con default para no romper documentos de disputa ya existentes.
    val respondidoPor: String? = null,          // uid del asignado que responde
    val motivoRespuesta: String? = null,        // versión del asignado (opcional)
    val pruebasRespuesta: List<String>? = null, // urls a fotos de la respuesta
    val fechaRespuesta: Timestamp? = null
)
