package com.example.tfg.data.inmemory

import com.example.tfg.modelo.Tarea
import com.example.tfg.repositorio.TareaRepositorio
import com.example.tfg.service.LocalizadorServicios
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import com.google.firebase.Timestamp

class TareaRepositorioInMemory : TareaRepositorio {

    private val tareas = mutableListOf<Tarea>()
    private val tareasFlow = MutableStateFlow<List<Tarea>>(emptyList())
    private val puntosFijosPersonalizada = 200

    private fun esCategoriaPersonalizada(categoria: String?): Boolean {
        return categoria.equals("personalizada", true) || categoria.equals("personalizado", true)
    }

    private fun normalizarTareaSegunReglas(tarea: Tarea): Tarea {
        return if (esCategoriaPersonalizada(tarea.categoria)) {
            tarea.copy(puntos = puntosFijosPersonalizada)
        } else {
            tarea
        }
    }

    private fun validarAutoasignacion(tarea: Tarea): Result<Unit> {
        return if (!tarea.creadoPor.isNullOrBlank() && !tarea.asignadoA.isNullOrBlank() && tarea.creadoPor == tarea.asignadoA) {
            Result.failure(Exception("No se permite autoasignarse tareas"))
        } else {
            Result.success(Unit)
        }
    }

    private fun esAutoasignada(tarea: Tarea): Boolean {
        return !tarea.creadoPor.isNullOrBlank() && !tarea.asignadoA.isNullOrBlank() && tarea.creadoPor == tarea.asignadoA
    }

    override suspend fun crearTarea(tarea: Tarea): Result<Tarea> = withContext(Dispatchers.Default) {
        val tareaNormalizada = normalizarTareaSegunReglas(tarea)
        val validacion = validarAutoasignacion(tareaNormalizada)
        if (validacion.isFailure) {
            return@withContext Result.failure(validacion.exceptionOrNull() ?: Exception("No se permite autoasignarse tareas"))
        }

        val t = tareaNormalizada.copy(id = UUID.randomUUID().toString())
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

    override fun observarTareasPorGrupo(grupoId: String): Flow<List<Tarea>> =
        tareasFlow.map { lista -> lista.filter { it.grupoId == grupoId } }

    override suspend fun actualizarTarea(tarea: Tarea): Result<Tarea> = withContext(Dispatchers.Default) {
        val tareaNormalizada = normalizarTareaSegunReglas(tarea)
        val validacion = validarAutoasignacion(tareaNormalizada)
        if (validacion.isFailure) {
            return@withContext Result.failure(validacion.exceptionOrNull() ?: Exception("No se permite autoasignarse tareas"))
        }

        val idx = tareas.indexOfFirst { it.id == tareaNormalizada.id }
        return@withContext if (idx >= 0) {
            val previo = tareas[idx]
            tareas[idx] = tareaNormalizada
            tareasFlow.value = tareas.toList()

            // Si la tarea pasó a 'confirmada' desde 'completada', transferir puntos al asignado
            if (previo.estado == "completada" && tarea.estado == "confirmada") {
                val asignado = tareaNormalizada.asignadoA
                val creador = tareaNormalizada.creadoPor
                if (!asignado.isNullOrBlank() && !creador.isNullOrBlank()) {
                    try {
                        // sumar puntos al ejecutor con bonificación por racha
                        LocalizadorServicios.repositorioAuth.sumarPuntosConBonificacion(asignado, tareaNormalizada.puntos)
                        // liberar los puntos reservados del creador (se han transferido)
                        LocalizadorServicios.repositorioAuth.liberarPuntos(creador, tareaNormalizada.puntos)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            // Si la tarea pasa de asignada a no asignada, liberar puntos del creador
            if (!previo.asignadoA.isNullOrBlank() && tareaNormalizada.asignadoA.isNullOrBlank()) {
                val creador = tareaNormalizada.creadoPor
                if (!creador.isNullOrBlank()) {
                    try {
                        LocalizadorServicios.repositorioAuth.liberarPuntos(creador, tareaNormalizada.puntos)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            Result.success(tareaNormalizada)
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

    override suspend fun marcarCompletada(tareaId: String, ejecutorUid: String): Result<Unit> = withContext(Dispatchers.Default) {
        val idx = tareas.indexOfFirst { it.id == tareaId }
        if (idx < 0) return@withContext Result.failure(Exception("Tarea no encontrada"))
        val tarea = tareas[idx]
        if (esAutoasignada(tarea)) return@withContext Result.failure(Exception("Tarea inválida: autoasignación no permitida"))
        if (tarea.estado != "pendiente") return@withContext Result.failure(Exception("Tarea no está en estado pendiente"))

        if (!tarea.requiereConfirmacion) {
            // confirmar y transferir
            val nueva = tarea.copy(estado = "confirmada")
            tareas[idx] = nueva
            // sumar puntos al ejecutor y liberar reservas del creador
            try {
                val asignado = tarea.asignadoA ?: ejecutorUid
                val creador = tarea.creadoPor
                if (!asignado.isNullOrBlank()) LocalizadorServicios.repositorioAuth.sumarPuntosConBonificacion(asignado, tarea.puntos)
                if (!creador.isNullOrBlank()) LocalizadorServicios.repositorioAuth.liberarPuntos(creador, tarea.puntos)
            } catch (e: Exception) { /* ignore */ }
            tareasFlow.value = tareas.toList()
            return@withContext Result.success(Unit)
        } else {
            // marcar completada y esperar confirmación
            tareas[idx] = tarea.copy(estado = "completada")
            tareasFlow.value = tareas.toList()
            return@withContext Result.success(Unit)
        }
    }

    override suspend fun confirmarTarea(tareaId: String, confirmadoPorUid: String): Result<Unit> = withContext(Dispatchers.Default) {
        val idx = tareas.indexOfFirst { it.id == tareaId }
        if (idx < 0) return@withContext Result.failure(Exception("Tarea no encontrada"))
        val tarea = tareas[idx]
        if (esAutoasignada(tarea)) return@withContext Result.failure(Exception("Tarea inválida: autoasignación no permitida"))
        if (tarea.estado != "pendiente_confirmacion" && tarea.estado != "completada") return@withContext Result.failure(Exception("La tarea no está en estado pendiente de confirmación"))

        // confirmar y transferir puntos al asignado
        val nueva = tarea.copy(estado = "confirmada", fechaReclamada = null, reclamadoPor = null, motivoReclamo = null)
        tareas[idx] = nueva
        tareasFlow.value = tareas.toList()

        try {
            val asignado = nueva.asignadoA ?: throw Exception("Tarea sin asignado")
            LocalizadorServicios.repositorioAuth.sumarPuntosConBonificacion(asignado, nueva.puntos)
            val creador = nueva.creadoPor
            if (!creador.isNullOrBlank()) LocalizadorServicios.repositorioAuth.liberarPuntos(creador, nueva.puntos)
        } catch (e: Exception) { /* ignore */ }

        Result.success(Unit)
    }
}
