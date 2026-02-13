package com.example.tfg.repositorio

import android.util.Log
import com.example.tfg.modelo.Grupo
import com.example.tfg.modelo.Invitacion
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class RepositorioPareja(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) : GrupoRepositorio {

    private val coleccionGrupos = "grupos"
    private val coleccionInvitaciones = "invitaciones"
    private val coleccionUsuarios = "usuarios"
    private val TAG = "RepositorioPareja"

    override suspend fun crearGrupo(grupo: Grupo): Result<Grupo> {
        return try {
            val ref = firestore.collection(coleccionGrupos).add(grupo.copy(fechaCreacion = grupo.fechaCreacion ?: Timestamp.now())).await()
            val creado = grupo.copy(id = ref.id)
            Log.d(TAG, "crearGrupo(obj) creado id=${ref.id}")
            Result.success(creado)
        } catch (e: Exception) {
            Log.e(TAG, "crearGrupo error", e)
            Result.failure(Exception(e.message ?: "Error creando grupo"))
        }
    }

    // Nueva versión: crear grupo y actualizar usuario con grupoId en operación atómica
    suspend fun crearGrupo(nombre: String, creadorUid: String): Result<String> {
        return try {
            val grupoData = mapOf(
                "nombre" to nombre,
                "miembros" to mapOf(creadorUid to "creador"),
                "puntos" to 0,
                "fechaCreacion" to Timestamp.now()
            )
            // Usar batch para crear documento y actualizar usuario
            val newDocRef = firestore.collection(coleccionGrupos).document()
            val userRef = firestore.collection(coleccionUsuarios).document(creadorUid)

            val batch = firestore.batch()
            batch.set(newDocRef, grupoData)
            // usar set con merge para crear/actualizar el documento usuario sin fallar si no existe
            batch.set(userRef, mapOf("grupoId" to newDocRef.id), SetOptions.merge())

            // Ejecutar batch
            batch.commit().await()

            Log.d(TAG, "crearGrupo(nombre) OK id=${newDocRef.id}")
            Result.success(newDocRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "crearGrupo(nombre) error", e)
            Result.failure(Exception(e.message ?: "Error creando grupo"))
        }
    }

    suspend fun crearInvitacion(grupoId: String, creadoPor: String, correoDestino: String? = null, expiracionHoras: Int? = 72): Result<String> {
        return try {
            val codigo = UUID.randomUUID().toString().substring(0, 8)
            val expiracion = expiracionHoras?.let { Timestamp(Date(nowInMillisPlusHours(it))) }
            val invitacion = Invitacion(codigo = codigo, creadoPor = creadoPor, grupoId = grupoId, correoDestino = correoDestino, estado = "pendiente", fechaCreacion = Timestamp.now(), expiracion = expiracion)
            firestore.collection(coleccionInvitaciones).document(codigo).set(invitacion).await()
            Log.d(TAG, "crearInvitacion OK codigo=$codigo grupo=$grupoId")
            Result.success(codigo)
        } catch (e: Exception) {
            Log.e(TAG, "crearInvitacion error", e)
            Result.failure(Exception(e.message ?: "Error creando invitación"))
        }
    }

    suspend fun buscarInvitacionesPorCorreo(correo: String): Result<List<Invitacion>> {
        return try {
            val q = firestore.collection(coleccionInvitaciones).whereEqualTo("correoDestino", correo).whereEqualTo("estado", "pendiente").get().await()
            val lista = q.documents.mapNotNull { it.toObject(Invitacion::class.java) }
            Result.success(lista)
        } catch (e: Exception) {
            Log.e(TAG, "buscarInvitacionesPorCorreo error", e)
            Result.failure(Exception(e.message ?: "Error buscando invitaciones"))
        }
    }

    suspend fun aceptarInvitacion(codigo: String, usuarioUid: String): Result<String> {
        return try {
            val docRef = firestore.collection(coleccionInvitaciones).document(codigo)
            var doc = docRef.get().await()
            if (!doc.exists()) {
                Log.w(TAG, "aceptarInvitacion: documento id=$codigo no existe, intentando búsqueda por campo 'codigo'")
                // intentar búsqueda por campo 'codigo' con variantes
                val alternativas = listOf(codigo.trim(), codigo.trim().uppercase(), codigo.trim().lowercase())
                var encontradoDocId: String? = null
                for (alt in alternativas) {
                    val q = firestore.collection(coleccionInvitaciones).whereEqualTo("codigo", alt).limit(1).get().await()
                    if (!q.isEmpty) {
                        val found = q.documents.first()
                        encontradoDocId = found.id
                        doc = found
                        Log.d(TAG, "aceptarInvitacion: encontrado invitación por campo codigo='$alt' id=${found.id}")
                        break
                    }
                }
                if (encontradoDocId == null) {
                    return Result.failure(Exception("Invitación no encontrada (probado id y campo 'codigo'). Comprueba el código y que ambas apps usan el mismo proyecto Firebase."))
                }
            }

            val invitacion = doc.toObject(Invitacion::class.java) ?: return Result.failure(Exception("Invitación inválida"))
            if (invitacion.estado != "pendiente") return Result.failure(Exception("Invitación no válida (estado=${invitacion.estado})"))
            // añadir usuario al grupo
            val grupoRef = firestore.collection(coleccionGrupos).document(invitacion.grupoId)
            val userRef = firestore.collection(coleccionUsuarios).document(usuarioUid)
            firestore.runTransaction { t ->
                val snap = t.get(grupoRef)
                val grupoObj = snap.toObject(Grupo::class.java)
                val miembros = grupoObj?.miembros ?: emptyMap()
                val nuevos = HashMap(miembros)
                nuevos[usuarioUid] = "miembro"
                t.update(grupoRef, "miembros", nuevos)
                t.update(firestore.collection(coleccionInvitaciones).document(invitacion.codigo), "estado", "aceptada")
                // también actualizar campo grupoId del usuario que acepta para persistir vínculo
                try {
                    t.set(userRef, mapOf("grupoId" to invitacion.grupoId), SetOptions.merge())
                } catch (_: Exception) {
                    // si falla, intentar update
                    t.update(userRef, "grupoId", invitacion.grupoId)
                }
            }.await()

            // Asegurar por seguridad que el campo grupoId queda persistido en el documento de usuario
            try {
                firestore.collection(coleccionUsuarios).document(usuarioUid).set(mapOf("grupoId" to invitacion.grupoId), SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(TAG, "aceptarInvitacion: set post-transacción fallo para user $usuarioUid: ${e.message}")
            }

            Log.d(TAG, "aceptarInvitacion OK codigo=${invitacion.codigo} usuario=$usuarioUid grupo=${invitacion.grupoId}")
            Result.success(invitacion.grupoId)
        } catch (e: Exception) {
            Log.e(TAG, "aceptarInvitacion error", e)
            val msg = e.message ?: "Error aceptando invitación"
            // Si hay permiso denegado, añadir sugerencia
            val detalle = if (msg.contains("PERMISSION_DENIED") || msg.contains("permission_denied", true)) {
                "$msg - Revisa las reglas de Firestore y que el usuario esté autenticado"
            } else {
                msg
            }
            Result.failure(Exception(detalle))
        }
    }

    override suspend fun obtenerGrupos(): Result<List<Grupo>> {
        return try {
            val q = firestore.collection(coleccionGrupos).get().await()
            val lista = q.documents.mapNotNull { it.toObject(Grupo::class.java)?.copy(id = it.id) }
            Result.success(lista)
        } catch (e: Exception) {
            Log.e(TAG, "obtenerGrupos error", e)
            Result.failure(Exception(e.message ?: "Error obteniendo grupos"))
        }
    }

    override fun observarGrupos(): Flow<List<Grupo>> = callbackFlow {
        val sub = firestore.collection(coleccionGrupos).addSnapshotListener { snap, error ->
            if (error != null) { close(error); Log.e(TAG, "observarGrupos listener error", error); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { it.toObject(Grupo::class.java) } ?: emptyList()
            trySend(list)
        }
        awaitClose { sub.remove() }
    }

    // Observar un único grupo por id (emite null si no existe)
    fun observarGrupoPorId(grupoId: String): Flow<Grupo?> = callbackFlow {
        val docRef = firestore.collection(coleccionGrupos).document(grupoId)
        val sub = docRef.addSnapshotListener { snap, error ->
            if (error != null) { close(error); Log.e(TAG, "observarGrupoPorId listener error", error); return@addSnapshotListener }
            if (snap == null || !snap.exists()) {
                trySend(null)
            } else {
                val g = snap.toObject(Grupo::class.java)?.copy(id = snap.id)
                trySend(g)
            }
        }
        awaitClose { sub.remove() }
    }

    suspend fun obtenerGrupoPorUsuario(usuarioUid: String): Result<Grupo?> {
        return try {
            // Firestore no permite buscar por clave de mapa fácilmente; leemos los grupos y filtramos client-side
            val q = firestore.collection(coleccionGrupos).get().await()
            val g = q.documents.mapNotNull { it.toObject(Grupo::class.java)?.copy(id = it.id) }.firstOrNull { it.miembros.containsKey(usuarioUid) }
            Result.success(g)
        } catch (e: Exception) {
            Log.e(TAG, "obtenerGrupoPorUsuario error", e)
            Result.failure(Exception(e.message ?: "Error obteniendo grupo por usuario"))
        }
    }

    suspend fun obtenerGrupoPorId(grupoId: String): Result<Grupo?> {
        return try {
            val doc = firestore.collection(coleccionGrupos).document(grupoId).get().await()
            if (!doc.exists()) return Result.success(null)
            val g = doc.toObject(Grupo::class.java)?.copy(id = doc.id)
            Result.success(g)
        } catch (e: Exception) {
            Log.e(TAG, "obtenerGrupoPorId error", e)
            Result.failure(Exception(e.message ?: "Error obteniendo grupo por id"))
        }
    }

    // Nuevo: obtener grupoId directo desde el documento del usuario (si existe)
    suspend fun obtenerGrupoIdDesdeUsuario(usuarioUid: String): Result<String?> {
        return try {
            val doc = firestore.collection(coleccionUsuarios).document(usuarioUid).get().await()
            if (!doc.exists()) return Result.success(null)
            val gid = doc.getString("grupoId")
            Result.success(gid)
        } catch (e: Exception) {
            Log.e(TAG, "obtenerGrupoIdDesdeUsuario error", e)
            Result.failure(Exception(e.message ?: "Error leyendo usuario"))
        }
    }

    suspend fun quitarMiembroGrupo(grupoId: String, usuarioUid: String): Result<Unit> {
        return try {
            val grupoRef = firestore.collection(coleccionGrupos).document(grupoId)
            val userRef = firestore.collection(coleccionUsuarios).document(usuarioUid)

            firestore.runTransaction { t ->
                val snap = t.get(grupoRef)
                if (!snap.exists()) throw Exception("Grupo no encontrado")
                val grupoObj = snap.toObject(Grupo::class.java)
                val miembros = grupoObj?.miembros?.toMutableMap() ?: mutableMapOf()
                miembros.remove(usuarioUid)
                if (miembros.isEmpty()) {
                    // borrar grupo si no quedan miembros
                    t.delete(grupoRef)
                } else {
                    t.update(grupoRef, "miembros", miembros)
                }
                // limpiar campo grupoId del usuario (poner a null)
                try {
                    t.update(userRef, "grupoId", null)
                } catch (_: Exception) {
                    // si userRef no existe, crear/merge con null
                    t.set(userRef, mapOf("grupoId" to null))
                }
            }.await()

            Log.d(TAG, "quitarMiembroGrupo OK usuario=$usuarioUid grupo=$grupoId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "quitarMiembroGrupo error", e)
            Result.failure(Exception(e.message ?: "Error saliendo del grupo"))
        }
    }

    // Actualiza el nombre de un grupo (campo 'nombre')
    suspend fun actualizarNombreGrupo(grupoId: String, nuevoNombre: String): Result<Unit> {
        return try {
            val docRef = firestore.collection(coleccionGrupos).document(grupoId)
            docRef.update("nombre", nuevoNombre).await()
            Log.d(TAG, "actualizarNombreGrupo OK grupo=$grupoId nombre=$nuevoNombre")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "actualizarNombreGrupo error", e)
            Result.failure(Exception(e.message ?: "Error actualizando nombre de grupo"))
        }
    }

    suspend fun limpiarGrupoIdUsuario(usuarioUid: String): Result<Unit> {
        return try {
            val userRef = firestore.collection(coleccionUsuarios).document(usuarioUid)
            // usar set con merge para no sobrescribir otros campos
            userRef.set(mapOf("grupoId" to null), SetOptions.merge()).await()
            Log.d(TAG, "limpiarGrupoIdUsuario OK uid=$usuarioUid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "limpiarGrupoIdUsuario error", e)
            Result.failure(Exception(e.message ?: "Error limpiando grupoId usuario"))
        }
    }

    private fun nowInMillisPlusHours(hours: Int): Long = System.currentTimeMillis() + hours * 3600 * 1000L
}
