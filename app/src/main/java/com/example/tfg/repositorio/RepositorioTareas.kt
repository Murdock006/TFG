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
                // transacción: marcar confirmada y sumar puntos
                firestore.runTransaction { t ->
                    val snap = t.get(tareaRef)
                    t.update(tareaRef, "estado", "confirmada")
                    // actualizar puntos del usuario
                    val userRef = firestore.collection(coleccionUsuarios).document(ejecutorUid)
                    val userSnap = t.get(userRef)
                    val puntosActuales = (userSnap.getLong("puntos") ?: 0L).toInt()
                    t.update(userRef, "puntos", puntosActuales + tarea.puntos)
                }.await()
                return Result.success(Unit)
            } else {
                // marcar como completada y notificar al grupo/otro miembro (notificaciones no implementadas aquí)
                tareaRef.update(mapOf("estado" to "completada")).await()
                return Result.success(Unit)
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmarTarea(tareaId: String, confirmadoPorUid: String): Result<Unit> {
        return try {
            val tareaRef = firestore.collection(coleccionTareas).document(tareaId)
            firestore.runTransaction { t ->
                val snap = t.get(tareaRef)
                val tarea = snap.toObject(Tarea::class.java) ?: throw Exception("Tarea inválida")
                if (tarea.estado != "completada") throw Exception("Tarea no está en estado completada")
                t.update(tareaRef, "estado", "confirmada")
                // sumar puntos al ejecutor (asignadoA)
                val ejecutor = tarea.asignadoA ?: throw Exception("Tarea sin asignado")
                val userRef = firestore.collection(coleccionUsuarios).document(ejecutor)
                val userSnap = t.get(userRef)
                val puntosActuales = (userSnap.getLong("puntos") ?: 0L).toInt()
                t.update(userRef, "puntos", puntosActuales + tarea.puntos)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
