package es.sintaxys.teamtask.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Fuente única de verdad para el tema elegido por el usuario (Claro / Oscuro / Sistema).
 *
 * Persiste la elección en SharedPreferences ("tfg_prefs") y la traduce al modo de
 * [AppCompatDelegate]. Se aplica en `TFGApplication.onCreate` para que el tema esté
 * activo antes de que cualquier Activity infle su UI.
 */
object PreferenciasTema {

    const val MODO_CLARO = "claro"
    const val MODO_OSCURO = "oscuro"
    const val MODO_SISTEMA = "sistema"

    private const val PREFS_NAME = "tfg_prefs"
    private const val CLAVE_MODO_TEMA = "modo_tema"

    /** Modo guardado por el usuario, o [MODO_SISTEMA] si todavía no eligió ninguno. */
    fun leerModo(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(CLAVE_MODO_TEMA, MODO_SISTEMA) ?: MODO_SISTEMA

    /** Aplica el modo guardado a AppCompatDelegate. Debe llamarse antes de inflar UI. */
    fun aplicarTemaGuardado(context: Context) {
        aplicar(leerModo(context))
    }

    /** Persiste el modo elegido y lo aplica de inmediato (la Activity se recrea sola). */
    fun guardarYAplicar(context: Context, modo: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(CLAVE_MODO_TEMA, modo)
            .apply()
        aplicar(modo)
    }

    /** Traduce el modo de la app al modo noche de AppCompatDelegate. */
    private fun aplicar(modo: String) {
        val nightMode = when (modo) {
            MODO_CLARO -> AppCompatDelegate.MODE_NIGHT_NO
            MODO_OSCURO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
