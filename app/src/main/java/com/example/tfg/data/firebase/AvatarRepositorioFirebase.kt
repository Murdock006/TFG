package com.example.tfg.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.example.tfg.data.local.AvatarRepositorioLocal
import com.example.tfg.repositorio.AvatarRepositorio
import com.example.tfg.service.firebase.FirebaseComposition
import com.example.tfg.util.AvatarImagen
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore base64 implementation of [AvatarRepositorio].
 *
 * Avatar bytes live as a compressed base64 string in `avatares/{uid}` (fields `base64`,
 * `contentType`, `updatedAt`). Uploads also write the `usuarios/{uid}.avatarUpdatedAt` hint in
 * the same [com.google.firebase.firestore.WriteBatch], so a blob and its version marker change
 * atomically (or not at all). No Firebase Storage is used.
 */
class AvatarRepositorioFirebase(
    private val firestore: FirebaseFirestore = FirebaseComposition.firestore(),
    private val auth: FirebaseAuth = FirebaseComposition.auth(),
    private val context: Context,
    private val cacheLocal: AvatarRepositorioLocal = AvatarRepositorioLocal(context)
) : AvatarRepositorio {

    private val coleccion = "avatares"
    private val cacheMemoria = LruCache<String, Bitmap>(CACHE_ENTRADAS)
    private val TAG = "AvatarRepoFirebase"

    override suspend fun subirAvatar(imageUri: Uri): Result<Timestamp> = withContext(Dispatchers.IO) {
        try {
            val uid = auth.currentUser?.uid
                ?: return@withContext Result.failure(Exception("No hay usuario autenticado"))

            val jpegBytes = AvatarImagen.comprimirJpeg(context.contentResolver, imageUri)
                ?: return@withContext Result.failure(Exception("No se pudo procesar la imagen seleccionada"))

            val base64 = AvatarImagen.codificarBase64(jpegBytes)
            if (base64.length > AvatarImagen.MAX_BASE64_CHARS) {
                return@withContext Result.failure(
                    Exception(
                        "La imagen es demasiado grande. Usa una imagen menor (límite: " +
                            "${AvatarImagen.MAX_BASE64_CHARS} caracteres codificados)."
                    )
                )
            }

            val ahora = Timestamp.now()
            val batch = firestore.batch()
            batch.set(
                firestore.collection(coleccion).document(uid),
                mapOf(
                    "base64" to base64,
                    "contentType" to AvatarImagen.CONTENT_TYPE_JPEG,
                    "updatedAt" to ahora
                )
            )
            batch.set(
                firestore.collection("usuarios").document(uid),
                mapOf("avatarUpdatedAt" to ahora),
                SetOptions.merge()
            )
            batch.commit().await()

            val bitmap = AvatarImagen.decodificarJpeg(jpegBytes)
            if (bitmap != null) {
                cacheMemoria.put(claveCache(uid, ahora), bitmap)
            }
            cacheLocal.guardarUltimoConocido(uid, jpegBytes)
            Result.success(ahora)
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo avatar", e)
            Result.failure(Exception(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun obtenerAvatar(uid: String, avatarUpdatedAt: Timestamp?): Bitmap? =
        withContext(Dispatchers.IO) {
            // No hint => no remote avatar => no Firestore read; legacy last-known only.
            if (avatarUpdatedAt == null) {
                return@withContext decodificarUltimoConocido(uid)
            }

            val clave = claveCache(uid, avatarUpdatedAt)
            cacheMemoria.get(clave)?.let { return@withContext it }

            try {
                val doc = firestore.collection(coleccion).document(uid).get().await()
                val base64 = doc.getString("base64")
                if (base64.isNullOrBlank()) {
                    return@withContext decodificarUltimoConocido(uid)
                }
                val bytes = AvatarImagen.decodificarBase64(base64)
                    ?: return@withContext decodificarUltimoConocido(uid)
                val bitmap = AvatarImagen.decodificarJpeg(bytes)
                    ?: return@withContext decodificarUltimoConocido(uid)
                cacheMemoria.put(clave, bitmap)
                cacheLocal.guardarUltimoConocido(uid, bytes)
                bitmap
            } catch (e: Exception) {
                Log.w(TAG, "Lectura remota de avatar fallida para uid=$uid", e)
                decodificarUltimoConocido(uid)
            }
        }

    private fun decodificarUltimoConocido(uid: String): Bitmap? {
        val bytes = cacheLocal.obtenerUltimoConocido(uid) ?: return null
        return AvatarImagen.decodificarJpeg(bytes)
    }

    private fun claveCache(uid: String, hint: Timestamp): String =
        "$uid#${hint.seconds}:${hint.nanoseconds}"

    companion object {
        private const val CACHE_ENTRADAS = 32
    }
}
