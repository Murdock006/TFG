package com.example.tfg.repositorio

import com.example.tfg.modelo.Usuario
import kotlinx.coroutines.flow.Flow

// Resultado por paso de la limpieza de datos asociados a la cuenta.
// `nombre` es un id estable para auditorías/reintentos (p. ej. "grupo:abc:resetSaldos").
data class PasoLimpieza(
    val nombre: String,
    val descripcion: String,
    val exito: Boolean,
    val intentos: Int,
    val error: String? = null
)

// Reporte agregado de la limpieza. `mensajeFallos()` es el único texto que cruza al UI.
data class ResultadoLimpiezaCuenta(val pasos: List<PasoLimpieza>) {
    val completado: Boolean get() = pasos.all { it.exito }
    val fallidos: List<PasoLimpieza> get() = pasos.filterNot { it.exito }

    fun mensajeFallos(): String {
        val etiquetas = fallidos.map { it.descripcion }.distinct()
        return "No se pudo completar la limpieza de la cuenta" +
            if (etiquetas.isEmpty()) "." else " (${etiquetas.joinToString(", ")}). Inténtalo de nuevo."
    }
}

interface AuthRepositorio {
    suspend fun registrar(usuario: Usuario, password: String): Result<Usuario>
    suspend fun login(email: String, password: String): Result<Usuario>
    suspend fun logout()
    suspend fun eliminarCuentaActual(password: String? = null): Result<Unit>
    fun usuarioActual(): Usuario?
    fun observarUsuarios(): Flow<List<Usuario>>

    // operaciones de puntos
    suspend fun sumarPuntos(usuarioId: String, puntos: Int): Result<Int>
    suspend fun reservarPuntos(usuarioId: String, puntos: Int): Result<Unit>
    suspend fun liberarPuntos(usuarioId: String, puntos: Int): Result<Unit>
    suspend fun comprarPuntos(usuarioId: String, puntos: Int): Result<Int>

    // sumar puntos aplicando bonificaciones por racha (devuelve puntos totales añadidos)
    suspend fun sumarPuntosConBonificacion(usuarioId: String, basePuntos: Int): Result<Int>

    // Soporte para iniciar sesión usando token de proveedor externo (ej. Google -> idToken)
    suspend fun loginConTokenProveedor(idToken: String, proveedor: String = "google"): Result<Usuario>
}
