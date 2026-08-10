# implementation-recipes

## Purpose

How to add features, fix bugs, and refactor in TeamTask without breaking the existing
XML/Fragments/ViewBinding, mixed MVVM/service-locator, and Firebase backend. This spec
is documentation policy, not new architecture. It does NOT introduce Hilt, Room, Retrofit,
or Compose.

## Current State (observable)

| Decision | Status | Source |
|---|---|---|
| UI toolkit | XML + Fragments + ViewBinding | `app/build.gradle.kts:32-34` |
| Navigation | AndroidX Navigation Component (manual bundles) | `res/navigation/nav_graph.xml`; `app/build.gradle.kts:69-70` |
| State holders | Mix of `StateFlow` and `LiveData` | `viewmodel/ParejaViewModel.kt:24-33`; `viewmodel/VistaModeloPrincipal.kt:16-23` |
| Concurrency | `viewModelScope`, `lifecycleScope`, `viewLifecycleOwner.lifecycleScope`; collectors in `repeatOnLifecycle(STARTED)` | `FragmentTareasPendientes.kt:48-175`; `MainActivity.kt:430-447` |
| DI | Service locator (`LocalizadorServicios`) | `service/LocalizadorServicios.kt:14-43` |
| Data | Firebase (Auth, Firestore, Storage) | `app/build.gradle.kts:46-58` |
| Local persistence | `SharedPreferences` (`tfg_prefs`) + `filesDir/avatars/` | `ParejaViewModel.kt:39-75`; `AvatarRepositorioLocal.kt:11,86` |
| Notifications | `WorkManager` + `NotificationCompat` | `service/NotificationScheduler.kt:14-111`; `app/build.gradle.kts:76` |
| Testing | JUnit 4 + AndroidX Test/Espresso templates only | `app/build.gradle.kts:85-87`; `ExampleUnitTest.kt:12-16`; `ExampleInstrumentedTest.kt:16-23` |
| Strict TDD | Off (template tests only) | `openspec/config.yaml:6` |
| Hilt / Room / Retrofit / Compose | NOT present | `app/build.gradle.kts`; absence confirmed in `explore.md` |

## Requirements

### Requirement: New code MUST remain compatible with the current stack

Any change MUST NOT introduce Hilt, Room, Retrofit, or Compose. Changes MAY add:

- New `Fragment` subclasses with ViewBinding-generated binding classes.
- New `ViewModel` subclasses with constructor-injected repository interfaces where
  feasible, falling back to the service locator only when testability is preserved.
- New repository methods under `repositorio/` (interface) and `data/firebase/` (impl).
- New `modelo` data classes; new state values for existing classes MUST be raw `String`
  to match the current pattern until the convergence work in `task-domain` lands.

#### Scenario: New feature request

- GIVEN a developer wants to add a "weekly summary" feature
- WHEN they plan the change
- THEN the plan MUST list:
  - New Fragment + ViewBinding layout
  - ViewModel that depends on an existing or new repository interface
  - Repository method (interface + Firebase impl) returning `Result<T>` or `Flow<T>`
  - Manual verification steps (no automated tests assumed)
- AND the plan MUST NOT propose Hilt/Room/Retrofit/Compose

### Requirement: Naming and file conventions MUST follow the existing ones

| Artifact | Convention | Example |
|---|---|---|
| Fragment | `Fragment<Noun>.kt` + `fragment_<noun>.xml` | `FragmentTareas.kt` + `fragment_tareas_lista.xml` |
| ViewModel | `VistaModelo<Noun>.kt` or `<Noun>ViewModel.kt` (both styles present) | `VistaModeloAuth.kt`, `ParejaViewModel.kt` |
| Adapter | `<Noun>Adapter.kt` | `TareasHomeAdapter.kt` |
| Repository interface | `<Noun>Repositorio.kt` | `TareaRepositorio.kt` |
| Repository impl | `<Noun>RepositorioFirebase.kt` (data/firebase) or `<Noun>RepositorioInMemory.kt` (data/inmemory) | `TareaRepositorioFirebase.kt` |
| Local repo | `<Noun>RepositorioLocal.kt` (data/local) | `AvatarRepositorioLocal.kt` |
| Constants | `util.Constants` (singleton) | `Constants.REWARD_PERCENTAGE` |

#### Scenario: New Fragment added

- GIVEN a new feature requires a screen
- WHEN the developer creates the Fragment
- THEN the file MUST be `Fragment<Name>.kt` and the layout `fragment_<name>.xml`
- AND the layout MUST be referenced from `nav_graph.xml`
- AND the Fragment MUST use ViewBinding (`Feature<Name>Binding`) — no `findViewById` for new fields

### Requirement: ViewModels MUST use lifecycle-aware coroutine collection

ViewModel-collected flows MUST be inside `viewModelScope.launch { ... }`. ViewModels
that expose `StateFlow` MUST back them with private `MutableStateFlow` and expose the
read-only view.

#### Scenario: New ViewModel exposes state

