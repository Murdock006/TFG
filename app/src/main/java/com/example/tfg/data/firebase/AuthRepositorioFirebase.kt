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

    // Caché del usuario completo (se actualiza en login y se actualiza vía observarUsuarios)
    private var _usuarioCache: Usuario? = null

    override suspend fun registrar(usuario: Usuario, password: String): Result<Usuario> {
        return try {
            val result = auth.createUserWithEmailAndPassword(usuario.email, password).await()
            val firebaseUser = result.user ?: throw Exception("Registro fallido: no hay usuario")
            Log.d(TAG, "registrar OK uid=${firebaseUser.uid} email=${firebaseUser.email}")
            
            // ENVIAR EMAIL DE VERIFICACIÓN
            firebaseUser.sendEmailVerification().await()
            Log.d(TAG, "Email de verificación enviado a ${firebaseUser.email}")
            
            // Guardar datos adicionales en Firestore
            val data = mapOf(
                "nombre" to usuario.nombre,
                "edad" to usuario.edad,
                "ciudad" to usuario.ciudad,
                "email" to usuario.email,
                "puntos" to 1000,
                "puntosReservados" to 0,
                "puntosRecompensa" to 0
            )
            firestore.collection("usuarios").document(firebaseUser.uid).set(data).await()
            Log.d(TAG, "usuario document creado uid=${firebaseUser.uid}")
            
            // Cerrar sesión inmediatamente hasta que verifique el email
            auth.signOut()
            Log.d(TAG, "Sesión cerrada. Usuario debe verificar email antes de iniciar sesión.")
            
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
            
            // VERIFICAR QUE EL EMAIL ESTÉ VERIFICADO
            if (!firebaseUser.isEmailVerified) {
                auth.signOut()
                throw Exception("Debes verificar tu correo electrónico antes de iniciar sesión. Revisa tu bandeja de entrada.")
            }
            
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
                    "puntosReservados" to 0,
                    "puntosRecompensa" to 0
                )
                docRef.set(dataDefault).await()
            }

            val reloaded = docRef.get().await()
            val nombre = reloaded.getString("nombre") ?: ""
            val edad = reloaded.getLong("edad")?.toInt()
            val ciudad = reloaded.getString("ciudad")
            val puntos = reloaded.getLong("puntos")?.toInt() ?: 0
            val puntosReservados = reloaded.getLong("puntosReservados")?.toInt() ?: 0
            val puntosRecompensa = reloaded.getLong("puntosRecompensa")?.toInt() ?: 0
            val user = Usuario(id = firebaseUser.uid, nombre = nombre, edad = edad, ciudad = ciudad, email = firebaseUser.email ?: email, puntos = puntos, puntosReservados = puntosReservados, puntosRecompensa = puntosRecompensa)
            Log.d(TAG, "usuario cargado desde Firestore uid=${firebaseUser.uid} puntos=$puntos")
            _usuarioCache = user
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error login", e)
            val msg = when {
                e.message?.contains("verificar tu correo") == true -> e.message ?: "Email no verificado"
                e is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "Usuario no encontrado"
                e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Email o contraseña incorrectos"
                e is com.google.firebase.FirebaseNetworkException -> "Fallo de red: comprueba tu conexión"
                e is com.google.firebase.auth.FirebaseAuthException && e.errorCode == "INVALID_API_KEY" ->
                    "API key inválida. Descarga el google-services.json actualizado de Firebase Console"
                e is com.google.firebase.auth.FirebaseAuthException && e.errorCode == "API_KEY_SERVICE_BLOCKED" ->
                    "La API key tiene restricciones. Habilita 'Identity Toolkit API' en Google Cloud Console"
                e is com.google.firebase.auth.FirebaseAuthException ->
                    "Error auth [${e.errorCode}]: ${e.message}"
                else -> e.message ?: "Error desconocido"
            }
            Result.failure(Exception(msg))
        }
    }

    // Inicio de sesión con token de proveedor externo (ej. Google idToken)
    override suspend fun loginConTokenProveedor(idToken: String, proveedor: String): Result<Usuario> {
        return try {
            // Actualmente implementamos para Google: crear credencial y firmar con FirebaseAuth
            val cred = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val res = auth.signInWithCredential(cred).await()
            val firebaseUser = res.user ?: throw Exception("Login con proveedor fallido: no hay usuario")
            Log.d(TAG, "loginConTokenProveedor OK uid=${firebaseUser.uid} provider=$proveedor email=${firebaseUser.email}")

            // Asegurar documento en Firestore y recuperar datos como en login()
            val docRef = firestore.collection("usuarios").document(firebaseUser.uid)
            val doc = docRef.get().await()
            if (!doc.exists()) {
                val dataDefault = mapOf(
                    "nombre" to (firebaseUser.displayName ?: ""),
                    "edad" to null,
                    "ciudad" to null,
                    "email" to (firebaseUser.email ?: ""),
                    "puntos" to 1000,
                    "puntosReservados" to 0,
                    "puntosRecompensa" to 0
                )
                docRef.set(dataDefault).await()
            }
            val reloaded = docRef.get().await()
             val nombre = reloaded.getString("nombre") ?: (firebaseUser.displayName ?: "")
             val edad = reloaded.getLong("edad")?.toInt()
             val ciudad = reloaded.getString("ciudad")
             val puntos = reloaded.getLong("puntos")?.toInt() ?: 0
             val puntosReservados = reloaded.getLong("puntosReservados")?.toInt() ?: 0
             val puntosRecompensa = reloaded.getLong("puntosRecompensa")?.toInt() ?: 0
             val user = Usuario(id = firebaseUser.uid, nombre = nombre, edad = edad, ciudad = ciudad, email = firebaseUser.email ?: "", puntos = puntos, puntosReservados = puntosReservados, puntosRecompensa = puntosRecompensa)
            _usuarioCache = user
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "loginConTokenProveedor error", e)
            Result.failure(Exception(e.message ?: "Error login proveedor"))
        }
    }

    override suspend fun logout() {
        _usuarioCache = null
        auth.signOut()
    }

    override fun usuarioActual(): Usuario? {
        val u = auth.currentUser ?: return null
        Log.d(TAG, "usuarioActual uid=${u.uid} email=${u.email}")
        // Devolver caché si coincide con el usuario autenticado (tiene puntos y puntosRecompensa reales)
        return _usuarioCache?.takeIf { it.id == u.uid }
            ?: Usuario(id = u.uid, nombre = u.displayName ?: "", edad = null, ciudad = null, email = u.email ?: "")
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
                val puntosRecompensa = doc.getLong("puntosRecompensa")?.toInt() ?: 0
                Usuario(id = id, nombre = nombre, edad = edad, ciudad = ciudad, email = email,
                    puntos = puntos, puntosReservados = puntosReservados, puntosRecompensa = puntosRecompensa)
            } ?: emptyList()
            // Actualizar caché del usuario actual con los datos frescos de Firestore
            val uidActual = auth.currentUser?.uid
            if (uidActual != null) {
                list.find { it.id == uidActual }?.let { _usuarioCache = it }
            }
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
