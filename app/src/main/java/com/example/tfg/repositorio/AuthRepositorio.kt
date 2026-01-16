package com.example.tfg.repositorio

import com.example.tfg.modelo.Usuario
import kotlinx.coroutines.flow.Flow

interface AuthRepositorio {
    suspend fun registrar(usuario: Usuario, password: String): Result<Usuario>
    suspend fun login(email: String, password: String): Result<Usuario>
    suspend fun logout()
    fun usuarioActual(): Usuario?
    fun observarUsuarios(): Flow<List<Usuario>>
}