- GIVEN a new ViewModel needs a state stream
- WHEN the developer writes it
- THEN they MUST use `private val _state = MutableStateFlow<X>(initial)` and `val state: StateFlow<X> = _state.asStateFlow()`
- AND collectors MUST live in `viewModelScope`
- Evidence: `ParejaViewModel.kt:24-36`; `TareasViewModel.kt:13-21`; `AvatarViewModel.kt:20-29`

### Requirement: Fragments MUST collect flows with `repeatOnLifecycle`

A Fragment that collects a flow MUST do so inside
`viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { ... } }`.
The only exception is Activity-scoped flows that require RESUMED gating.

#### Scenario: New Fragment reads a StateFlow

- GIVEN a new Fragment needs to render a ViewModel state
- WHEN the developer wires the collector
- THEN the collector MUST be inside `repeatOnLifecycle(STARTED)` using
  `viewLifecycleOwner.lifecycleScope`
- Evidence (positive): `FragmentCalendario.kt:90-113`; `FragmentPerfil.kt:115-190`; `FragmentRecompensas.kt:76-121`
- Evidence (exception, Activity): `MainActivity.kt:437-447`

### Requirement: Repository writes that span multiple docs MUST be transactional

Operations that read and write across multiple Firestore documents (task confirmation,
canje, group creation, member removal) MUST be wrapped in
`firestore.runTransaction { t -> ... }` with all reads performed before writes inside
the lambda. The current `resolverReclamo` is a known exception and SHOULD NOT be
replicated.

#### Scenario: New cross-doc write

- GIVEN a developer adds a new feature that updates two collections
- WHEN they write the repository method
- THEN the method MUST use `firestore.runTransaction`
- AND it MUST read all docs before writing
- AND it MUST propagate exceptions via `Result.failure`
- Evidence: `TareaRepositorioFirebase.kt:456-497`; `RepositorioRecompensas.kt:84-118`; `RepositorioPareja.kt:119-141`

### Requirement: Source-of-truth policy MUST be applied

Authoritative ordering for any claim in a spec, PR, or review:

1. Real source code in `app/src/main/`.
2. Verified repo config (`app/build.gradle.kts`, `google-services.json`,
   `openspec/config.yaml`).
3. README and docs in `docs/`.
4. Historical Engram memories (cross-checked against the above).

Any claim that contradicts a higher tier MUST be corrected; lower-tier sources are
non-authoritative. Claims that cannot be verified against tiers 1-2 MUST be marked
`[UNVERIFIED]`.

#### Scenario: Conflicting claim

- GIVEN a new spec or PR says "uses StateFlow" for a ViewModel
- WHEN a reviewer checks the source
- AND the file uses `LiveData` (e.g., `VistaModeloPrincipal.kt:16-23`)
- THEN the spec/PR MUST be corrected to reflect the source
- AND the spec/PR MUST NOT cite the README as evidence

### Requirement: New features MUST include manual verification steps

Until strict TDD or a meaningful test suite exists, every new feature MUST include a
manual verification matrix in its PR description and (when applicable) in the
corresponding spec.

| Column | Meaning |
|---|---|
| Preconditions | App state, data state, build state required |
| Steps | Numbered user actions |
| Expected | Observable outcome (UI text, log, Firestore doc) |
| Risk if it fails | What invariant is at risk |

#### Scenario: New "tarea recurrente semanal" feature

- GIVEN a developer merges a new recurrence type
- WHEN they open the PR
- THEN the PR MUST list at minimum:
  - "Create task with tipoRecurrencia=semanal, confirm"
  - "Expected: new doc in `tareas` with `fechaProgramada` 7 days later"
  - "Risk if fails: recurrence contract broken"
- AND the test command field in `openspec/config.yaml` MUST remain empty or annotated
  if no automated test exists

## Technical debt register

Severity legend: **H**igh = blocks a future spec's invariants; **M**ed = divergence
between paths; **L**ow = cleanup.

