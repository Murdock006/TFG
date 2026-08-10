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
| (none) | `pendiente` | `crearTarea` | `TareaRepositorioFirebase.kt:76-120`; `RepositorioTareas.kt:44-52` |
| `pendiente` | `pendiente_confirmacion` | `marcarCompletada` with `requiereConfirmacion=true` | `TareaRepositorioFirebase.kt:390-401`; `RepositorioTareas.kt:73-76` |
| `pendiente` | `confirmada` (no `pendiente_confirmacion`) | `marcarCompletada` with `requiereConfirmacion=false` in transaction | `TareaRepositorioFirebase.kt:404-435`; `RepositorioTareas.kt:79-129` |
| `pendiente_confirmacion` or `completada` | `confirmada` | `confirmarTarea` in transaction | `TareaRepositorioFirebase.kt:441-497`; `RepositorioTareas.kt:141-200` |
| `reclamada` | `confirmada` or `pendiente` | `resolverReclamo(aceptado=true|false)` | `TareaRepositorioFirebase.kt:362-388` |
| any | `eliminada` | `actualizarTarea(estado="eliminada")` from UI | `FragmentTareas.kt:492` |

The state `eliminada` and `pendiente_confirmacion` are NOT declared in the model
comment (`Tarea.kt:15` lists only `pendiente | completada | confirmada | reclamada`).

### Points invariants

`Usuario.puntos` = activity points (spendable), `puntosReservados` = blocked when a task
is assigned, `puntosRecompensa` = reward balance (redeemable for `Recompensa`s).
Source: `modelo/Usuario.kt:13-16`; constants `util/Constants.kt:4-9`.

| Operation | Effect on creator (`puntos`/`puntosReservados`) | Effect on ejecutor (`puntos`/`puntosRecompensa`) | Evidence |
|---|---|---|---|
| Create with `puntos>0` | `puntos -= puntos`; `puntosReservados += puntos` | — | `TareaRepositorioFirebase.kt:85-91` via `LocalizadorServicios.repositorioAuth.reservarPuntos` (`AuthRepositorioFirebase.kt:440-454`) |
| Assign (`asignadoA` becomes non-blank) | `puntosReservados += puntos`; `puntos` unchanged | — | `TareaRepositorioFirebase.kt:295-303` (in `actualizarTarea` tx) |
| Unassign (`asignadoA` becomes blank) | `puntosReservados -= min(reservados, puntos)`; `puntos` unchanged | — | `TareaRepositorioFirebase.kt:306-316` |
| Confirm (with `multiplicador>=1`, racha bonus) | `puntosReservados -= puntos` (coerced to ≥0) | `puntos += puntosBase*(1+bonus)`; `puntosRecompensa += max(1, floor(puntosFinales*0.10))`; `rachaDias += 1` | `TareaRepositorioFirebase.kt:466-494` |
| `marcarCompletada` no-confirm | — | `puntos += puntos`; `puntosRecompensa += floor(puntos*0.10)` (no racha, no multiplicador) | `TareaRepositorioFirebase.kt:404-435`; `RepositorioTareas.kt:97-113` |
| `resolverReclamo(aceptado=true)` | `puntosReservados -= puntos` (via `liberarPuntos`) | `puntos += puntos` (via `sumarPuntosConBonificacion`) | `TareaRepositorioFirebase.kt:362-388` (transfers done OUTSIDE the `update` tx) |
| Canjear recompensa | — | `puntosRecompensa -= coste`; `canjes` doc created with `estado="pendiente"` | `RepositorioRecompensas.kt:84-118` |
| `responderCanje(aceptado=false)` | — | `puntosRecompensa += coste` | `RepositorioRecompensas.kt:121-146` |
| Streak bonus | — | If new `rachaDias` ≥ `STREAK_BONUS_THRESHOLD=7`, add `floor(puntos*0.10)` to `puntos` | `AuthRepositorioFirebase.kt:493-511`; `Constants.kt:7-8` |

### Task creation rules

- **Auto-assignment is forbidden** in `TareaRepositorioFirebase` (lines 31-41, 232-235) and in
  the UI form (`FragmentTareas.kt:354-357`).
