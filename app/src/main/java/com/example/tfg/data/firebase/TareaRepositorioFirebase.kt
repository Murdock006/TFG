package com.example.tfg.data.firebase

import com.example.tfg.modelo.Tarea
import com.example.tfg.repositorio.TareaRepositorio
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TareaRepositorioFirebase(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) : TareaRepositorio {

    private val coleccion = "tareas"

    private fun docToTarea(doc: DocumentSnapshot): Tarea? {
        val data = doc.data ?: return null
        return try {
            Tarea(
                id = doc.id,
                titulo = data["titulo"] as? String ?: "",
                descripcion = data["descripcion"] as? String,
                categoria = data["categoria"] as? String,
                dificultad = (data["dificultad"] as? Long)?.toInt() ?: (data["dificultad"] as? Int) ?: 1,
                puntos = (data["puntos"] as? Long)?.toInt() ?: (data["puntos"] as? Int) ?: 0,
                asignadoA = data["asignadoA"] as? String,
                creadoPor = data["creadoPor"] as? String,
                grupoId = data["grupoId"] as? String,
                estado = data["estado"] as? String ?: "pendiente",
                requiereConfirmacion = data["requiereConfirmacion"] as? Boolean ?: true,
                fechaCreada = data["fechaCreada"] as? Timestamp,
                fechaProgramada = data["fechaProgramada"] as? Timestamp,
                fechaReclamada = data["fechaReclamada"] as? Timestamp,
                reclamadoPor = data["reclamadoPor"] as? String,
                motivoReclamo = data["motivoReclamo"] as? String
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun crearTarea(tarea: Tarea): Result<Tarea> {
        return try {
            // reservar puntos en la cuenta del creador si aplica
            if (!tarea.creadoPor.isNullOrBlank() && tarea.puntos > 0) {
                try {
                    com.example.tfg.service.LocalizadorServicios.repositorioAuth.reservarPuntos(tarea.creadoPor!!, tarea.puntos)
                } catch (e: Exception) {
                    return Result.failure(Exception("No se pudieron reservar puntos: ${e.message}"))
                }
            }

            val map = mutableMapOf<String, Any?>(
                "titulo" to tarea.titulo,
                "descripcion" to tarea.descripcion,
                "categoria" to tarea.categoria,
                "dificultad" to tarea.dificultad,
                "puntos" to tarea.puntos,
                "asignadoA" to tarea.asignadoA,
                "creadoPor" to tarea.creadoPor,
                "grupoId" to tarea.grupoId,
                "estado" to tarea.estado,
                "requiereConfirmacion" to tarea.requiereConfirmacion,
                "fechaCreada" to (tarea.fechaCreada ?: Timestamp.now()),
                "fechaProgramada" to tarea.fechaProgramada
            )
            val ref = firestore.collection(coleccion).add(map).await()
            val nuevo = tarea.copy(id = ref.id, fechaCreada = (map["fechaCreada"] as? Timestamp))
            Result.success(nuevo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun obtenerTareas(): Result<List<Tarea>> {
        return try {
            val uid = com.example.tfg.service.LocalizadorServicios.repositorioAuth.usuarioActual()?.id
            if (uid.isNullOrBlank()) return Result.success(emptyList())
            // obtener grupoId del usuario
            val doc = try { firestore.collection("usuarios").document(uid).get().await() } catch (_: Exception) { null }
            var grupoId = doc?.getString("grupoId")
            // validar que el grupo realmente contiene al usuario
            if (!grupoId.isNullOrBlank()) {
                try {
                    val gdoc = firestore.collection("grupos").document(grupoId).get().await()
                    if (!gdoc.exists()) {
                        grupoId = null
                    } else {
                        val miembros = gdoc.get("miembros") as? Map<*, *>
                        if (miembros == null || !miembros.containsKey(uid)) grupoId = null
                    }
                } catch (_: Exception) { grupoId = null }
            }

            // ejecutar consultas específicas y combinar resultados evitando duplicados
            val mapa = LinkedHashMap<String, Tarea>()

            val q1 = firestore.collection(coleccion).whereEqualTo("creadoPor", uid).get().await()
            q1.documents.mapNotNull { docToTarea(it) }.forEach { mapa[it.id] = it }

            val q2 = firestore.collection(coleccion).whereEqualTo("asignadoA", uid).get().await()
            q2.documents.mapNotNull { docToTarea(it) }.forEach { mapa[it.id] = it }

            if (!grupoId.isNullOrBlank()) {
                val q3 = firestore.collection(coleccion).whereEqualTo("grupoId", grupoId).get().await()
                q3.documents.mapNotNull { docToTarea(it) }.forEach { mapa[it.id] = it }
            }

            Result.success(mapa.values.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observarTareas(): Flow<List<Tarea>> = callbackFlow {
        val uid = com.example.tfg.service.LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        if (uid.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val doc = try { firestore.collection("usuarios").document(uid).get().await() } catch (e: Exception) { null }
        var grupoId = doc?.getString("grupoId")
        if (!grupoId.isNullOrBlank()) {
            try {
                val gdoc = firestore.collection("grupos").document(grupoId).get().await()
                if (!gdoc.exists()) {
                    grupoId = null
                } else {
                    val miembros = gdoc.get("miembros") as? Map<*, *>
                    if (miembros == null || !miembros.containsKey(uid)) grupoId = null
                }
            } catch (_: Exception) { grupoId = null }
        }

        // Map para combinar resultados de múltiples listeners
        val combinado = mutableMapOf<String, Tarea>()

        val subCreado = firestore.collection(coleccion).whereEqualTo("creadoPor", uid).addSnapshotListener { snap, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            snap?.documents?.mapNotNull { docToTarea(it) }?.forEach { combinado[it.id] = it }
            trySend(combinado.values.toList())
        }

        val subAsignado = firestore.collection(coleccion).whereEqualTo("asignadoA", uid).addSnapshotListener { snap, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            snap?.documents?.mapNotNull { docToTarea(it) }?.forEach { combinado[it.id] = it }
            trySend(combinado.values.toList())
        }

        var subGrupo: com.google.firebase.firestore.ListenerRegistration? = null
        if (!grupoId.isNullOrBlank()) {
            subGrupo = firestore.collection(coleccion).whereEqualTo("grupoId", grupoId).addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                snap?.documents?.mapNotNull { docToTarea(it) }?.forEach { combinado[it.id] = it }
                trySend(combinado.values.toList())
            }
        }

        awaitClose {
            subCreado.remove()
            subAsignado.remove()
            subGrupo?.remove()
        }
    }

    override suspend fun actualizarTarea(tarea: Tarea): Result<Tarea> {
        return try {
            val docRef = firestore.collection(coleccion).document(tarea.id)
            val snapPrev = docRef.get().await()
            val previo = docToTarea(snapPrev)

            val map = mutableMapOf<String, Any?>(
                "titulo" to tarea.titulo,
                "descripcion" to tarea.descripcion,
                "categoria" to tarea.categoria,
                "dificultad" to tarea.dificultad,
                "puntos" to tarea.puntos,
                "asignadoA" to tarea.asignadoA,
                "creadoPor" to tarea.creadoPor,
                "grupoId" to tarea.grupoId,
                "estado" to tarea.estado,
                "requiereConfirmacion" to tarea.requiereConfirmacion,
                "fechaProgramada" to tarea.fechaProgramada,
                "fechaReclamada" to tarea.fechaReclamada,
                "reclamadoPor" to tarea.reclamadoPor,
                "motivoReclamo" to tarea.motivoReclamo
            )
            // reemplazar set simple por transacción que también gestione puntos reservados/transferencias
            firestore.runTransaction { t ->
                val snapTx = t.get(docRef)
                val previoTx = docToTarea(snapTx)

                // Si la tarea pasó de 'completada' a 'confirmada', transferir puntos al asignado y disminuir reservas del creador
                if (previoTx != null && previoTx.estado == "completada" && tarea.estado == "confirmada") {
                    val ejecutor = tarea.asignadoA
                    val creador = tarea.creadoPor
                    if (!ejecutor.isNullOrBlank()) {
                        val ejecRef = firestore.collection("usuarios").document(ejecutor)
                        val ejecSnap = t.get(ejecRef)
                        val puntosEjec = (ejecSnap.getLong("puntos") ?: 0L).toInt()
                        t.update(ejecRef, "puntos", puntosEjec + tarea.puntos)
                    }
                    if (!creador.isNullOrBlank()) {
                        val creadRef = firestore.collection("usuarios").document(creador)
                        val creadSnap = t.get(creadRef)
                        val reservados = (creadSnap.getLong("puntosReservados") ?: 0L).toInt()
                        val nuevoReservados = maxOf(0, reservados - tarea.puntos)
                        t.update(creadRef, "puntosReservados", nuevoReservados)
                    }
                }

                // Si la tarea pasa de asignada a no asignada, liberar puntos del creador
                if (previoTx != null && !previoTx.asignadoA.isNullOrBlank() && tarea.asignadoA.isNullOrBlank()) {
                    val creador = tarea.creadoPor
                    if (!creador.isNullOrBlank()) {
                        val creadRef = firestore.collection("usuarios").document(creador)
                        val creadSnap = t.get(creadRef)
                        val reservados = (creadSnap.getLong("puntosReservados") ?: 0L).toInt()
                        val puntosAct = (creadSnap.getLong("puntos") ?: 0L).toInt()
                        val liberar = minOf(reservados, tarea.puntos)
                        t.update(creadRef, mapOf("puntos" to (puntosAct + liberar), "puntosReservados" to (reservados - liberar)))
                    }
                }

                // Si la tarea pasa de no asignada a asignada, reservar puntos en la cuenta del creador (si tiene saldo)
                if (previoTx != null && previoTx.asignadoA.isNullOrBlank() && !tarea.asignadoA.isNullOrBlank()) {
                    val creador = tarea.creadoPor
                    if (!creador.isNullOrBlank() && tarea.puntos > 0) {
                        val creadRef = firestore.collection("usuarios").document(creador)
                        val creadSnap = t.get(creadRef)
                        val puntosAct = (creadSnap.getLong("puntos") ?: 0L).toInt()
                        val reservados = (creadSnap.getLong("puntosReservados") ?: 0L).toInt()
                        if (puntosAct < tarea.puntos) throw Exception("Fondos insuficientes")
                        t.update(creadRef, mapOf("puntos" to (puntosAct - tarea.puntos), "puntosReservados" to (reservados + tarea.puntos)))
                    }
                }

                // escribir la tarea actualizada
                t.set(docRef, map)

                // Retornar valor (no usado)
                null
            }.await()

            // Notificación fuera de la transacción: si se asignó ahora, crear notificación
            if (previo != null && previo.asignadoA.isNullOrBlank() && !tarea.asignadoA.isNullOrBlank()) {
                try {
                    val repoNot = com.example.tfg.repositorio.RepositorioNotificaciones()
                    val contenido = mapOf("tipo" to "asignacion", "tareaId" to tarea.id, "titulo" to tarea.titulo, "puntos" to tarea.puntos, "desde" to (tarea.creadoPor ?: ""))
                    val destinatario = tarea.asignadoA!!
                    val not = com.example.tfg.modelo.Notificacion(id = "", tipo = "asignacion", contenido = contenido, destinatario = destinatario, visto = false, fecha = com.google.firebase.Timestamp.now())
                    repoNot.enviarNotificacion(not)
                } catch (e: Exception) {
                    // ignore notification failures
                }
            }

            Result.success(tarea)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resolverReclamo(tareaId: String, aceptado: Boolean): Result<Tarea> {
        // Simple implementation: toggle estado
        return try {
            val docRef = firestore.collection(coleccion).document(tareaId)
            val snap = docRef.get().await()
            val tarea = docToTarea(snap) ?: return Result.failure(Exception("Tarea no encontrada"))
            val nueva = if (aceptado) tarea.copy(estado = "confirmada", fechaReclamada = null, reclamadoPor = null, motivoReclamo = null) else tarea.copy(estado = "pendiente", fechaReclamada = null, reclamadoPor = null, motivoReclamo = null)
            val map = mapOf("estado" to nueva.estado, "fechaReclamada" to nueva.fechaReclamada, "reclamadoPor" to nueva.reclamadoPor, "motivoReclamo" to nueva.motivoReclamo)
            docRef.update(map).await()

            // si aceptado, también transferir
            if (aceptado) {
                try {
                    if (!nueva.asignadoA.isNullOrBlank() && !nueva.creadoPor.isNullOrBlank()) {
                        com.example.tfg.service.LocalizadorServicios.repositorioAuth.sumarPuntosConBonificacion(nueva.asignadoA!!, nueva.puntos)
                        com.example.tfg.service.LocalizadorServicios.repositorioAuth.liberarPuntos(nueva.creadoPor!!, nueva.puntos)
                    }
                } catch (e: Exception) { /* ignore */ }
            }

            Result.success(nueva)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun marcarCompletada(tareaId: String, ejecutorUid: String): Result<Unit> {
        return try {
            val docRef = firestore.collection(coleccion).document(tareaId)
            val snap = docRef.get().await()
            if (!snap.exists()) return Result.failure(Exception("Tarea no encontrada"))
            val tarea = docToTarea(snap) ?: return Result.failure(Exception("Tarea inválida"))

            if (!tarea.requiereConfirmacion) {
                // confirmar y transferir en transacción
                firestore.runTransaction { t ->
                    val snapTx = t.get(docRef)
                    val tareaTx = docToTarea(snapTx) ?: throw Exception("Tarea inválida")
                    if (tareaTx.estado != "pendiente") throw Exception("Tarea no está en estado pendiente")

                    // Leer referencias necesarias ANTES de cualquier escritura
                    val ejecRef = firestore.collection("usuarios").document(ejecutorUid)
                    val ejecSnap = t.get(ejecRef)

                    // ahora aplicar escrituras
                    t.update(docRef, "estado", "confirmada")

                    val actuales = (ejecSnap.getLong("puntos") ?: 0L).toInt()
                    t.update(ejecRef, "puntos", actuales + tarea.puntos)
                }.await()
                Result.success(Unit)
            } else {
                // solo marcar como completada
                docRef.update("estado", "completada").await()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
