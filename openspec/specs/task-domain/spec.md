# task-domain

## Purpose

Factual specification of TeamTask's task and group domain: the task state machine, the
points/reservation/reward invariants, recurrence, dispute lifecycle, and assignment rules.
The spec documents CURRENT observable behavior from source code, with `[UNVERIFIED]`
markers for any claim that cannot be confirmed from the repository.

## Current State (observable)

### Task state machine

`Tarea.estado` is a raw `String` (`modelo/Tarea.kt:15`). The implemented states and
transitions are:

| From | To | Trigger | Evidence |
|---|---|---|---|
| (none) | `pendiente` | `crearTarea` | `TareaRepositorioFirebase.kt:76-120` |
| `pendiente` | `pendiente_confirmacion` | `marcarCompletada` with `requiereConfirmacion=true` | `TareaRepositorioFirebase.kt:390-401` |
| `pendiente` | `confirmada` (no `pendiente_confirmacion`) | `marcarCompletada` with `requiereConfirmacion=false` in transaction | `TareaRepositorioFirebase.kt:404-435` |
| `pendiente_confirmacion` or `completada` | `confirmada` | `confirmarTarea` in transaction | `TareaRepositorioFirebase.kt:441-497` |
| `reclamada` | `confirmada` or `pendiente` | `resolverReclamo(aceptado=true|false)` | `TareaRepositorioFirebase.kt:362-388` |
| any (not `eliminada`) | `eliminada` | `actualizarTarea(estado="eliminada")` from UI: the "Eliminar" action on a personalizada or the "Cancelar recurrencia" action on a `pendiente` recurring instance (creator only) | `FragmentTareas.kt:568`, `FragmentTareas.kt:480` |

The state `eliminada` and `pendiente_confirmacion` are NOT declared in the model
comment (`Tarea.kt:15` lists only `pendiente | completada | confirmada | reclamada`).

### Points invariants

`Usuario.puntos` = activity points (spendable), `puntosReservados` = blocked when a task
is assigned, `puntosRecompensa` = reward balance (redeemable for `Recompensa`s).
Source: `modelo/Usuario.kt:13-16`; constants `util/Constants.kt:4-9`.

| Operation | Effect on creator (`puntos`/`puntosReservados`) | Effect on ejecutor (`puntos`/`puntosRecompensa`) | Evidence |
|---|---|---|---|
| Create with `puntos>0` | `puntos -= puntos`; `puntosReservados += puntos`; creation FAILS and no task doc is written if `reservarPuntos` returns failure (insufficient funds) | — | `TareaRepositorioFirebase.kt:92-101` via `LocalizadorServicios.repositorioAuth.reservarPuntos` (`AuthRepositorioFirebase.kt:450-464`) |
| Assign (`asignadoA` becomes non-blank) | no balance change; reservation owner is creation | — | `TareaRepositorioFirebase.kt:299-350` (in `actualizarTarea` tx; assign-path reservation removed) |
| Unassign (`asignadoA` becomes blank) | no balance change; reservation owner is creation | — | `TareaRepositorioFirebase.kt:299-350` (in `actualizarTarea` tx; unassign-path release removed) |
| Confirm (with `multiplicador>=1`, racha bonus) | `puntosReservados -= puntos` (coerced to ≥0) | `puntos += puntosBase*(1+bonus)`; `puntosRecompensa += max(1, floor(puntosFinales*0.10))`; `rachaDias += 1` | `TareaRepositorioFirebase.kt:466-494` |
| `marcarCompletada` no-confirm | `puntosReservados -= puntos` (coerced to ≥0) | `puntos += puntos`; `puntosRecompensa += max(1, floor(puntos*0.10))` (no racha, no multiplicador) | `TareaRepositorioFirebase.kt:404-435` |
| `resolverReclamo(aceptado=true)` | `puntosReservados -= puntos` (via `liberarPuntos`) | `puntos += puntos` (via `sumarPuntosConBonificacion`) | `TareaRepositorioFirebase.kt:362-388` (transfers done OUTSIDE the `update` tx) |
| Soft-delete (`eliminada`, from a non-`eliminada` state) | release = `min(puntos, puntosReservados)`; `puntosReservados -= release`; `puntos += release` | — | `TareaRepositorioFirebase.kt:307-370` (inside the `actualizarTarea` tx, reads before writes) |
| Canjear recompensa | — | `puntosRecompensa -= coste`; `canjes` doc created with `estado="pendiente"` | `RepositorioRecompensas.kt:84-118` |
| `responderCanje(aceptado=false)` | — | `puntosRecompensa += coste` | `RepositorioRecompensas.kt:121-146` |
| Streak bonus | — | If new `rachaDias` ≥ `STREAK_BONUS_THRESHOLD=7`, add `floor(puntos*0.10)` to `puntos` | `AuthRepositorioFirebase.kt:493-511`; `Constants.kt:7-8` |

