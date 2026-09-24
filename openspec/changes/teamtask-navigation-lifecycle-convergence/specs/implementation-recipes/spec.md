# Delta for implementation-recipes

## Change shape

Documentation-status delta plus one ADDED recipe requirement. The technical debt register and
the convergence roadmap are non-requirement tables; their updated rows are recorded as exact
replacements for archive.

- **ADDED** — one requirement: "Navigation arguments MUST use AndroidX Safe Args" (the
  navigation recipe moves from manual bundles to typed Safe Args, resolving TD-15).
- **Non-requirement updates** — the debt register marks TD-4, TD-5, TD-6, and TD-15
  `Resolved (teamtask-navigation-lifecycle-convergence)`; the convergence roadmap marks step 6
  `Done (teamtask-navigation-lifecycle-convergence)`; the "Current State (observable)"
  Navigation row moves from manual bundles to Safe Args.

**Choice recorded here:** the debt-register, roadmap, and current-state updates are not
requirement-shaped, so this delta records the exact table replacements to apply at archive
instead of `## MODIFIED Requirements` blocks, following the `teamtask-task-repo-consolidation`
and `teamtask-avatar-authority` pattern. The archive step MUST apply the tables below to
`openspec/specs/implementation-recipes/spec.md`. All existing requirements and scenarios are
unchanged and MUST be preserved byte-for-byte.

Grounding: `proposal.md` (Modified Capabilities; Resolved Decisions 3-5; Success Criteria),
`exploration.md`, and source `MainActivity.kt:94,225,232-286`; `FragmentTareas.kt:56-57,78,155,205,226,397`;
`TareasHomeAdapter.kt:34-39,144,177,197,272-279`; `FragmentPgPrincipal.kt:78-99,312-316`;
`TareaRepositorioFirebase.kt:163-214,224-225`; `nav_graph.xml:80-84`; `app/build.gradle.kts:1-5,87-88`;
`gradle/libs.versions.toml:16,37-39`; `docs/architecture/TEAMTASK_GUIDE.md:148-159,175,229`.

## ADDED Requirements

### Requirement: Navigation arguments MUST use AndroidX Safe Args

New navigation MUST use the AndroidX Navigation Safe Args generated directions and typed
argument accessors instead of manual `Bundle` reads/writes. The `androidx.navigation.safeargs.kotlin`
Gradle plugin MUST be applied and version-matched to the Navigation Component (2.9.6). Every
declared navigation argument used by this stack (`taskId`, `modo`, `categoria`) MUST be declared
as a typed `<argument>` in `nav_graph.xml` and consumed through generated accessors. The
`"openTaskId"` intent extra is not a navigation argument and MUST remain a raw intent extra.

#### Scenario: New navigation site with arguments

- GIVEN a developer adds or edits a navigation to a destination that takes arguments
- WHEN they pass those arguments
- THEN they MUST use the generated directions / argument accessors
- AND they MUST NOT write a manual `Bundle` for a declared navigation argument

#### Scenario: Safe Args plugin is present and version-matched

- GIVEN the app module is built
- WHEN its Gradle plugins and version catalog are inspected
- THEN `androidx.navigation.safeargs.kotlin` MUST be applied
- AND its version MUST match the Navigation Component
- Evidence: current absence in `app/build.gradle.kts:1-5` and `gradle/libs.versions.toml:37-39`;
  Navigation Component `2.9.6` at `gradle/libs.versions.toml:16`

#### Scenario: `openTaskId` remains an intent extra

- GIVEN a developer reviews the notification deep-link path
- WHEN they check how `openTaskId` is read
- THEN it MUST still be read as a raw intent extra and MUST NOT be declared as a navigation
  argument
- Evidence: `MainActivity.kt:133,206`; `NotificationScheduler.kt:74-80`

## Non-requirement updates (apply at archive)

### Technical debt register (updated)

