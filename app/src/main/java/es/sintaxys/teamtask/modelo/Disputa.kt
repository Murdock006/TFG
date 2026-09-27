package es.sintaxys.teamtask.modelo

import com.google.firebase.Timestamp

data class Disputa(
    val id: String = "",
    val tareaId: String = "",
    val iniciador: String = "",
    val estado: String = "abierta", // abierta | en_progreso | cerrada
    val pruebas: List<String> = emptyList(), // fotos de la evidencia en base64 (JPEG codificado)
    val fechaCreacion: Timestamp? = null,
    // --- Respuesta del asignado (flujo bidireccional) ---
    // Nullable con default para no romper documentos de disputa ya existentes.
    val respondidoPor: String? = null,          // uid del asignado que responde
    val motivoRespuesta: String? = null,        // versión del asignado (opcional)
    val pruebasRespuesta: List<String>? = null, // fotos en base64 de la respuesta (JPEG codificado)
    val fechaRespuesta: Timestamp? = null
)
