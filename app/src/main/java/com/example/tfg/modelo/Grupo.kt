package com.example.tfg.modelo

data class Grupo(
    val id: String,
    val nombre: String,
    val miembros: List<String> = emptyList(), // lista de emails o ids
    val puntos: Int = 0
)
