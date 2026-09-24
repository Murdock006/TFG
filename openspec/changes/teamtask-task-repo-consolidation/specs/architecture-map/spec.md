# Delta for architecture-map

## Change shape

Documentation-status delta. The consolidation removes two files from the observed package
inventory and closes one Future Convergence Work row. There are no requirement block
changes: the existing requirements ("Layer flow SHALL match the declared diagram except
where documented" and "Service locator MUST be the single bootstrap point") remain
satisfied. `RepositorioTareas` was never resolved through `LocalizadorServicios`, and
`TareaRepositorioFirebase` already implements the retained `TareaRepositorio` interface.

Because the update is not requirement-shaped, this delta records the exact table
replacements to apply at archive instead of a `## MODIFIED Requirements` block. The archive
step MUST apply the tables below to `openspec/specs/architecture-map/spec.md`.

Grounding: `proposal.md` (Modified Capabilities, decision 3), `exploration.md`, and source
`LocalizadorServicios.kt:7,24-27`, `TareaRepositorio.kt:6-15`, and the removed
`data/inmemory/TareaRepositorioInMemory.kt` and `repositorio/RepositorioTareas.kt`.

## Package inventory (updated rows)

Two rows of the "Current State (observable)" package table change. All other rows are
unchanged.

| Package | Responsibility (observed) | Evidence |
|---|---|---|
| `repositorio` | Interfaces (`TareaRepositorio`, `AuthRepositorio`, `GrupoRepositorio`) + concrete classes (`RepositorioPareja`, `RepositorioRecompensas`, `RepositorioDisputas`, `RepositorioNotificaciones`, `CategoriasRepositorio`) | `app/src/main/java/com/example/tfg/repositorio/*.kt` |
| `data/inmemory` | In-memory substitutes for `Auth` and `Grupo` | `app/src/main/java/com/example/tfg/data/inmemory/*.kt` |

Changes:

- `repositorio/RepositorioTareas` is REMOVED from the `repositorio` row because the file is
  deleted.
- `Tarea` is REMOVED from the `data/inmemory` row because `data/inmemory/TareaRepositorioInMemory.kt`
  is deleted (it was never wired).
- The stale `USAR_FIREBASE=false` selector reference is REMOVED from the `data/inmemory` row:
  the selector no longer exists in `LocalizadorServicios` (removed by
  `teamtask-testing-emulator-strategy` WU1).

After this change the task domain has one canonical repository: the `TareaRepositorio`
interface (`repositorio/TareaRepositorio.kt`) implemented by `TareaRepositorioFirebase`
(`data/firebase/TareaRepositorioFirebase.kt`) and resolved through
`LocalizadorServicios.repositorioTarea` (`LocalizadorServicios.kt:24-27`).

## Future Convergence Work (dual-repo row removed)

The row "Remove dual task repos (`TareaRepositorioFirebase` vs `RepositorioTareas`) | High"
is closed by this change and removed from the table.

| Work item | Severity | Notes |
|---|---|---|
| Unify avatar authority: `AvatarViewModel` uses `AvatarRepositorioLocal` only; `AvatarRepositorioFirebase` is dead code | High | Evidence: `AvatarViewModel.kt:14-16` vs `AvatarRepositorioFirebase.kt:1-174` (no ViewModel calls it) |
| Push direct UI→Firebase paths (`MainActivity` auth, `FragmentPareja` stats, `TareasHomeAdapter` reads) into ViewModels | Med | Evidence: `MainActivity.kt:230-283,510-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` |
| Replace `LocalizadorServicios` with constructor injection | Med | Out of scope for this change (no Hilt assumed) |
| Convert `modelo` state `String` fields to sealed types (`Tarea.estado`, `Disputa.estado`, `Canje.estado`, `Invitacion.estado`) | Med | Currently raw strings; see `task-domain` spec for the state machine |

## Manual Verification (updated row)

Only the "Dual task-repo usage split" row changes; the other three rows are unchanged.

| Check | How | Expected |
|---|---|---|
| Service-locator flag wiring | Edit `LocalizadorServicios.kt:17` to `false`, rebuild, run | No Firebase calls; in-memory only (manual; no tests) |
| Direct Firebase imports outside `data/firebase/`, `TFGApplication.kt`, `LocalizadorServicios.kt` | `grep -r "FirebaseFirestore\|FirebaseAuth" app/src/main/java/com/example/tfg` | Matches only in documented evidence rows |
| ViewModel constructor parameter `repo` | Read each file in `viewmodel/` | Constructor accepts an interface, not a concrete class |
| Dual task-repo usage split | `grep -r "RepositorioTareas()\|TareaRepositorioInMemory" app/src/main/java` | Zero matches (consolidation complete) |

[UNVERIFIED] Whether future Firestore rules or App Check will reshape any of these allowed exceptions. No Firebase console access was available during this exploration.
