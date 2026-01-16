package com.example.tfg.data.inmemory

import com.example.tfg.modelo.Tarea
import com.example.tfg.repositorio.TareaRepositorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class TareaRepositorioInMemory : TareaRepositorio {

    private val tareas = mutableListOf<Tarea>()
    private val tareasFlow = MutableStateFlow<List<Tarea>>(emptyList())

    override suspend fun crearTarea(tarea: Tarea): Result<Tarea> = withContext(Dispatchers.Default) {
        val t = tarea.copy(id = UUID.randomUUID().toString())
        tareas.add(t)
        tareasFlow.value = tareas.toList()
        Result.success(t)
    }

    override suspend fun obtenerTareas(): Result<List<Tarea>> = withContext(Dispatchers.Default) {
        Result.success(tareas.toList())
    }

    override fun observarTareas(): Flow<List<Tarea>> = tareasFlow

    override suspend fun actualizarTarea(tarea: Tarea): Result<Tarea> = withContext(Dispatchers.Default) {
        val idx = tareas.indexOfFirst { it.id == tarea.id }
        return@withContext if (idx >= 0) {
            tareas[idx] = tarea
            tareasFlow.value = tareas.toList()
            Result.success(tarea)
        } else {
            Result.failure(Exception("Tarea no encontrada"))
        }
    }
}
