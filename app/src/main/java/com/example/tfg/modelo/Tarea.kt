package com.example.tfg.modelo

data class Tarea(
    val id: String,
    val titulo: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    val asignadoA: String? = null, // email o id
    val puntos: Int = 0,
    val completada: Boolean = false,
    val fecha: Long? = null
)
