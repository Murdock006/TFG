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

### Requirement: Binary avatar assets and preference namespaces MUST follow the canonical convention

Avatar bytes MUST be stored as compressed base64 in the dedicated Firestore collection
`avatares/{uid}` (fields `base64`, `contentType`, `updatedAt`). Binary avatar data MUST NOT be
added to Firebase Storage and MUST NOT be added to the widely-streamed `usuarios` documents.
Avatar uploads MUST be self-only; avatar reads MUST resolve any group member. Client-side
compression (downscale to roughly 256px on the long edge, JPEG) and a hard size cap below the
Firestore 1 MiB limit MUST be enforced, with oversized input rejected before any write.
SharedPreferences namespaces MUST have a single owner and a single purpose: the retired
`avatar_prefs` namespace MUST NOT be reintroduced, and `tfg_prefs` MUST be used only as a
local cache of the last-known avatar, never as the authority. New binary assets MUST follow
the same pattern rather than adding blobs to widely-streamed documents.

#### Scenario: New binary asset feature

- GIVEN a developer adds an avatar-like binary asset
- WHEN they plan the storage
- THEN the plan MUST use a dedicated Firestore collection with client-side compression and a hard size cap
- AND the plan MUST NOT add the bytes to `usuarios` or Firebase Storage

#### Scenario: Retired namespace is not reintroduced

- GIVEN the change is implemented
- WHEN production code is searched for `avatar_prefs`
- THEN there MUST be zero matches

#### Scenario: Local preferences are cache-only

- GIVEN a developer needs to persist avatar state locally
- WHEN they choose a namespace
- THEN they MUST use `tfg_prefs` as a cache only
- AND the local value MUST NOT be treated as proof that a remote avatar exists or is current

## Technical debt register

Severity legend: **H**igh = blocks a future spec's invariants; **M**ed = divergence
between paths; **L**ow = cleanup.

