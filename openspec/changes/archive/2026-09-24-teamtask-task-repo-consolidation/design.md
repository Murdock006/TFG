# Design: TeamTask Task Repository Consolidation

## Context and Goal

Create/assign/read/update already run through the canonical `TareaRepositorio` interface
(`TareaRepositorioFirebase`), but complete/confirm run through the simplified concrete
`RepositorioTareas`. This change (convergence roadmap step 2, `TEAMTASK_GUIDE.md:168`)
repoints the ViewModel to the canonical path and closes TD-1/TD-11. Consolidation exposes one
latent defect — the canonical no-confirmation transaction never consumes the creator's
`puntosReservados` (`TareaRepositorioFirebase.kt:405-435`) — so the fix lands in the same
change to avoid turning a dormant inconsistency into an active points leak.

This design is grounded in the approved `proposal.md`, `exploration.md`, the three delta specs,
and the source read at design time. It changes no application code; it specifies the apply phase.

## Technical Approach

Keep the manual `LocalizadorServicios` locator, XML/Fragments/ViewBinding, and the existing
`ViewModel` constructor-default injection style. No Hilt, Room, Retrofit, Compose, new
dependency, or Gradle change is introduced.

1. `TareasViewModel` swaps its concrete `RepositorioTareas` dependency for the `TareaRepositorio`
   interface, defaulting to the canonical singleton through the locator — the exact pattern
   already used by `VistaModeloAuth.kt:14-16`. Because every constructor parameter keeps a
   default, Kotlin emits the public no-arg constructor that `by activityViewModels()` /
   `by viewModels()` require. UI callers do not change.
2. `TareaRepositorioFirebase.marcarCompletada` (no-confirmation branch) reads the creator
   document inside the transaction and decrements `puntosReservados` by the task `puntos`,
   coerced to `>= 0`, mirroring the confirmation path (`:491-495`). All reads stay before all
   writes.
3. Delete the two dead repositories, the unused locator import, and the unused ViewModel members.
4. Update the guide (`§6/§7/§9`) so the debt register and roadmap reflect the resolved items.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| D1 | Depend on the `TareaRepositorio` interface with default `LocalizadorServicios.repositorioTarea` | Keep concrete class; add a factory; inject through Hilt | Matches `VistaModeloAuth.kt:14-16`; keeps the no-arg constructor for `activityViewModels()`; preserves UI signatures; single canonical instance |
| D2 | Consume the reservation inside the existing no-confirmation transaction, mirroring `:491-495` | Read/write the creator outside the transaction; reuse `reservarPuntos`/`liberarPuntos` helpers | Atomicity: task state, executor credit, and creator reservation commit together; identical semantics to the confirm path |
| D3 | Guard the creator write on non-blank `creadoPor`; never `set` a creator document | Create a creator doc when missing (as `RepositorioTareas` did) | Delta scenario "Task without a creator does not create a creator document"; mirrors the confirm-path guard at `:492` |
| D4 | Delete `RepositorioTareas` and `TareaRepositorioInMemory` outright | Deprecate/keep behind a flag | Both are unreferenced (audit below); the interface `TareaRepositorio` is retained |
| D5 | Mark debt/roadmap status in the guide by adding a `Status` column | Inline prose annotations | Keeps the guide's tables aligned with the `implementation-recipes` delta, which adds a `Status` column to the same tables |

## Interfaces / Contracts

`TareaRepositorio` is unchanged and remains the only task contract
(`repositorio/TareaRepositorio.kt:6-15`):

```kotlin
interface TareaRepositorio {
    suspend fun crearTarea(tarea: Tarea): Result<Tarea>
    suspend fun obtenerTareas(): Result<List<Tarea>>
    fun observarTareas(): Flow<List<Tarea>>
    fun observarTareasPorGrupo(grupoId: String): Flow<List<Tarea>>
    suspend fun actualizarTarea(tarea: Tarea): Result<Tarea>
    suspend fun resolverReclamo(tareaId: String, aceptado: Boolean): Result<Tarea>
    suspend fun marcarCompletada(tareaId: String, ejecutorUid: String): Result<Unit>
    suspend fun confirmarTarea(tareaId: String, confirmadoPorUid: String): Result<Unit>
}
```

