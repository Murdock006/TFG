package com.example.tfg.data.firebase

import android.util.Log
import com.example.tfg.modelo.Usuario
import com.example.tfg.repositorio.AuthRepositorio
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Implementación Firebase para AuthRepositorio
class AuthRepositorioFirebase : AuthRepositorio {

    private val auth: FirebaseAuth = Firebase.auth
    private val firestore = Firebase.firestore
    private var usuariosListener: ListenerRegistration? = null
    private val TAG = "AuthRepoFirebase"

    override suspend fun registrar(usuario: Usuario, password: String): Result<Usuario> {
        return try {
            val result = auth.createUserWithEmailAndPassword(usuario.email, password).await()
            val firebaseUser = result.user ?: throw Exception("Registro fallido: no hay usuario")
            Log.d(TAG, "registrar OK uid=${firebaseUser.uid} email=${firebaseUser.email}")
            // Guardar datos adicionales en Firestore
            val data = mapOf(
                "nombre" to usuario.nombre,
                "edad" to usuario.edad,
                "ciudad" to usuario.ciudad,
                "email" to usuario.email,
                "puntos" to 1000,
                "puntosReservados" to 0
            )
            firestore.collection("usuarios").document(firebaseUser.uid).set(data).await()
            Log.d(TAG, "usuario document creado uid=${firebaseUser.uid}")
            Result.success(Usuario(id = firebaseUser.uid, nombre = usuario.nombre, edad = usuario.edad, ciudad = usuario.ciudad, email = firebaseUser.email ?: usuario.email))
        } catch (e: Exception) {
            Log.e(TAG, "Error registrar", e)
            // Mejora de mensajes para errores comunes
            val msg = when (e) {
                is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Contraseña débil: ${e.reason ?: e.message}"
                is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese email"
                is com.google.firebase.FirebaseNetworkException -> "Fallo de red: comprueba tu conexión"
                is com.google.firebase.auth.FirebaseAuthException -> "Error de autenticación: ${e.errorCode}"
                else -> e.message ?: "Error desconocido"
            }
            Result.failure(Exception(msg))
        }
    }

    override suspend fun login(email: String, password: String): Result<Usuario> {
        return try {
            val res = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = res.user ?: throw Exception("Login fallido: no hay usuario")
            Log.d(TAG, "login OK uid=${firebaseUser.uid} email=${firebaseUser.email}")
            // Leer datos del usuario en Firestore
            val docRef = firestore.collection("usuarios").document(firebaseUser.uid)
            val doc = docRef.get().await()
            if (!doc.exists()) {
                // crear documento por defecto si falta
                Log.w(TAG, "Documento usuario no existe, creando por defecto uid=${firebaseUser.uid}")
                val dataDefault = mapOf(
                    "nombre" to (firebaseUser.displayName ?: ""),
                    "edad" to null,
                    "ciudad" to null,
                    "email" to (firebaseUser.email ?: email),
                    "puntos" to 1000,
                    "puntosReservados" to 0
                )
                docRef.set(dataDefault).await()
            }

            val reloaded = docRef.get().await()
            val nombre = reloaded.getString("nombre") ?: ""
            val edad = reloaded.getLong("edad")?.toInt()
            val ciudad = reloaded.getString("ciudad")
            val puntos = reloaded.getLong("puntos")?.toInt() ?: 0
            val puntosReservados = reloaded.getLong("puntosReservados")?.toInt() ?: 0
            val user = Usuario(id = firebaseUser.uid, nombre = nombre, edad = edad, ciudad = ciudad, email = firebaseUser.email ?: email, puntos = puntos)
            Log.d(TAG, "usuario cargado desde Firestore uid=${firebaseUser.uid} puntos=$puntos")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error login", e)
            val msg = when (e) {
                is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "Usuario no encontrado"
                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Credenciales inválidas"
                is com.google.firebase.FirebaseNetworkException -> "Fallo de red: comprueba tu conexión"
                else -> e.message ?: "Error desconocido"
            }
            Result.failure(Exception(msg))
        }
    }

    override suspend fun logout() {
        auth.signOut()
    }