| # | Issue | Severity | Evidence | Spec to fix it | Status |
|---|---|---|---|---|---|
| TD-1 | Two task repos with divergent rules | H | `RepositorioTareas.kt:1-31,97-113,164-179` vs `TareaRepositorioFirebase.kt:466-494` | `task-domain` convergence | Resolved (`teamtask-task-repo-consolidation`) |
| TD-2 | Avatar authority split (`tfg_prefs` + `avatar_prefs`) | H | `AvatarRepositorioLocal.kt:11,86`; `FragmentPgPrincipal.kt:376-380`; `MainActivity.kt:484-485` | `architecture-map` convergence | Resolved (`teamtask-avatar-authority`) |
| TD-3 | `AvatarRepositorioFirebase` is dead code | H | `AvatarViewModel.kt:14-16` (no caller); class is `class`, not `object`/`companion` | `architecture-map` convergence | Resolved (`teamtask-avatar-authority`) |
| TD-4 | Auto-login one-shot listener may fire twice on config change | M | `MainActivity.kt:255-275` | `navigation-lifecycle` | Open |
| TD-5 | `TareaRepositorioFirebase.observarTareas` mutates shared map without sync | M | `TareaRepositorioFirebase.kt:184-212` | `navigation-lifecycle` | Open |
| TD-6 | `TareasHomeAdapter` external `CoroutineScope` | M | `TareasHomeAdapter.kt:39,145-210` | `navigation-lifecycle` | Open |
| TD-7 | `resolverReclamo` does state update then points transfer outside tx | H | `TareaRepositorioFirebase.kt:362-388` | `task-domain` | Open |
| TD-8 | `modelo.Tarea.estado` is raw `String`; not all states listed in comment | M | `Tarea.kt:15`; `TareaRepositorioFirebase.kt:397-400,448`; `FragmentTareas.kt:492` | `task-domain` | Open |
| TD-9 | No `firestore.rules` / `storage.rules` / `indexes.json` in repo | H | Local artifacts now exist: `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `tools/firebase/*` (committed by `teamtask-testing-emulator-strategy` WU2); deployment, console parity, and App Check remain pending; `firebase-database-ktx` declared but no consumer | `firestore-contracts` | Partially resolved (`teamtask-testing-emulator-strategy`) |
| TD-10 | UI direct Firebase reads | M | `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` | `architecture-map` | Open |
| TD-11 | `ejecutorUid` param ignored in `RepositorioTareas.marcarCompletada` no-confirm path | M | `RepositorioTareas.kt:86` (overrides with `tareaTx.asignadoA ?: ejecutorUid`) | `task-domain` | Resolved (`teamtask-task-repo-consolidation`) |
| TD-12 | Disputa state machine has no resolver | M | `Disputa.kt:9`; `RepositorioDisputas.kt:17-34` | `task-domain` | Open |
| TD-13 | Best-effort account cleanup | M | `AuthRepositorioFirebase.kt:259-357` | `firestore-contracts` | Open |
| TD-14 | `USAR_FIREBASE` flag in `LocalizadorServicios` has no test | L | Selector removed by `teamtask-testing-emulator-strategy` WU1; `LocalizadorServicios` now requires the initialized `FirebaseComposition` | `architecture-map` | Resolved (`teamtask-testing-emulator-strategy`) |
| TD-15 | Manual navigation bundles | L | `MainActivity.kt:223`; `FragmentTareas.kt:56-72,397-398` | `navigation-lifecycle` | Open |

## Convergence roadmap

| # | Title | Depends on | Estimated impact | Status |
|---|---|---|---|---|
| 1 | Publish `architecture-map`, `task-domain`, `firestore-contracts`, `navigation-lifecycle`, `implementation-recipes` specs | — | docs only | Done |
| 2 | Unify task repos (pick `TareaRepositorioFirebase`; remove `RepositorioTareas`) | 1 | refactor `FragmentTareas`, `TareasViewModel`, `VistaModeloPrincipal` | Done (`teamtask-task-repo-consolidation`) |
| 3 | Audit and fix avatar authority (decide local vs Firebase; unify `tfg_prefs`/`avatar_prefs`; delete dead Firebase impl) | 1 | changes `AvatarViewModel`, `FragmentPerfil`, `FragmentPgPrincipal`, `MainActivity` | Done (`teamtask-avatar-authority`) |
| 4 | Add `firestore.rules`, `storage.rules`, `firestore.indexes.json`; commit and deploy | 1 | Local artifacts committed by `teamtask-testing-emulator-strategy` WU2 and verified against emulators; deployment, console parity, and App Check pending | Partially done |
| 5 | Add focused tests per spec: `ParejaViewModel`, `TareaRepositorioFirebase.crearTarea`/`confirmarTarea`, `RepositorioRecompensas.canjearRecompensa` | 2 | 3-5 unit tests using `firebase emulators:exec` | Pending |
| 6 | Refactor `TareaRepositorioFirebase.observarTareas` to typed `combine` and reiniciar-on-group-change | 5 | safer observers | Pending |
| 7 | Encapsulate logout + auto-login flow into dedicated controllers | 1 | smaller `MainActivity` | Pending |

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
| Avatar storage round-trip | Upload an image in the emulator/release build; inspect `avatares/{uid}` and a group member's dashboard card | Document contains `base64`/`contentType`/`updatedAt` under the cap; member card renders the decoded avatar; `usuarios/{uid}.avatarUpdatedAt` is set |
| Avatar oversize rejection | Upload an image that encodes above the hard cap | Failure surfaced in UI; no `avatares` document written |
| Avatar cross-user write denied | Attempt to write `avatares/{other}` from a signed-in user in the emulator | Write denied |
| Avatar namespace audit | `grep -r "avatar_prefs" app/src/main/java` | Zero matches |
| Avatar offline fallback | Disable network with a cached avatar present, then with none | Cached avatar shown when present; otherwise `R.drawable.perfil` |
