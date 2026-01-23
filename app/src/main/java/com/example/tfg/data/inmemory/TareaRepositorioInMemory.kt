package com.example.tfg.data.inmemory

import com.example.tfg.modelo.Tarea
import com.example.tfg.repositorio.TareaRepositorio
import com.example.tfg.service.LocalizadorServicios
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import com.google.firebase.Timestamp

class TareaRepositorioInMemory : TareaRepositorio {

    private val tareas = mutableListOf<Tarea>()
    private val tareasFlow = MutableStateFlow<List<Tarea>>(emptyList())

    override suspend fun crearTarea(tarea: Tarea): Result<Tarea> = withContext(Dispatchers.Default) {
        val t = tarea.copy(id = UUID.randomUUID().toString())
        // Si el creador ofrece puntos, reservarlos en su cuenta
        if (!t.creadoPor.isNullOrBlank() && t.puntos > 0) {
            try {
                LocalizadorServicios.repositorioAuth.reservarPuntos(t.creadoPor!!, t.puntos)
            } catch (e: Exception) {
                // ignore reservation failure; could return failure
                return@withContext Result.failure(Exception("No se pudieron reservar puntos: ${e.message}"))
            }
        }
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
            val previo = tareas[idx]
            tareas[idx] = tarea
            tareasFlow.value = tareas.toList()

            // Si la tarea pasó a 'confirmada' desde 'completada', transferir puntos al asignado
            if (previo.estado == "completada" && tarea.estado == "confirmada") {
                val asignado = tarea.asignadoA
                val creador = tarea.creadoPor
                if (!asignado.isNullOrBlank() && !creador.isNullOrBlank()) {
                    try {
                        // sumar puntos al ejecutor con bonificación por racha
                        LocalizadorServicios.repositorioAuth.sumarPuntosConBonificacion(asignado, tarea.puntos)
                        // liberar los puntos reservados del creador (se han transferido)
                        LocalizadorServicios.repositorioAuth.liberarPuntos(creador, tarea.puntos)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            // Si la tarea pasa de asignada a no asignada, liberar puntos del creador
            if (!previo.asignadoA.isNullOrBlank() && tarea.asignadoA.isNullOrBlank()) {
                val creador = tarea.creadoPor
                if (!creador.isNullOrBlank()) {
                    try {
                        LocalizadorServicios.repositorioAuth.liberarPuntos(creador, tarea.puntos)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            Result.success(tarea)
        } else {
            Result.failure(Exception("Tarea no encontrada"))
        }
    }

    override suspend fun resolverReclamo(tareaId: String, aceptado: Boolean): Result<Tarea> = withContext(Dispatchers.Default) {
        val idx = tareas.indexOfFirst { it.id == tareaId }
        if (idx < 0) return@withContext Result.failure(Exception("Tarea no encontrada"))
        val tarea = tareas[idx]
        if (tarea.estado != "reclamada") return@withContext Result.failure(Exception("La tarea no está en estado reclamado"))

        val creador = tarea.creadoPor
        val asignado = tarea.asignadoA

        if (aceptado) {
            // aceptar reclamo -> mantener reclamo? Here we assume accept means asignador estaba equivocado and confirm task
            val nueva = tarea.copy(estado = "confirmada", fechaReclamada = null, reclamadoPor = null, motivoReclamo = null)
            tareas[idx] = nueva
            tareasFlow.value = tareas.toList()
            // transferir puntos al asignado y liberar reserva del creador
            if (!asignado.isNullOrBlank() && !creador.isNullOrBlank()) {
                try {
                    LocalizadorServicios.repositorioAuth.sumarPuntosConBonificacion(asignado, tarea.puntos)
                    LocalizadorServicios.repositorioAuth.liberarPuntos(creador, tarea.puntos)
                } catch (e: Exception) { /* ignore */ }
            }
            return@withContext Result.success(nueva)
        } else {
            // rechazado -> volver a pendiente y liberar puntos reservados al creador
            val nueva = tarea.copy(estado = "pendiente", fechaReclamada = null, reclamadoPor = null, motivoReclamo = null)
            tareas[idx] = nueva
            tareasFlow.value = tareas.toList()
            if (!creador.isNullOrBlank()) {
                try { LocalizadorServicios.repositorioAuth.liberarPuntos(creador, tarea.puntos) } catch (e: Exception) { }
            }
            return@withContext Result.success(nueva)
        }
    }
}
