package es.sintaxys.teamtask.data.local

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Last-known avatar cache.
 *
 * This class is NOT an avatar authority. The avatar authority is the canonical
 * [es.sintaxys.teamtask.repositorio.AvatarRepositorio] (Firestore). This cache only stores the most
 * recent compressed JPEG bytes it has seen for a uid, so the UI can degrade gracefully when
 * Firestore is unreachable. A `tfg_prefs`/file hit MUST NOT be treated as proof that a remote
 * avatar exists or is current.
 */
class AvatarRepositorioLocal(private val context: Context) {

    private val prefs = context.getSharedPreferences("tfg_prefs", Context.MODE_PRIVATE)
    private val TAG = "AvatarRepoLocal"

    /**
     * Persists [jpegBytes] as the last-known avatar for [uid], replacing any prior file, and
     * records its path under the retained `avatar_path_$uid` key.
     *
     * @return `true` when the bytes were written, `false` on any I/O failure.
     */
    fun guardarUltimoConocido(uid: String, jpegBytes: ByteArray): Boolean {
        return try {
            val avatarDir = File(context.filesDir, "avatars")
            if (!avatarDir.exists()) {
                avatarDir.mkdirs()
            }
            val file = File(avatarDir, "$uid.jpg")
            file.writeBytes(jpegBytes)
            prefs.edit().putString(clave(uid), file.absolutePath).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando el último avatar conocido", e)
            false
        }
    }

    /** Reads the last-known compressed JPEG bytes for [uid], or null when nothing is cached. */
    fun obtenerUltimoConocido(uid: String): ByteArray? {
        return try {
            val path = prefs.getString(clave(uid), null) ?: return null
            val file = File(path)
            if (!file.exists()) return null
            file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo el último avatar conocido", e)
            null
        }
    }

    /** Clears the cached copy for [uid]. Does NOT touch the remote document. */
    fun limpiar(uid: String) {
        try {
            val path = prefs.getString(clave(uid), null)
            if (path != null) {
                val file = File(path)
                if (file.exists()) file.delete()
            }
            prefs.edit().remove(clave(uid)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando el avatar local", e)
        }
    }

    private fun clave(uid: String): String = "avatar_path_$uid"
}