### Task creation rules

- **Auto-assignment is forbidden** in `TareaRepositorioFirebase` (lines 31-41, 232-235) and in
  the UI form (`FragmentTareas.kt:354-357`).
- **Personalizada normalization**: `categoria ∈ {personalizada, personalizado}` ⇒
  `puntos = PUNTOS_FIJOS_PERSONALIZADA=200` (`TareaRepositorioFirebase.kt:16-29`; `Constants.kt:9`).

### Recurrence

- When `esRecurrente=true` and `tipoRecurrencia ∈ {diaria, semanal, mensual}`,
  `confirmarTarea` creates the next occurrence OUTSIDE the transaction
  (`TareaRepositorioFirebase.kt:499-524`).
- `rotarMiembros=true` swaps `asignadoA` to `creadoPor` (only if different); otherwise
  keeps the same assignee (`TareaRepositorioFirebase.kt:504-510`).
- Cancelling a recurrence (`esRecurrente=true`, `estado="pendiente"`, creator only) soft-deletes
  the pending instance (`estado="eliminada"`, `esRecurrente=false`) instead of only clearing the
  recurrence flags (`FragmentTareas.kt:467-484`). Because a soft-deleted pending instance never
  reaches `confirmarTarea`, no next occurrence is spawned and the task disappears from every list
  and the calendar.

### Disputes

- `Disputa.estado ∈ {abierta, en_progreso, cerrada}` (`modelo/Disputa.kt:9`).
- Repository can only `abrirDisputa` and `listarDisputasPorUsuario`; no resolution flow
  exists (`RepositorioDisputas.kt:17-34`).
- Storage path: `disputas/<tareaId>/<uuid>.jpg` (`RepositorioDisputas.kt:36-46`).
- UI may open a dispute with `pruebas=emptyList()` if upload fails or no image is chosen
  (`FragmentTareas.kt:90-99`).

## Requirements

### Requirement: Task creation MUST reserve points when `puntos>0`

The system MUST subtract the task `puntos` from the creator's `puntos` and add the same
amount to `puntosReservados` when creating a task with `puntos>0`.
Creation is the SINGLE OWNER of the reservation: assign/unassign MUST NOT mutate balances,
and the reservation is consumed at confirmation/completion. `crearTarea` MUST inspect the
`Result` returned by `reservarPuntos` (which never throws) and MUST fail without writing the
task document when the reservation fails.

#### Scenario: Creator has enough points

- GIVEN a creator with `puntos=1000`, `puntosReservados=0`
- WHEN they create a task with `puntos=100`
- THEN the creator's `puntos` MUST be `900`
- AND `puntosReservados` MUST be `100`
- Evidence path: `TareaRepositorioFirebase.kt:92-101` → `AuthRepositorioFirebase.kt:450-464`

#### Scenario: Creator has insufficient points

- GIVEN a creator with `puntos=50`
- WHEN they create a task with `puntos=100`
- THEN the creation MUST fail with `Result.failure(Exception("Fondos insuficientes para reservar 100 puntos"))`
- AND no task document MUST be written
- Evidence: `crearTarea` checks `reserva.isFailure` (`TareaRepositorioFirebase.kt:95-101`); `reservarPuntos` returns `Result.failure("Fondos insuficientes")` (`AuthRepositorioFirebase.kt:456`)

### Requirement: Task confirmation MUST credit points and rewards atomically

The system MUST update `tareas.estado` to `confirmada`, increment ejecutor's
`puntos`/`puntosRecompensa`/`rachaDias`, and decrement creator's `puntosReservados`
inside a single `firestore.runTransaction`.

#### Scenario: Confirmation succeeds with racha bonus

- GIVEN a task `pendiente_confirmacion` with `puntos=100`, `multiplicadorPuntos=1.0`,
  ejecutor has `rachaDias=6`
