// Se añade modelo simple para categorías sugeridas
package com.example.tfg.modelo

import com.example.tfg.modelo.TareaSugerida

data class Categoria(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String? = null,
    val colorHex: String? = null,
    val icono: String? = null,
    val orden: Int = 0,
    val tareas: List<TareaSugerida> = emptyList()
)
