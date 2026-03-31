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
                motivoReclamo = data["motivoReclamo"] as? String,
                esEmergencia = data["esEmergencia"] as? Boolean ?: false,
                multiplicadorPuntos = (data["multiplicadorPuntos"] as? Double) ?: (data["multiplicadorPuntos"] as? Long)?.toDouble() ?: 1.0,
                esRecurrente = data["esRecurrente"] as? Boolean ?: false,
                tipoRecurrencia = data["tipoRecurrencia"] as? String,
                rotarMiembros = data["rotarMiembros"] as? Boolean ?: false,
                minutosAntes = (data["minutosAntes"] as? Long)?.toInt() ?: (data["minutosAntes"] as? Int) ?: 30,
                esImportante = data["esImportante"] as? Boolean ?: false
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
                "fechaProgramada" to tarea.fechaProgramada,
                "esEmergencia" to tarea.esEmergencia,
                "multiplicadorPuntos" to tarea.multiplicadorPuntos,
                "esRecurrente" to tarea.esRecurrente,
                "tipoRecurrencia" to tarea.tipoRecurrencia,
                "rotarMiembros" to tarea.rotarMiembros,
                "minutosAntes" to tarea.minutosAntes,
                "esImportante" to tarea.esImportante
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

    override fun observarTareasPorGrupo(grupoId: String): Flow<List<Tarea>> = callbackFlow {
        val combinado = mutableMapOf<String, Tarea>()
        val sub = firestore.collection(coleccion)
            .whereEqualTo("grupoId", grupoId)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                snap?.documents?.mapNotNull { docToTarea(it) }?.forEach { combinado[it.id] = it }
                // Eliminar los que ya no están en el snapshot
                val idsActuales = snap?.documents?.map { it.id }?.toSet() ?: emptySet()
                combinado.keys.removeAll { it !in idsActuales }
                trySend(combinado.values.toList())
            }
        awaitClose { sub.remove() }
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
                "fechaCreada" to (tarea.fechaCreada ?: Timestamp.now()),
                "fechaProgramada" to tarea.fechaProgramada,
                "fechaReclamada" to tarea.fechaReclamada,
                "reclamadoPor" to tarea.reclamadoPor,
                "motivoReclamo" to tarea.motivoReclamo,
                "esEmergencia" to tarea.esEmergencia,
                "multiplicadorPuntos" to tarea.multiplicadorPuntos,
                "esRecurrente" to tarea.esRecurrente,
                "tipoRecurrencia" to tarea.tipoRecurrencia,
                "rotarMiembros" to tarea.rotarMiembros,
                "minutosAntes" to tarea.minutosAntes,
                "esImportante" to tarea.esImportante
            )
            // reestructurar la transacción: leer TODO antes de escribir y usar las mismas DocumentReference
            firestore.runTransaction { t ->
                val snapTx = t.get(docRef)
                val previoTx = docToTarea(snapTx)

                // determinar qué acciones se deben hacer en la transacción
                val necesitaTransferir = (previoTx != null && previoTx.estado == "completada" && tarea.estado == "confirmada")
                val necesitaReservar = (previoTx == null || (previoTx.asignadoA.isNullOrBlank() && !tarea.asignadoA.isNullOrBlank()))
                val necesitaLiberarPorDesasignar = (previoTx != null && !previoTx.asignadoA.isNullOrBlank() && tarea.asignadoA.isNullOrBlank())

                // referencias cacheadas
                val refsPorUid = mutableMapOf<String, com.google.firebase.firestore.DocumentReference>()
                val snapsLectura = mutableMapOf<String, DocumentSnapshot>()

                // preparar referencias y lecturas
                if (necesitaReservar || necesitaLiberarPorDesasignar || necesitaTransferir) {
                    val uids = mutableSetOf<String>()
                    if (!tarea.creadoPor.isNullOrBlank()) uids.add(tarea.creadoPor!!)
                    if (!tarea.asignadoA.isNullOrBlank()) uids.add(tarea.asignadoA!!)
                    if (!previoTx?.creadoPor.isNullOrBlank()!!) uids.add(previoTx.creadoPor!!)

                    uids.forEach { uid ->
                        val ref = firestore.collection("usuarios").document(uid)
                        refsPorUid[uid] = ref
                        snapsLectura[uid] = t.get(ref)
                    }
                }

                // acciones sobre usuarios (leer antes)
                if (necesitaReservar) {
                    val creador = tarea.creadoPor
                    if (!creador.isNullOrBlank()) {
                        val creadRef = refsPorUid[creador] ?: firestore.collection("usuarios").document(creador)
                        val creadSnap = snapsLectura[creador] ?: t.get(creadRef)
                        val puntosAct = (creadSnap.getLong("puntos") ?: 0L).toInt()
                        val reservados = (creadSnap.getLong("puntosReservados") ?: 0L).toInt()
                        t.update(creadRef, mapOf("puntos" to puntosAct, "puntosReservados" to (reservados + tarea.puntos)))
                    }
                }

                if (necesitaLiberarPorDesasignar) {
                    val creador = previoTx?.creadoPor
                    if (!creador.isNullOrBlank()) {
                        val creadRef = refsPorUid[creador] ?: firestore.collection("usuarios").document(creador)
                        val creadSnap = snapsLectura[creador] ?: t.get(creadRef)
                        val reservados = (creadSnap.getLong("puntosReservados") ?: 0L).toInt()
                        val puntosAct = (creadSnap.getLong("puntos") ?: 0L).toInt()
                        val liberar = minOf(reservados, tarea.puntos)
                        t.update(creadRef, mapOf("puntosReservados" to (reservados - liberar), "puntos" to puntosAct))
                    }
                }

                if (necesitaTransferir) {
                    val creador = previoTx?.creadoPor
                    val creadRef = if (!creador.isNullOrBlank()) refsPorUid[creador] ?: firestore.collection("usuarios").document(creador) else null
                    val ejecUid = tarea.asignadoA ?: ""
                    val ejecRef = refsPorUid[ejecUid] ?: firestore.collection("usuarios").document(ejecUid)
                    val ejecSnap = snapsLectura[ejecUid] ?: t.get(ejecRef)
                    val puntosAct = (ejecSnap.getLong("puntos") ?: 0L).toInt()

                    if (creadRef != null) {
                        // obtener snapshot del creador desde cache o leyendo
                        val creadSnapLocal = snapsLectura[creador] ?: t.get(creadRef)
                        val reservadosAct = (creadSnapLocal.getLong("puntosReservados") ?: 0L).toInt()
                        val nuevoReservados = (reservadosAct - tarea.puntos).coerceAtLeast(0)
                        t.update(creadRef, mapOf("puntosReservados" to nuevoReservados))
                    }

                    t.update(ejecRef, mapOf("puntos" to puntosAct + tarea.puntos))
                }

                // por último escribir la tarea actualizada (última escritura)
                t.set(docRef, map)

                null
            }.await()

            // Notificación fuera de la transacción: si se asignó ahora o se reasignó, crear notificación
            if (!tarea.asignadoA.isNullOrBlank() && (previo == null || previo.asignadoA.isNullOrBlank() || previo.asignadoA != tarea.asignadoA)) {
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
            val tarea = docToTarea(snap) ?: return Result.failure(Exception("Tarea no encontrada"))

            // Si requiere confirmación, marcar estado intermedio
            if (tarea.requiereConfirmacion) {
                docRef.update(mapOf("estado" to "pendiente_confirmacion")).await()
                return Result.success(Unit)
            }

            // No requiere confirmación -> confirmar y transferir en transacción
            firestore.runTransaction { t ->
                val snapTx = t.get(docRef)
                val tareaTx = docToTarea(snapTx) ?: throw Exception("Tarea inválida")
                if (tareaTx.estado != "pendiente") throw Exception("Tarea no está en estado pendiente")

                // Referencia a ejecutor
                val ejecRef = firestore.collection("usuarios").document(ejecutorUid)

                // Leer antes de escribir
                val ejecSnap = t.get(ejecRef)

                // 10% de los puntos va a puntosRecompensa
                val incrementoRecompensa = (tareaTx.puntos * 0.10).toInt().coerceAtLeast(1)
                val puntosRecompensaActuales = (ejecSnap.getLong("puntosRecompensa") ?: 0L).toInt()

                // ahora aplicar escrituras
                t.update(docRef, "estado", "confirmada")

                if (!ejecSnap.exists()) {
                    val datos = mapOf("puntos" to tareaTx.puntos, "puntosRecompensa" to incrementoRecompensa)
                    t.set(ejecRef, datos)
                } else {
                    val actuales = (ejecSnap.getLong("puntos") ?: 0L).toInt()
                    t.update(ejecRef, mapOf(
                        "puntos"           to actuales + tareaTx.puntos,
                        "puntosRecompensa" to puntosRecompensaActuales + incrementoRecompensa
                    ))
                }

                null
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmarTarea(tareaId: String, confirmadoPorUid: String): Result<Unit> {
        return try {
            val docRef = firestore.collection(coleccion).document(tareaId)
            val snap = docRef.get().await()
            if (!snap.exists()) return Result.failure(Exception("Tarea no encontrada"))
            val tarea = docToTarea(snap) ?: return Result.failure(Exception("Tarea inválida"))
            if (tarea.estado != "pendiente_confirmacion" && tarea.estado != "completada")
                return Result.failure(Exception("La tarea no está pendiente de confirmación"))

            val ejecUid = tarea.asignadoA ?: return Result.failure(Exception("Tarea sin asignado"))
            val ejecRef = firestore.collection("usuarios").document(ejecUid)
            val creadorUid = tarea.creadoPor
            val creadorRef = if (!creadorUid.isNullOrBlank()) firestore.collection("usuarios").document(creadorUid) else null

            firestore.runTransaction { t ->
                val snapTx = t.get(docRef)
                val tareaTx = docToTarea(snapTx) ?: throw Exception("Tarea inválida en transacción")
                if (tareaTx.estado != "pendiente_confirmacion" && tareaTx.estado != "completada")
                    throw Exception("Estado incorrecto para confirmar")

                val ejecSnap = t.get(ejecRef)
                val creadorSnap = creadorRef?.let { t.get(it) }

                // --- Calcular puntos finales con multiplicador y racha ---
                val rachaActual = (ejecSnap.getLong("rachaDias") ?: 0L).toInt()
                val bonificacionRacha = if (rachaActual > 0 && rachaActual % 7 == 0) 0.10 else 0.0
                val multiplicador = tareaTx.multiplicadorPuntos.coerceAtLeast(1.0)
                val puntosBase = (tareaTx.puntos * multiplicador).toInt()
                val puntosFinales = (puntosBase * (1.0 + bonificacionRacha)).toInt()
                // 10% de los puntos ganados va a puntosRecompensa (redondeado, mínimo 1)
                val incrementoRecompensa = (puntosFinales * 0.10).toInt().coerceAtLeast(1)

                // --- Actualizar ejecutor ---
                val puntosActualesEjec = (ejecSnap.getLong("puntos") ?: 0L).toInt()
                val puntosRecompensaActuales = (ejecSnap.getLong("puntosRecompensa") ?: 0L).toInt()
                val nuevaRacha = rachaActual + 1
                t.update(docRef, "estado", "confirmada")
                if (!ejecSnap.exists()) {
                    t.set(ejecRef, mapOf("puntos" to puntosFinales, "rachaDias" to nuevaRacha, "puntosRecompensa" to incrementoRecompensa))
                } else {
                    t.update(ejecRef, mapOf(
                        "puntos"           to puntosActualesEjec + puntosFinales,
                        "rachaDias"        to nuevaRacha,
                        "puntosRecompensa" to puntosRecompensaActuales + incrementoRecompensa
                    ))
                }

                // --- Liberar puntos reservados del creador ---
                if (creadorRef != null && creadorSnap != null) {
                    val reservados = (creadorSnap.getLong("puntosReservados") ?: 0L).toInt()
                    t.update(creadorRef, "puntosReservados", (reservados - tareaTx.puntos).coerceAtLeast(0))
                }

                null
            }.await()

            // --- Crear siguiente tarea si es recurrente (fuera de la transacción) ---
            if (tarea.esRecurrente && !tarea.tipoRecurrencia.isNullOrBlank()) {
                try {
                    val siguienteFecha = calcularSiguienteFecha(tarea.fechaProgramada, tarea.tipoRecurrencia!!)
                    // Rotar miembro si aplica
                    val siguienteAsignado = if (tarea.rotarMiembros && !tarea.creadoPor.isNullOrBlank() && tarea.asignadoA != tarea.creadoPor) {
                        tarea.creadoPor
                    } else if (tarea.rotarMiembros) {
                        tarea.asignadoA
                    } else {
                        tarea.asignadoA
                    }
                    val nuevaTarea = tarea.copy(
                        id = "",
                        estado = "pendiente",
                        fechaCreada = Timestamp.now(),
                        fechaProgramada = siguienteFecha,
                        asignadoA = siguienteAsignado,
                        multiplicadorPuntos = 1.0,
                        esEmergencia = false
                    )
                    crearTarea(nuevaTarea)
                } catch (_: Exception) { /* no bloquear si falla la recurrencia */ }
            }

            // --- Programar recordatorio si tiene fecha y minutos ---
            try {
                val contexto = com.example.tfg.TFGApplication.appContext
                if (contexto != null && tarea.fechaProgramada != null) {
                    val triggerMs = tarea.fechaProgramada.toDate().time - (tarea.minutosAntes * 60 * 1000L)
                    if (triggerMs > System.currentTimeMillis()) {
                        com.example.tfg.service.NotificationScheduler.scheduleReminder(
                            contexto, tarea.id,
                            "Recordatorio: ${tarea.titulo}",
                            "Tarea programada en ${tarea.minutosAntes} min",
                            triggerMs
                        )
                    }
                }
            } catch (_: Exception) { }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Calcula la siguiente fecha programada según tipo de recurrencia */
    private fun calcularSiguienteFecha(fechaBase: Timestamp?, tipo: String): Timestamp {
        val cal = java.util.Calendar.getInstance()
        if (fechaBase != null) cal.time = fechaBase.toDate()
        when (tipo.lowercase()) {
            "diaria"   -> cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            "semanal"  -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            "mensual"  -> cal.add(java.util.Calendar.MONTH, 1)
        }
        return Timestamp(cal.time)
    }
}
