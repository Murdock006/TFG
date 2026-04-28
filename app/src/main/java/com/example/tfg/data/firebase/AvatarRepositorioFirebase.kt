package com.example.tfg.data.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class AvatarRepositorioFirebase(private val context: Context? = null) {

    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage
    private val TAG = "AvatarRepoFirebase"

    /**
     * Sube un avatar a Firebase Storage y actualiza la URL en el documento del usuario en Firestore.
     *
     * @param imageUri URI de la imagen seleccionada por el usuario
     * @return Result<String> con la URL del avatar subido, o error
     */
    suspend fun subirAvatar(imageUri: Uri): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("No hay usuario autenticado")
            
            if (context == null) {
                throw Exception("Context no disponible para lectura de imagen")
            }
            
            // Paso 1: Leer contenido del Uri
            Log.d(TAG, "Leyendo contenido del Uri: $imageUri")
            val imageBytes = context.contentResolver.openInputStream(imageUri)?.use { 
                it.readBytes() 
            } ?: throw Exception("No se puede leer la imagen: stream nulo")
            
            Log.d(TAG, "Imagen leída: ${imageBytes.size} bytes")
            
            // Paso 2: Determinar formato de la imagen y extensión
            val extension = determinarExtension(imageUri, context)
            Log.d(TAG, "Extensión detectada: $extension")
            
            // Paso 3: Crear archivo temporal local con nombre único
            val nombreArchivo = "avatares/$uid/${UUID.randomUUID()}.$extension"
            Log.d(TAG, "Subiendo avatar a $nombreArchivo")
            
            // Paso 4: Subir a Firebase Storage usando putBytes
            val ref = storage.reference.child(nombreArchivo)
            
            // Detectar MIME type para metadata
            val mimeType = obtenerMimeType(extension)
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(mimeType)
                .build()
            
            ref.putBytes(imageBytes, metadata).await()
            Log.d(TAG, "Avatar subido exitosamente (${imageBytes.size} bytes)")
            
            // Paso 5: Obtener URL de descarga
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d(TAG, "URL de descarga obtenida: $downloadUrl")
            
            // Paso 6: Actualizar documento del usuario en Firestore
            firestore.collection("usuarios").document(uid).update(
                mapOf("avatarUrl" to downloadUrl)
            ).await()
            Log.d(TAG, "Documento de usuario actualizado con avatarUrl")
            
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo avatar", e)
            e.printStackTrace()
            val msg = when (e) {
                is com.google.firebase.FirebaseNetworkException -> "Fallo de red: comprueba tu conexión"
                is com.google.firebase.storage.StorageException -> "Error al subir la imagen: ${e.message}"
                is SecurityException -> "Permiso denegado para leer la imagen"
                else -> e.message ?: "Error desconocido"
            }
            Result.failure(Exception(msg))
        }
    }

    /**
     * Determina la extensión del archivo basada en el Uri y MIME type
     */
    private fun determinarExtension(uri: Uri, context: Context): String {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            when {
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                mimeType.contains("gif") -> "gif"
                mimeType.contains("bmp") -> "bmp"
                else -> "jpg" // default a JPEG
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detectando MIME type, usando jpg por defecto: ${e.message}")
            "jpg"
        }
    }

    /**
     * Obtiene el MIME type correcto para Firebase Storage metadata
     */
    private fun obtenerMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> "image/jpeg"
        }
    }

    /**
     * Obtiene la URL del avatar del usuario actual desde Firestore.
     *
     * @return URL del avatar, o null si no tiene
     */
    suspend fun obtenerAvatarUrlActual(): String? {
        return try {
            val uid = auth.currentUser?.uid ?: return null
            
            val doc = firestore.collection("usuarios").document(uid).get().await()
            doc.getString("avatarUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo avatarUrl", e)
            null
        }
    }

    /**
     * Obtiene la URL del avatar de un usuario específico.
     *
     * @param uid ID del usuario
     * @return URL del avatar, o null si no tiene
     */
    suspend fun obtenerAvatarUrl(uid: String): String? {
        return try {
            val doc = firestore.collection("usuarios").document(uid).get().await()
            doc.getString("avatarUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo avatarUrl para uid=$uid", e)
            null
        }
    }

    /**
     * Elimina el avatar del usuario actual.
     *
     * @return Result<Unit> indicando éxito o error
     */
    suspend fun eliminarAvatar(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("No hay usuario autenticado")
            
            // Eliminar la URL de Firestore
            firestore.collection("usuarios").document(uid).update(
                mapOf("avatarUrl" to null)
            ).await()
            Log.d(TAG, "Avatar eliminado del documento de usuario")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando avatar", e)
            Result.failure(e)
        }
    }
}
