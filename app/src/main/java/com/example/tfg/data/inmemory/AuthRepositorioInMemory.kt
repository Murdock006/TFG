package com.example.tfg.data.inmemory

import com.example.tfg.modelo.Usuario
import com.example.tfg.repositorio.AuthRepositorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class AuthRepositorioInMemory : AuthRepositorio {

    private val usuarios = mutableListOf<Usuario>()
    private var usuarioLogueado: Usuario? = null
    private val usuariosFlow = MutableStateFlow<List<Usuario>>(emptyList())

    override suspend fun registrar(usuario: Usuario, password: String): Result<Usuario> {
        return withContext(Dispatchers.Default) {
            if (usuarios.any { it.email == usuario.email }) {
                Result.failure(Exception("El email ya está registrado"))
            } else {
                val u = usuario.copy(id = UUID.randomUUID().toString())
                usuarios.add(u)
                usuariosFlow.value = usuarios.toList()
                usuarioLogueado = u
                Result.success(u)
            }
        }
    }

    override suspend fun login(email: String, password: String): Result<Usuario> {
        return withContext(Dispatchers.Default) {
            val u = usuarios.find { it.email == email }
            if (u != null) {
                usuarioLogueado = u
                Result.success(u)
            } else {
                Result.failure(Exception("Usuario no encontrado"))
            }
        }
    }

    override suspend fun logout() {
        usuarioLogueado = null
    }

    override fun usuarioActual(): Usuario? = usuarioLogueado

    override fun observarUsuarios(): Flow<List<Usuario>> = usuariosFlow
}
