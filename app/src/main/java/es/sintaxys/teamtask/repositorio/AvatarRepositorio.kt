package es.sintaxys.teamtask.repositorio

import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.Timestamp

/**
 * Canonical avatar contract. It is the single avatar authority for TeamTask: every read
 * and write of avatar data goes through this interface, resolved via
 * [es.sintaxys.teamtask.service.LocalizadorServicios.repositorioAvatar].
 *
 * There is deliberately no delete operation: avatar deletion is out of scope.
 */
interface AvatarRepositorio {

    /**
     * Uploads [imageUri] for the SIGNED-IN user only. The implementation compresses the
     * image client-side, base64-encodes it, enforces the size cap, then atomically writes
     * `avatares/{uid}` and the `usuarios/{uid}.avatarUpdatedAt` hint.
     *
     * @return the new version marker (also written as the hint) on success; `Result.failure`
     *         with no partial write when there is no session, the image cannot be decoded, or
     *         the encoded payload exceeds [es.sintaxys.teamtask.util.AvatarImagen.MAX_BASE64_CHARS].
     */
    suspend fun subirAvatar(imageUri: Uri): Result<Timestamp>

    /**
     * Resolves the avatar bitmap for ANY uid (current user or group member).
     *
     * Returns `null` when there is nothing to show, so callers render `R.drawable.perfil`.
     * Performs NO Firestore read when [avatarUpdatedAt] is null.
     * Never throws: a remote failure degrades to the local last-known avatar or `null`.
     */
    suspend fun obtenerAvatar(uid: String, avatarUpdatedAt: Timestamp?): Bitmap?
}
