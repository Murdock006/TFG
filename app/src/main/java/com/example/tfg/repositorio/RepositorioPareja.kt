package com.example.tfg.repositorio

import com.example.tfg.modelo.Grupo
import com.example.tfg.modelo.Invitacion
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class RepositorioPareja(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val coleccionGrupos = "grupos"
    private val coleccionInvitaciones = "invitaciones"

    suspend fun crearGrupo(nombre: String, creadorUid: String): Result<String> {
        return try {
            val grupo = Grupo(id = "", nombre = nombre, miembros = mapOf(creadorUid to "creador"), fechaCreacion = Timestamp.now())
            val ref = firestore.collection(coleccionGrupos).add(grupo).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun crearInvitacion(grupoId: String, creadoPor: String, expiracionHoras: Int? = 72): Result<String> {
        return try {
            val codigo = UUID.randomUUID().toString().substring(0, 8)
            val expiracion = expiracionHoras?.let { Timestamp(Date(nowInMillisPlusHours(it))) }
            val invitacion = Invitacion(codigo = codigo, creadoPor = creadoPor, grupoId = grupoId, estado = "pendiente", fechaCreacion = Timestamp.now(), expiracion = expiracion)
            firestore.collection(coleccionInvitaciones).document(codigo).set(invitacion).await()
            Result.success(codigo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun aceptarInvitacion(codigo: String, usuarioUid: String): Result<String> {
        return try {
            val doc = firestore.collection(coleccionInvitaciones).document(codigo).get().await()
            if (!doc.exists()) return Result.failure(Exception("Invitación no encontrada"))
            val invitacion = doc.toObject(Invitacion::class.java) ?: return Result.failure(Exception("Invitación inválida"))
            if (invitacion.estado != "pendiente") return Result.failure(Exception("Invitación no válida"))
            // añadir usuario al grupo
            val grupoRef = firestore.collection(coleccionGrupos).document(invitacion.grupoId)
            firestore.runTransaction { t ->
                val snap = t.get(grupoRef)
                val grupoObj = snap.toObject(Grupo::class.java)
                val miembros = grupoObj?.miembros ?: emptyMap()
                val nuevos = HashMap(miembros)
                nuevos[usuarioUid] = "miembro"
                t.update(grupoRef, "miembros", nuevos)
                t.update(firestore.collection(coleccionInvitaciones).document(codigo), "estado", "aceptada")
            }.await()
            Result.success(invitacion.grupoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun obtenerGrupoPorUsuario(usuarioUid: String): Result<Grupo?> {
        return try {
            // Firestore no permite buscar por clave de mapa fácilmente; leemos los grupos y filtramos client-side
            val q = firestore.collection(coleccionGrupos).get().await()
            val g = q.documents.mapNotNull { it.toObject(Grupo::class.java) }.firstOrNull { it.miembros.containsKey(usuarioUid) }
            Result.success(g)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun nowInMillisPlusHours(hours: Int): Long = System.currentTimeMillis() + hours * 3600 * 1000L
}
