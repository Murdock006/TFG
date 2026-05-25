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
                val u = usuario.copy(id = UUID.randomUUID().toString(), puntos = 1000)
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

    override suspend fun eliminarCuentaActual(password: String?): Result<Unit> {
        return withContext(Dispatchers.Default) {
            val actual = usuarioLogueado ?: return@withContext Result.failure(Exception("No hay sesión activa"))
            val eliminado = usuarios.removeIf { it.id == actual.id }
            usuarioLogueado = null
            usuariosFlow.value = usuarios.toList()
            if (eliminado) Result.success(Unit) else Result.failure(Exception("No se pudo eliminar la cuenta local"))
        }
    }

    override fun usuarioActual(): Usuario? = usuarioLogueado

    override fun observarUsuarios(): Flow<List<Usuario>> = usuariosFlow

    override suspend fun sumarPuntos(usuarioId: String, puntos: Int): Result<Int> {
        return withContext(Dispatchers.Default) {
            val idx = usuarios.indexOfFirst { it.id == usuarioId }
            if (idx < 0) return@withContext Result.failure(Exception("Usuario no encontrado"))
            val u = usuarios[idx]
            val nuevo = u.copy(puntos = u.puntos + puntos)
            usuarios[idx] = nuevo
            if (usuarioLogueado?.id == usuarioId) usuarioLogueado = nuevo
            usuariosFlow.value = usuarios.toList()
            Result.success(nuevo.puntos)
        }
    }

    override suspend fun reservarPuntos(usuarioId: String, puntos: Int): Result<Unit> {
        return withContext(Dispatchers.Default) {
            val idx = usuarios.indexOfFirst { it.id == usuarioId }
            if (idx < 0) return@withContext Result.failure(Exception("Usuario no encontrado"))
            val u = usuarios[idx]
            if (u.puntos < puntos) return@withContext Result.failure(Exception("Fondos insuficientes"))
            val nuevo = u.copy(puntos = u.puntos - puntos, puntosReservados = u.puntosReservados + puntos)
            usuarios[idx] = nuevo
            if (usuarioLogueado?.id == usuarioId) usuarioLogueado = nuevo
            usuariosFlow.value = usuarios.toList()
            Result.success(Unit)
        }
    }

    override suspend fun liberarPuntos(usuarioId: String, puntos: Int): Result<Unit> {
        return withContext(Dispatchers.Default) {
            val idx = usuarios.indexOfFirst { it.id == usuarioId }
            if (idx < 0) return@withContext Result.failure(Exception("Usuario no encontrado"))
            val u = usuarios[idx]
            val puntosReservados = u.puntosReservados
            val aLiberar = if (puntos > puntosReservados) puntosReservados else puntos
            val nuevo = u.copy(puntos = u.puntos + aLiberar, puntosReservados = u.puntosReservados - aLiberar)
            usuarios[idx] = nuevo
            if (usuarioLogueado?.id == usuarioId) usuarioLogueado = nuevo
            usuariosFlow.value = usuarios.toList()
            Result.success(Unit)
        }
    }

    override suspend fun comprarPuntos(usuarioId: String, puntos: Int): Result<Int> {
        return withContext(Dispatchers.Default) {
            val idx = usuarios.indexOfFirst { it.id == usuarioId }
            if (idx < 0) return@withContext Result.failure(Exception("Usuario no encontrado"))
            val u = usuarios[idx]
            val nuevo = u.copy(puntos = u.puntos + puntos)
            usuarios[idx] = nuevo
            if (usuarioLogueado?.id == usuarioId) usuarioLogueado = nuevo
            usuariosFlow.value = usuarios.toList()
            Result.success(nuevo.puntos)
        }
    }

    override suspend fun sumarPuntosConBonificacion(usuarioId: String, basePuntos: Int): Result<Int> {
        return withContext(Dispatchers.Default) {
            val idx = usuarios.indexOfFirst { it.id == usuarioId }
            if (idx < 0) return@withContext Result.failure(Exception("Usuario no encontrado"))
            val u = usuarios[idx]
            val nuevaRacha = u.rachaDias + 1
            val bonus = if (nuevaRacha >= 7) (basePuntos * 0.10).toInt() else 0
            val totalAñadido = basePuntos + bonus
            val nuevo = u.copy(puntos = u.puntos + totalAñadido, rachaDias = nuevaRacha)
            usuarios[idx] = nuevo
            if (usuarioLogueado?.id == usuarioId) usuarioLogueado = nuevo
            usuariosFlow.value = usuarios.toList()
            Result.success(totalAñadido)
        }
    }

    // Implementación simulada para login con token de proveedor (ej. Google)
    override suspend fun loginConTokenProveedor(idToken: String, proveedor: String): Result<Usuario> {
        return withContext(Dispatchers.Default) {
            // Si el token contiene un '@' lo interpretamos como email (prueba local).
            val email = if (idToken.contains('@')) idToken else "user_${UUID.randomUUID()}@${proveedor}.local"
            val existente = usuarios.find { it.email == email }
            if (existente != null) {
                usuarioLogueado = existente
                usuariosFlow.value = usuarios.toList()
                Result.success(existente)
            } else {
                val nuevo = Usuario(
                    id = UUID.randomUUID().toString(),
                    nombre = "Usuario ${proveedor.capitalize()}",
                    fechaNacimiento = null,
                    sexo = null,
                    pais = null,
                    ciudad = null,
                    email = email,
                    puntos = 1000
                )
                usuarios.add(nuevo)
                usuarioLogueado = nuevo
                usuariosFlow.value = usuarios.toList()
                Result.success(nuevo)
            }
        }
    }
}
