package es.sintaxys.teamtask.repositorio

import android.content.ContentResolver
import es.sintaxys.teamtask.modelo.Disputa
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import es.sintaxys.teamtask.service.firebase.FirebaseComposition
import es.sintaxys.teamtask.util.AvatarImagen
import java.util.*

class RepositorioDisputas(
    private val firestore: FirebaseFirestore = FirebaseComposition.firestore(),
    private val storage: FirebaseStorage = FirebaseComposition.storage()
) {

    private val coleccionDisputas = "disputas"

    private companion object {
        // Contrato de storage.rules: solo image/jpeg y < 5 MB. Se comprime antes de subir para
        // cumplirlo siempre, con más resolución que un avatar porque aquí la imagen es evidencia.
        const val CONTENT_TYPE_JPEG = "image/jpeg"
        const val LADO_LARGO_EVIDENCIA_PX = 1280
        const val CALIDAD_EVIDENCIA_JPEG = 80
    }

    suspend fun abrirDisputa(disputa: Disputa): Result<String> {
        return try {
            val doc = firestore.collection(coleccionDisputas).add(disputa.copy(fechaCreacion = Timestamp.now())).await()
            Result.success(doc.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listarDisputasPorTarea(tareaId: String): Result<List<Disputa>> {
        return try {
            val snap = firestore.collection(coleccionDisputas).whereEqualTo("tareaId", tareaId).get().await()
            val lista = snap.documents.mapNotNull { it.toObject(Disputa::class.java)?.copy(id = it.id) }
            Result.success(lista)
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

    suspend fun subirFotoDisputa(tareaId: String, localUriString: String, resolver: ContentResolver): Result<String> {
        return try {
            val localUri = android.net.Uri.parse(localUriString)
            // Se reconvierte cualquier imagen a JPEG antes de subir: storage.rules solo acepta
            // `image/jpeg` < 5 MB, así que subir el archivo original (PNG/HEIC/>5 MB) daría
            // PERMISSION_DENIED. Se reutiliza la conversión a JPEG de AvatarImagen con una
            // resolución mayor que la de avatar, ya que aquí la imagen es evidencia.
            val bytes = AvatarImagen.comprimirJpeg(
                resolver,
                localUri,
                ladoLargo = LADO_LARGO_EVIDENCIA_PX,
                calidad = CALIDAD_EVIDENCIA_JPEG
            ) ?: return Result.failure(Exception("No se pudo procesar la imagen"))
            val ref = storage.reference.child("disputas/$tareaId/${UUID.randomUUID()}.jpg")
            val metadata = StorageMetadata.Builder().setContentType(CONTENT_TYPE_JPEG).build()
            ref.putBytes(bytes, metadata).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Respuesta del asignado a una disputa abierta: registra su motivo/pruebas y pasa la
     * disputa a `en_progreso` para que el creador pueda revisarla y resolver.
     */
    suspend fun responderDisputa(
        tareaId: String,
        respondidoPor: String,
        motivo: String?,
        pruebas: List<String>
    ): Result<String> {
        return try {
            val snap = firestore.collection(coleccionDisputas).whereEqualTo("tareaId", tareaId).get().await()
            val doc = snap.documents.firstOrNull()
                ?: return Result.failure(Exception("No hay disputa registrada para esta tarea"))
            val cambios = mutableMapOf<String, Any?>(
                "estado" to "en_progreso",
                "respondidoPor" to respondidoPor,
                "motivoRespuesta" to motivo,
                "pruebasRespuesta" to pruebas,
                "fechaRespuesta" to Timestamp.now()
            )
            firestore.collection(coleccionDisputas).document(doc.id).update(cambios).await()
            Result.success(doc.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
