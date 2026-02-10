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

            // Si la tarea requiere confirmación del asignador, marcamos un estado intermedio
            if (tarea.requiereConfirmacion) {
                tareaRef.update(mapOf("estado" to "pendiente_confirmacion")).await()
                return Result.success(Unit)
            } else {
                // No requiere confirmación: confirmar y transferir puntos en transacción
                firestore.runTransaction { t ->
                    // Leer todo antes de escribir
                    val snap = t.get(tareaRef)
                    val tareaTx = snap.toObject(Tarea::class.java) ?: throw Exception("Tarea inválida en transacción")
                    if (tareaTx.estado != "pendiente") throw Exception("Tarea no está en estado pendiente")

                    val ejecutor = tareaTx.asignadoA ?: ejecutorUid
                    val userRef = firestore.collection(coleccionUsuarios).document(ejecutor)
                    val creadorId = tareaTx.creadoPor
                    val creadorRef = if (!creadorId.isNullOrBlank()) firestore.collection(coleccionUsuarios).document(creadorId) else null

                    // Lecturas necesarias
                    val userSnap = t.get(userRef)
                    val creadorSnap = creadorRef?.let { t.get(it) }

                    // Escrituras: actualizar estado y sumar puntos al ejecutor
                    t.update(tareaRef, "estado", "confirmada")

                    if (!userSnap.exists()) {
                        val datos = mapOf("puntos" to tareaTx.puntos, "puntosReservados" to 0)
                        t.set(userRef, datos)
                    } else {
                        val puntosActuales = (userSnap.getLong("puntos") ?: 0L).toInt()
                        t.update(userRef, "puntos", puntosActuales + tareaTx.puntos)
                    }

                    if (creadorRef != null) {
                        if (!creadorSnap!!.exists()) {
                            val datosCreador = mapOf("puntos" to 0, "puntosReservados" to 0)
                            t.set(creadorRef, datosCreador)
                        } else {
                            val reservados = (creadorSnap.getLong("puntosReservados") ?: 0L).toInt()
                            val nuevoReservados = (reservados - tareaTx.puntos).coerceAtLeast(0)
                            t.update(creadorRef, "puntosReservados", nuevoReservados)
                        }
                    }

                    null
                }.await()
                return Result.success(Unit)
            }

        } catch (e: Exception) {
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
            val snap = tareaRef.get().await()
            if (!snap.exists()) return Result.failure(Exception("Tarea no encontrada"))
            val tarea = snap.toObject(Tarea::class.java) ?: return Result.failure(Exception("Tarea inválida"))
            if (tarea.estado != "pendiente_confirmacion" && tarea.estado != "completada") return Result.failure(Exception("La tarea no está en estado pendiente de confirmación"))

            // confirmar y transferir en transacción (leer todo antes de escribir)
            firestore.runTransaction { t ->
                val snapTx = t.get(tareaRef)
                val tareaTx = snapTx.toObject(Tarea::class.java) ?: throw Exception("Tarea inválida")
                if (tareaTx.estado != "pendiente_confirmacion" && tareaTx.estado != "completada") throw Exception("La tarea no está en estado correcto para confirmar")

                val ejecUid = tareaTx.asignadoA ?: throw Exception("Tarea sin asignado")
                val ejecRef = firestore.collection(coleccionUsuarios).document(ejecUid)
                val creadorUid = tareaTx.creadoPor
                val creadorRef = if (!creadorUid.isNullOrBlank()) firestore.collection(coleccionUsuarios).document(creadorUid) else null

                // leer usuarios antes de escribir
                val ejecSnap = t.get(ejecRef)
                val creadorSnap = creadorRef?.let { t.get(it) }

                // aplicar escrituras
                t.update(tareaRef, "estado", "confirmada")

                if (!ejecSnap.exists()) {
                    val datos = mapOf("puntos" to (tareaTx.puntos))
                    t.set(ejecRef, datos)
                } else {
                    val actuales = (ejecSnap.getLong("puntos") ?: 0L).toInt()
                    t.update(ejecRef, "puntos", actuales + tareaTx.puntos)
                }

                if (creadorRef != null) {
                    if (!creadorSnap!!.exists()) {
                        val datosCreador = mapOf("puntosReservados" to 0)
                        t.set(creadorRef, datosCreador)
                    } else {
                        val reservados = (creadorSnap.getLong("puntosReservados") ?: 0L).toInt()
                        val nuevoReservados = (reservados - tareaTx.puntos).coerceAtLeast(0)
                        t.update(creadorRef, "puntosReservados", nuevoReservados)
                    }
                }

                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
