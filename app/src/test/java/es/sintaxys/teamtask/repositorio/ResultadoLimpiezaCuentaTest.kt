package es.sintaxys.teamtask.repositorio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused JVM test for [ResultadoLimpiezaCuenta], the report that gates the Firebase Auth
 * account deletion.
 *
 * These cases intentionally use only pure-JVM types: no Firebase or Android types are touched,
 * so the test runs in the default host JVM path. They cover the "never report success when a
 * step failed" invariant that the manual matrix cannot automate.
 */
class ResultadoLimpiezaCuentaTest {

    private fun paso(
        nombre: String,
        descripcion: String,
        exito: Boolean = true,
        intentos: Int = 1,
        error: String? = null
    ) = PasoLimpieza(nombre, descripcion, exito, intentos, error)

    @Test
    fun completado_allStepsSuccessful_isTrue() {
        val reporte = ResultadoLimpiezaCuenta(
            listOf(
                paso("tareas:creadas", "tareas creadas por el usuario"),
                paso("avatar", "avatar"),
                paso("usuario", "perfil de usuario")
            )
        )

        assertTrue(reporte.completado)
        assertTrue(reporte.fallidos.isEmpty())
    }

    @Test
    fun completado_oneFailedStep_isFalseAndListsTheStep() {
        val fallido = paso("usuario", "perfil de usuario", exito = false, intentos = 3, error = "boom")
        val reporte = ResultadoLimpiezaCuenta(
            listOf(
                paso("avatar", "avatar"),
                fallido
            )
        )

        assertFalse(reporte.completado)
        assertEquals(listOf(fallido), reporte.fallidos)
        assertTrue(reporte.mensajeFallos().contains("perfil de usuario"))
    }

    @Test
    fun mensajeFallos_duplicateDescriptions_areDeduplicated() {
        val reporte = ResultadoLimpiezaCuenta(
            listOf(
                paso("grupo:a:disolver", "disolución del grupo", exito = false),
                paso("grupo:a:resetSaldos", "disolución del grupo", exito = false)
            )
        )

        val ocurrencias = "disolución del grupo".toRegex().findAll(reporte.mensajeFallos()).count()

        assertEquals(1, ocurrencias)
    }

    @Test
    fun completado_emptyReport_isTrue() {
        assertTrue(ResultadoLimpiezaCuenta(emptyList()).completado)
    }
}