- WHEN `confirmarTarea` runs
- THEN `rachaDias` MUST become `7`
- AND `puntos` MUST increase by `100 * 1.0 * 1.10 = 110`
- AND `puntosRecompensa` MUST increase by `max(1, floor(110*0.10)) = 11`
- Evidence: `TareaRepositorioFirebase.kt:466-494`; `Constants.kt:4,7-8`

#### Scenario: Multiplier doubles puntos for emergencia

- GIVEN `tarea.esEmergencia=true`, `multiplicadorPuntos=1.5`, `puntos=100`
- WHEN `confirmarTarea` runs
- THEN `puntosBase = 100*1.5 = 150`; final `puntos` increment is `150 * (1+bonus)`
- Evidence: `TareaRepositorioFirebase.kt:469-471`; `Constants.kt:10`

### Requirement: Personalizada tasks MUST be normalized to fixed points

The system MUST set `puntos=200` when the task's `categoria` matches
`personalizada` or `personalizado` (case-insensitive).

#### Scenario: User creates a personalizada with 50 puntos

- GIVEN a creator submits a `personalizada` task with `puntos=50`
- WHEN `TareaRepositorioFirebase.crearTarea` runs
- THEN the persisted `puntos` MUST be `200`
- Evidence: `TareaRepositorioFirebase.kt:19-29`; `Constants.kt:9`

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

### Requirement: `ResolverReclamo` MUST mutate state and transfer points

The system MUST toggle `tarea.estado` between `confirmada` and `pendiente` and,
when `aceptado=true`, MUST additionally transfer `puntos` from creator's reserved
balance to ejecutor's `puntos` via `sumarPuntosConBonificacion`/`liberarPuntos`.

#### Scenario: Reclamo accepted

- GIVEN a `reclamada` task
- WHEN `resolverReclamo(tareaId, aceptado=true)` runs
- THEN the task state MUST be `confirmada`
- AND ejecutor's `puntos` MUST increase by `tarea.puntos` (with racha bonus)
- AND creator's `puntosReservados` MUST decrease by `tarea.puntos`
- Evidence: `TareaRepositorioFirebase.kt:362-388`

#### Scenario: Reclamo rejected

- GIVEN a `reclamada` task
- WHEN `resolverReclamo(tareaId, aceptado=false)` runs
- THEN the task state MUST be `pendiente`
- AND points MUST NOT move
- Evidence: `TareaRepositorioFirebase.kt:368`

### Requirement: Canje MUST validate reward balance in a transaction

The system MUST execute `puntosRecompensa >= coste` and decrement the balance plus
create a `canjes` doc inside a single `firestore.runTransaction`.

#### Scenario: Sufficient balance

- GIVEN user with `puntosRecompensa=500`, `coste=200`
- WHEN `canjearRecompensa` runs
- THEN `puntosRecompensa` MUST become `300`
- AND a `canjes/{id}` doc with `estado="pendiente"` MUST exist
- Evidence: `RepositorioRecompensas.kt:84-118`

#### Scenario: Insufficient balance

- GIVEN user with `puntosRecompensa=100`, `coste=200`
- WHEN `canjearRecompensa` runs
- THEN the transaction MUST throw and return `Result.failure("Puntos de recompensa insuficientes...")`
- Evidence: `RepositorioRecompensas.kt:97`

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

### Requirement: Soft-deleting a task MUST release the creator's reservation

When `actualizarTarea` transitions a task INTO `estado="eliminada"` from any state other than
`eliminada`, the system MUST, inside the same `firestore.runTransaction` and with every read
performed before any write, release the creator's reservation with the same semantics as
`AuthRepositorioFirebase.liberarPuntos`:

- `release = min(tarea.puntos, creador.puntosReservados)`;
- `creador.puntosReservados -= release` (never negative);
- `creador.puntos += release`.

The release MUST run exactly once per transition (a task already `eliminada` MUST NOT release
again), and MUST be a no-op when the task has no creator. This applies to ALL soft-delete paths,
including the "Cancelar recurrencia" action, closing the pre-existing hole where any soft-deleted
task kept its reservation stuck. Lists and the calendar MUST NOT render tasks whose
`estado="eliminada"`.

#### Scenario: Cancel recurrence releases the reservation

