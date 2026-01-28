// Modelo de tarea sugerida usado en resource JSON
package com.example.tfg.modelo

data class TareaSugerida(
    val id: String = "",
    val titulo: String = "",
    val descripcion: String? = null,
    val dificultad: String = "FACIL",
    val puntos: Int = 0,
    val duracionMinutos: Int? = null
)
