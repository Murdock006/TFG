package com.example.tfg.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Pure image helpers for the avatar path: downscale/compress, base64 encode/decode, and
 * JPEG decode.
 *
 * Encoding uses [java.util.Base64] (standard alphabet, no line wrapping) instead of
 * `android.util.Base64`, which keeps [codificarBase64] and [decodificarBase64] pure-JVM and
 * unit-testable without Robolectric.
 */
object AvatarImagen {

    const val LADO_LARGO_PX = 256
    const val CALIDAD_JPEG = 75
    const val MAX_BASE64_CHARS = 700_000
    const val CONTENT_TYPE_JPEG = "image/jpeg"

    /**
     * Reads [uri] through [resolver], downscales it so its long edge is [ladoLargo], and
     * encodes the result as a JPEG at [calidad].
     *
     * Two-pass decode: first read the dimensions with `inJustDecodeBounds`, then decode with a
     * power-of-two `inSampleSize` so the decoded long edge stays >= [ladoLargo], then scale to
     * exactly [ladoLargo] on the long edge.
     *
     * @return the compressed JPEG bytes, or `null` when the image cannot be read or decoded.
     */
    fun comprimirJpeg(
        resolver: ContentResolver,
        uri: Uri,
        ladoLargo: Int = LADO_LARGO_PX,
        calidad: Int = CALIDAD_JPEG
    ): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opciones = BitmapFactory.Options().apply {
                inSampleSize = calcularInSampleSize(bounds.outWidth, bounds.outHeight, ladoLargo)
            }
            val decodificado = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return null

            val escalado = escalarLadoLargo(decodificado, ladoLargo)
            val salida = ByteArrayOutputStream()
            val ok = escalado.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
            if (escalado !== decodificado) escalado.recycle()
            decodificado.recycle()
            if (!ok) return null
            salida.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /** Encodes [bytes] as standard base64 with no line wrapping. */
    fun codificarBase64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    /** Decodes a standard base64 string, or returns `null` when it is malformed. */
    fun decodificarBase64(base64: String): ByteArray? = try {
        Base64.getDecoder().decode(base64)
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Decodes [bytes] as a JPEG bitmap, or returns `null` when it cannot be decoded. */
    fun decodificarJpeg(bytes: ByteArray): Bitmap? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    private fun calcularInSampleSize(ancho: Int, alto: Int, ladoLargo: Int): Int {
        var muestra = 1
        val ladoMayor = maxOf(ancho, alto)
        while (ladoMayor / (muestra * 2) >= ladoLargo) {
            muestra *= 2
        }
        return muestra
    }

    private fun escalarLadoLargo(bitmap: Bitmap, ladoLargo: Int): Bitmap {
        val ancho = bitmap.width
        val alto = bitmap.height
        val ladoMayor = maxOf(ancho, alto)
        if (ladoMayor <= ladoLargo) return bitmap
        val factor = ladoLargo.toFloat() / ladoMayor.toFloat()
        val nuevoAncho = (ancho * factor).toInt().coerceAtLeast(1)
        val nuevoAlto = (alto * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nuevoAncho, nuevoAlto, true)
    }
}
