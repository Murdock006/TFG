package es.sintaxys.teamtask.data.firebase

import android.util.Log
import es.sintaxys.teamtask.modelo.Usuario
import es.sintaxys.teamtask.repositorio.AuthRepositorio
import es.sintaxys.teamtask.repositorio.PasoLimpieza
import es.sintaxys.teamtask.repositorio.ResultadoLimpiezaCuenta
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import es.sintaxys.teamtask.util.Constants
import es.sintaxys.teamtask.service.firebase.FirebaseComposition
import kotlinx.coroutines.flow.callbackFlow

// Implementación Firebase para AuthRepositorio
class AuthRepositorioFirebase(
    private val auth: FirebaseAuth = FirebaseComposition.auth(),
    private val firestore: com.google.firebase.firestore.FirebaseFirestore = FirebaseComposition.firestore(),
    private val storage: FirebaseStorage = FirebaseComposition.storage()
) : AuthRepositorio {

    private companion object {
        // Presupuesto acotado de reintentos por paso de limpieza (errores transitorios).
        const val MAX_INTENTOS_LIMPIEZA = 3
        const val DELAY_REINTENTO_MS = 300L
        const val LIMITE_BATCH_FIRESTORE = 500
    }

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
                "fechaNacimiento" to usuario.fechaNacimiento,
                "sexo" to usuario.sexo,
                "pais" to usuario.pais,
                "ciudad" to usuario.ciudad,
                "email" to usuario.email,
                "puntos" to Constants.INITIAL_POINTS,
                "puntosReservados" to 0,
                "puntosRecompensa" to 0
            )
            firestore.collection("usuarios").document(firebaseUser.uid).set(data).await()
            Log.d(TAG, "usuario document creado uid=${firebaseUser.uid}")
            
            // Cerrar sesión inmediatamente hasta que verifique el email
            auth.signOut()
            Log.d(TAG, "Sesión cerrada. Usuario debe verificar email antes de iniciar sesión.")
            
            Result.success(
                Usuario(
                    id = firebaseUser.uid,
                    nombre = usuario.nombre,
                    fechaNacimiento = usuario.fechaNacimiento,
                    sexo = usuario.sexo,
                    pais = usuario.pais,
                    ciudad = usuario.ciudad,
                    email = firebaseUser.email ?: usuario.email
                )
            )
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
                    "fechaNacimiento" to null,
                    "sexo" to null,
                    "pais" to null,
                    "ciudad" to null,
                    "email" to (firebaseUser.email ?: email),
                    "puntos" to Constants.INITIAL_POINTS,
                    "puntosReservados" to 0,
                    "puntosRecompensa" to 0
                )
                docRef.set(dataDefault).await()
            }

            val reloaded = docRef.get().await()
            val nombre = reloaded.getString("nombre") ?: ""
            val fechaNacimiento = reloaded.getString("fechaNacimiento")
            val sexo = reloaded.getString("sexo")
            val pais = reloaded.getString("pais")
            val ciudad = reloaded.getString("ciudad")
            val puntos = reloaded.getLong("puntos")?.toInt() ?: 0
            val puntosReservados = reloaded.getLong("puntosReservados")?.toInt() ?: 0
            val puntosRecompensa = reloaded.getLong("puntosRecompensa")?.toInt() ?: 0
            val rachaDias = reloaded.getLong("rachaDias")?.toInt() ?: 0
            val user = Usuario(
                id = firebaseUser.uid,
                nombre = nombre,
                fechaNacimiento = fechaNacimiento,
                sexo = sexo,
                pais = pais,
                ciudad = ciudad,
                email = firebaseUser.email ?: email,
                puntos = puntos,
                puntosReservados = puntosReservados,
                puntosRecompensa = puntosRecompensa,
                rachaDias = rachaDias,
                avatarUpdatedAt = reloaded.getTimestamp("avatarUpdatedAt")
            )
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
                    "fechaNacimiento" to null,
                    "sexo" to null,
                    "pais" to null,
                    "ciudad" to null,
                    "email" to (firebaseUser.email ?: ""),
                    "puntos" to Constants.INITIAL_POINTS,
                    "puntosReservados" to 0,
                    "puntosRecompensa" to 0
                )
                docRef.set(dataDefault).await()
            }
            val reloaded = docRef.get().await()
             val nombre = reloaded.getString("nombre") ?: (firebaseUser.displayName ?: "")
             val fechaNacimiento = reloaded.getString("fechaNacimiento")
             val sexo = reloaded.getString("sexo")
             val pais = reloaded.getString("pais")
             val ciudad = reloaded.getString("ciudad")
             val puntos = reloaded.getLong("puntos")?.toInt() ?: 0
             val puntosReservados = reloaded.getLong("puntosReservados")?.toInt() ?: 0
             val puntosRecompensa = reloaded.getLong("puntosRecompensa")?.toInt() ?: 0
             val rachaDias = reloaded.getLong("rachaDias")?.toInt() ?: 0
             val user = Usuario(
                 id = firebaseUser.uid,
                 nombre = nombre,
                 fechaNacimiento = fechaNacimiento,
                 sexo = sexo,
                 pais = pais,
                 ciudad = ciudad,
                 email = firebaseUser.email ?: "",
                 puntos = puntos,
                 puntosReservados = puntosReservados,
                 puntosRecompensa = puntosRecompensa,
                 rachaDias = rachaDias,
                 avatarUpdatedAt = reloaded.getTimestamp("avatarUpdatedAt")
             )
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

    override suspend fun eliminarCuentaActual(password: String?): Result<Unit> {
        val usuarioActual = auth.currentUser ?: return Result.failure(Exception("No hay sesión activa"))
        val uid = usuarioActual.uid

        return try {
            // Re-autenticar si se proporciona contraseña (requerido por Firebase para eliminar cuenta)
            if (!password.isNullOrBlank()) {
                try {
                    val email = usuarioActual.email
                    if (!email.isNullOrBlank()) {
                        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                        usuarioActual.reauthenticate(credential).await()
                        Log.d(TAG, "Re-autenticación exitosa para uid=$uid")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Re-autenticación fallida para uid=$uid", e)
                    return Result.failure(Exception("Contraseña incorrecta. No se puede eliminar la cuenta sin verificar tu identidad."))
                }
            }

            // 1) Limpiar datos asociados y recoger el reporte por paso (nunca lanza por paso).
            val reporte = limpiarDatosAsociados(uid)
            if (!reporte.completado) {
                Log.w(TAG, "Limpieza incompleta; no se elimina la cuenta uid=$uid: ${reporte.mensajeFallos()}")
                return Result.failure(Exception(reporte.mensajeFallos()))
            }

            // 2) Borrar cuenta de Firebase Auth SOLO con limpieza completa.
            usuarioActual.delete().await()

            // 3) Limpiar caché/sesión local
            _usuarioCache = null
            auth.signOut()

            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Log.w(TAG, "eliminarCuentaActual requiere reautenticación", e)
            Result.failure(Exception("Por seguridad, introduce tu contraseña para confirmar la eliminación."))
        } catch (e: Exception) {
            Log.e(TAG, "eliminarCuentaActual error", e)
            Result.failure(Exception(e.message ?: "No se pudo eliminar la cuenta"))
        }
    }

    // Colector exception-safe: intenta todos los pasos en orden y nunca lanza por un fallo
    // de paso; el reporte resultante decide (gate) si se puede borrar la cuenta de Auth.
    private suspend fun limpiarDatosAsociados(uid: String): ResultadoLimpiezaCuenta {
        val pasos = mutableListOf<PasoLimpieza>()

        // 1-5) Grupos: enumeración, borrado si queda vacío, disolución o actualización de miembros.
        pasos += limpiarGrupos(uid)

        // 6) Tareas creadas por el usuario (cascada destructiva y documentada).
        pasos += ejecutarPaso(
            nombre = "tareas:creadas",
            descripcion = "tareas creadas por el usuario"
        ) {
            firestore.collection("tareas").whereEqualTo("creadoPor", uid).get().await()
                .documents.forEach { it.reference.delete().await() }
        }

        // 7) Tareas asignadas al usuario que no creó: se desasignan, no se borran.
        pasos += ejecutarPaso(
            nombre = "tareas:asignadas",
            descripcion = "tareas asignadas al usuario"
        ) {
            firestore.collection("tareas").whereEqualTo("asignadoA", uid).get().await()
                .documents.forEach { doc ->
                    if (doc.getString("creadoPor") == uid) return@forEach
                    doc.reference.update(
                        mapOf(
                            "asignadoA" to null,
                            "estado" to "pendiente",
                            "fechaReclamada" to null,
                            "reclamadoPor" to null,
                            "motivoReclamo" to null
                        )
                    ).await()
                }
        }

        // 8) Barridos por campo; cada paso se intenta aunque fallen los anteriores.
        pasos += ejecutarPaso("invitaciones", "invitaciones") {
            borrarDocumentosPorCampo("invitaciones", "creadoPor", uid)
        }
        pasos += ejecutarPaso("notificaciones:recibidas", "notificaciones recibidas") {
            borrarDocumentosPorCampo("notificaciones", "destinatario", uid)
        }
        pasos += ejecutarPaso("notificaciones:emitidas", "notificaciones emitidas") {
            borrarDocumentosPorCampo("notificaciones", "contenido.desde", uid)
        }
        pasos += ejecutarPaso("recompensas", "recompensas") {
            borrarDocumentosPorCampo("recompensas", "creadoPor", uid)
        }
        pasos += ejecutarPaso("canjes", "canjes") {
            borrarDocumentosPorCampo("canjes", "usuarioUid", uid)
        }

        // 9) Disputas: primero la evidencia de Storage, después el documento de la disputa.
        pasos += ejecutarPaso("disputas", "disputas y evidencias") {
            val disputas = firestore.collection("disputas").whereEqualTo("iniciador", uid).get().await()
            for (doc in disputas.documents) {
                val pruebas = doc.get("pruebas") as? List<*>
                pruebas?.mapNotNull { it as? String }?.forEach { url ->
                    try {
                        storage.getReferenceFromUrl(url).delete().await()
                    } catch (e: StorageException) {
                        if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
                    }
                }
                doc.reference.delete().await()
            }
        }

        // 10) Avatar (borrado de un documento ausente es un no-op, no un fallo).
        pasos += ejecutarPaso("avatar", "avatar") {
            firestore.collection("avatares").document(uid).delete().await()
        }

        // 11) Perfil de usuario, SIEMPRE al final: es el ancla de identidad.
        pasos += ejecutarPaso("usuario", "perfil de usuario") {
            firestore.collection("usuarios").document(uid).delete().await()
        }

        return ResultadoLimpiezaCuenta(pasos)
    }

    // Envuelve un paso de limpieza sin propagar la excepción: captura éxito/fallo y reintenta
    // solo errores transitorios hasta MAX_INTENTOS_LIMPIEZA. No hay backoff exponencial.
    private suspend fun ejecutarPaso(
        nombre: String,
        descripcion: String,
        intentosMax: Int = MAX_INTENTOS_LIMPIEZA,
        bloque: suspend () -> Unit
    ): PasoLimpieza = ejecutarPasoConValor(nombre, descripcion, intentosMax) { bloque(); Unit }.first

    // Variante que devuelve, además del paso, el valor leído por el bloque (null si falló).
    private suspend fun <T> ejecutarPasoConValor(
        nombre: String,
        descripcion: String,
        intentosMax: Int = MAX_INTENTOS_LIMPIEZA,
        bloque: suspend () -> T
    ): Pair<PasoLimpieza, T?> {
        var ultimoError: String? = null
        for (intento in 1..intentosMax) {
            try {
                return PasoLimpieza(nombre, descripcion, exito = true, intentos = intento) to bloque()
            } catch (e: Exception) {
                ultimoError = e.message ?: e::class.java.simpleName
                if (!esErrorTransitorio(e)) {
                    return PasoLimpieza(nombre, descripcion, false, intento, ultimoError) to null
                }
                if (intento < intentosMax) delay(DELAY_REINTENTO_MS)
            }
        }
        return PasoLimpieza(nombre, descripcion, false, intentosMax, ultimoError) to null
    }

    // Reintentar un fallo de autorización/entrada no aporta; solo se reintenta lo transitorio.
    private fun esErrorTransitorio(e: Throwable): Boolean = when (e) {
        is FirebaseNetworkException -> true
        is FirebaseFirestoreException -> e.code in setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.CANCELLED
        )
        else -> false
    }

    // Enumera los grupos del usuario y aplica la salida que corresponda por grupo.
    // La enumeración fallida devuelve el paso fallido sin arriesgar escrituras ciegas.
    private suspend fun limpiarGrupos(uid: String): List<PasoLimpieza> {
        val pasos = mutableListOf<PasoLimpieza>()

        val (pasoLectura, grupos) = ejecutarPasoConValor("grupos:enumeracion", "grupos") {
            firestore.collection("grupos").get().await().documents
                .filter { (it.get("miembros") as? Map<*, *>)?.containsKey(uid) == true }
        }
        pasos += pasoLectura
        if (grupos == null) return pasos

        for (doc in grupos) {
            val miembros = doc.get("miembros") as? Map<*, *> ?: continue
            val restantes = miembros.keys.filterIsInstance<String>().filter { it != uid }

            when {
                restantes.isEmpty() -> pasos += ejecutarPaso("grupo:${doc.id}:borrarVacio", "grupo") {
                    doc.reference.delete().await()
                }

                restantes.size == 1 -> pasos += disolverGrupo(doc, restantes.first())

                else -> {
                    val nuevos = miembros.filterKeys { it is String && it != uid }
                    pasos += ejecutarPaso("grupo:${doc.id}:miembros", "grupo") {
                        doc.reference.update("miembros", nuevos).await()
                    }
                }
            }
        }
        return pasos
    }

    // Disuelve un grupo de dos miembros: primero borra las tareas del grupo (dejando el documento
    // de grupo para que un reintento lo redescubra), después un batch atómico con el borrado del
    // grupo y el reinicio del miembro restante.
    private suspend fun disolverGrupo(grupoDoc: DocumentSnapshot, restanteUid: String): List<PasoLimpieza> {
        val gid = grupoDoc.id
        val pasos = mutableListOf<PasoLimpieza>()

        val (pasoTareasLectura, tareas) = ejecutarPasoConValor("grupo:$gid:tareas:leer", "tareas del grupo") {
            firestore.collection("tareas").whereEqualTo("grupoId", gid).get().await()
                .documents.map { it.reference }
        }
        pasos += pasoTareasLectura
        if (tareas == null) return pasos

        pasos += ejecutarPaso("grupo:$gid:tareas:borrar", "tareas del grupo") {
            tareas.chunked(LIMITE_BATCH_FIRESTORE).forEach { chunk ->
                val b = firestore.batch()
                chunk.forEach { b.delete(it) }
                b.commit().await()
            }
        }
        if (!pasos.last().exito) return pasos

        val pasoCore = ejecutarPaso("grupo:$gid:disolver", "disolución del grupo") {
            val batch: WriteBatch = firestore.batch()
            batch.delete(grupoDoc.reference)
            batch.set(
                firestore.collection("usuarios").document(restanteUid),
                mapOf(
                    "grupoId" to null,
                    "puntos" to 0,
                    "puntosReservados" to 0,
                    "puntosRecompensa" to 0,
                    "rachaDias" to 0
                ),
                SetOptions.merge()
            )
            batch.commit().await()
        }
        pasos += pasoCore.copy(nombre = "grupo:$gid:disolver", descripcion = "disolución del grupo")
        pasos += pasoCore.copy(nombre = "grupo:$gid:limpiarGrupoId", descripcion = "grupoId del miembro restante")
        pasos += pasoCore.copy(nombre = "grupo:$gid:resetSaldos", descripcion = "reinicio de puntos y racha del miembro restante")
        return pasos
    }

    private suspend fun borrarDocumentosPorCampo(coleccion: String, campo: String, valor: String) {
        val snap = firestore.collection(coleccion).whereEqualTo(campo, valor).get().await()
        for (doc in snap.documents) {
            doc.reference.delete().await()
        }
    }

    override fun usuarioActual(): Usuario? {
        val u = auth.currentUser ?: return null
        Log.d(TAG, "usuarioActual uid=${u.uid} email=${u.email}")
        // Devolver caché si coincide con el usuario autenticado (tiene puntos y puntosRecompensa reales)
        return _usuarioCache?.takeIf { it.id == u.uid }
            ?: Usuario(
                id = u.uid,
                nombre = u.displayName ?: "",
                fechaNacimiento = null,
                sexo = null,
                pais = null,
                ciudad = null,
                email = u.email ?: "",
                rachaDias = 0
            )
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
                val fechaNacimiento = doc.getString("fechaNacimiento")
                val sexo = doc.getString("sexo")
                val pais = doc.getString("pais")
                val ciudad = doc.getString("ciudad")
                val email = doc.getString("email") ?: ""
                val puntos = doc.getLong("puntos")?.toInt() ?: 0
                val puntosReservados = doc.getLong("puntosReservados")?.toInt() ?: 0
                val puntosRecompensa = doc.getLong("puntosRecompensa")?.toInt() ?: 0
                val rachaDias = doc.getLong("rachaDias")?.toInt() ?: 0
                Usuario(
                    id = id,
                    nombre = nombre,
                    fechaNacimiento = fechaNacimiento,
                    sexo = sexo,
                    pais = pais,
                    ciudad = ciudad,
                    email = email,
                    puntos = puntos, puntosReservados = puntosReservados, puntosRecompensa = puntosRecompensa,
                    rachaDias = rachaDias,
                    avatarUpdatedAt = doc.getTimestamp("avatarUpdatedAt"))
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
                val bonus = if (nuevaRacha >= Constants.STREAK_BONUS_THRESHOLD) (basePuntos * Constants.PORCENTAJE_BONIFICACION_RACHA).toInt() else 0
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
