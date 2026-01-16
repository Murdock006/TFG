package com.example.tfg.repositorio

import com.example.tfg.modelo.Tarea
import kotlinx.coroutines.flow.Flow

interface TareaRepositorio {
    suspend fun crearTarea(tarea: Tarea): Result<Tarea>
    suspend fun obtenerTareas(): Result<List<Tarea>>
    fun observarTareas(): Flow<List<Tarea>>
    suspend fun actualizarTarea(tarea: Tarea): Result<Tarea>
}
