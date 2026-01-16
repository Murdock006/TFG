package com.example.tfg.data.firebase

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

    override suspend fun registrar(usuario: Usuario, password: String): Result<Usuario> {
        return try {
            val result = auth.createUserWithEmailAndPassword(usuario.email, password).await()
            val firebaseUser = result.user ?: throw Exception("Registro fallido: no hay usuario")
            // Guardar datos adicionales en Firestore
            val data = mapOf(
                "nombre" to usuario.nombre,
                "edad" to usuario.edad,
                "ciudad" to usuario.ciudad,
                "email" to usuario.email
            )
            firestore.collection("usuarios").document(firebaseUser.uid).set(data).await()
            Result.success(Usuario(id = firebaseUser.uid, nombre = usuario.nombre, edad = usuario.edad, ciudad = usuario.ciudad, email = firebaseUser.email ?: usuario.email))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(email: String, password: String): Result<Usuario> {
        return try {
            val res = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = res.user ?: throw Exception("Login fallido: no hay usuario")
            // Leer datos del usuario en Firestore
            val doc = firestore.collection("usuarios").document(firebaseUser.uid).get().await()
            val nombre = doc.getString("nombre") ?: ""
            val edad = doc.getLong("edad")?.toInt()
            val ciudad = doc.getString("ciudad")
            val user = Usuario(id = firebaseUser.uid, nombre = nombre, edad = edad, ciudad = ciudad, email = firebaseUser.email ?: email)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        auth.signOut()
    }

    override fun usuarioActual(): Usuario? {
        val u = auth.currentUser ?: return null
        // Nota: datos extra (nombre, edad, ciudad) no se devuelven aquí sin consultar Firestore
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
                Usuario(id = id, nombre = nombre, edad = edad, ciudad = ciudad, email = email)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }
}
