package com.example.tfg.repositorio

import com.example.tfg.modelo.Canje
import com.example.tfg.modelo.Recompensa
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class RepositorioRecompensas(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val colRecompensas = "recompensas"
    private val colUsuarios   = "usuarios"
    private val colCanjes     = "canjes"

    // ─── Recompensas predefinidas del sistema ────────────────────────────────
    private val predefinidas = listOf(
        Recompensa(id = "pre_cena",       titulo = "Cena Romántica",          descripcion = "Una cena especial a elegir por quien la canjea.",      coste = 500,  esPredefinida = true),
        Recompensa(id = "pre_masaje",     titulo = "Masaje Relajante 30 min", descripcion = "Un masaje de 30 minutos cuando quieras.",              coste = 600,  esPredefinida = true),
        Recompensa(id = "pre_dia_libre",  titulo = "Día libre de tareas",     descripcion = "Un día completo sin asignación de ninguna tarea.",     coste = 800,  esPredefinida = true),
        Recompensa(id = "pre_pelicula",   titulo = "Película a elección",     descripcion = "Elegir la película del viernes sin debate.",           coste = 200,  esPredefinida = true),
        Recompensa(id = "pre_desayuno",   titulo = "Desayuno en cama",        descripcion = "Desayuno preparado y servido en cama.",                coste = 350,  esPredefinida = true),
        Recompensa(id = "pre_capricho",   titulo = "Capricho sin preguntas",  descripcion = "Pedir un capricho (razonable) sin justificarlo.",      coste = 400,  esPredefinida = true),
        Recompensa(id = "pre_noche_fuera", titulo = "Noche fuera",              descripcion = "Una noche de hotel o casa rural a elegir.",            coste = 1500, esPredefinida = true),
        Recompensa(id = "pre_tarde_sofa", titulo = "Tarde de sofá",           descripcion = "Tarde entera de sofá y series sin interrupciones.",    coste = 250,  esPredefinida = true)
    )

    // ─── Listar: predefinidas + personalizadas del grupo ─────────────────────
    suspend fun listarRecompensas(grupoId: String?): Result<List<Recompensa>> {
        return try {
            val personalizadas = if (grupoId.isNullOrBlank()) {
                val snap = firestore.collection(colRecompensas)
                    .whereEqualTo("esPersonalizada", true).get().await()
                snap.documents.mapNotNull { docToRecompensa(it) }
            } else {
                val snap = firestore.collection(colRecompensas)
                    .whereEqualTo("grupoId", grupoId)
                    .whereEqualTo("esPersonalizada", true).get().await()
                snap.documents.mapNotNull { docToRecompensa(it) }
            }
            // Predefinidas siempre primero, luego personalizadas
            Result.success(predefinidas + personalizadas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Crear recompensa personalizada ──────────────────────────────────────
    suspend fun crearRecompensaPersonalizada(
        titulo: String, descripcion: String?, coste: Int,
        creadoPor: String, grupoId: String?
    ): Result<Recompensa> {
        return try {
            val data = mapOf(
                "titulo"          to titulo,
                "descripcion"     to descripcion,
                "coste"           to coste,
                "creadoPor"       to creadoPor,
                "grupoId"         to grupoId,
                "fechaCreacion"   to Timestamp.now(),
                "esPredefinida"   to false,
                "esPersonalizada" to true
            )
            val ref = firestore.collection(colRecompensas).add(data).await()
            val r = Recompensa(id = ref.id, titulo = titulo, descripcion = descripcion,
                coste = coste, creadoPor = creadoPor, grupoId = grupoId,
                fechaCreacion = Timestamp.now(), esPersonalizada = true)
            Result.success(r)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Eliminar recompensa personalizada ───────────────────────────────────
    suspend fun eliminarRecompensa(recompensaId: String): Result<Unit> {
        return try {
            firestore.collection(colRecompensas).document(recompensaId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Canjear recompensa (descuenta puntos y registra canje pendiente) ────
    suspend fun canjearRecompensa(
        recompensaId: String, usuarioUid: String,
        tituloRecompensa: String, coste: Int,
        nombreUsuario: String, grupoId: String?
    ): Result<String> {      // devuelve el id del canje creado
        return try {
            val usuarioRef = firestore.collection(colUsuarios).document(usuarioUid)
            var canjeId = ""
            firestore.runTransaction { t ->
                val uSnap = t.get(usuarioRef)
                // Usar puntosRecompensa, no puntos de actividad
                val puntosRecomp = (uSnap.getLong("puntosRecompensa") ?: 0L).toInt()
                if (puntosRecomp < coste) throw Exception("Puntos de recompensa insuficientes (tienes $puntosRecomp, necesitas $coste)")
                t.update(usuarioRef, "puntosRecompensa", puntosRecomp - coste)
                // Registrar canje con estado "pendiente" (el otro miembro confirmará)
                val canjeRef = firestore.collection(colCanjes).document()
                canjeId = canjeRef.id
                val canje = mapOf(
                    "recompensaId"      to recompensaId,
                    "tituloRecompensa"  to tituloRecompensa,
                    "coste"             to coste,
                    "usuarioUid"        to usuarioUid,
                    "nombreUsuario"     to nombreUsuario,
                    "grupoId"           to grupoId,
                    "fecha"             to Timestamp.now(),
                    "estado"            to "pendiente"
                )
                t.set(canjeRef, canje)
            }.await()
            Result.success(canjeId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Confirmar o rechazar un canje ───────────────────────────────────────
    suspend fun responderCanje(canjeId: String, aceptado: Boolean): Result<Unit> {
        return try {
            val nuevoEstado = if (aceptado) "aceptado" else "rechazado"
            val canjeRef = firestore.collection(colCanjes).document(canjeId)
            if (!aceptado) {
                // Si se rechaza, devolver los puntos al usuario
                val snap = canjeRef.get().await()
                val uid   = snap.getString("usuarioUid") ?: ""
                val coste = (snap.getLong("coste") ?: 0L).toInt()
                if (uid.isNotBlank() && coste > 0) {
                    val uRef = firestore.collection(colUsuarios).document(uid)
                    firestore.runTransaction { t ->
                        val uSnap = t.get(uRef)
                        val pts   = (uSnap.getLong("puntosRecompensa") ?: 0L).toInt()
                        t.update(uRef, "puntosRecompensa", pts + coste)
                        t.update(canjeRef, "estado", nuevoEstado)
                    }.await()
                    return Result.success(Unit)
                }
            }
            canjeRef.update("estado", nuevoEstado).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Historial de canjes del usuario (o del grupo) ───────────────────────
    suspend fun obtenerHistorialCanjes(usuarioUid: String?, grupoId: String?): Result<List<Canje>> {
        return try {
            val query: Query = when {
                !grupoId.isNullOrBlank()     -> firestore.collection(colCanjes).whereEqualTo("grupoId", grupoId)
                !usuarioUid.isNullOrBlank()  -> firestore.collection(colCanjes).whereEqualTo("usuarioUid", usuarioUid)
                else                         -> return Result.success(emptyList())
            }
            val snap = query.orderBy("fecha", Query.Direction.DESCENDING).limit(50).get().await()
            val lista = snap.documents.mapNotNull { d ->
                try {
                    Canje(
                        id                = d.id,
                        recompensaId      = d.getString("recompensaId") ?: "",
                        tituloRecompensa  = d.getString("tituloRecompensa") ?: "",
                        coste             = (d.getLong("coste") ?: 0L).toInt(),
                        usuarioUid        = d.getString("usuarioUid") ?: "",
                        nombreUsuario     = d.getString("nombreUsuario") ?: "",
                        grupoId           = d.getString("grupoId"),
                        fecha             = d.getTimestamp("fecha"),
                        estado            = d.getString("estado") ?: "pendiente"
                    )
                } catch (_: Exception) { null }
            }
            Result.success(lista)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Canjes pendientes dirigidos al otro miembro (para que los confirme) ─
    suspend fun obtenerCanjesPendientesParaMiembro(grupoId: String, miUid: String): Result<List<Canje>> {
        return try {
            val snap = firestore.collection(colCanjes)
                .whereEqualTo("grupoId", grupoId)
                .whereEqualTo("estado", "pendiente")
                .orderBy("fecha", Query.Direction.DESCENDING)
                .get().await()
            // Solo los que NO son míos (son de otro miembro y yo debo confirmar)
            val lista = snap.documents.mapNotNull { d ->
                val uid = d.getString("usuarioUid") ?: ""
                if (uid == miUid) return@mapNotNull null
                try {
                    Canje(
                        id               = d.id,
                        recompensaId     = d.getString("recompensaId") ?: "",
                        tituloRecompensa = d.getString("tituloRecompensa") ?: "",
                        coste            = (d.getLong("coste") ?: 0L).toInt(),
                        usuarioUid       = uid,
                        nombreUsuario    = d.getString("nombreUsuario") ?: "",
                        grupoId          = d.getString("grupoId"),
                        fecha            = d.getTimestamp("fecha"),
                        estado           = "pendiente"
                    )
                } catch (_: Exception) { null }
            }
            Result.success(lista)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Helper privado ───────────────────────────────────────────────────────
    private fun docToRecompensa(doc: com.google.firebase.firestore.DocumentSnapshot): Recompensa? {
        return try {
            Recompensa(
                id               = doc.id,
                titulo           = doc.getString("titulo") ?: "",
                descripcion      = doc.getString("descripcion"),
                coste            = (doc.getLong("coste") ?: 0L).toInt(),
                creadoPor        = doc.getString("creadoPor"),
                grupoId          = doc.getString("grupoId"),
                fechaCreacion    = doc.getTimestamp("fechaCreacion"),
                esPredefinida    = doc.getBoolean("esPredefinida") ?: false,
                esPersonalizada  = doc.getBoolean("esPersonalizada") ?: true
            )
        } catch (_: Exception) { null }
    }
}
