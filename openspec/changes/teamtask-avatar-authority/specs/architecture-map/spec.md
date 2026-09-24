# Delta for architecture-map

## Change shape

Mixed delta. One requirement is MODIFIED — "Service locator MUST be the single bootstrap
point" loses the avatar bypass exception because the canonical avatar repository is now
resolved through `LocalizadorServicios`. The remaining updates are non-requirement table
changes (package inventory, effective flow, Future Convergence Work, Manual Verification).
The other requirement ("Layer flow SHALL match the declared diagram except where documented")
is unchanged and MUST be preserved verbatim at archive.

**Choice recorded here:** the package-inventory and effective-flow updates are not
requirement-shaped, so this delta records the exact table replacements to apply at archive
instead of MODIFIED blocks, following the `teamtask-task-repo-consolidation` pattern. The
archive step MUST apply the tables below to `openspec/specs/architecture-map/spec.md`.

Grounding: `proposal.md` (Modified Capabilities; Approach steps 1-3), `exploration.md`, the
`avatar-management` spec, and source `service/LocalizadorServicios.kt:12-27`,
`service/firebase/FirebaseComposition.kt:109-112`, `viewmodel/AvatarViewModel.kt:14-16`,
`data/firebase/AvatarRepositorioFirebase.kt:15-19`, and
`data/local/AvatarRepositorioLocal.kt:11,86`.

## MODIFIED Requirements

### Requirement: Service locator MUST be the single bootstrap point

The system MUST resolve Firebase-backed repos through `LocalizadorServicios` (object).
The system MUST NOT instantiate `FirebaseFirestore.getInstance()` outside `data/firebase/`
or `LocalizadorServicios` except in `TFGApplication`. The canonical avatar repository
(`repositorio/AvatarRepositorio.kt`) MUST be resolved through
`LocalizadorServicios.repositorioAvatar`, `AvatarViewModel` MUST NOT instantiate a concrete
avatar repository, and the Firebase-backed avatar implementation MUST receive its Firestore
client through the composition boundary rather than constructing SDK clients itself.
`data/local/AvatarRepositorioLocal.kt` MUST NOT be an avatar authority; it MAY remain only as
a local last-known cache.
(Previously: the requirement carried an avatar bypass exception — `AvatarRepositorioFirebase`
constructed its own SDK clients and `AvatarViewModel` constructed `AvatarRepositorioLocal`
directly.)

#### Scenario: Resolving `AuthRepositorio` from a ViewModel

- GIVEN a ViewModel needs auth operations
- WHEN it constructs the dependency, it MUST go through `LocalizadorServicios.repositorioAuth`
- AND it MUST NOT call `AuthRepositorioFirebase()` directly
- AND the same rule MUST hold for avatar: `AvatarViewModel` MUST receive `AvatarRepositorio` through `LocalizadorServicios.repositorioAvatar`, and no avatar implementation MUST construct Firebase SDK clients outside the composition boundary

#### Scenario: Resolving repos from a Fragment

- GIVEN a Fragment needs a repository for a one-off action
- WHEN it bypasses the ViewModel, the path MUST be listed in the "Effective flow" table
  with a justification column
- Future convergence work SHOULD remove this allowance

## Non-requirement updates (apply at archive)

### Package inventory (updated rows)

Three rows of the "Current State (observable)" package table change (`repositorio`,
`data/firebase`, `data/local`). All other rows are unchanged.

| Package | Responsibility (observed) | Evidence |
|---|---|---|
| `repositorio` | Interfaces (`TareaRepositorio`, `AuthRepositorio`, `GrupoRepositorio`, `AvatarRepositorio`) + concrete classes (`RepositorioPareja`, `RepositorioRecompensas`, `RepositorioDisputas`, `RepositorioNotificaciones`, `CategoriasRepositorio`) | `app/src/main/java/com/example/tfg/repositorio/*.kt` |
| `data/firebase` | Firebase impls of `Auth`, `Tarea`, and the Firestore base64 avatar impl (receives its client through `FirebaseComposition`) | `app/src/main/java/com/example/tfg/data/firebase/*.kt` |
| `data/local` | Local last-known avatar cache under `filesDir/avatars/` + the `tfg_prefs` SharedPreferences namespace; no avatar authority role | `app/src/main/java/com/example/tfg/data/local/AvatarRepositorioLocal.kt` |

