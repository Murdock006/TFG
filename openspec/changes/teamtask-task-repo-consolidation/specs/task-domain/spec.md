# Delta for task-domain

## Change shape

This delta updates the task-domain completion/confirmation points invariants for the
`teamtask-task-repo-consolidation` change.

- **ADDED** — one requirement: the no-confirmation completion path now consumes the
  creator's `puntosReservados` (coerced to ≥ 0, same transaction) and credits the
  `ejecutorUid` argument (resolves TD-11).
- **RENAMED + MODIFIED** — the auto-assignment requirement loses the `RepositorioTareas`
  asymmetry because that repository is removed by this change (resolves TD-1).
- **Non-requirement updates** — the archive step MUST also apply the state-machine,
  points-invariant, task-creation, Future Convergence Work, Known Risks, and Manual
  Verification updates recorded at the end of this file.

Grounding: `proposal.md` (Resolved Decisions 1-2 and Scope), `exploration.md`, and source
`TareaRepositorioFirebase.kt:391-549`, `RepositorioTareas.kt:66-201`,
`TareasViewModel.kt:11-54`, `LocalizadorServicios.kt:7,24-27`, `TareaRepositorio.kt:6-15`.

## RENAMED Requirements

### Requirement: Auto-assignment MUST be rejected for non-simplified flows → Auto-assignment MUST be rejected

