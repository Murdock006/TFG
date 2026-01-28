package com.example.tfg.repositorio

import com.example.tfg.modelo.Tarea
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RepositorioTareas(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val coleccionTareas = "tareas"
    private val coleccionUsuarios = "usuarios"

    suspend fun crearTarea(tarea: Tarea): Result<String> {
        return try {
            val datos = tarea.copy(fechaCreada = Timestamp.now())
            val ref = firestore.collection(coleccionTareas).add(datos).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun asignarTarea(tareaId: String, asignadoA: String?): Result<Unit> {
        return try {
            val docRef = firestore.collection(coleccionTareas).document(tareaId)
            val update = if (asignadoA == null) mapOf("asignadoA" to null) else mapOf("asignadoA" to asignadoA)
            docRef.update(update).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun marcarCompletada(tareaId: String, ejecutorUid: String): Result<Unit> {
        return try {
            val tareaRef = firestore.collection(coleccionTareas).document(tareaId)
            val tareaSnap = tareaRef.get().await()
            if (!tareaSnap.exists()) return Result.failure(Exception("Tarea no encontrada"))
            val tarea = tareaSnap.toObject(Tarea::class.java) ?: return Result.failure(Exception("Tarea inválida"))
            if (tarea.estado != "pendiente") return Result.failure(Exception("Tarea no está en estado pendiente"))

            if (!tarea.requiereConfirmacion) {
                // Transacción: LEER TODO PRIMERO, LUEGO ESCRIBIR
                firestore.runTransaction { t ->
                    // 1) Leer todos los documentos necesarios
                    val tareaSnapTx = t.get(tareaRef)
                    val ta = tareaSnapTx.toObject(Tarea::class.java) ?: throw Exception("Tarea inválida en transacción")

                    val ejecutorRef = firestore.collection(coleccionUsuarios).document(ejecutorUid)
                    val creadorId = ta.creadoPor
                    val creadorRef = if (!creadorId.isNullOrBlank()) firestore.collection(coleccionUsuarios).document(creadorId) else null

                    // Leer referencias de usuario antes de escribir
                    val ejecutorSnap = t.get(ejecutorRef)
                    val creadorSnap = creadorRef?.let { t.get(it) }

                    // 2) Aplicar escrituras (todas después de las lecturas)
                    t.update(tareaRef, "estado", "confirmada")

                    // actualizar o crear ejecutor
                    if (!ejecutorSnap.exists()) {
                        val datosEjecutor = mapOf("puntos" to ta.puntos, "puntosReservados" to 0)
                        t.set(ejecutorRef, datosEjecutor)
                    } else {
                        val puntosActuales = (ejecutorSnap.getLong("puntos") ?: 0L).toInt()
                        t.update(ejecutorRef, "puntos", puntosActuales + ta.puntos)
                    }

                    // liberar reservas del creador
                    if (creadorRef != null) {
                        if (!creadorSnap!!.exists()) {
                            val datosCreador = mapOf("puntos" to 0, "puntosReservados" to 0)
                            t.set(creadorRef, datosCreador)
                        } else {
                            val reservados = (creadorSnap.getLong("puntosReservados") ?: 0L).toInt()
                            val nuevoReservados = (reservados - ta.puntos).coerceAtLeast(0)
                            t.update(creadorRef, "puntosReservados", nuevoReservados)
                        }
                    }
                }.await()
                return Result.success(Unit)
            } else {
                // marcar como completada y notificar al grupo/otro miembro (notificaciones no implementadas aquí)
                tareaRef.update(mapOf("estado" to "completada")).await()
                return Result.success(Unit)
            }

        } catch (e: Exception) {
            // mensajes más concretos para ayudar en debugging
            val msg = when (e) {
                is com.google.firebase.firestore.FirebaseFirestoreException -> (e.message ?: "Error Firestore")
                else -> e.message ?: "Error desconocido"
            }
            Result.failure(Exception("No se pudo marcar completada: $msg"))
        }
    }

    suspend fun confirmarTarea(tareaId: String, confirmadoPorUid: String): Result<Unit> {
        return try {
            val tareaRef = firestore.collection(coleccionTareas).document(tareaId)
            // Reorganizar transacción: leer todo antes de escribir
            firestore.runTransaction { t ->
                val snap = t.get(tareaRef)
                val tarea = snap.toObject(Tarea::class.java) ?: throw Exception("Tarea inválida")
                if (tarea.estado != "completada") throw Exception("Tarea no está en estado completada")

                val ejecutor = tarea.asignadoA ?: throw Exception("Tarea sin asignado")
                val userRef = firestore.collection(coleccionUsuarios).document(ejecutor)
                val creador = tarea.creadoPor
                val creadorRef = if (!creador.isNullOrBlank()) firestore.collection(coleccionUsuarios).document(creador) else null

                // leer usuarios necesarios
                val userSnap = t.get(userRef)
                val creadorSnap = creadorRef?.let { t.get(it) }

                // ahora aplicar escrituras
                t.update(tareaRef, "estado", "confirmada")

                if (!userSnap.exists()) {
                    val datos = mapOf("puntos" to tarea.puntos, "puntosReservados" to 0)
                    t.set(userRef, datos)
                } else {
                    val puntosActuales = (userSnap.getLong("puntos") ?: 0L).toInt()
                    t.update(userRef, "puntos", puntosActuales + tarea.puntos)
                }

                if (creadorRef != null) {
                    if (!creadorSnap!!.exists()) {
                        val datosCreador = mapOf("puntos" to 0, "puntosReservados" to 0)
                        t.set(creadorRef, datosCreador)
                    } else {
                        val reservados = (creadorSnap.getLong("puntosReservados") ?: 0L).toInt()
                        val nuevoReservados = (reservados - tarea.puntos).coerceAtLeast(0)
                        t.update(creadorRef, "puntosReservados", nuevoReservados)
                    }
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
