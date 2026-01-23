package com.example.tfg.repositorio

import com.example.tfg.modelo.Notificacion
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
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
}