(Reason: The simplified `RepositorioTareas` completion flow is removed by this change, so the "non-simplified flows" qualifier no longer describes a real distinction. Auto-assignment is now rejected by the single canonical task repository.)
(Migration: The requirement body is replaced by the `MODIFIED` block below. References in `architecture-map` and `implementation-recipes` that cited the two-repo asymmetry are updated by this change's deltas. No persisted behavior or test depends on the old requirement name.)

## MODIFIED Requirements

### Requirement: Auto-assignment MUST be rejected

`TareaRepositorioFirebase.crearTarea` and `actualizarTarea` MUST return `Result.failure`
when `creadoPor == asignadoA` and both are non-blank. The canonical completion paths
(`marcarCompletada` and `confirmarTarea`) MUST also reject a task whose
`creadoPor == asignadoA` with `Result.failure` before writing any task state or points.
(Previously: the requirement was scoped "for non-simplified flows" and stated that the simplified `RepositorioTareas` MUST NOT auto-block, documenting a known divergence tracked under Future Convergence Work.)

#### Scenario: Full impl rejects self-assignment

- GIVEN `creadoPor="uA"`, `asignadoA="uA"`
- WHEN `TareaRepositorioFirebase.crearTarea` runs
- THEN it MUST return `Result.failure(Exception("No se permite autoasignarse tareas"))`
- Evidence: `TareaRepositorioFirebase.kt:31-41,79-82,232-235`

#### Scenario: Completion path rejects self-assignment

- GIVEN a `pendiente` task with `creadoPor="uA"`, `asignadoA="uA"`
- WHEN `TareaRepositorioFirebase.marcarCompletada` or `confirmarTarea` runs
- THEN it MUST return `Result.failure(Exception("Tarea inválida: autoasignación no permitida"))`
- AND no task state or points MUST be written
- Evidence: `TareaRepositorioFirebase.kt:396,448,460`

## ADDED Requirements

### Requirement: Task completion without confirmation MUST consume the creator's reserved points

When `marcarCompletada` runs on a `pendiente` task whose `requiereConfirmacion=false`, the
system MUST, inside a single `firestore.runTransaction` with every read performed before
any write:

- set `tareas.estado` to `confirmada`;
- credit the `ejecutorUid` argument with the task `puntos`;
- add `max(1, floor(puntos * REWARD_PERCENTAGE))` to that user's `puntosRecompensa`;
- decrement the creator's `puntosReservados` by the task `puntos`, coerced to ≥ 0.

The credit MUST follow the `ejecutorUid` argument and MUST NOT be overridden by
`tarea.asignadoA`. The creator's reservation MUST be consumed with the same semantics as
the confirmation path. If the task has no creator, the system MUST credit the executor and
MUST NOT create a creator document. Streak (`rachaDias`), `multiplicadorPuntos`, recurrence
spawn, and reminder scheduling remain confirmation-path behaviors and MUST NOT be added to
this path by this change.

#### Scenario: No-confirmation completion consumes the reservation

- GIVEN a `pendiente` task with `requiereConfirmacion=false`, `puntos=100`, `creadoPor="uC"`, `asignadoA="uE"`
- AND creator `uC` has `puntosReservados=100`
- AND executor `uE` has `puntos=0`, `puntosRecompensa=0`
- WHEN `marcarCompletada(tareaId, "uE")` runs
- THEN the task `estado` MUST be `confirmada`
- AND `usuarios/uC.puntosReservados` MUST be `0`
- AND `usuarios/uE.puntos` MUST be `100`
- AND `usuarios/uE.puntosRecompensa` MUST be `max(1, floor(100*0.10)) = 10`
- Evidence: `TareaRepositorioFirebase.kt:404-435` (updated by this change); confirm-path semantics `TareaRepositorioFirebase.kt:491-495`

#### Scenario: Reservation decrement is coerced to zero

- GIVEN a `pendiente` task with `requiereConfirmacion=false`, `puntos=100`, `creadoPor="uC"`
- AND creator `uC` has `puntosReservados=30`
- WHEN `marcarCompletada` runs
- THEN `usuarios/uC.puntosReservados` MUST be `0` (coerced, never negative)
- Evidence: coercion `TareaRepositorioFirebase.kt:494`

#### Scenario: Executor credit follows the `ejecutorUid` argument

- GIVEN a `pendiente` task with `asignadoA="uE"`
- WHEN `marcarCompletada(tareaId, "uX")` runs
- THEN `usuarios/uX.puntos` MUST increase by the task `puntos`
- AND `usuarios/uE.puntos` MUST NOT change from this operation
- Evidence: `TareaRepositorioFirebase.kt:411` uses the `ejecutorUid` parameter (resolves TD-11)

#### Scenario: Task without a creator does not create a creator document

- GIVEN a `pendiente` task with `requiereConfirmacion=false` and blank `creadoPor`
- WHEN `marcarCompletada` runs
- THEN the executor MUST be credited
- AND no `usuarios` document MUST be created for a creator
- Evidence: creator guard `TareaRepositorioFirebase.kt:492`

## Non-requirement updates (apply at archive)

The archive step MUST replace the following sections/rows in
`openspec/specs/task-domain/spec.md`. They are not requirement-shaped, so they are recorded
here instead of as delta requirement blocks.

### State machine (updated `Evidence` column)

The `RepositorioTareas` evidence references are removed because the file is deleted by this
change.

| From | To | Trigger | Evidence |
|---|---|---|---|
| (none) | `pendiente` | `crearTarea` | `TareaRepositorioFirebase.kt:76-120` |
| `pendiente` | `pendiente_confirmacion` | `marcarCompletada` with `requiereConfirmacion=true` | `TareaRepositorioFirebase.kt:390-401` |
| `pendiente` | `confirmada` (no `pendiente_confirmacion`) | `marcarCompletada` with `requiereConfirmacion=false` in transaction | `TareaRepositorioFirebase.kt:404-435` |
| `pendiente_confirmacion` or `completada` | `confirmada` | `confirmarTarea` in transaction | `TareaRepositorioFirebase.kt:441-497` |
| `reclamada` | `confirmada` or `pendiente` | `resolverReclamo(aceptado=true|false)` | `TareaRepositorioFirebase.kt:362-388` |
| any | `eliminada` | `actualizarTarea(estado="eliminada")` from UI | `FragmentTareas.kt:492` |

The note that `eliminada` and `pendiente_confirmacion` are not declared in the model comment
(`Tarea.kt:15`) is unchanged.

### Points invariants (updated `marcarCompletada` no-confirm row)

The no-confirmation row now shows reservation consumption on the creator and the canonical
reward floor (`coerceAtLeast(1)`), and drops the `RepositorioTareas` evidence.

| Operation | Effect on creator (`puntos`/`puntosReservados`) | Effect on ejecutor (`puntos`/`puntosRecompensa`) | Evidence |
|---|---|---|---|
| Create with `puntos>0` | `puntos -= puntos`; `puntosReservados += puntos` | — | `TareaRepositorioFirebase.kt:85-91` via `LocalizadorServicios.repositorioAuth.reservarPuntos` (`AuthRepositorioFirebase.kt:440-454`) |
| Assign (`asignadoA` becomes non-blank) | `puntosReservados += puntos`; `puntos` unchanged | — | `TareaRepositorioFirebase.kt:295-303` (in `actualizarTarea` tx) |
| Unassign (`asignadoA` becomes blank) | `puntosReservados -= min(reservados, puntos)`; `puntos` unchanged | — | `TareaRepositorioFirebase.kt:306-316` |
| Confirm (with `multiplicador>=1`, racha bonus) | `puntosReservados -= puntos` (coerced to ≥0) | `puntos += puntosBase*(1+bonus)`; `puntosRecompensa += max(1, floor(puntosFinales*0.10))`; `rachaDias += 1` | `TareaRepositorioFirebase.kt:466-494` |
| `marcarCompletada` no-confirm | `puntosReservados -= puntos` (coerced to ≥0) | `puntos += puntos`; `puntosRecompensa += max(1, floor(puntos*0.10))` (no racha, no multiplicador) | `TareaRepositorioFirebase.kt:404-435` |
| `resolverReclamo(aceptado=true)` | `puntosReservados -= puntos` (via `liberarPuntos`) | `puntos += puntos` (via `sumarPuntosConBonificacion`) | `TareaRepositorioFirebase.kt:362-388` (transfers done OUTSIDE the `update` tx) |
| Canjear recompensa | — | `puntosRecompensa -= coste`; `canjes` doc created with `estado="pendiente"` | `RepositorioRecompensas.kt:84-118` |
| `responderCanje(aceptado=false)` | — | `puntosRecompensa += coste` | `RepositorioRecompensas.kt:121-146` |
| Streak bonus | — | If new `rachaDias` ≥ `STREAK_BONUS_THRESHOLD=7`, add `floor(puntos*0.10)` to `puntos` | `AuthRepositorioFirebase.kt:493-511`; `Constants.kt:7-8` |

### Task creation rules (updated bullets)

- **Auto-assignment is forbidden** in `TareaRepositorioFirebase` (lines 31-41, 232-235) and in
  the UI form (`FragmentTareas.kt:354-357`).
- **Personalizada normalization**: `categoria ∈ {personalizada, personalizado}` ⇒
  `puntos = PUNTOS_FIJOS_PERSONALIZADA=200` (`TareaRepositorioFirebase.kt:16-29`; `Constants.kt:9`).
- The previous third bullet stating that `RepositorioTareas.kt:44-52` does NOT normalize,
  auto-block, or reserve points is REMOVED because the file is deleted.

### Future Convergence Work (dual-repo row removed)

The row "Unify `TareaRepositorioFirebase` and `RepositorioTareas`; pick the full impl as
canonical | High" is closed by this change and removed from the table.

| Work item | Severity | Notes |
|---|---|---|
| Add `pendiente_confirmacion` and `eliminada` to `Tarea.estado` enum or sealed type | Med | Today only documented in source; `Tarea.kt:15` lists four |
| Re-evaluate `resolverReclamo` point transfer (currently OUTSIDE the doc `update` transaction) | High | Two-step write is not atomic; can leave inconsistent state |
| Make `recompensa.canje` filtering and authorization server-side | Med | Today client filters own uid (`RepositorioRecompensas.kt:179-208`) |
| Implement dispute resolution state machine (`en_progreso`, `cerrada`) | Med | Model declares it, repo does not (`Disputa.kt:9`; `RepositorioDisputas.kt:1-47`) |
| Add `tarea.eliminada` policy (soft delete vs hard delete) | Low | `FragmentTareas.kt:492` sets state but `eliminarCuentaActual` deletes (`AuthRepositorioFirebase.kt:296-302`) |

### Known Risks (two-repo bullets removed)

The bullets "Two task repos, two rules", "Auto-assignment in `RepositorioTareas` is not
blocked", and "Custom task without 200 puntos cap" are REMOVED because the simplified
repository no longer exists. The remaining risk is preserved:

- **Resolver-reclamo write split.** State update is `await`-ed, then points transfers are
  fired without rollback on failure (`TareaRepositorioFirebase.kt:362-388`).

[UNVERIFIED] Server-side enforcement of point invariants (Firestore rules, Cloud Functions, triggers). The repo contains no such artefacts.

### Manual Verification (rows added)

| Check | How | Expected |
|---|---|---|
| Reservation at create | Trigger create with `puntos>0` from emulator, inspect `usuarios/{uid}` | `puntos` decreased, `puntosReservados` increased |
| Streak bonus at confirm | Set `rachaDias=6`, confirm task, check `puntos` | Increased by `1.10 * puntos` |
| Personalizada normalization | Submit `categoria="Personalizada"` with `puntos=999` | Persisted `puntos=200` |
| Canje atomicity | Patch `canjes` write to throw, retry | `puntosRecompensa` unchanged (tx rolled back) |
| Auto-assign via full impl | Call `TareaRepositorioFirebase.crearTarea` with `creadoPor==asignadoA` | `Result.failure` |
| No-confirmation reservation consumption | Create a `requiereConfirmacion=false` task with `puntos=100`, mark complete, inspect `usuarios/{creador}` | `puntosReservados` decreased by 100 (coerced ≥0) |
| No-confirmation executor credit | Same task, inspect `usuarios/{ejecutorUid}` | `puntos` +100; `puntosRecompensa` +10 |
