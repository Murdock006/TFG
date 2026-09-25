# Tasks: Harden Account Deletion and Security Cleanup

Implementation breakdown for `teamtask-account-deletion-security`. The operative contract is
`design.md` (read-only): types `PasoLimpieza` / `ResultadoLimpiezaCuenta`, the retry budget
`MAX_INTENTOS_LIMPIEZA = 3`, the 11-step cleanup sequence, the Auth-deletion gate, and the
verification plan. This file turns that contract into ordered, session-sized work units.

Scope: client-only Kotlin. Three production files change, one JVM test is added. No Gradle,
rules, schema, navigation, or Compose changes. `strict_tdd: false` (`openspec/config.yaml:6`
(read-only)); the JVM report test plus grep audits plus the manual matrix are the verification
net. Full artifacts: `proposal.md` (read-only), `exploration.md` (read-only),
`specs/account-deletion/spec.md` (read-only), `specs/firestore-contracts/spec.md` (read-only),
`specs/implementation-recipes/spec.md` (read-only).

## Review Workload Forecast

> **Informational only.** Owner directive: solo developer, direct-to-`master`, no pull requests.
> This forecast does not gate apply, does not request a chained/stacked split, and MUST NOT be
> used to slice the change. The 400-line review policy does not apply to this delivery path.

| Field | Value |
|-------|-------|
| Estimated changed lines | ~420–560 (`additions + deletions`) |
| 400-line budget risk | High |
| Chained PRs recommended | No |
| Suggested split | None — single direct-to-`master` delivery (solo dev, no PRs) |
| Delivery strategy | auto-chain (preflight) |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: High

Rationale for the estimate: `AuthRepositorioFirebase.kt` is the bulk (~250–330 changed lines:
the current ~110-line best-effort cleanup at `:265-374` is replaced by a collector, a retry
wrapper, group enumeration/dissolution, and batch write shapes). `AuthRepositorio.kt` adds
~25 lines, `FragmentPerfil.kt` ~20, and the new JVM test ~70. This is recorded as High only to
make the reviewer-aware cost visible; per the owner directive it produces **no** size decision
and **no** PR slicing.

### Suggested Work Units

Not applicable — no PR chain exists for this change. The phases below are implementation order,
not review slices. Each phase remains independently revertible by file.

## Global Gates

Run after every phase that touches `AuthRepositorioFirebase.kt`; run the full set before apply
is considered complete. Commands are PowerShell (repo root `C:\Users\Victor\AndroidStudioProjects\TFG2`).

- [ ] G.1 **Compile gate** — both build types compile:
      `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain`
