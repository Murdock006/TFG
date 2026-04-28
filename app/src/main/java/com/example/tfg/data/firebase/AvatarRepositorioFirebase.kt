package com.example.tfg.data.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
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
            
            // Leer contenido del Uri (es más confiable que putFile)
            val imageData = if (context != null) {
                try {
                    context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                        ?: throw Exception("No se puede leer la imagen del Uri: stream nulo")
                } catch (e: Exception) {
                    Log.w(TAG, "Error leyendo Uri con context, intentando putFile: ${e.message}")
                    null
                }
            } else {
                null
            }
            
            // Generar nombre único para la imagen
            val nombreArchivo = "avatares/$uid/${UUID.randomUUID()}.jpg"
            Log.d(TAG, "Subiendo avatar a $nombreArchivo")
            val ref = storage.reference.child(nombreArchivo)
            
            // Subir usando putBytes si se pudo leer, sino putFile
            if (imageData != null) {
                ref.putBytes(imageData).await()
                Log.d(TAG, "Avatar subido con putBytes (${imageData.size} bytes)")
            } else {
                ref.putFile(imageUri).await()
                Log.d(TAG, "Avatar subido con putFile")
            }
            
            // Obtener URL de descarga
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d(TAG, "Avatar subido OK: $downloadUrl")
            
            // Actualizar documento del usuario en Firestore con la nueva URL
            firestore.collection("usuarios").document(uid).update(
                mapOf("avatarUrl" to downloadUrl)
            ).await()
            Log.d(TAG, "Documento de usuario actualizado con avatarUrl")
            
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo avatar", e)
            val msg = when (e) {
                is com.google.firebase.FirebaseNetworkException -> "Fallo de red: comprueba tu conexión"
                is com.google.firebase.storage.StorageException -> "Error al subir la imagen: ${e.message}"
                else -> e.message ?: "Error desconocido"
            }
            Result.failure(Exception(msg))
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
