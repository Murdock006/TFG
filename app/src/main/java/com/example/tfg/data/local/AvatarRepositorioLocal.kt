package com.example.tfg.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import java.io.File

class AvatarRepositorioLocal(private val context: Context) {

    private val prefs = context.getSharedPreferences("tfg_prefs", Context.MODE_PRIVATE)
    private val TAG = "AvatarRepoLocal"

    fun subirAvatar(imageUri: Uri): Result<String> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: throw Exception("No hay usuario autenticado")

            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw Exception("No se puede leer la imagen: stream nulo")

            val extension = determinarExtension(imageUri)
            val avatarDir = File(context.filesDir, "avatars")
            if (!avatarDir.exists()) {
                avatarDir.mkdirs()
            }

            val file = File(avatarDir, "$uid.$extension")
            file.writeBytes(bytes)

            prefs.edit().putString(avatarKey(uid), file.absolutePath).apply()
            Log.d(TAG, "Avatar guardado localmente en ${file.absolutePath}")

            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando avatar local", e)
            Result.failure(Exception(e.message ?: "Error desconocido"))
        }
    }

    fun obtenerAvatarPathActual(): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return obtenerAvatarPath(uid)
    }

    fun obtenerAvatarPath(uid: String): String? {
        val path = prefs.getString(avatarKey(uid), null) ?: return null
        return if (File(path).exists()) path else null
    }

    fun eliminarAvatarActual(): Result<Unit> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: throw Exception("No hay usuario autenticado")

            val path = prefs.getString(avatarKey(uid), null)
            if (path != null) {
                val file = File(path)
                if (file.exists()) file.delete()
            }

            prefs.edit().remove(avatarKey(uid)).apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando avatar local", e)
            Result.failure(e)
        }
    }

    private fun determinarExtension(uri: Uri): String {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            when {
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                mimeType.contains("gif") -> "gif"
                mimeType.contains("bmp") -> "bmp"
                else -> "jpg"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detectando MIME type, usando jpg por defecto: ${e.message}")
            "jpg"
        }
    }

    private fun avatarKey(uid: String): String = "avatar_path_$uid"
}
