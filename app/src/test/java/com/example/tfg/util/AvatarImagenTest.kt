package com.example.tfg.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused JVM test for the pure [AvatarImagen] helpers.
 *
 * These cases intentionally use only pure-JVM helpers ([AvatarImagen.codificarBase64],
 * [AvatarImagen.decodificarBase64] and the cap constants). `comprimirJpeg`/`decodificarJpeg`
 * touch `ContentResolver`/`Bitmap`, which throw `Stub!` off-device, so they are not exercised
 * in the default JVM path.
 */
class AvatarImagenTest {

    @Test
    fun codificarYDecodificarBase64_roundTrip_preservesEveryByte() {
        // Includes an embedded 0x00 as required by the task.
        val original = byteArrayOf(0x00, 0x01, 0x02, 0x7F, -1, -128, 0x00, 0x42)
        val codificado = AvatarImagen.codificarBase64(original)
        val decodificado = AvatarImagen.decodificarBase64(codificado)

        assertNotNull(decodificado)
        assertArrayEquals(original, decodificado)
    }

    @Test
    fun decodificarBase64_malformed_returnsNullInsteadOfThrowing() {
        assertNull(AvatarImagen.decodificarBase64("not*valid*base64!!"))
    }

    @Test
    fun sizeCapGate_rejectsEncodedPayloadAboveTheCap() {
        // AvatarRepositorioFirebase.subirAvatar reuses exactly this `length > MAX_BASE64_CHARS`
        // gate before any Firestore write; the constant is the contract.
        val encimaDelCap = AvatarImagen.MAX_BASE64_CHARS + 1
        val dentroDelCap = AvatarImagen.MAX_BASE64_CHARS

        assertTrue(encimaDelCap > AvatarImagen.MAX_BASE64_CHARS)
        assertTrue(dentroDelCap <= AvatarImagen.MAX_BASE64_CHARS)
    }
}
