# architecture-map

## Purpose

Canonical map of TeamTask's package responsibilities, dependency directions, and allowed
layer exceptions, derived from the real source tree. This spec is the reference for
future refactors and for any reviewer that needs to know where a given concern lives.

## Current State (observable)

| Package | Responsibility (observed) | Evidence |
|---|---|---|
| `vista` | `MainActivity`, Fragments, RecyclerView adapters, dialogs, navigation, **part of business rules** | `app/src/main/java/com/example/tfg/vista/*.kt` |
| `viewmodel` | State holders for auth, groups, tasks, avatar, dashboard; mix of `StateFlow` and `LiveData` | `app/src/main/java/com/example/tfg/viewmodel/*.kt` |
| `repositorio` | Interfaces (`TareaRepositorio`, `AuthRepositorio`, `GrupoRepositorio`, `AvatarRepositorio`) + concrete classes (`RepositorioPareja`, `RepositorioRecompensas`, `RepositorioDisputas`, `RepositorioNotificaciones`, `CategoriasRepositorio`) | `app/src/main/java/com/example/tfg/repositorio/*.kt` |
| `data/firebase` | Firebase impls of `Auth`, `Tarea`, and the Firestore base64 avatar impl (receives its client through `FirebaseComposition`) | `app/src/main/java/com/example/tfg/data/firebase/*.kt` |
| `data/inmemory` | In-memory substitutes for `Auth` and `Grupo` | `app/src/main/java/com/example/tfg/data/inmemory/*.kt` |
| `data/local` | Local last-known avatar cache under `filesDir/avatars/` + the `tfg_prefs` SharedPreferences namespace; no avatar authority role | `app/src/main/java/com/example/tfg/data/local/AvatarRepositorioLocal.kt` |
| `modelo` | Data classes for `Usuario`, `Tarea`, `Grupo`, `Disputa`, `Recompensa`, `Canje`, `Notificacion`, `Invitacion`; states are raw `String`, not sealed | `app/src/main/java/com/example/tfg/modelo/*.kt` |
| `service` | `LocalizadorServicios` (service locator), `NotificationScheduler`/`NotificationWorker` (WorkManager), `IcsExporter` | `app/src/main/java/com/example/tfg/service/*.kt` |
| `util` | `Constants` (REWARD_PERCENTAGE=0.10, STREAK_BONUS_THRESHOLD=7, INITIAL_POINTS=1000, PUNTOS_FIJOS_PERSONALIZADA=200, MULTIPLICADOR_EMERGENCIA=1.5, DOUBLE_BACK_TIMEOUT_MS=2000L) | `app/src/main/java/com/example/tfg/util/Constants.kt` |

### Allowed flow (declared in README)

`UI (Fragments) → ViewModel (StateFlow) → Repository (Firebase/In-memory) → Data Sources → Model`.

### Effective flow (observed)

| Path | Evidence | Why it exists today |
|---|---|---|
| `UI → LocalizadorServicios` (global repos) | `FragmentTareas.kt:370,410,452`; `FragmentTareasPendientes.kt:49-175`; `MainActivity.kt:287-302,440` | Quick reads; bypasses ViewModel |
| `UI → concrete repositorio` | `FragmentTareas.kt:47,96,381`; `FragmentRecompensas.kt:33-34`; `MainActivity.kt:302` | Notifications, disputes, recompensas |
| `UI → Firebase SDK` | `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` | Auth state, group stats |
| `UI → domain logic` (decide state/points) | `FragmentTareas.kt:354-368,415-525`; `TareasHomeAdapter.kt:158-267` | Adapter and form own rules |
| `ViewModel → service locator` | `VistaModeloPrincipal.kt:36-42`; `TareasViewModel.kt:11`; `ParejaViewModel.kt:19-22`; `AvatarViewModel.kt` resolves `AvatarRepositorio` through `LocalizadorServicios.repositorioAvatar` (this change) | Default repos wired in constructors; the avatar repository is no longer constructed directly |
| `Repository → service locator` | `TareaRepositorioFirebase.kt:87,124,376-377` | Reuses auth points API; circular dep |

## Requirements

### Requirement: Layer flow SHALL match the declared diagram except where documented

The system SHOULD restrict code to the declared flow `UI → ViewModel → Repository → Data → Model`.
The system MUST keep every direct deviation (UI→Locator, UI→Firebase, Repository→Locator) listed
in this spec with evidence.

#### Scenario: Reviewer audits a new code path

- GIVEN a reviewer inspects any new file under `vista/`, `viewmodel/`, `repositorio/`, or `data/`
- WHEN they trace imports for `com.example.tfg.service.LocalizadorServicios`, `FirebaseAuth`, or `FirebaseFirestore`
- THEN every match MUST be either in this spec's "Effective flow" table or in a future
  change's delta spec

#### Scenario: Adding a new repository

- GIVEN a developer needs a new domain repo
- WHEN they add it under `repositorio/` (interface) and `data/firebase/` (impl)
- THEN the ViewModel SHOULD receive the interface via constructor injection
- AND a service-locator wiring MUST be added to `LocalizadorServicios` (lines 14-43)
  only if a future change justifies the global access

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

## Future Convergence Work

| Work item | Severity | Notes |
|---|---|---|
| Push direct UI→Firebase paths (`MainActivity` auth, `FragmentPareja` stats, `TareasHomeAdapter` reads) into ViewModels | Med | Evidence: `MainActivity.kt:230-283,510-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` |
| Replace `LocalizadorServicios` with constructor injection | Med | Out of scope for this change (no Hilt assumed) |
| Convert `modelo` state `String` fields to sealed types (`Tarea.estado`, `Disputa.estado`, `Canje.estado`, `Invitacion.estado`) | Med | Currently raw strings; see `task-domain` spec for the state machine |

## Known Risks

- **Service locator hides coupling.** A change to `LocalizadorServicios.kt:17` (`USAR_FIREBASE`)
  silently swaps all wired repos; no test asserts the flag.
- **Repository→Locator cycle** (`TareaRepositorioFirebase.kt:84-91,372-380`) makes the graph
  non-layered; refactors that remove the locator must resolve it first.
- **README claims "MVVM/StateFlow"** (`README.md:81-93,244-259`) but real code mixes `LiveData`
  (`VistaModeloPrincipal.kt:16-42`) and the service locator. Specs document the truth; README
  is non-authoritative.

## Manual Verification

| Check | How | Expected |
|---|---|---|
| Service-locator flag wiring | Edit `LocalizadorServicios.kt:17` to `false`, rebuild, run | No Firebase calls; in-memory only (manual; no tests) |
| Direct Firebase imports outside `data/firebase/`, `TFGApplication.kt`, `LocalizadorServicios.kt` | `grep -r "FirebaseFirestore\|FirebaseAuth" app/src/main/java/com/example/tfg` | Matches only in documented evidence rows |
| ViewModel constructor parameter `repo` | Read each file in `viewmodel/`, including `AvatarViewModel` | Constructor accepts an interface (or resolves it through the locator), not a concrete class |
| Avatar authority audit | `grep -r "AvatarRepositorioLocal(\|AvatarRepositorioFirebase(" app/src/main/java` | No matches outside `LocalizadorServicios`/`data/` (single authority wired) |
| Dual task-repo usage split | `grep -r "RepositorioTareas()\|TareaRepositorioInMemory" app/src/main/java` | Zero matches (consolidation complete) |

[UNVERIFIED] Whether future Firestore rules or App Check will reshape any of these allowed
exceptions. No Firebase console access was available during this exploration.
