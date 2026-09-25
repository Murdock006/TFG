# Design: Harden Account Deletion and Security Cleanup

## Technical Approach

Make the client-side deletion flow **complete, verifiable, and honest**, and implement the owner's
**group dissolution + remaining-member reset** semantics. The cleanup moves from silent best-effort
(`AuthRepositorioFirebase.limpiarDatosAsociados`, current `data/firebase/AuthRepositorioFirebase.kt:265-363`)
to a per-step report with bounded retry; the Firebase Auth deletion is **gated** on a fully successful
cleanup. Group handling gains dissolution (delete `grupos/{gid}`, clear the remaining member's
`grupoId` in Firestore, reset balances/streak) and deletion of the dissolved group's remaining tasks.
This implements convergence roadmap step 7 aligned to the authoritative guide
(`docs/architecture/TEAMTASK_GUIDE.md:176`) and closes TD-13, while preserving the re-auth dialog and
the `EliminacionCuentaActivity` info page.

The change stays inside the existing mixed MVVM/service-locator architecture
(`openspec/config.yaml:20`): no Hilt, Room, Retrofit, or Compose. `strict_tdd: false`
(`openspec/config.yaml:6`); verification is the focused JVM suite plus grep audits plus a manual
matrix.

## Deferred Spec Questions — Resolved

These three items were explicitly deferred to design by the spec phase.

### (a) Cleanup report surface — which layer carries per-step results

**Decision: the per-step report lives entirely in the repository layer. Only a rendered failure
message crosses to `VistaModeloAuth` and `FragmentPerfil`.**

- `PasoLimpieza` and `ResultadoLimpiezaCuenta` are declared in
  `app/src/main/java/com/example/tfg/repositorio/AuthRepositorio.kt` (the contract file).
- `AuthRepositorioFirebase.limpiarDatosAsociados(uid)` returns `ResultadoLimpiezaCuenta` (private
  method — the type does not cross the interface).
- `AuthRepositorio.eliminarCuentaActual(password): Result<Unit>` keeps its **existing signature**.
  On an incomplete report it returns `Result.failure(Exception("..."))` whose message is
  `ResultadoLimpiezaCuenta.mensajeFallos()`, naming the failed steps.
- `VistaModeloAuth.eliminarCuentaActual` needs **no code change**: it already forwards
  `res.exceptionOrNull()?.message` and stores the whole `Result<Unit>` in
  `_eliminacionCuenta` (`viewmodel/VistaModeloAuth.kt:102-120`).
- `FragmentPerfil` consumes that failure message (`vista/FragmentPerfil.kt:277-295`).