- GIVEN a `pendiente` recurring task with `puntos=100`, `creadoPor="uC"`, `asignadoA="uE"`
- AND creator `uC` has `puntos=900`, `puntosReservados=100`
- WHEN the creator cancels the recurrence so the task becomes `estado="eliminada"`
- THEN `usuarios/uC.puntosReservados` MUST be `0`
- AND `usuarios/uC.puntos` MUST be `900 + 100 = 1000`
- AND no next occurrence MUST be created
- AND the task MUST NOT appear in any list or the calendar
- Evidence: `FragmentTareas.kt:467-484`; release `TareaRepositorioFirebase.kt:307-370`

#### Scenario: Deleting an already-eliminada task releases nothing

- GIVEN a task with `estado="eliminada"` and creator `uC`
- WHEN `actualizarTarea` runs again with `estado="eliminada"`
- THEN the creator's `puntos`/`puntosReservados` MUST NOT change
- Evidence: guard `TareaRepositorioFirebase.kt:309` (`previoTx.estado != "eliminada"`)

#### Scenario: Release is clamped to the reserved amount

- GIVEN a `pendiente` task with `puntos=100`, `creadoPor="uC"`
- AND creator `uC` has `puntos=900`, `puntosReservados=30`
- WHEN the task is soft-deleted
- THEN `usuarios/uC.puntosReservados` MUST be `0`
- AND `usuarios/uC.puntos` MUST be `900 + 30 = 930` (release clamped to `min(100, 30)`)
- Evidence: `TareaRepositorioFirebase.kt:368-370`

## Future Convergence Work

| Work item | Severity | Notes |
|---|---|---|
| Add `pendiente_confirmacion` and `eliminada` to `Tarea.estado` enum or sealed type | Med | Today only documented in source; `Tarea.kt:15` lists four |
| Re-evaluate `resolverReclamo` point transfer (currently OUTSIDE the doc `update` transaction) | High | Two-step write is not atomic; can leave inconsistent state |
| Make `recompensa.canje` filtering and authorization server-side | Med | Today client filters own uid (`RepositorioRecompensas.kt:179-208`) |
| Implement dispute resolution state machine (`en_progreso`, `cerrada`) | Med | Model declares it, repo does not (`Disputa.kt:9`; `RepositorioDisputas.kt:1-47`) |
| Define `tarea.eliminada` retention/tombstone policy | Low | Soft delete now releases the creator reservation (`TareaRepositorioFirebase.kt:307-370`); documents still persist with `estado="eliminada"` and account deletion hard-deletes (`AuthRepositorioFirebase.kt:296-302`) |

## Known Risks

- **Resolver-reclamo write split.** State update is `await`-ed, then points transfers are
  fired without rollback on failure (`TareaRepositorioFirebase.kt:362-388`).

[UNVERIFIED] Server-side enforcement of point invariants (Firestore rules, Cloud Functions,
triggers). The repo contains no such artefacts.

## Manual Verification

| Check | How | Expected |
|---|---|---|
| Reservation at create | Trigger create with `puntos>0` from emulator, inspect `usuarios/{uid}` | `puntos` decreased, `puntosReservados` increased |
| Streak bonus at confirm | Set `rachaDias=6`, confirm task, check `puntos` | Increased by `1.10 * puntos` |
| Personalizada normalization | Submit `categoria="Personalizada"` with `puntos=999` | Persisted `puntos=200` |
| Canje atomicity | Patch `canjes` write to throw, retry | `puntosRecompensa` unchanged (tx rolled back) |
| Auto-assign via full impl | Call `TareaRepositorioFirebase.crearTarea` with `creadoPor==asignadoA` | `Result.failure` |
| No-confirmation reservation consumption | Create a `requiereConfirmacion=false` task with `puntos=100`, mark complete, inspect `usuarios/{creador}` | `puntosReservados` decreased by 100 (coerced ≥0) |
| No-confirmation executor credit | Same task, inspect `usuarios/{ejecutorUid}` | `puntos` +100; `puntosRecompensa` +10 |
| Soft-delete releases reservation | Soft-delete a task (Eliminar or Cancelar recurrencia), inspect `usuarios/{creador}` | `puntosReservados` decreased, `puntos` increased by the released amount |
| Eliminada hidden from lists | Soft-delete a task, open Inicio/Tareas/Calendario | Task not rendered in any list or calendar |