TD-4, TD-5, TD-6, and TD-15 become `Resolved (teamtask-navigation-lifecycle-convergence)`.
Every other row is unchanged. Evidence line numbers are corrected where the current source
differs from the previously recorded drift.
Severity legend unchanged: **H**igh = blocks a future spec's invariants; **M**ed = divergence
between paths; **L**ow = cleanup.

| # | Issue | Severity | Evidence | Spec to fix it | Status |
|---|---|---|---|---|---|
| TD-1 | Two task repos with divergent rules | H | `RepositorioTareas.kt:1-31,97-113,164-179` vs `TareaRepositorioFirebase.kt:466-494` | `task-domain` convergence | Resolved (`teamtask-task-repo-consolidation`) |
| TD-2 | Avatar authority split (`tfg_prefs` + `avatar_prefs`) | H | `AvatarRepositorioLocal.kt:11,86`; `FragmentPgPrincipal.kt:376-380`; `MainActivity.kt:484-485` | `architecture-map` convergence | Resolved (`teamtask-avatar-authority`) |
| TD-3 | `AvatarRepositorioFirebase` is dead code | H | `AvatarViewModel.kt:14-16` (no caller); class is `class`, not `object`/`companion` | `architecture-map` convergence | Resolved (`teamtask-avatar-authority`) |
| TD-4 | Auto-login one-shot listener may fire twice on config change | M | `MainActivity.kt:255-277` | `navigation-lifecycle` | Resolved (`teamtask-navigation-lifecycle-convergence`) |
| TD-5 | `TareaRepositorioFirebase.observarTareas` mutates shared map without sync | M | `TareaRepositorioFirebase.kt:163-214` (map `:186`, listeners `:188,194,202`) | `navigation-lifecycle` | Resolved (`teamtask-navigation-lifecycle-convergence`) |
| TD-6 | `TareasHomeAdapter` external `CoroutineScope` | M | `TareasHomeAdapter.kt:34-39,144,177,197` | `navigation-lifecycle` | Resolved (`teamtask-navigation-lifecycle-convergence`) |
| TD-7 | `resolverReclamo` does state update then points transfer outside tx | H | `TareaRepositorioFirebase.kt:362-388` | `task-domain` | Open |
| TD-8 | `modelo.Tarea.estado` is raw `String`; not all states listed in comment | M | `Tarea.kt:15`; `TareaRepositorioFirebase.kt:397-400,448`; `FragmentTareas.kt:492` | `task-domain` | Open |
| TD-9 | No `firestore.rules` / `storage.rules` / `indexes.json` in repo | H | Local artifacts now exist: `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `tools/firebase/*` (committed by `teamtask-testing-emulator-strategy` WU2); deployment, console parity, and App Check remain pending; `firebase-database-ktx` declared but no consumer | `firestore-contracts` | Partially resolved (`teamtask-testing-emulator-strategy`) |
| TD-10 | UI direct Firebase reads | M | `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` | `architecture-map` | Open |
| TD-11 | `ejecutorUid` param ignored in `RepositorioTareas.marcarCompletada` no-confirm path | M | `RepositorioTareas.kt:86` (overrides with `tareaTx.asignadoA ?: ejecutorUid`) | `task-domain` | Resolved (`teamtask-task-repo-consolidation`) |
| TD-12 | Disputa state machine has no resolver | M | `Disputa.kt:9`; `RepositorioDisputas.kt:17-34` | `task-domain` | Open |
| TD-13 | Best-effort account cleanup | M | `AuthRepositorioFirebase.kt:259-357` | `firestore-contracts` | Open |
| TD-14 | `USAR_FIREBASE` flag in `LocalizadorServicios` has no test | L | Selector removed by `teamtask-testing-emulator-strategy` WU1; `LocalizadorServicios` now requires the initialized `FirebaseComposition` | `architecture-map` | Resolved (`teamtask-testing-emulator-strategy`) |
| TD-15 | Manual navigation bundles | L | `MainActivity.kt:225`; `TareasHomeAdapter.kt:274`; `FragmentPgPrincipal.kt:78-99,312-316`; `FragmentTareas.kt:56-57,78,155,205,226,397` | `navigation-lifecycle` | Resolved (`teamtask-navigation-lifecycle-convergence`) |

Resolution detail (for the archive trail): TD-4 was resolved by a single recreation-safe
coordinator; TD-5 by stale-key pruning plus single-writer discipline (not a typed `combine`
rewrite); TD-6 by removing the external `CoroutineScope` and adding an adapter-owned,
lifecycle-safe load mechanism; TD-15 by adopting AndroidX Safe Args.

### Convergence roadmap (updated)

Step 6 becomes `Done (teamtask-navigation-lifecycle-convergence)`; every other row is unchanged.

| # | Title | Depends on | Estimated impact | Status |
|---|---|---|---|---|
| 1 | Publish `architecture-map`, `task-domain`, `firestore-contracts`, `navigation-lifecycle`, `implementation-recipes` specs | — | docs only | Done |
| 2 | Unify task repos (pick `TareaRepositorioFirebase`; remove `RepositorioTareas`) | 1 | refactor `FragmentTareas`, `TareasViewModel`, `VistaModeloPrincipal` | Done (`teamtask-task-repo-consolidation`) |
| 3 | Audit and fix avatar authority (decide local vs Firebase; unify `tfg_prefs`/`avatar_prefs`; delete dead Firebase impl) | 1 | changes `AvatarViewModel`, `FragmentPerfil`, `FragmentPgPrincipal`, `MainActivity` | Done (`teamtask-avatar-authority`) |
| 4 | Add `firestore.rules`, `storage.rules`, `firestore.indexes.json`; commit and deploy | 1 | Local artifacts committed by `teamtask-testing-emulator-strategy` WU2 and verified against emulators; deployment, console parity, and App Check pending | Partially done |
| 5 | Add focused tests per spec: `ParejaViewModel`, `TareaRepositorioFirebase.crearTarea`/`confirmarTarea`, `RepositorioRecompensas.canjearRecompensa` | 2 | 3-5 unit tests using `firebase emulators:exec` | Pending |
| 6 | Refactor `TareaRepositorioFirebase.observarTareas` to typed `combine` and reiniciar-on-group-change | 5 | safer observers | Done (`teamtask-navigation-lifecycle-convergence`) |
| 7 | Encapsulate logout + auto-login flow into dedicated controllers | 1 | smaller `MainActivity` | Pending |

Step 6 note (for the archive trail): the observers were converged via stale-key pruning plus
single-writer discipline and a documented group-resolved-once limitation, not via a typed
`combine` rewrite; the navigation/scope/safe-argument pieces of step 6 are delivered as stated.

### Current State (observable) — Navigation row (updated)

The Navigation row changes from manual bundles to Safe Args and its source citation is
corrected. All other rows are unchanged.

| Decision | Status | Source |
|---|---|---|
| Navigation | AndroidX Navigation Component with Safe Args (typed directions/arguments) | `res/navigation/nav_graph.xml`; `app/build.gradle.kts:1-5,87-88`; `gradle/libs.versions.toml:16,37-39` |

Note: the previous source citation `app/build.gradle.kts:69-70` pointed at the Firestore
dependency block, not the Navigation dependency block; this change corrects it.

### Guide updates (apply phase, not this spec file)

The proposal also scopes `docs/architecture/TEAMTASK_GUIDE.md` §6 (TD-4/5/6/15 status), §7
(roadmap step 6 status), and §9 (change status) updates. Those are documentation edits applied
during the apply phase, not deltas to this spec file.

## Unchanged content (preserved)

The following sections are NOT changed by this delta and MUST be preserved verbatim at
archive: the "Current State (observable)" rows not listed above, all Requirements blocks (with
their scenarios), the "Future Convergence Work" table, the "Known Risks" section, the "Manual
Verification (for this spec)" table, and this marker:

[UNVERIFIED] No Firebase emulator, CI, or test-orchestration infrastructure was inspected.