**Why not surface the report through the ViewModel and Fragment too?** The spec only requires the
failure to *name the failed step(s)* (`account-deletion` requirement "Cleanup failures SHALL never be
reported as success"); it does not require a per-step UI. Keeping a `Result<Unit>` contract avoids a
breaking interface change, keeps the UI dumb, and preserves the existing LiveData observer. Exposing
`Result<ResultadoLimpiezaCuenta>` was rejected: it would force richer Fragment state, a new UI, and a
wider diff for no spec-required behavior.

### (b) Bounded-retry policy

**Decision: every mutation cleanup step runs through one retry wrapper with a fixed budget.**

- `MAX_INTENTOS_LIMPIEZA = 3` attempts per step.
- Fixed `DELAY_REINTENTO_MS = 300L` between attempts (no exponential backoff — the app is a
  two-person couple app; simplicity beats tuning).
- Retry only on **transient** errors: `FirebaseNetworkException`;
  `FirebaseFirestoreException` with code `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `ABORTED`,
  `RESOURCE_EXHAUSTED`, `INTERNAL`, or `CANCELLED`.
- Non-transient errors (`PERMISSION_DENIED`, `UNAUTHENTICATED`, `INVALID_ARGUMENT`, …) fail fast on
  attempt 1: retrying an authorization failure is pointless and only delays the user.
- **Idempotency makes retry safe**: every step is a delete or a set/update to a fixed value, so
  re-running the whole cleanup converges. Firestore `delete()` is a no-op when the document is absent
  (satisfies "Already-deleted tasks are tolerated" and "Missing avatar document is not a failure").
  Storage evidence deletion tolerates `StorageException.ERROR_OBJECT_NOT_FOUND` as success.
- Every step is attempted regardless of earlier failures (the report is collected across all steps;
  no step aborts the sweep). Only the final **Auth deletion** is gated.

### (c) Sequencing of dissolution + reset relative to cleanup steps

**Decision: within each group, delete the dissolved group's remaining tasks FIRST, then commit the
dissolution core (delete group doc + clear `grupoId` + reset balances) as one atomic batch. The
deleted user's own `usuarios/{uid}` document is deleted LAST.**

Sequencing table (the whole cleanup is one ordered pass):

| # | Step id | Operation | Why this position |
|---|---|---|---|
| 1 | `grupos:enumeracion` | Read `grupos`, filter to groups whose `miembros` map contains `uid` | The group document is the only discovery handle for that group's tasks; do it while it exists |
| 2 | `grupo:{gid}:borrarVacio` | No members remain → delete group doc | Current behavior preserved |
| 3 | `grupo:{gid}:tareas` | Exactly one remains → query `tareas whereEqualTo("grupoId", gid)`, delete refs in chunks ≤ 500 | **Tasks before group doc**: if this fails, the group still exists so a retry can re-discover the group and its tasks |
| 4 | `grupo:{gid}:disolver` / `:limpiarGrupoId` / `:resetSaldos` | One atomic batch: delete group doc + `set merge` remaining user doc (`grupoId=null`, balances/streak 0) | Atomic core; emitted as three sub-steps sharing one commit outcome |
| 5 | `grupo:{gid}:miembros` | Two+ remain → update `miembros` map removing `uid` | No dissolution |
| 6 | `tareas:creadas` | Delete `tareas whereEqualTo("creadoPor", uid)` | After groups; idempotent overlap with step 3 is fine |
| 7 | `tareas:asignadas` | Unassign `tareas whereEqualTo("asignadoA", uid)` not created by `uid` | After created-tasks deletion |
| 8 | `invitaciones`, `notificaciones:recibidas`, `notificaciones:emitidas`, `recompensas`, `canjes` | Field sweeps | Independent |
| 9 | `disputas` | Delete evidence first, then the dispute doc | Evidence must be deletable while its URL is still discoverable from the doc |
| 10 | `avatar` | Delete `avatares/{uid}` | Independent |
| 11 | `usuario` | Delete `usuarios/{uid}` | **Last**: the identity anchor; a partial run keeps the profile intact and `login()` recreates a default doc only if truly missing (`AuthRepositorioFirebase.kt:99-114`) |

**Convergence argument.** Deleting the group doc before its tasks would orphan those tasks forever
(a retry enumerates groups by `uid` membership and would no longer find the dissolved group). The
chosen order — tasks, then group core — means any failure leaves the group discoverable, so the
idempotent retry converges. There is no ordering dependency between the group steps and the generic
`tareas` steps: whichever deletes first, the other is a no-op (Firestore deletes are idempotent).

## Architecture Decisions

### Decision: One structured report in the repository, message-only surface upward

**Choice**: `ResultadoLimpiezaCuenta` + `PasoLimpieza` in `AuthRepositorio.kt`;
`eliminarCuentaActual` keeps `Result<Unit>` and returns a failure naming failed steps.
**Alternatives considered**: (1) `Result<ResultadoLimpiezaCuenta>` across the interface; (2) a sealed
`ResultadoEliminacion` hierarchy through the ViewModel; (3) a bare boolean.
**Rationale**: (1) and (2) widen the UI contract for no spec-required behavior; (3) cannot name the
failed step. The report must exist (spec requires per-step outcomes) but need only be consumed at the
repository boundary.

### Decision: Gate `FirebaseAuth.delete()` on `reporte.completado`

**Choice**: run every cleanup step, collect the report, and only call `usuarioActual.delete()` when
`reporte.completado`; otherwise return a failure and leave the account alive.
**Alternatives considered**: (1) keep the current cleanup-then-delete order; (2) delete Auth first,
then clean up.
**Rationale**: (1) is the TD-13 failure mode (live account with wiped data); (2) loses the ability to
authenticate the cleanup writes (Firestore rules require a signed-in owner). Gating removes (1); the
residual inverse risk (Auth deletion fails after a complete cleanup) is surfaced through the
preserved re-auth path.

### Decision: Atomic batch for the dissolution core; tasks deleted separately

**Choice**: `batch.delete(grupoRef)` + `batch.set(remainingUserRef, {grupoId:null, puntos:0,
puntosReservados:0, puntosRecompensa:0, rachaDias:0}, SetOptions.merge())` as one commit; the group's
task deletions are a separate, chunked step executed before it.
**Alternatives considered**: (1) separate writes for group delete / grupoId clear / balance reset;
(2) fold the task deletions into the same batch; (3) one `runTransaction`.
**Rationale**: (1) loses atomicity of the core (a half-dissolved group is worse than a retryable
failure). (2) hits the 500-write batch limit for large groups and moves the query-then-refs pattern
inside a batch, which Firestore does not allow. (3) reads-then-writes inside a transaction conflict
under concurrent writes and buys nothing over an idempotent retry. `set merge` is used instead of
`update` so a missing remaining-user document does not fail the batch (matches the existing
`RepositorioPareja.limpiarGrupoIdUsuario` pattern, `repositorio/RepositorioPareja.kt:291-302`).

### Decision: Delete dispute evidence before its dispute document

**Choice**: for each dispute, delete every `pruebas[]` Storage object; only then delete the dispute
doc. A non-`OBJECT_NOT_FOUND` storage failure fails the `disputas` step and the doc is not deleted.
**Alternatives considered**: delete the doc first, or swallow per-URL errors (current behavior,
`:352-356`).
**Rationale**: the document is the only place the evidence URLs exist; deleting it first makes orphaned
evidence undiscoverable to a retry. Swallowing violates "no silent cleanup failures".

### Decision: Persistent dialog for the partial-failure message

**Choice**: in `FragmentPerfil`, replace the partial-failure `Toast` with an `AlertDialog`
("Eliminación incompleta") showing `resultado.exceptionOrNull()?.message`. The success path
(`limpiarEstadoLocalPostEliminacion()` + `navegarALoginLimpiandoBackstack()`) and the button
re-enable are unchanged. The re-auth dialog (`:214-275`) and `EliminacionCuentaActivity` are not
touched.
**Alternatives considered**: keep the transient `Toast` (`:288-291`).
**Rationale**: the failure names one or more steps and can be long; a sustained dialog guarantees the
"clear partial-failure message" required by the spec and gives the retry affordance without changing
protected UX.

## Data Flow

```
FragmentPerfil                         VistaModeloAuth (unchanged)          AuthRepositorioFirebase
-------------                          --------------------------          -----------------------
btnEliminarCuenta
  └─ dialog (ELIMINAR + password) ──► eliminarCuentaActual(password)
                                          └─ repositorio.eliminarCuentaActual(password)
                                                                              ├─ re-auth (EmailAuthProvider)
                                                                              │    └─ fail → Result.failure("Contraseña incorrecta…")
                                                                              ├─ limpiarDatosAsociados(uid)
                                                                              │    → ResultadoLimpiezaCuenta (per-step)
                                                                              ├─ if (!completado)
                                                                              │    └─ Result.failure(mensajeFallos())   ← no Auth delete
                                                                              └─ if (completado)
                                                                                   ├─ usuarioActual.delete()  (or RecentLoginRequired)
                                                                                   ├─ clear cache + signOut
                                                                                   └─ Result.success(Unit)
  ◄──── LiveData _eliminacionCuenta ◄──── _eliminacionCuenta.value = res
  ├─ isSuccess → clear local tfg_prefs grupoId, Toast ok, navigate to login
  └─ isFailure → AlertDialog "Eliminación incompleta" + re-enable button (no navigation)

Remaining member's device (reactive, no new mechanism):
  grupos/{gid} deleted ──► ParejaViewModel.startObservingGrupo (ParejaViewModel.kt:83-105)
                            └─ observer emits null ──► clears tfg_prefs "grupoId" (:91-98)
```

## Cleanup Sequence and Write Shapes

`limpiarDatosAsociados` becomes an exception-safe collector; it never throws for a step failure.

```kotlin
private suspend fun limpiarDatosAsociados(uid: String): ResultadoLimpiezaCuenta {
    val pasos = mutableListOf<PasoLimpieza>()

    pasos += limpiarGrupos(uid)                                  // steps 1-5

    pasos += ejecutarPaso(
        nombre = "tareas:creadas",
        descripcion = "tareas creadas por el usuario"
    ) {
        firestore.collection("tareas").whereEqualTo("creadoPor", uid).get().await()
            .documents.forEach { it.reference.delete().await() }
    }

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

    pasos += ejecutarPaso("invitaciones", "invitaciones") { borrarDocumentosPorCampo("invitaciones", "creadoPor", uid) }
    pasos += ejecutarPaso("notificaciones:recibidas", "notificaciones recibidas") { borrarDocumentosPorCampo("notificaciones", "destinatario", uid) }
    pasos += ejecutarPaso("notificaciones:emitidas", "notificaciones emitidas") { borrarDocumentosPorCampo("notificaciones", "contenido.desde", uid) }
    pasos += ejecutarPaso("recompensas", "recompensas") { borrarDocumentosPorCampo("recompensas", "creadoPor", uid) }
    pasos += ejecutarPaso("canjes", "canjes") { borrarDocumentosPorCampo("canjes", "usuarioUid", uid) }

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
            doc.reference.delete().await()          // only reached if every evidence delete succeeded
        }
    }

    pasos += ejecutarPaso("avatar", "avatar") {
        firestore.collection("avatares").document(uid).delete().await()
    }

    pasos += ejecutarPaso("usuario", "perfil de usuario") {   // LAST
        firestore.collection("usuarios").document(uid).delete().await()
    }

    return ResultadoLimpiezaCuenta(pasos)
}
```

Group handling:

```kotlin
private suspend fun limpiarGrupos(uid: String): List<PasoLimpieza> {
    val pasos = mutableListOf<PasoLimpieza>()

    val (pasoLectura, grupos) = ejecutarPasoConValor("grupos:enumeracion", "grupos") {
        firestore.collection("grupos").get().await().documents
            .filter { (it.get("miembros") as? Map<*, *>)?.containsKey(uid) == true }
    }
    pasos += pasoLectura
    if (grupos == null) return pasos                    // read failed: nothing else to do safely

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

private suspend fun disolverGrupo(grupoDoc: DocumentSnapshot, restanteUid: String): List<PasoLimpieza> {
    val gid = grupoDoc.id
    val pasos = mutableListOf<PasoLimpieza>()

    // 1) Dissolved group's tasks FIRST (idempotent, chunked).
    val (pasoTareas, tareas) = ejecutarPasoConValor("grupo:$gid:tareas", "tareas del grupo") {
        firestore.collection("tareas").whereEqualTo("grupoId", gid).get().await()
            .documents.map { it.reference }
    }
    pasos += pasoTareas
    if (tareas == null) return pasos        // group doc kept on purpose: retry re-discovers it

    pasos += ejecutarPaso("grupo:$gid:tareas", "tareas del grupo") {
        tareas.chunked(LIMITE_BATCH_FIRESTORE).forEach { chunk ->
            val b = firestore.batch()
            chunk.forEach { b.delete(it) }
            b.commit().await()
        }
    }
    if (!pasos.last().exito) return pasos   // do NOT delete the group; keep it discoverable

    // 2) Atomic dissolution core (3 sub-steps share one commit outcome).
    val pasoCore = ejecutarPaso("grupo:$gid:disolver", "disolución del grupo") {
        val batch = firestore.batch()
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
    pasos += pasoCore.copy(nombre = "grupo:$gid:disolver",    descripcion = "disolución del grupo")
    pasos += pasoCore.copy(nombre = "grupo:$gid:limpiarGrupoId", descripcion = "grupoId del miembro restante")
    pasos += pasoCore.copy(nombre = "grupo:$gid:resetSaldos", descripcion = "reinicio de puntos y racha del miembro restante")
    return pasos
}
```

Retry wrapper:

```kotlin
private suspend fun ejecutarPaso(
    nombre: String,
    descripcion: String,
    intentosMax: Int = MAX_INTENTOS_LIMPIEZA,
    bloque: suspend () -> Unit
): PasoLimpieza = ejecutarPasoConValor(nombre, descripcion, intentosMax) { bloque(); Unit }.first

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
            if (!esErrorTransitorio(e)) return PasoLimpieza(nombre, descripcion, false, intento, ultimoError) to null
            if (intento < intentosMax) delay(DELAY_REINTENTO_MS)
        }
    }
    return PasoLimpieza(nombre, descripcion, false, intentosMax, ultimoError) to null
}

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
```

`eliminarCuentaActual` (gate):

```kotlin
// 1) re-auth (unchanged, AuthRepositorioFirebase.kt:231-243)
// 2) cleanup + gate
val reporte = limpiarDatosAsociados(uid)
if (!reporte.completado) {
    Log.w(TAG, "Limpieza incompleta; no se elimina la cuenta uid=$uid: ${reporte.mensajeFallos()}")
    return Result.failure(Exception(reporte.mensajeFallos()))
}
// 3) only now delete Auth (unchanged: :249-253)
usuarioActual.delete().await()
_usuarioCache = null
auth.signOut()
Result.success(Unit)
```

`borrarDocumentosPorCampo` keeps its query-loop shape but **stops swallowing**: remove its internal
`try/catch` so failures propagate to `ejecutarPaso` (grep-auditable: the `Log.w("No se pudieron…")`
lines at `:270, :297, :307, :326, :355, :361, :372` disappear from the cleanup path).

### Atomicity boundaries (what cannot be atomic)

- The **dissolution core** (delete group doc + remaining-user reset) is one batch: atomic.
- **Group task deletions** before it are chunked batches: not atomic with the core, but ordered so a
  retry converges (group still discoverable on failure).
- **Storage evidence deletion** is not part of any Firestore transaction.
- **Each group** is its own batch; a user in multiple groups has no cross-group atomicity.
- **The generic field sweeps** are separate query+delete loops.
- **The final `FirebaseAuth.delete()`** is a different service entirely; the whole flow is therefore
  not atomic, which the spec accepts and requires to be surfaced.

## Interfaces / Contracts

Added to `app/src/main/java/com/example/tfg/repositorio/AuthRepositorio.kt` (no signature change to
the interface):

```kotlin
data class PasoLimpieza(
    val nombre: String,        // stable id for audits/retries, e.g. "grupo:abc:resetSaldos"
    val descripcion: String,   // user-facing label, e.g. "reinicio de puntos y racha del miembro restante"
    val exito: Boolean,
    val intentos: Int,
    val error: String? = null
)