`TareasViewModel` public surface after the change (signatures preserved for UI callers):

```kotlin
class TareasViewModel(
    private val repo: TareaRepositorio = LocalizadorServicios.repositorioTarea
) : ViewModel() {
    val marcarCompletadaState: StateFlow<Result<Unit>?>
    val confirmarTareaState: StateFlow<Result<Unit>?>
    fun marcarCompletada(tareaId: String, ejecutorUid: String)
    fun confirmarTarea(tareaId: String, confirmadoPorUid: String)
    fun resetMarcarCompletadaState()
    fun resetConfirmarTareaState()
}
```

No-confirmation completion transaction contract (behavioral):

- Every read (`tareas/{id}`, `usuarios/{ejecutorUid}`, `usuarios/{creadoPor}` when non-blank)
  MUST happen before any write.
- The task MUST be `pendiente` inside the transaction, else `throw`.
- Executor credit MUST follow the `ejecutorUid` argument and MUST NOT be overridden by
  `tarea.asignadoA`.
- Creator `puntosReservados` MUST become `max(0, reservados - tarea.puntos)` when a creator is
  present; no creator document MUST be created when `creadoPor` is blank.

## Detailed Changes

### 1. `TareasViewModel` dependency switch

File: `app/src/main/java/com/example/tfg/viewmodel/TareasViewModel.kt`.

- Line 11 changes from
  `class TareasViewModel(private val repo: RepositorioTareas = RepositorioTareas()) : ViewModel()`
  to
  `class TareasViewModel(private val repo: TareaRepositorio = LocalizadorServicios.repositorioTarea) : ViewModel()`.
- Imports: remove `com.example.tfg.repositorio.RepositorioTareas` (line 6); remove
  `com.example.tfg.modelo.Tarea` (line 5, only used by the deleted `crearTarea`); add
  `com.example.tfg.repositorio.TareaRepositorio` and
  `com.example.tfg.service.LocalizadorServicios`.

Members to remove (with no-caller evidence):

| Member | Lines | No-caller evidence |
|---|---|---|
| `crearTarea(tarea: Tarea)` | 22-27 | Repo-wide `.crearTarea(` matches are only `LocalizadorServicios.repositorioTarea.crearTarea(...)` (`FragmentPgPrincipal.kt:267`, `FragmentTareas.kt:370,719,862`) and the VM-internal call being deleted; no `tareasVM.crearTarea` caller exists |
| `_tareaCreada` | 13 | Only referenced inside `TareasViewModel.kt` (lines 13,14,25,53) |
| `tareaCreada` | 14 | Only referenced inside `TareasViewModel.kt` (line 14) |
| `resetTareaCreada()` | 52-54 | Only defined inside `TareasViewModel.kt` (lines 52-53); no caller |

Default-argument pattern precedent: `VistaModeloAuth.kt:14-16` declares
`class VistaModeloAuth(private val repositorio: AuthRepositorio = LocalizadorServicios.repositorioAuth)`
and is consumed with `by viewModels()` (`FragmentLogin.kt:28`, `FragmentRegistro.kt:21`,
`FragmentPerfil.kt:38`). `TareasViewModel` is consumed with `by activityViewModels()`
(`FragmentTareas.kt:46`, `FragmentTareasPendientes.kt:27`, `FragmentPgPrincipal.kt:43`) and passed
to `TareasHomeAdapter` (`TareasHomeAdapter.kt:37`). All constructor parameters retain defaults, so
the generated no-arg constructor satisfies the default `ViewModelProvider` factory exactly as it
does today.

### 2. Reservation-consumption fix in `TareaRepositorioFirebase.marcarCompletada`

File: `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt`.

Replace the transaction body at lines 405-435 with the following shape. The change adds the
creator reference, the creator read (before any write), and the reservation decrement; it leaves
the executor credit, reward floor, and task-state write untouched.