    override fun usuarioActual(): Usuario? {
        val u = auth.currentUser ?: return null
        // Nota: datos extra (nombre, edad, ciudad) no se devuelven aquí sin consultar Firestore
        Log.d(TAG, "usuarioActual uid=${u.uid} email=${u.email}")
        return Usuario(id = u.uid, nombre = u.displayName ?: "", edad = null, ciudad = null, email = u.email ?: "")
    }

    override fun observarUsuarios(): Flow<List<Usuario>> = callbackFlow {
        val coll = firestore.collection("usuarios")
        val listener = coll.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                val id = doc.id
                val nombre = doc.getString("nombre") ?: ""
                val edad = doc.getLong("edad")?.toInt()
                val ciudad = doc.getString("ciudad")
                val email = doc.getString("email") ?: ""
                val puntos = doc.getLong("puntos")?.toInt() ?: 0
                val puntosReservados = doc.getLong("puntosReservados")?.toInt() ?: 0
                Usuario(id = id, nombre = nombre, edad = edad, ciudad = ciudad, email = email, puntos = puntos)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun sumarPuntos(usuarioId: String, puntos: Int): Result<Int> {
        return try {
            val userRef = firestore.collection("usuarios").document(usuarioId)
            val result = firestore.runTransaction { t ->
                val snap = t.get(userRef)
                val actuales = (snap.getLong("puntos") ?: 0L).toInt()
                val nuevo = actuales + puntos
                t.update(userRef, "puntos", nuevo)
                nuevo
            }.await()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reservarPuntos(usuarioId: String, puntos: Int): Result<Unit> {
        return try {
            val userRef = firestore.collection("usuarios").document(usuarioId)
            firestore.runTransaction { t ->
                val snap = t.get(userRef)
                val actuales = (snap.getLong("puntos") ?: 0L).toInt()
                if (actuales < puntos) throw Exception("Fondos insuficientes")
                val reservados = (snap.getLong("puntosReservados") ?: 0L).toInt()
                t.update(userRef, mapOf("puntos" to (actuales - puntos), "puntosReservados" to (reservados + puntos)))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Implementación requerida por la interfaz: liberar puntos reservados (devolver a saldo disponible)
    override suspend fun liberarPuntos(usuarioId: String, puntos: Int): Result<Unit> {
        return try {
            val userRef = firestore.collection("usuarios").document(usuarioId)
            firestore.runTransaction { t ->
                val snap = t.get(userRef)
                val reservados = (snap.getLong("puntosReservados") ?: 0L).toInt()
                val actuales = (snap.getLong("puntos") ?: 0L).toInt()
                val aLiberar = minOf(puntos, reservados)
                val nuevoReservados = reservados - aLiberar
                val nuevoPuntos = actuales + aLiberar
                t.update(userRef, mapOf("puntos" to nuevoPuntos, "puntosReservados" to nuevoReservados))
            }.await()
            Log.d(TAG, "liberarPuntos OK usuario=$usuarioId puntos=$puntos")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "liberarPuntos error", e)
            Result.failure(e)
        }
    }

    override suspend fun comprarPuntos(usuarioId: String, puntos: Int): Result<Int> {
        return try {
            val userRef = firestore.collection("usuarios").document(usuarioId)
            val result = firestore.runTransaction { t ->
                val snap = t.get(userRef)
                val actuales = (snap.getLong("puntos") ?: 0L).toInt()
                val nuevo = actuales + puntos
                t.update(userRef, "puntos", nuevo)
                nuevo
            }.await()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sumarPuntosConBonificacion(usuarioId: String, basePuntos: Int): Result<Int> {
        return try {
            val userRef = firestore.collection("usuarios").document(usuarioId)
            val result = firestore.runTransaction { t ->
                val snap = t.get(userRef)
                val actuales = (snap.getLong("puntos") ?: 0L).toInt()
                val racha = (snap.getLong("rachaDias") ?: 0L).toInt()
                val nuevaRacha = racha + 1
                val bonus = if (nuevaRacha >= 7) (basePuntos * 0.10).toInt() else 0
                val totalAñadido = basePuntos + bonus
                val nuevo = actuales + totalAñadido
                t.update(userRef, mapOf("puntos" to nuevo, "rachaDias" to nuevaRacha))
                totalAñadido
            }.await()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