- **Personalizada normalization**: `categoria ∈ {personalizada, personalizado}` ⇒
  `puntos = PUNTOS_FIJOS_PERSONALIZADA=200` (`TareaRepositorioFirebase.kt:16-29`; `Constants.kt:9`).
- `RepositorioTareas.kt:44-52` does NOT normalize and does NOT auto-block assignment;
  it does not reserve points either.

### Recurrence

- When `esRecurrente=true` and `tipoRecurrencia ∈ {diaria, semanal, mensual}`,
  `confirmarTarea` creates the next occurrence OUTSIDE the transaction
  (`TareaRepositorioFirebase.kt:499-524`).
- `rotarMiembros=true` swaps `asignadoA` to `creadoPor` (only if different); otherwise
  keeps the same assignee (`TareaRepositorioFirebase.kt:504-510`).

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
amount to `puntosReservados` when creating a non-personalized task with `puntos>0`.

#### Scenario: Creator has enough points

- GIVEN a creator with `puntos=1000`, `puntosReservados=0`
- WHEN they create a task with `puntos=100`
- THEN the creator's `puntos` MUST be `900`
- AND `puntosReservados` MUST be `100`
- Evidence path: `TareaRepositorioFirebase.kt:85-91` → `AuthRepositorioFirebase.kt:440-454`

#### Scenario: Creator has insufficient points

- GIVEN a creator with `puntos=50`
- WHEN they create a task with `puntos=100`
- THEN the creation MUST fail with `Result.failure`
- AND no task document MUST be written
- Evidence: `AuthRepositorioFirebase.kt:446` throws `Fondos insuficientes`

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

### Requirement: Auto-assignment MUST be rejected for non-simplified flows

`TareaRepositorioFirebase.crearTarea` and `actualizarTarea` MUST return
`Result.failure` when `creadoPor == asignadoA` and both are non-blank.
The simplified `RepositorioTareas` MUST NOT auto-block (this asymmetry is a known
divergence and is tracked under Future Convergence Work).

#### Scenario: Full impl rejects self-assignment

- GIVEN `creadoPor="uA"`, `asignadoA="uA"`
- WHEN `TareaRepositorioFirebase.crearTarea` runs
- THEN it MUST return `Result.failure(Exception("No se permite autoasignarse tareas"))`
- Evidence: `TareaRepositorioFirebase.kt:31-41,79-82,232-235`

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

## Future Convergence Work

| Work item | Severity | Notes |
|---|---|---|
| Add `pendiente_confirmacion` and `eliminada` to `Tarea.estado` enum or sealed type | Med | Today only documented in source; `Tarea.kt:15` lists four |
| Unify `TareaRepositorioFirebase` and `RepositorioTareas`; pick the full impl as canonical | High | `RepositorioTareas.kt:28-31` already declares the duplication |
| Re-evaluate `resolverReclamo` point transfer (currently OUTSIDE the doc `update` transaction) | High | Two-step write is not atomic; can leave inconsistent state |
| Make `recompensa.canje` filtering and authorization server-side | Med | Today client filters own uid (`RepositorioRecompensas.kt:179-208`) |
| Implement dispute resolution state machine (`en_progreso`, `cerrada`) | Med | Model declares it, repo does not (`Disputa.kt:9`; `RepositorioDisputas.kt:1-47`) |
| Add `tarea.eliminada` policy (soft delete vs hard delete) | Low | `FragmentTareas.kt:492` sets state but `eliminarCuentaActual` deletes (`AuthRepositorioFirebase.kt:296-302`) |

## Known Risks

- **Two task repos, two rules.** A flow that uses `RepositorioTareas` (`TareasViewModel`,
  `FragmentTareas`) skips streak bonus, multiplier, normalization, and auto-assignment check.
  Confirmed in source: `RepositorioTareas.kt:65-77,79-129,141-200`.
- **Resolver-reclamo write split.** State update is `await`-ed, then points transfers are
  fired without rollback on failure (`TareaRepositorioFirebase.kt:362-388`).
- **Auto-assignment in `RepositorioTareas`** is not blocked; UI form blocks it
  (`FragmentTareas.kt:354-357`) but a direct caller could bypass.
- **Custom task without 200 puntos cap** when path uses `RepositorioTareas`.

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