```kotlin
firestore.runTransaction { t ->
    val snapTx = t.get(docRef)
    val tareaTx = docToTarea(snapTx) ?: throw Exception("Tarea inválida")
    if (tareaTx.estado != "pendiente") throw Exception("Tarea no está en estado pendiente")

    // References
    val ejecRef = firestore.collection("usuarios").document(ejecutorUid)
    val creadorRef = if (!tareaTx.creadoPor.isNullOrBlank())
        firestore.collection("usuarios").document(tareaTx.creadoPor!!) else null

    // --- ALL reads before ANY write ---
    val ejecSnap = t.get(ejecRef)
    val creadorSnap = creadorRef?.let { t.get(it) }

    // 10% de los puntos va a puntosRecompensa (floor, minimo 1)
    val incrementoRecompensa = (tareaTx.puntos * Constants.REWARD_PERCENTAGE).toInt().coerceAtLeast(1)
    val puntosRecompensaActuales = (ejecSnap.getLong("puntosRecompensa") ?: 0L).toInt()

    // --- Writes ---
    t.update(docRef, "estado", "confirmada")

    if (!ejecSnap.exists()) {
        t.set(ejecRef, mapOf("puntos" to tareaTx.puntos, "puntosRecompensa" to incrementoRecompensa))
    } else {
        val actuales = (ejecSnap.getLong("puntos") ?: 0L).toInt()
        t.update(ejecRef, mapOf(
            "puntos"           to actuales + tareaTx.puntos,
            "puntosRecompensa" to puntosRecompensaActuales + incrementoRecompensa
        ))
    }

    // --- Consume creator reservation (mirror confirm path :491-495) ---
    if (creadorRef != null && creadorSnap != null) {
        val reservados = (creadorSnap.getLong("puntosReservados") ?: 0L).toInt()
        t.update(creadorRef, "puntosReservados", (reservados - tareaTx.puntos).coerceAtLeast(0))
    }

    null
}.await()
```

Notes:

- The pre-transaction read at line 394 and the `esAutoasignada` / `requiereConfirmacion` guards
  at lines 396-402 are unchanged. The `esAutoasignada` guard already rejects
  `creadoPor == asignadoA` before any write (delta scenario "Completion path rejects
  self-assignment").
- `creadorRef` is derived from `tareaTx.creadoPor` inside the transaction (fresh read), not from
  the pre-transaction snapshot.
- Blank `creadoPor` ⇒ `creadorRef == null` ⇒ executor credited, no creator write (delta scenario
  "Task without a creator does not create a creator document").
- Do NOT add the confirmation-path in-transaction auto-assignment re-check (`:460`) to this path:
  proposal decision 2 bounds this branch to the reservation-consumption fix only.

### 3. Dead code removal

| Path | Action |
|---|---|
| `app/src/main/java/com/example/tfg/repositorio/RepositorioTareas.kt` | Delete file (whole file, 202 lines) |
| `app/src/main/java/com/example/tfg/data/inmemory/TareaRepositorioInMemory.kt` | Delete file (whole file, 205 lines) |
| `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` | Delete line 7 `import com.example.tfg.data.inmemory.TareaRepositorioInMemory` |
| `app/src/main/java/com/example/tfg/viewmodel/TareasViewModel.kt` | Delete members listed in section 1 |

Reference audit (whole `app/`, including `test/` and `androidTest/`):

- `RepositorioTareas` matches only `RepositorioTareas.kt` itself and `TareasViewModel.kt:6,11`
  (both removed by this change).
- `TareaRepositorioInMemory` matches only `TareaRepositorioInMemory.kt` itself and the
  `LocalizadorServicios.kt:7` import (both removed by this change).
- `TareasViewModel` is referenced only from the four UI files listed above, all via
  `activityViewModels()` / constructor pass-through — no factory or reflective construction.
- `data/inmemory/` retains `AuthRepositorioInMemory.kt` and `GrupoRepositorioInMemory.kt`; the
  package and directory are not removed.
- `TareaRepositorio` (interface) is retained and still resolved through
  `LocalizadorServicios.repositorioTarea` (`LocalizadorServicios.kt:24-27`).

### 4. Documentation updates — `docs/architecture/TEAMTASK_GUIDE.md`

Add a trailing `Status` column to the §6 debt table and the §7 roadmap table, and to the §9
follow-up table, so the guide matches the `implementation-recipes` delta. No evidence cell is
edited or removed: the historical pointers, including the deleted `RepositorioTareas.kt`
references, are retained because the delta retains them and the rows are marked `Resolved`. Exact
rows/lines:

§6 (table header line 140, separator line 141, rows 142-156):

| Row | Line | Change |
|---|---|---|
| Header/separator | 140-141 | Append `Status` column |
| TD-1 | 142 | Append `Status` column only; evidence cell unchanged (keeps `RepositorioTareas.kt:1-31,97-113,164-179` vs `TareaRepositorioFirebase.kt:466-494`, matching the delta's retained resolved-divergence history); Status `Resolved (teamtask-task-repo-consolidation)` |
| TD-11 | 152 | Append `Status` column only; evidence cell unchanged (keeps `RepositorioTareas.kt:86`, matching the delta); Status `Resolved (teamtask-task-repo-consolidation)` |
| All other TD rows | 143-151, 153-156 | Status `Open`, except TD-9 and TD-14 (see below) |

Consistency alignment (required if the `Status` column is added, to avoid contradicting the
canonical spec): TD-9 (line 150) `Partially resolved (teamtask-testing-emulator-strategy)` and
TD-14 (line 155) `Resolved (teamtask-testing-emulator-strategy)`, matching
`specs/implementation-recipes/spec.md` lines 37 and 42. TD-14's evidence also cites
`LocalizadorServicios.kt:17`, a stale line (the `USAR_FIREBASE` selector no longer exists).

§7 (table header line 165, separator line 166, rows 167-173):

| Row | Line | Change |
|---|---|---|
| Header/separator | 165-166 | Append `Status` column |
| Step 1 | 167 | `Done` |
| Step 2 | 168 | `Done (teamtask-task-repo-consolidation)` |
| Step 4 | 170 | `Partially done` (matches canonical roadmap) |
| Steps 3, 5-7 | 169, 171-173 | `Pending` |
| Trailing note | 175-176 | Update: step 2 is now done, so "Steps 2-7 require their own baseline" and "None is part of this docs-only apply phase" are stale |

§9 (table header line 216, separator line 217, rows 218-223):

| Row | Line | Change |
|---|---|---|
| Header/separator | 216-217 | Append `Status` column |
| `teamtask-task-repo-consolidation` | 219 | Status `Done (this change)` |
| Other rows | 218, 220-223 | `Pending`, except `teamtask-testing-emulator-strategy` (line 221) which is already delivered and may be marked `Done (teamtask-testing-emulator-strategy)` |

## File-Level Changes

| Path | Action | Notes |
|---|---|---|
| `app/src/main/java/com/example/tfg/viewmodel/TareasViewModel.kt` | Modified | Interface dependency via locator; remove `crearTarea`, `_tareaCreada`, `tareaCreada`, `resetTareaCreada`; adjust imports |
| `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` | Modified | Consume creator `puntosReservados` in the no-confirmation transaction (`:405-435`) |
| `app/src/main/java/com/example/tfg/repositorio/RepositorioTareas.kt` | Removed | Entire simplified repo deleted |
| `app/src/main/java/com/example/tfg/data/inmemory/TareaRepositorioInMemory.kt` | Removed | Never-wired in-memory task repo deleted |
| `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` | Modified | Remove unused `TareaRepositorioInMemory` import (line 7) |
| `app/src/main/java/com/example/tfg/repositorio/TareaRepositorio.kt` | Unchanged | Canonical interface retained |
| `docs/architecture/TEAMTASK_GUIDE.md` | Modified | §6/§7/§9 `Status` column additions (evidence cells unchanged) |
| `openspec/changes/teamtask-task-repo-consolidation/specs/**` | Unchanged | Already-written deltas |

## Sequenced Work Units

1. **Canonical dependency switch + reservation fix.** Edit `TareasViewModel.kt` and
   `TareaRepositorioFirebase.kt`. Gate: both compile tasks pass; manual no-confirmation matrix
   rows pass.
2. **Dead code removal.** Delete both files, drop the import and the unused members. Gate: grep
   audits return zero matches; compile still passes.
3. **Docs update.** Apply the §6/§7/§9 edits. Gate: table rows match the canonical spec statuses.

WU1 and WU2 can land together (WU2 depends on WU1 only for a clean compile); WU3 is independent.

## Verification Plan

No automated test covers these paths (`app/src/test` and `app/src/androidTest` contain only the
template `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt`; the emulator seam test is not
present). Verification is compile + grep audit + manual matrix.

Compile (both build types exist: `emulator` at `app/build.gradle.kts:28-35`, `release` at
`:36-46`):

```powershell
.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain
```

Grep audits under `app/src/main`:

| Audit | Command | Expected |
|---|---|---|
| Simplified repo removed | `rg -n "RepositorioTareas" app/src/main` | Zero matches |
| In-memory task repo removed | `rg -n "TareaRepositorioInMemory" app/src/main` | Zero matches |
| Dead VM members removed | `rg -n "tareaCreada\|resetTareaCreada" app/src/main` | Zero matches |
| No new direct Firebase client | `rg -n "FirebaseFirestore.getInstance\|Firebase\.firestore" app/src/main` | Only `FirebaseComposition.kt:96` (unchanged posture) |
| Stack purity preserved | `rg -n "androidx.compose\|dagger.hilt\|androidx.room\|retrofit2" app/` | No new matches |

Manual matrix (emulator or device; the rows from the `task-domain` delta plus regressions):

| Check | How | Expected |
|---|---|---|
| Reservation at create | Create a task with `puntos>0` from the UI, inspect `usuarios/{creador}` | `puntos` decreased, `puntosReservados` increased |
| No-confirmation reservation consumption | Create a `requiereConfirmacion=false` task with `puntos=100`, mark complete, inspect `usuarios/{creador}` | `puntosReservados` decreased by 100 (coerced `>= 0`) |
| No-confirmation executor credit | Same task, inspect `usuarios/{ejecutorUid}` | `puntos` +100; `puntosRecompensa` +10 |
| Reservation decrement coercion | Set creator `puntosReservados=30`, complete a 100-point no-confirm task | `puntosReservados` becomes 0, never negative |
| Executor credit follows argument | Call `marcarCompletada(tareaId, "uX")` with `asignadoA="uE"` | `uX.puntos` increases; `uE.puntos` unchanged |
| No creator document created | Complete a task with blank `creadoPor` | Executor credited; no `usuarios` doc created for a creator |
| Confirmation regression | Confirm a `pendiente_confirmacion` task | `puntosReservados -= puntos` (coerced); `puntos`/`puntosRecompensa`/`rachaDias` per confirm rules |
| Auto-assign rejection | Complete a task with `creadoPor == asignadoA` | `Result.failure`; no state/points written |

## Risks and Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Silent points behavior change with no test net | High | Compile + grep audit + manual matrix; state the absence of automated coverage explicitly |
| Transaction read/write ordering violation (Firestore requires all reads before all writes) | Med | Creator read is placed before the first write; the exact shape above is the contract; a misplaced write surfaces as a transaction exception at runtime |
| Creator document missing while `creadoPor` is non-blank | Low | Mirrors the confirm path (`:492-495`): the `t.update` fails `NOT_FOUND` at commit. Pre-existing confirm behavior, not newly introduced; keep semantics identical |
| `ejecutorUid == tareaTx.creadoPor` (two writes to one document in one transaction) | Low | Mirrors the confirm-path shape; not introduced by this change and not fixed here (out of scope). Add to the manual matrix only if the UI permits completing one's own created task |
| Legacy auto-assigned tasks become uncompletable | Med | The canonical path rejects `creadoPor == asignadoA` at `:396`; previously the live complete flow used `RepositorioTareas` (no guard). Run a pre-release data check for `tareas` where `creadoPor == asignadoA` and manually reassign or clear `asignadoA` |
| Reward-floor behavior delta for small point values | Low | `RepositorioTareas` used `(puntos * 0.10).toInt()` (0 for `puntos < 10`); the canonical path uses `coerceAtLeast(1)`. The delta spec requires the floor, so this is intended and documented, not a regression |
| Confirm-required branch lacks an in-transaction state guard | Low | The canonical `marcarCompletada` sets `pendiente_confirmacion` for `requiereConfirmacion=true` without re-checking `estado` (`:399-401`), unlike the deleted `RepositorioTareas` (`:72`). Verify UI gating shows "complete" only for `pendiente` tasks; if not, track as a follow-up (adding a guard is beyond proposal decision 2) |
| Docs drift left behind | Med | Apply the §6/§7/§9 edits; §1/§2 stale pointers are listed under Discovered Drift |

## Rollback Boundary

Solo developer, commits directly to `master`, no PRs. Rollback is a `git revert` of this
change's commit(s):

1. Revert restores `RepositorioTareas.kt`, `TareaRepositorioInMemory.kt`, the
   `LocalizadorServicios.kt:7` import, the original `TareasViewModel` dependency/members, and the
   original `marcarCompletada` transaction.
2. No Firestore data rollback is implied: the fix only corrects future writes; persisted
   `puntosReservados` values are not rewritten.
3. Revert the guide §6/§7/§9 edits (or leave the archive trail and mark the change superseded).

Because the change is dominated by deletions, revert restores the prior dual-repo state without a
data migration.

## Out of Scope and Boundaries

- TD-7 (`resolverReclamo` split write, `:363-388`), avatar authority, navigation/lifecycle,
  account deletion, Firestore rules/indexes, and `Tarea.estado` typing.
- No Hilt, Room, Retrofit, Compose, new dependency, or Gradle change.
- No automated tests (roadmap step 5 is a separate change).
- No change to Firestore collection/field contracts or UI caller signatures.
- Streak, `multiplicadorPuntos`, recurrence spawn, and reminder scheduling remain
  confirmation-path behaviors and are not added to the no-confirmation path.

## Discovered Drift (beyond proposal scope)

These are stale after this change and are not covered by the proposal's §6/§7/§9 scope. Flagged
for a decision; the design does not silently expand scope:

- `TEAMTASK_GUIDE.md:49` — "two task repositories have divergent financial rules" becomes false.
- `TEAMTASK_GUIDE.md:57-59` — the state-index rows cite `RepositorioTareas.kt:44-52`, `:73-129`,
  `:141-200`, all deleted.
- `TEAMTASK_GUIDE.md:69` — "The asymmetry behind TD-1 ... are deliberate debt signals" no longer
  applies.
- `TEAMTASK_GUIDE.md:10,175-176` — "This change is docs-only" / "None is part of this docs-only
  apply phase" are scoped to the architecture-guide change and read as stale once the guide is
  edited by a code change.

Recommendation: fold the §2 and §0 stale sentences into the same docs pass (low risk, keeps the
guide honest); otherwise record them as a follow-up. `TEAMTASK_GUIDE.md:43`
(`ViewModel → service locator: TareasViewModel.kt:11`) stays accurate after the change because the
ViewModel still reaches the locator through its default argument.

## Design Evidence Index

- `TareasViewModel.kt:5-6,11,13-14,22-27,52-54` — dependency and members to change.
- `VistaModeloAuth.kt:14-16` — default-argument locator pattern precedent.
- `FragmentTareas.kt:46`; `FragmentTareasPendientes.kt:27`; `FragmentPgPrincipal.kt:43`;
  `TareasHomeAdapter.kt:37` — `TareasViewModel` construction sites.
- `TareaRepositorioFirebase.kt:391-402,405-435,491-495` — no-confirm path, fix site, confirm
  semantics.
- `RepositorioTareas.kt:66-140` — simplified completion behavior being removed.
- `TareaRepositorioInMemory.kt:14`; `LocalizadorServicios.kt:7,24-27` — dead code and canonical
  wiring.
- `TareaRepositorio.kt:6-15` — retained interface.
- `Constants.kt:4` — `REWARD_PERCENTAGE = 0.10`.
- `app/build.gradle.kts:28-46` — `emulator` (28-35) and `release` (36-46) build types for the compile gate.
