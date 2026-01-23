package com.example.tfg.repositorio

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

// Repositorio simple para autenticación con Firebase
class RepositorioAutenticacion(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    suspend fun registrar(email: String, contraseña: String): Result<FirebaseUser?> {
        return try {
            val resultado = auth.createUserWithEmailAndPassword(email, contraseña).await()
            Result.success(resultado.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun iniciarSesion(email: String, contraseña: String): Result<FirebaseUser?> {
        return try {
            val resultado = auth.signInWithEmailAndPassword(email, contraseña).await()
            Result.success(resultado.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cerrarSesion() {
        auth.signOut()
    }

    fun usuarioActual(): FirebaseUser? = auth.currentUser
}