| # | Issue | Severity | Evidence | Spec to fix it |
|---|---|---|---|---|
| TD-1 | Two task repos with divergent rules | H | `RepositorioTareas.kt:1-31,97-113,164-179` vs `TareaRepositorioFirebase.kt:466-494` | `task-domain` convergence |
| TD-2 | Avatar authority split (`tfg_prefs` + `avatar_prefs`) | H | `AvatarRepositorioLocal.kt:11,86`; `FragmentPgPrincipal.kt:376-380`; `MainActivity.kt:484-485` | `architecture-map` convergence |
| TD-3 | `AvatarRepositorioFirebase` is dead code | H | `AvatarViewModel.kt:14-16` (no caller); class is `class`, not `object`/`companion` | `architecture-map` convergence |
| TD-4 | Auto-login one-shot listener may fire twice on config change | M | `MainActivity.kt:255-275` | `navigation-lifecycle` |
| TD-5 | `TareaRepositorioFirebase.observarTareas` mutates shared map without sync | M | `TareaRepositorioFirebase.kt:184-212` | `navigation-lifecycle` |
| TD-6 | `TareasHomeAdapter` external `CoroutineScope` | M | `TareasHomeAdapter.kt:39,145-210` | `navigation-lifecycle` |
| TD-7 | `resolverReclamo` does state update then points transfer outside tx | H | `TareaRepositorioFirebase.kt:362-388` | `task-domain` |
| TD-8 | `modelo.Tarea.estado` is raw `String`; not all states listed in comment | M | `Tarea.kt:15`; `TareaRepositorioFirebase.kt:397-400,448`; `FragmentTareas.kt:492` | `task-domain` |
| TD-9 | No `firestore.rules` / `storage.rules` / `indexes.json` in repo | H | absence confirmed; `firebase-database-ktx` declared but no consumer | `firestore-contracts` |
| TD-10 | UI direct Firebase reads | M | `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` | `architecture-map` |
| TD-11 | `ejecutorUid` param ignored in `RepositorioTareas.marcarCompletada` no-confirm path | M | `RepositorioTareas.kt:85` (overrides with `tareaTx.asignadoA ?: ejecutorUid`) | `task-domain` |
| TD-12 | Disputa state machine has no resolver | M | `Disputa.kt:9`; `RepositorioDisputas.kt:17-34` | `task-domain` |
| TD-13 | Best-effort account cleanup | M | `AuthRepositorioFirebase.kt:259-357` | `firestore-contracts` |
| TD-14 | `USAR_FIREBASE` flag in `LocalizadorServicios` has no test | L | `LocalizadorServicios.kt:17` | `architecture-map` |
| TD-15 | Manual navigation bundles | L | `MainActivity.kt:223`; `FragmentTareas.kt:56-72,397-398` | `navigation-lifecycle` |

## Convergence roadmap

| # | Title | Depends on | Estimated impact |
|---|---|---|---|
| 1 | Publish `architecture-map`, `task-domain`, `firestore-contracts`, `navigation-lifecycle`, `implementation-recipes` specs | — | docs only |
| 2 | Unify task repos (pick `TareaRepositorioFirebase`; remove `RepositorioTareas`) | 1 | refactor `FragmentTareas`, `TareasViewModel`, `VistaModeloPrincipal` |
| 3 | Audit and fix avatar authority (decide local vs Firebase; unify `tfg_prefs`/`avatar_prefs`; delete dead Firebase impl) | 1 | changes `AvatarViewModel`, `FragmentPerfil`, `FragmentPgPrincipal`, `MainActivity` |
| 4 | Add `firestore.rules`, `storage.rules`, `firestore.indexes.json`; commit and deploy | 1 | requires Firebase console access; safe-write test in `gcloud`/`firebase emulators` |
| 5 | Add focused tests per spec: `ParejaViewModel`, `TareaRepositorioFirebase.crearTarea`/`confirmarTarea`, `RepositorioRecompensas.canjearRecompensa` | 2 | 3-5 unit tests using `firebase emulators:exec` |
| 6 | Refactor `TareaRepositorioFirebase.observarTareas` to typed `combine` and reiniciar-on-group-change | 5 | safer observers |
| 7 | Encapsulate logout + auto-login flow into dedicated controllers | 1 | smaller `MainActivity` |

## Future Convergence Work

| Work item | Severity | Notes |
|---|---|---|
| Add a CI step that builds the app and runs `gradle lint` | M | Today no `.github/`; no lint config |
| Add Firebase emulator-based tests for repositories | H | No automated tests today |
| Add a contract test for `LocalizadorServicios` flag wiring | L | See `architecture-map` |
| Move constants out of `Constants` into a `BuildConfig` (or per-flavour) for testability | L | Today `Constants.kt` is the only source of magic numbers |
| Document the avatar decision in a dedicated ADR | H | See convergence step 3 |

## Known Risks

- **Spec drift.** Without automated tests, specs can drift from the source. Mitigation:
  source-of-truth policy; review checklist requires `file:line` evidence.
- **Stack assumptions.** Any future change that introduces Compose, Hilt, or Room MUST
  be a separate SDD change that updates this spec first; doing it inline violates the
  out-of-scope rule from the proposal.
- **TDD gap.** Strict TDD is off; the manual verification matrix is the only barrier
  between merges and silent regressions. Risk concentrated in `TareaRepositorioFirebase`,
  `AuthRepositorioFirebase`, and `ParejaViewModel` because they own transactional logic.

[UNVERIFIED] No Firebase emulator, CI, or test-orchestration infrastructure was inspected.

## Manual Verification (for this spec)

| Check | How | Expected |
|---|---|---|
| Spec drift | Pick 3 spec claims; open the cited `file:line` | All three match |
| Stack purity | `grep -r "androidx.compose\\|dagger.hilt\\|androidx.room\\|retrofit2" app/` | Zero matches |
| Naming | List `vista/`, `viewmodel/`, `repositorio/`, `data/firebase/` files | All match convention table |
| Source-of-truth policy | Find a README claim contradicted by source | Spec/PR is corrected to source, not vice versa |