data class ResultadoLimpiezaCuenta(val pasos: List<PasoLimpieza>) {
    val completado: Boolean get() = pasos.all { it.exito }
    val fallidos: List<PasoLimpieza> get() = pasos.filterNot { it.exito }
    fun mensajeFallos(): String {
        val etiquetas = fallidos.map { it.descripcion }.distinct()
        return "No se pudo completar la limpieza de la cuenta" +
            if (etiquetas.isEmpty()) "." else " (${etiquetas.joinToString(", ")}). Inténtalo de nuevo."
    }
}
```

Constants (implementation detail, in `AuthRepositorioFirebase`):

```kotlin
private const val MAX_INTENTOS_LIMPIEZA = 3
private const val DELAY_REINTENTO_MS = 300L
private const val LIMITE_BATCH_FIRESTORE = 500
```

New imports needed in `AuthRepositorioFirebase.kt`: `com.google.firebase.firestore.SetOptions`,
`com.google.firebase.firestore.DocumentSnapshot`, `com.google.firebase.firestore.FirebaseFirestoreException`,
`com.google.firebase.firestore.WriteBatch`, `com.google.firebase.storage.StorageException`,
`kotlinx.coroutines.delay`.

## File Changes

| File | Action | Description |
|---|---|---|
| `app/src/main/java/com/example/tfg/repositorio/AuthRepositorio.kt` | Modify | Add `PasoLimpieza` + `ResultadoLimpiezaCuenta`; `eliminarCuentaActual` signature unchanged |
| `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt` | Modify | Retry wrapper + per-step report; add `avatares/{uid}` delete; group dissolution + remaining-member reset + dissolved-group task deletion; `borrarDocumentosPorCampo` no longer swallows; reorder cleanup; gate Auth deletion |
| `app/src/main/java/com/example/tfg/vista/FragmentPerfil.kt` | Modify | Partial-failure branch: AlertDialog instead of Toast; success path and re-auth dialog unchanged |
| `app/src/main/java/com/example/tfg/viewmodel/VistaModeloAuth.kt` | Reused | Already forwards `Result<Unit>` failure message; no code change (verified `:102-120`) |
| `app/src/main/java/com/example/tfg/viewmodel/ParejaViewModel.kt` | Reused | Reactive local `grupoId` clear already exists (`:83-105`, clear `:91-98`); no new mechanism |
| `app/src/main/java/com/example/tfg/repositorio/RepositorioPareja.kt` | Reused | `quitarMiembroGrupo`/`limpiarGrupoIdUsuario` pattern referenced only; no change |
| `app/src/test/java/com/example/tfg/repositorio/ResultadoLimpiezaCuentaTest.kt` | Create | Pure-JVM JUnit 4 test of the report aggregation invariant |
| `firestore.rules` | Unchanged | Evidence only: `:27-32` self-delete/any-update on `usuarios`, `:37-40` self-write on `avatares`, `:43-48` delete on `grupos` |
| `openspec/specs/*` canonical + deltas | Unchanged | Owned by the spec/archive flow, not this design |

## Testing Strategy

No mocking framework, coroutines-test, or Turbine is on the classpath (`app/build.gradle.kts:104-106`
declares only JUnit 4 + AndroidX Test). Firebase-dependent behavior cannot be unit-tested here.

| Layer | What to test | Approach |
|---|---|---|
| Unit (JVM) | `ResultadoLimpiezaCuenta.completado`, `.fallidos`, `.mensajeFallos()` — the "never report success when a step failed" invariant | New pure JUnit 4 test, no Firebase/Android types |
| Integration | Cleanup report collection, retry, gate, dissolution writes | Not automatable in this repo; covered by the manual matrix |
| E2E | Full two-account deletion + remaining-member end state | Manual matrix (no emulator/device in this run; `strict_tdd: false`) |

`ResultadoLimpiezaCuentaTest` cases: all steps successful → `completado == true`; one failed step →
`completado == false`, `fallidos` contains it, `mensajeFallos()` includes its description; duplicate
descriptions → deduplicated; empty list → `completado == true`.

## Verification Plan

Ordered gates for `sdd-verify` / apply (no device or emulator in this run).

1. **Compile gate** (both build types must compile):
   `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain`
   (the `emulator` and `release` build types exist — `app/build.gradle.kts:29-47`).
2. **JVM suite** (existing regression + the new report test):
   `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
3. **Grep audits** (against `AuthRepositorioFirebase.kt`):
   - No silent cleanup catches: `Select-String -Pattern 'No se pudieron|No se pudo borrar|No se pudo limpiar'`
     returns **zero** hits inside the cleanup path (the re-auth message at `:241` and the generic
     `:261` failure are not cleanup catches).
   - Avatar coverage: `Select-String -Pattern 'collection\("avatares"\)'` returns **one** hit.
   - Dissolution writes present: `Select-String -Pattern '"grupoId" to null'`,
     `'whereEqualTo\("grupoId"', 'rachaDias'` each present.
   - Gate present: `Select-String -Pattern 'if \(!reporte\.completado\)'` present.
4. **Manual matrix** (below) executed by the developer against the Firebase project/console later.
   No test net beyond the JVM suite; no device/emulators in this run.

## Manual Verification Matrix

Mapped to the `account-deletion` delta scenarios. Setup: two accounts A and B in one two-member group,
with at least one task per direction, a custom reward, a redemption, a notification, an invitation,
and a dispute with evidence; account A also owns an `avatares/A` document.

| Scenario (spec) | How | Expected |
|---|---|---|
| Valid password proceeds to cleanup | A deletes with the correct password | Cleanup runs after re-auth; A's account is deleted |
| Incorrect password mutates nothing | A confirms with a wrong password | Failure "Contraseña incorrecta…"; inspect collections → nothing deleted/updated |
| Recent-login requirement is surfaced | Force `delete()` to need recent login | Re-auth message shown; account not reported deleted |
| Wrong confirmation text / blank password rejected | Type not-`ELIMINAR` / leave password blank | Dialog does not dispatch; no writes |
| Info page is display-only | Open `EliminacionCuentaActivity` | Only renders `eliminacion-cuenta.html`; no mutation |
| Complete cleanup deletes the Auth account | Happy path with all collections populated | Account gone; local session/prefs cleared; login screen shown |
| Partial failure aborts Auth deletion | Force one cleanup step to fail (e.g. deny a rule) | Auth account **not** deleted; dialog names the failed step; no success |
| Retry converges | After a partial run, retry deletion | Whole cleanup re-runs; already-cleaned targets succeed without error |
| Auth-deletion failure after complete cleanup | Cleanup succeeds, force `delete()` to fail | Re-auth message; account survives with cleaned data; retry re-runs cleanup |
| Every cleanup target removed/updated | Inspect `usuarios`, `avatares`, `grupos`, `tareas`, `invitaciones`, `notificaciones`, `recompensas`, `canjes`, `disputas`, dispute Storage folder | No A-owned document/evidence remains; report records a step per target |
| Assigned tasks are unassigned, not deleted | B creates a task assigned to A in a **kept** group context; delete A | Task remains with `asignadoA=null`, `estado=pendiente`, reclamada fields null |
| Dispute evidence deleted with its dispute | A's dispute with `pruebas[]` | Each Storage object gone; dispute doc gone |
| Avatar document is removed | A owns `avatares/A` | `avatares/A` gone |
| Missing avatar document is not a failure | Delete A without an avatar doc | `avatar` step reported success; Auth deletion proceeds |
| User-created tasks deleted (cascade) | A created tasks | Tasks gone; B can no longer read them (documented cascade) |
| Exactly one member remains and the group is dissolved | Delete A from the A+B group | `grupos/{gid}` gone; `usuarios/B.grupoId == null`; group unreadable to B |
| Two or more members remain and the group is kept | Three-member group; delete A | Group remains; `miembros` no longer contains A; B/C `grupoId` untouched |
| No members remain and the group is deleted | A is the only member | Group doc deleted |
| Balances and streak are reset | B had non-zero `puntos`/`puntosReservados`/`puntosRecompensa`/`rachaDias` | All four are `0`; `resetSaldos` step reported success |
| Other profile fields are preserved | Inspect B after dissolution | `nombre`, `email`, and other fields unchanged |
| Group tasks are deleted on dissolution | Group had tasks with that `grupoId` | All such tasks deleted; `grupo:{gid}:tareas` step success |
| Already-deleted tasks are tolerated | Retry after tasks were already removed | Delete succeeds without error |
| Remaining member's local group reference clears | On B's device, A deletes | Group observer emits `null`; B's `tfg_prefs` `grupoId` cleared with no manual action |
| Deleting client clears only its own local state | Complete A's deletion | A clears its own `tfg_prefs` `grupoId`; no write to B's prefs |
| Success is shown only on a completed deletion | Happy path | Success message + login navigation |
| Partial failure shows a clear message and allows retry | Partial-run flow | Dialog identifies incomplete cleanup; button re-enabled; no navigation, no success claim |
| Deleting user's observable outcome | After A's success | `usuarios/A` and `avatares/A` gone; memberships/data cleaned; local session + `grupoId` cleared; login shown |
| Remaining member's observable outcome | After dissolution | B has no active group; four fields `0`; group tasks unreadable; B's device cleared local `grupoId` automatically |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. This is a client-side Firestore/Storage data-deletion change.

## Migration / Rollout

No migration. Client-only change; no schema, index, or rules change, no Cloud Function. Rollout is a
normal direct-to-`master` commit by the solo developer. Rollback by reverting the change's commit(s)
restores the previous best-effort cleanup and the old group-removal behavior; the rollback boundary is
`AuthRepositorio`, `AuthRepositorioFirebase`, `FragmentPerfil`, and the new test. Irreversible data
already deleted by a completed run cannot be restored by a revert (a Firebase point-in-time restore
would be required, `[UNVERIFIED]`).

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Non-atomic cleanup leaves partial state on abort | Med | Med | Idempotent steps; failed report gates Auth deletion; retry re-runs everything; documented |
| A persistent cleanup failure blocks deletion | Med | Med | Bounded retry (3) + fast-fail on non-transient; message names the failed step; user retries |
| `usuarios` cross-user update denied in production | Low | Med | Local rules allow it (`firestore.rules:27-32`); failure is surfaced, not swallowed; console parity `[UNVERIFIED]` |
| Dissolution deletes B's tasks/balances (irreversible) | Med | Med | Confirmed owner decisions; confinement to the one-remaining-member case; documented |
| `FirebaseAuth.delete()` fails after a complete cleanup | Low | Low | Preserved re-auth path (`AuthRepositorioFirebase.kt:256-258`); retry re-runs idempotent cleanup |
| 500-write batch limit on large dissolved groups | Low | Low | Tasks are deleted in chunks ≤ 500 before the 2-op core batch |
| No automated Firebase tests; regression risk in Auth/group flows | Med | Med | JVM report test + grep audits + manual matrix; `strict_tdd: false` (`openspec/config.yaml:6`) |
| `[UNVERIFIED]` console rule parity | Med | Med | Marked throughout; failures surface; deploy/parity deferred to the rules/indexes change |

## Open Questions

- [ ] Do production Firestore rules allow a signed-in user to update another member's `usuarios`
      document (needed for dissolution `limpiarGrupoId`/`resetSaldos`)? Local rules say yes
      (`firestore.rules:27-32`); Console parity is `[UNVERIFIED]`. If denied, the dissolution core
      step fails, the account is not deleted, and the failure is surfaced — accepted.
- [ ] Does production allow self-delete of `avatares/{uid}` and delete of `grupos/{gid}`? Local rules
      say yes (`firestore.rules:37-40`, `:43-48`); Console parity `[UNVERIFIED]`.
- [ ] Point-in-time restore availability for the irreversible cascade (decisions 1 and 4) —
      `[UNVERIFIED]`; no code impact.
