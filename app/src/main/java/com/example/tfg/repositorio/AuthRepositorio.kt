package com.example.tfg.repositorio

import com.example.tfg.modelo.Usuario
import kotlinx.coroutines.flow.Flow

interface AuthRepositorio {
    suspend fun registrar(usuario: Usuario, password: String): Result<Usuario>
    suspend fun login(email: String, password: String): Result<Usuario>
    suspend fun logout()
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
