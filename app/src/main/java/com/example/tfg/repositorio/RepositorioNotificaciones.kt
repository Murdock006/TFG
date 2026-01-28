package com.example.tfg.repositorio

import com.example.tfg.modelo.Notificacion
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RepositorioNotificaciones(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val coleccionNotificaciones = "notificaciones"

    suspend fun enviarNotificacion(notificacion: Notificacion): Result<String> {
        return try {
            val doc = firestore.collection(coleccionNotificaciones).add(notificacion.copy(fecha = Timestamp.now())).await()
            Result.success(doc.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listarNoLeidas(destinatarioUid: String): Result<List<Notificacion>> {
        return try {
            val snap = firestore.collection(coleccionNotificaciones).whereEqualTo("destinatario", destinatarioUid).whereEqualTo("visto", false).get().await()
            val lista = snap.documents.mapNotNull { it.toObject(Notificacion::class.java)?.copy(id = it.id) }
            Result.success(lista)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Observa en tiempo real las notificaciones para un destinatario (incluye las leídas y no leídas)
    fun observarNotificaciones(destinatarioUid: String): Flow<List<Notificacion>> = callbackFlow {
        val q = firestore.collection(coleccionNotificaciones).whereEqualTo("destinatario", destinatarioUid)
        val sub = q.addSnapshotListener { snap, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { it.toObject(Notificacion::class.java)?.copy(id = it.id) } ?: emptyList()
            trySend(list)
        }
        awaitClose { sub.remove() }
    }

    suspend fun marcarNotificacionVista(notificacionId: String): Result<Unit> {
        return try {
            firestore.collection(coleccionNotificaciones).document(notificacionId).update(mapOf("visto" to true)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