- [ ] G.2 **JVM suite** — existing regression tests plus `ResultadoLimpiezaCuentaTest`:
      `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
- [ ] G.3 **Grep audit: no silent cleanup catches** —
      `Select-String -Path app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt -Pattern 'No se pudieron|No se pudo borrar|No se pudo limpiar'`
      returns **zero** hits inside the cleanup path (the re-auth message at `:241` and the generic
      `:261` failure are not cleanup catches).
- [ ] G.4 **Grep audit: avatar coverage** —
      `Select-String -Path app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt -Pattern 'collection\("avatares"\)'`
      returns **one** hit.
- [ ] G.5 **Grep audit: dissolution writes present** — each pattern present:
      `'\"grupoId\" to null'`, `'whereEqualTo\("grupoId"'`, `'rachaDias'`.
- [ ] G.6 **Grep audit: Auth-deletion gate present** —
      `Select-String -Path app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt -Pattern 'if \(!reporte\.completado\)'`
      returns a hit.
- [ ] G.7 **Manual matrix** — execute the checklist in **Manual Verification Matrix** below
      against the Firebase project/console. No device/emulator exists in this run.

## Manual Verification Matrix

`design.md` §"Manual Verification Matrix" (read-only) is the authoritative matrix; it is mapped
1:1 to the `account-deletion` delta scenarios. Execute it in these four batches and record the
result per row:

- **Batch M1 — Re-auth & UI guard:** valid password proceeds; incorrect password mutates nothing;
  recent-login surfaced; wrong confirmation text / blank password rejected; info page display-only.
- **Batch M2 — Full cleanup & gate:** every cleanup target removed/updated; avatar removed; missing
  avatar not a failure; dispute evidence deleted with dispute; user-created tasks deleted (cascade);
  assigned tasks unassigned; complete cleanup deletes the Auth account; partial failure aborts Auth
  deletion; retry converges; Auth-deletion failure after complete cleanup; success/partial-failure
  UI outcomes.
- **Batch M3 — Group dissolution:** one member remains → dissolve; two+ remain → keep; no members
  remain → delete; balances and streak reset; other profile fields preserved; group tasks deleted on
  dissolution; already-deleted tasks tolerated; remaining member's local group reference clears;
  deleting client clears only its own local state.
- **Batch M4 — Observable end states:** deleting user's observable outcome; remaining member's
  observable outcome.

---

## Phase 1: Contracts & Report Unit Test

- [ ] 1.1 Add the report types to `app/src/main/java/com/example/tfg/repositorio/AuthRepositorio.kt`
      (edit target): `data class PasoLimpieza(nombre, descripcion, exito, intentos, error = null)`
      and `data class ResultadoLimpiezaCuenta(val pasos: List<PasoLimpieza>)` with `completado`
      (`pasos.all { it.exito }`), `fallidos` (`pasos.filterNot { it.exito }`), and
      `mensajeFallos()` producing `"No se pudo completar la limpieza de la cuenta (…). Inténtalo de nuevo."`
      with `distinct()` descriptions. Do **not** change the `AuthRepositorio` interface signature:
      `eliminarCuentaActual(password: String? = null): Result<Unit>` stays as-is (`:10`).
  - Verify: `.\gradlew.bat :app:compileEmulatorKotlin --no-daemon --console=plain` succeeds.
  - Rollback: delete the two data classes; no other file references them yet.
  - Depends on: none. Parallel: safe with Phase 2 prep (import list planning).

- [ ] 1.2 Create `app/src/test/java/com/example/tfg/repositorio/ResultadoLimpiezaCuentaTest.kt`
      (edit target), package `com.example.tfg.repositorio`, pure JUnit 4, no Firebase/Android types.
      Cover: all steps successful → `completado == true`; one failed step → `completado == false`,
      `fallidos` contains it, `mensajeFallos()` includes its `descripcion`; duplicate descriptions
      deduplicated; empty list → `completado == true`. This is the only automatable test for the
      "never report success when a step failed" invariant.
  - Verify: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tfg.repositorio.ResultadoLimpiezaCuentaTest" --no-daemon --console=plain`
  - Rollback: delete the test file; production behavior unaffected.
  - Depends on: 1.1. Parallel: none.

## Phase 2: Retry Engine & Non-Swallowing Helper

- [ ] 2.1 In `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt` (edit target)
      add the constants `MAX_INTENTOS_LIMPIEZA = 3`, `DELAY_REINTENTO_MS = 300L`,
      `LIMITE_BATCH_FIRESTORE = 500`, and the imports `com.google.firebase.firestore.SetOptions`,
      `com.google.firebase.firestore.DocumentSnapshot`,
      `com.google.firebase.firestore.FirebaseFirestoreException`,
      `com.google.firebase.firestore.WriteBatch`, `com.google.firebase.storage.StorageException`,
      `kotlinx.coroutines.delay`, and **`com.google.firebase.FirebaseNetworkException`**.
      The last import is required: the design retry snippet uses `is FirebaseNetworkException` and
      the current file does not import it; add the import (or use the fully-qualified
      `com.google.firebase.FirebaseNetworkException` in `esErrorTransitorio`).
  - Verify: `.\gradlew.bat :app:compileEmulatorKotlin --no-daemon --console=plain` succeeds.
  - Rollback: remove the added imports/constants.
  - Depends on: none. Parallel: safe with Phase 1.

- [ ] 2.2 Add the retry wrapper to `AuthRepositorioFirebase.kt` (edit target): `ejecutarPaso`
      (returns `PasoLimpieza`), `ejecutarPasoConValor` (returns `Pair<PasoLimpieza, T?>`), and
      `esErrorTransitorio(e: Throwable)`. `esErrorTransitorio` returns `true` for
      `FirebaseNetworkException` and for `FirebaseFirestoreException` codes `UNAVAILABLE`,
      `DEADLINE_EXCEEDED`, `ABORTED`, `RESOURCE_EXHAUSTED`, `INTERNAL`, `CANCELLED`; non-transient
      errors fail fast on attempt 1. No exponential backoff — fixed `DELAY_REINTENTO_MS`.
  - Verify: compile gate G.1; G.4/G.5 unaffected until Phase 3.
  - Rollback: remove the three helpers and the import from 2.1 if 2.2 is reverted too.
  - Depends on: 2.1. Parallel: 2.3 can proceed concurrently.

