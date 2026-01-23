package com.example.tfg.repositorio

import com.example.tfg.modelo.Recompensa
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RepositorioRecompensas(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val coleccionRecompensas = "recompensas"
    private val coleccionUsuarios = "usuarios"
    private val coleccionCanjes = "canjes"

    suspend fun listarRecompensas(grupoId: String?): Result<List<Recompensa>> {
        return try {
            val query = if (grupoId.isNullOrBlank()) firestore.collection(coleccionRecompensas) else firestore.collection(coleccionRecompensas).whereEqualTo("grupoId", grupoId)
            val snap = query.get().await()
            val lista = snap.documents.mapNotNull { it.toObject(Recompensa::class.java)?.copy(id = it.id) }
            Result.success(lista)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun canjearRecompensa(recompensaId: String, usuarioUid: String): Result<Unit> {
        return try {
            val recompensaRef = firestore.collection(coleccionRecompensas).document(recompensaId)
            val usuarioRef = firestore.collection(coleccionUsuarios).document(usuarioUid)
            firestore.runTransaction { t ->
                val rSnap = t.get(recompensaRef)
                if (!rSnap.exists()) throw Exception("Recompensa no encontrada")
                val coste = (rSnap.getLong("coste") ?: 0L).toInt()
                val uSnap = t.get(usuarioRef)
                val puntos = (uSnap.getLong("puntos") ?: 0L).toInt()
                if (puntos < coste) throw Exception("Puntos insuficientes")
                t.update(usuarioRef, "puntos", puntos - coste)
                // registrar canje
                val canje = mapOf(
                    "recompensaId" to recompensaId,
                    "usuarioUid" to usuarioUid,
                    "fecha" to Timestamp.now()
                )
                t.set(firestore.collection(coleccionCanjes).document(), canje)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
