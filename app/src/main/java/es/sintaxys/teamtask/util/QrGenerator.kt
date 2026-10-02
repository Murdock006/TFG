package es.sintaxys.teamtask.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * Generates QR code bitmaps with ZXing.
 *
 * Kept isolated from the UI so fragments only request a bitmap and publish it.
 * Encoding should be invoked off the main thread by the caller.
 */
object QrGenerator {

    private const val TAG = "QrGenerator"

    /**
     * Encodes [texto] as a QR code of [tamanoPx] x [tamanoPx] pixels.
     *
     * @return a black/white ARGB_8888 bitmap, or null when [texto] is blank,
     *         [tamanoPx] is not positive, or ZXing cannot encode the content.
     */
    fun generar(texto: String, tamanoPx: Int): Bitmap? {
        if (texto.isBlank() || tamanoPx <= 0) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix: BitMatrix = MultiFormatWriter().encode(
                texto,
                BarcodeFormat.QR_CODE,
                tamanoPx,
                tamanoPx,
                hints
            )
            val bitmap = Bitmap.createBitmap(tamanoPx, tamanoPx, Bitmap.Config.ARGB_8888)
            val negro = Color.BLACK
            val blanco = Color.WHITE
            for (x in 0 until tamanoPx) {
                for (y in 0 until tamanoPx) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) negro else blanco)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo generar el QR: ${e.message}")
            null
        }
    }
}
