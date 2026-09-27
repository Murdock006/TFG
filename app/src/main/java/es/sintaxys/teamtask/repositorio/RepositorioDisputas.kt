package es.sintaxys.teamtask.repositorio

import android.content.ContentResolver
import es.sintaxys.teamtask.modelo.Disputa
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import es.sintaxys.teamtask.service.firebase.FirebaseComposition
import es.sintaxys.teamtask.util.AvatarImagen

class RepositorioDisputas(
    private val firestore: FirebaseFirestore = FirebaseComposition.firestore()
) {

    private val coleccionDisputas = "disputas"

    private companion object {
        // La evidencia vive como base64 dentro del documento de `disputas` (Firestore, 1 MB por
        // documento), no en Firebase Storage. Un documento guarda DOS fotos (la de quien reclama
        // y la de quien responde), así que cada base64 debe ser holgado: un JPEG 800px q70 pesa
        // ~150-250 KB. Se limita cada foto a MAX_BASE64_EVIDENCIA_CHARS caracteres para que las
        // dos sumen <= 800 000 y el resto del documento entre dentro del límite de 1 MB.
        const val LADO_LARGO_EVIDENCIA_PX = 800
        const val CALIDAD_EVIDENCIA_JPEG = 70
        const val MAX_BASE64_EVIDENCIA_CHARS = 400_000
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

    /**
     * Comprime la imagen elegida y devuelve su contenido como JPEG en base64, listo para
     * guardarlo dentro del documento de `disputas` en Firestore. No usa Firebase Storage.
     *
     * @return base64 de la evidencia, o fallo si no se puede procesar o si excede
     * [MAX_BASE64_EVIDENCIA_CHARS] (se rechaza para no romper el documento de la disputa).
     */
    suspend fun procesarEvidenciaDisputa(localUriString: String, resolver: ContentResolver): Result<String> {
        return try {
            val localUri = android.net.Uri.parse(localUriString)
            // Se reconvierte cualquier imagen a JPEG (PNG/HEIC incluidas) y se reduce antes de
            // codificarla: la evidencia es un string base64 dentro del documento de Firestore.
            val bytes = AvatarImagen.comprimirJpeg(
                resolver,
                localUri,
                ladoLargo = LADO_LARGO_EVIDENCIA_PX,
                calidad = CALIDAD_EVIDENCIA_JPEG
            ) ?: return Result.failure(Exception("No se pudo procesar la imagen seleccionada"))
            val base64 = AvatarImagen.codificarBase64(bytes)
            if (base64.length > MAX_BASE64_EVIDENCIA_CHARS) {
                return Result.failure(
                    Exception(
                        "La imagen es demasiado grande para adjuntarla como evidencia " +
                            "(máximo: $MAX_BASE64_EVIDENCIA_CHARS caracteres codificados)."
                    )
                )
            }
            Result.success(base64)
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