Changes:

- `AvatarRepositorio` is ADDED to the `repositorio` interface list because the canonical
  contract (`repositorio/AvatarRepositorio.kt`) is introduced by this change.
- The `data/firebase` avatar responsibility changes from a Storage-backed impl to the Firestore
  base64 impl; it receives its Firestore client through `FirebaseComposition`
  (`FirebaseComposition.kt:109-112`).
- The `data/local` responsibility changes: `AvatarRepositorioLocal` is demoted from authority
  to an optional last-known cache (the `avatar_prefs` namespace is retired; `tfg_prefs` is
  cache-only).

### Effective flow (updated row)

The `ViewModel → service locator` row changes its avatar evidence. All other rows are
unchanged.

| Path | Evidence | Why it exists today |
|---|---|---|
| `ViewModel → service locator` | `VistaModeloPrincipal.kt:36-42`; `TareasViewModel.kt:11`; `ParejaViewModel.kt:19-22`; `AvatarViewModel.kt` resolves `AvatarRepositorio` through `LocalizadorServicios.repositorioAvatar` (this change) | Default repos wired in constructors; the avatar repository is no longer constructed directly |

Note: the previous `AvatarViewModel.kt:14-16` evidence was a direct
`AvatarRepositorioLocal(...)` construction, not a service-locator resolution; this change
makes the row accurate for avatar.

### Future Convergence Work (avatar row closed)

The row "Unify avatar authority: `AvatarViewModel` uses `AvatarRepositorioLocal` only;
`AvatarRepositorioFirebase` is dead code | High" is closed by this change and removed from the
table. All other rows are unchanged.

| Work item | Severity | Notes |
|---|---|---|
| Push direct UI→Firebase paths (`MainActivity` auth, `FragmentPareja` stats, `TareasHomeAdapter` reads) into ViewModels | Med | Evidence: `MainActivity.kt:230-283,510-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` |
| Replace `LocalizadorServicios` with constructor injection | Med | Out of scope for this change (no Hilt assumed) |
| Convert `modelo` state `String` fields to sealed types (`Tarea.estado`, `Disputa.estado`, `Canje.estado`, `Invitacion.estado`) | Med | Currently raw strings; see `task-domain` spec for the state machine |

### Manual Verification (updated row)

The "ViewModel constructor parameter `repo`" row now explicitly covers `AvatarViewModel`. All
other rows are unchanged.

| Check | How | Expected |
|---|---|---|
| Service-locator flag wiring | Edit `LocalizadorServicios.kt:17` to `false`, rebuild, run | No Firebase calls; in-memory only (manual; no tests) |
| Direct Firebase imports outside `data/firebase/`, `TFGApplication.kt`, `LocalizadorServicios.kt` | `grep -r "FirebaseFirestore\|FirebaseAuth" app/src/main/java/com/example/tfg` | Matches only in documented evidence rows |
| ViewModel constructor parameter `repo` | Read each file in `viewmodel/`, including `AvatarViewModel` | Constructor accepts an interface (or resolves it through the locator), not a concrete class |
| Avatar authority audit | `grep -r "AvatarRepositorioLocal(\|AvatarRepositorioFirebase(" app/src/main/java` | No matches outside `LocalizadorServicios`/`data/` (single authority wired) |
| Dual task-repo usage split | `grep -r "RepositorioTareas()\|TareaRepositorioInMemory" app/src/main/java` | Zero matches (consolidation complete) |

## Unchanged content (preserved)

The following sections are NOT changed by this delta and MUST be preserved verbatim at
archive: the "Current State (observable)" package table rows not listed above, the "Allowed
flow (declared in README)" section, the "Effective flow" rows not listed above, the
requirement "Layer flow SHALL match the declared diagram except where documented" and its
scenarios, the "Known Risks" section, and this marker:

[UNVERIFIED] Whether future Firestore rules or App Check will reshape any of these allowed
exceptions. No Firebase console access was available during this exploration.
