package com.example.tfg.repositorio

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RepositorioBaseDatos(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    suspend fun escribirDocumento(coleccion: String, id: String? = null, datos: Map<String, Any>): Result<String> {
        return try {
            val ref = if (id.isNullOrBlank()) firestore.collection(coleccion).add(datos).await() else {
                firestore.collection(coleccion).document(id)
                    .set(datos)
                    .await().let { firestore.collection(coleccion).document(id) }
            }
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leerDocumento(coleccion: String, id: String): Result<Map<String, Any>?> {
        return try {
            val snapshot = firestore.collection(coleccion).document(id).get().await()
            Result.success(snapshot.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observarColeccion(coleccion: String) = callbackFlow {
        val listener = firestore.collection(coleccion).addSnapshotListener { snapshots, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val lista = snapshots?.documents?.mapNotNull { it.data }
            trySend(lista ?: emptyList())
        }
        awaitClose { listener.remove() }
    }
}
