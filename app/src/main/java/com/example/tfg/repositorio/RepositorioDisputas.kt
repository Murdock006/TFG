package com.example.tfg.repositorio

import com.example.tfg.modelo.Disputa
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.*

class RepositorioDisputas(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    private val coleccionDisputas = "disputas"

    suspend fun abrirDisputa(disputa: Disputa): Result<String> {
        return try {
            val doc = firestore.collection(coleccionDisputas).add(disputa.copy(fechaCreacion = Timestamp.now())).await()
            Result.success(doc.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listarDisputasPorUsuario(usuarioUid: String): Result<List<Disputa>> {
        return try {
            val snap = firestore.collection(coleccionDisputas).whereEqualTo("iniciador", usuarioUid).get().await()
            val lista = snap.documents.mapNotNull { it.toObject(Disputa::class.java)?.copy(id = it.id) }
            Result.success(lista)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun subirFotoDisputa(tareaId: String, localUriString: String): Result<String> {
        return try {
            val localUri = android.net.Uri.parse(localUriString)
            val ref = storage.reference.child("disputas/$tareaId/${UUID.randomUUID()}.jpg")
            ref.putFile(localUri).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