- [ ] 2.3 Change `borrarDocumentosPorCampo` in `AuthRepositorioFirebase.kt` (edit target) so it
      **stops swallowing**: remove the internal `try/catch` and let exceptions propagate to
      `ejecutarPaso`. Query-loop shape is unchanged. This is grep-auditable: the
      `Log.w(TAG, "No se pudieron borrar documentos…")` line at `:372` disappears from the cleanup
      path.
  - Verify: grep G.3 reports zero silent-catch hits.
  - Rollback: restore the removed `try/catch` (only safe if Phase 4 callers still wrap it).
  - Depends on: 2.2 (callers must wrap it in `ejecutarPaso` first). Parallel: none.

## Phase 3: Group Enumeration & Dissolution

- [ ] 3.1 Add `limpiarGrupos(uid): List<PasoLimpieza>` to `AuthRepositorioFirebase.kt` (edit target).
      Enumerate groups via `ejecutarPasoConValor("grupos:enumeracion", …)` filtering on
      `miembros.containsKey(uid)`; return early if the read failed. For each group:
      no members → `ejecutarPaso("grupo:${gid}:borrarVacio", …)` deletes the group doc;
      two+ members → `ejecutarPaso("grupo:${gid}:miembros", …)` updates `miembros` minus `uid`
      (no dissolution); exactly one member → delegate to `disolverGrupo` (3.2/3.3).
      **Interpretation limit (consistent with `account-deletion`):** the no-members branch deletes
      only the group document — it does **not** cascade to any tasks; only the one-remaining-member
      dissolution deletes the group's tasks.
  - Verify: compile gate G.1; grep G.5 shows `whereEqualTo("grupoId"` once Phase 3.2 lands.
  - Rollback: remove `limpiarGrupos` and its call site (added in 4.1); no other file depends on it.
  - Depends on: 2.2. Parallel: 3.3 depends on 3.1.

- [ ] 3.2 In `disolverGrupo` (`AuthRepositorioFirebase.kt` (edit target)) implement the group-task
      deletion **before** the dissolution core: query step `ejecutarPasoConValor("grupo:${gid}:tareas:leer", …)`
      over `tareas.whereEqualTo("grupoId", gid)`, then delete step
      `ejecutarPaso("grupo:${gid}:tareas:borrar", …)` deleting refs in chunks of
      `LIMITE_BATCH_FIRESTORE` (≤ 500). **Use distinct step ids for the read and the delete** — the
      design's shorthand emits `grupo:$gid:tareas` twice, which makes the audit ambiguous; the
      report MUST contain `:leer` and `:borrar` as separate names. If the read fails, keep the group
      document discoverable for retry; if the chunked delete fails, do **not** proceed to the core.
  - Verify: compile gate G.1; grep G.5 shows `whereEqualTo("grupoId"`; inspect the report step names
    include both `:leer` and `:borrar`.
  - Rollback: remove `disolverGrupo`'s task-deletion block; group core (3.3) stays intact.
  - Depends on: 3.1. Parallel: none (must precede 3.3).

- [ ] 3.3 Add the atomic dissolution core in `disolverGrupo` (`AuthRepositorioFirebase.kt` (edit
      target)): one `firestore.batch()` that `delete(grupoRef)` and
      `set(usuarios/{remainingUid}, {grupoId: null, puntos: 0, puntosReservados: 0, puntosRecompensa: 0, rachaDias: 0}, SetOptions.merge())`,
      then `commit().await()`. Emit the outcome as **three sub-steps sharing one commit outcome**:
      `grupo:${gid}:disolver`, `grupo:${gid}:limpiarGrupoId`, `grupo:${gid}:resetSaldos`. The reset
      includes `rachaDias` (confirmed owner decision 3a). `set merge` avoids failing when the
      remaining user doc is missing.
  - Verify: compile gate G.1; grep G.5 shows `"grupoId" to null` and `rachaDias`; three report
    entries appear under the group with the same `exito`.
  - Rollback: remove the batch block; group-task deletion from 3.2 remains but no dissolution core.
  - Depends on: 3.1, 3.2. Parallel: safe with 4.2/4.3 while 4.1 is pending.

