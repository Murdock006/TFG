# Exploration: TeamTask task-repository consolidation

## Summary

Consolidating the task repositories is convergence roadmap step 2
(`docs/architecture/TEAMTASK_GUIDE.md:168`) and resolves debt items **TD-1** (two task repos
with divergent rules) and **TD-11** (`ejecutorUid` ignored in one completion path)
(`docs/architecture/TEAMTASK_GUIDE.md:142,152`).

The repository split is **per operation, not per screen**: create/assign/read/update already
flow through `TareaRepositorioFirebase` (the canonical `TareaRepositorio` interface), while
**complete/confirm** flow through the simplified `RepositorioTareas`, which is where the
divergent financial rules execute. This is a read-only exploration; no application code,
Gradle files, canonical specs, or git state were modified.

## Key finding: the split is per operation

| Operation | Current path | Callers |
|---|---|---|
| create / assign / read / update | `LocalizadorServicios.repositorioTarea` → interface `TareaRepositorio` → `TareaRepositorioFirebase` | `FragmentPgPrincipal.kt:267`; `FragmentTareas.kt:370,719,862` |
| complete / confirm | `TareasViewModel` → concrete `RepositorioTareas` (simplified) | `TareasHomeAdapter.kt:167,256`; `FragmentTareas.kt:497,512,519,585` |

Consequence: streak (`rachaDias`), `multiplicadorPuntos`, recurrence spawn, and reminder
scheduling are implemented **only in the full implementation** and therefore do **not** run on
the real complete/confirm flow (`TareaRepositorioFirebase.kt:466-543`).

`TareasViewModel` holds the concrete dependency:
`class TareasViewModel(private val repo: RepositorioTareas = RepositorioTareas())`
(`TareasViewModel.kt:11`). Its `crearTarea`/`tareaCreada`/`resetTareaCreada` members have no
external callers; only `marcarCompletada`, `confirmarTarea`, and their state/reset members are
used (`TareasViewModel.kt:13-53`; callers listed above).

## Divergences: full implementation vs simplified repo

| Concern | `TareaRepositorioFirebase` (full) | `RepositorioTareas` (simplified) |
|---|---|---|
| `crearTarea` | normalizes `personalizada`→200 (`:24-30`), rejects self-assignment (`:32-38,80-83`), reserves creator points (`:85-91`) | none of the above (`:45-53`) |
| `marcarCompletada` (no confirmation) | credits `ejecutorUid` (caller) + reward floor 1 (`:404-435`); **does NOT consume creator `puntosReservados`** (points leak) | credits `asignadoA ?: ejecutorUid` (`:86`) and **does** consume creator reservation (`:117-126`) |
| `confirmarTarea` | consumes creator reservation (`:491-495`), credits `asignadoA`, adds streak +1/bonus, multiplier, reward floor 1 (`:466-494`), recurrence spawn (`:500-525`), reminder (`:527-543`) | consumes creator reservation identically (`:183-192`); no streak/multiplier/recurrence/reminder |
| auto-assign guards | in complete/confirm (`:396,448,460`) | none |
| TD-11 | credits the caller's `ejecutorUid` (UI already restricts to assignee) | credits `asignadoA ?: ejecutorUid` (overrides the param) (`:86`) |

Points model established by the code (not a product question): the **creator pays** (reservation
consumed on confirm/complete), the **executor earns**, and bonuses (streak/multiplier/reward)
are added by the system. `liberarPuntos` (reservation→balance) is used only by
`resolverReclamo` (TD-7, out of scope) (`TareaRepositorioFirebase.kt:363-388`).

## The leak that consolidation would introduce

The full `marcarCompletada` no-confirm transaction (`TareaRepositorioFirebase.kt:405-435`) never
reads the creator's document, so it never decrements `puntosReservados`. Today this is masked
because the real no-confirm flow runs through `RepositorioTareas`, which does decrement it.
Switching `TareasViewModel` to the canonical path without fixing lines 405-435 would leak the
creator's reserved points on every no-confirmation completion.

## Dead code confirmed

- `repositorio/RepositorioTareas.kt` — whole file; only referenced by `TareasViewModel.kt:6,11`.
- `RepositorioTareas.asignarTarea` (`RepositorioTareas.kt:55`) — no callers.
- `data/inmemory/TareaRepositorioInMemory.kt` — implements `TareaRepositorio` but is never
  instantiated; `LocalizadorServicios.kt:24-27` always wires `TareaRepositorioFirebase`.
- `service/LocalizadorServicios.kt:7` — unused `import ...TareaRepositorioInMemory`.
- `TareasViewModel` members `crearTarea`, `_tareaCreada`/`tareaCreada`, `resetTareaCreada`
  (`TareasViewModel.kt:13-14,22-27,52-54`) — no external callers.

## Migration mechanics

`TareasViewModel.kt:11` changes its dependency to the `TareaRepositorio` interface
(defaulting to `TareaRepositorioFirebase(...)` via the locator). Method signatures already match
(`TareaRepositorio.kt:13-14`), so UI callers do not change. The `TareaRepositorio` interface is
kept. `RepositorioTareas` is not registered in `LocalizadorServicios` (only imported indirectly
by the ViewModel).

## Risks

- **Silent points behavior change with no test net.** Roadmap step 5 (tests) is not done
  (`TEAMTASK_GUIDE.md:171`), so verification is compile + audit + manual matrix.
- **Legacy auto-assigned tasks become uncompletable.** The canonical path rejects
  `creadoPor == asignadoA` in complete/confirm (`TareaRepositorioFirebase.kt:396,448`). Tasks
  created before the guard existed could be permanently blocked.
- **TD-7 not addressed.** `resolverReclamo`'s split state/points write
  (`TareaRepositorioFirebase.kt:363-388`) is explicitly out of scope.

## Open decisions

None at product level. The seven questions from the exploration resolve as: the full
implementation wins for its features; add the reservation-consumption fix to the no-confirm
path; delete the dead code.

## Evidence index

- `TareaRepositorioFirebase.kt:15` class implements `TareaRepositorio`; `:405-435` no-confirm tx;
  `:442-549` confirm tx; `:24-38` normalization/auto-assign; `:85-91` reservation on create.
- `RepositorioTareas.kt:40-202` simplified repo; `:86` assignee override (TD-11);
  `:117-126` reservation consumption; `:183-192` confirm reservation consumption.
- `TareasViewModel.kt:11-54` concrete dependency and members.
- `LocalizadorServicios.kt:7,24-27` unused import and canonical task wiring.
- `TareaRepositorio.kt:6-15` interface contract.
- `TareasHomeAdapter.kt:167,256`; `FragmentTareas.kt:497,512,519,585` completion callers.
- `docs/architecture/TEAMTASK_GUIDE.md:142,152,168` TD-1/TD-11 and roadmap step 2.

The full exploration is mirrored in Engram under topic
`sdd/teamtask-task-repo-consolidation/explore`.