## Phase 4: Cleanup Coverage, Ordering & Gate

- [ ] 4.1 Rebuild `limpiarDatosAsociados(uid): ResultadoLimpiezaCuenta` in
      `AuthRepositorioFirebase.kt` (edit target) as an exception-safe collector that never throws
      for a step failure. Order: `pasos += limpiarGrupos(uid)` first, then
      `ejecutarPaso("tareas:creadas", …)` (`whereEqualTo("creadoPor", uid)` → delete each) and
      `ejecutarPaso("tareas:asignadas", …)` (`whereEqualTo("asignadoA", uid)`, skipping docs whose
      `creadoPor == uid`, resetting `asignadoA=null`, `estado="pendiente"`, `fechaReclamada=null`,
      `reclamadoPor=null`, `motivoReclamo=null`). Return `ResultadoLimpiezaCuenta(pasos)`.
  - Verify: compile gate G.1; G.3 zero silent catches; G.6 gate present after 4.4.
  - Rollback: revert `limpiarDatosAsociados` to the prior body; Phase 3 helpers become unused.
  - Depends on: Phase 3. Parallel: none.

- [ ] 4.2 Add the field sweeps to the collector in `AuthRepositorioFirebase.kt` (edit target):
      `invitaciones` (`creadoPor`), `notificaciones` (`destinatario`), `notificaciones`
      (`contenido.desde`), `recompensas` (`creadoPor`), `canjes` (`usuarioUid`) — each through
      `ejecutarPaso` calling the now-propagating `borrarDocumentosPorCampo`. Every step is attempted
      regardless of earlier failures.
  - Verify: compile gate G.1; G.3 zero silent catches.
  - Rollback: remove the five `ejecutarPaso` lines; the query helper stays.
  - Depends on: 4.1, 2.3. Parallel: safe with 4.3.

- [ ] 4.3 Add the remaining sweep to `AuthRepositorioFirebase.kt` (edit target):
      `ejecutarPaso("disputas", …)` deletes each `pruebas[]` Storage object first and only then the
      dispute doc; tolerate `StorageException.ERROR_OBJECT_NOT_FOUND` as success but rethrow any
      other storage failure so the doc is **not** deleted. Then
      `ejecutarPaso("avatar", …)` deletes `avatares/{uid}` (an absent doc is a success —
      Firestore `delete()` is a no-op). Finally, as the **last** step,
      `ejecutarPaso("usuario", …)` deletes `usuarios/{uid}`.
  - Verify: compile gate G.1; grep G.4 returns exactly one `collection("avatares")` hit; step order
    in the report ends with `usuario`.
  - Rollback: remove the three blocks; other sweeps remain.
  - Depends on: 4.1. Parallel: safe with 4.2/4.4.

- [ ] 4.4 Harden `eliminarCuentaActual` in `AuthRepositorioFirebase.kt` (edit target): keep the
      existing re-auth block (`:231-243`) and its failure message unchanged; replace the ungated
      `limpiarDatosAsociados(uid)` + `delete()` sequence with:
      `val reporte = limpiarDatosAsociados(uid)`; if `!reporte.completado`, `Log.w` the failed steps
      and `return Result.failure(Exception(reporte.mensajeFallos()))` **without** calling
      `usuarioActual.delete()`; only on `completado` run `usuarioActual.delete().await()`, clear
      `_usuarioCache`, `auth.signOut()`, and `Result.success(Unit)`. Preserve the
      `FirebaseAuthRecentLoginRequiredException` and generic catch branches (`:256-262`).
  - Verify: compile gate G.1; grep G.6 shows `if (!reporte.completado)`; manual batch M2
    (partial failure aborts Auth deletion).
  - Rollback: restore the prior ungated sequence; the report collector from 4.1 stays available.
  - Depends on: 4.1, 4.3. Parallel: none (final gate ordering).

## Phase 5: UI Partial-Failure Surface

- [ ] 5.1 In `app/src/main/java/com/example/tfg/vista/FragmentPerfil.kt` (edit target) replace the
      partial-failure `Toast` (`:288-291`) with a persistent `AlertDialog` titled
      `"Eliminación incompleta"` showing `resultado.exceptionOrNull()?.message`. Keep the success
      branch (`limpiarEstadoLocalPostEliminacion()` + `navegarALoginLimpiandoBackstack()`,
      `:283-286`) and the button re-enable (`:281`) unchanged. Do **not** touch the re-auth dialog
      (`:214-275`) or `EliminacionCuentaActivity` (read-only). No change to `VistaModeloAuth.kt`
      (read-only) — it already forwards `Result<Unit>` (`:102-120`); this supersedes the
      `proposal.md` Affected Areas row that listed it as Modified. Follow `design.md` (read-only).
  - Verify: compile gate G.1; manual batch M2 (partial failure shows a clear message, re-enables
    the button, does not navigate).
  - Rollback: restore the `Toast` line; the repository gate is unaffected.
  - Depends on: 4.4 (the failure message must exist). Parallel: none.

## Phase 6: Verification

- [ ] 6.1 Run **Global Gates G.1–G.6** and record each result. G.2 must pass including
      `ResultadoLimpiezaCuentaTest`; G.3 must be zero hits; G.4 exactly one hit; G.5 all patterns;
      G.6 present.
  - Verify: the gate commands above.
  - Rollback: N/A (verification only).
  - Depends on: Phases 1–5. Parallel: none.

- [ ] 6.2 Execute **Manual Verification Matrix** batches M1–M4 against the Firebase
      project/console and record per-row results in `verify-report.md` (read-only for this phase;
      created by `sdd-verify`). Keep `[UNVERIFIED]` markers for console-only rule parity: production
      may deny the cross-user `usuarios` update, the self `avatares` delete, or the `grupos` delete;
      if denied, the affected step fails, the Auth account is not deleted, and the failure is
      surfaced (accepted behavior).
  - Verify: all matrix rows pass, or each failure is recorded with its observed evidence.
  - Rollback: N/A (verification only).
  - Depends on: 6.1. Parallel: none.

## Scope Boundaries & Reused Files

No task edits these files; they are refactored context only:

- `app/src/main/java/com/example/tfg/viewmodel/VistaModeloAuth.kt` (read-only) — already forwards
  `Result<Unit>` and stores it in `_eliminacionCuenta` (`:102-120`); **no code change** (design
  Decision (a); overrides the proposal's Affected Areas row).
- `app/src/main/java/com/example/tfg/viewmodel/ParejaViewModel.kt` (read-only) — the reactive local
  `grupoId` clear already exists (`:83-105`, clear `:91-98`); no second mechanism is added.
- `app/src/main/java/com/example/tfg/repositorio/RepositorioPareja.kt` (read-only) — referenced
  pattern only (`limpiarGrupoIdUsuario`, `:291-302`); no change.
- `firestore.rules` (read-only) — evidence only: `:27-32` self-delete / any-update on `usuarios`,
  `:37-40` self-write on `avatares`, `:43-48` delete on `grupos`. Console parity stays `[UNVERIFIED]`.

## Traceability

| Requirement (account-deletion) | Tasks |
|---|---|
| Re-auth before any mutation | 4.4 |
| Literal confirmation + password / info page preserved | 5.1 (UI untouched) |
| Cleanup complete, verifiable, gates Auth deletion | 2.2, 2.3, 4.1–4.4, 1.2 |
| Cover every collection + Storage evidence | 2.3, 4.2, 4.3 |
| `avatares/{uid}` deleted | 4.3 |
| User-created tasks deleted (documented cascade) | 4.1 |
| Group dissolved when exactly one member remains | 3.1, 3.3 |
| Dissolved group remaining balances reset (incl. `rachaDias`) | 3.3 |
| Dissolved group's remaining tasks deleted | 3.2 |
| Remaining member's local group reference clears reactively | 3.1, 3.3 (no new mechanism) |
| Failures never reported as success | 1.1, 1.2, 4.4, 5.1 |
| Observable outcomes for both users | 4.3, 4.4, 6.2 |
