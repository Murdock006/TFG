# Delta for navigation-lifecycle

## Change shape

Robustness/convergence delta for `teamtask-navigation-lifecycle-convergence`. It changes
lifecycle/navigation orchestration internals, not user-visible navigation destinations:

- **MODIFIED** — "Auto-login MUST require verified email before navigating" gains the
  single-coordinator + recreation-safety invariant (resolves TD-4) while preserving the
  verified-session condition and the `Presentacion → Login → PgPrincipal` sequence.
- **MODIFIED** — "Listeners MUST be released in `awaitClose` or equivalent" gains the
  Fragment `onDestroyView` job-cancellation clause and the `MainActivity.avatarDrawerJob`
  case (bounded collateral lifecycle cleanup).
- **MODIFIED** — "Notification intent extras MUST route to the task" moves the `taskId`
  hand-off to the typed navigation argument and adds the duplicate-stack avoid clause; the
  raw `"openTaskId"` intent extra contract is unchanged.
- **ADDED** — task-observer state consistency (TD-5: stale-key pruning + single-writer
  discipline + documented assumptions).
- **ADDED** — adapter asynchronous-work scope ownership (TD-6: no external `CoroutineScope`;
  lifecycle-safe loading).
- **ADDED** — typed navigation arguments via AndroidX Safe Args (TD-15).
- **Unchanged** — "Back behavior MUST follow declared rules per destination", "Logout MUST
  clear local state and rewind the back stack", and "Lifecycle-aware collection SHOULD be the
  default" MUST be preserved verbatim at archive; back handling is explicitly out of scope.

Grounding: `proposal.md` (Resolved Decisions 1-5, Scope, Success Criteria),
`exploration.md`, and the current source: `MainActivity.kt:94,133,156-196,206,216-221,232-286`
(listener `:257-277`), `FragmentPresentacion.kt:36,39-57`, `AndroidManifest.xml:21-31` (no
`configChanges`), `TareaRepositorioFirebase.kt:163-214` (map `:186`, listeners
`:188,194,202`, `awaitClose` `:209-213`) vs parity `:216-229` (pruning `:224-225`),
`TareasHomeAdapter.kt:34-39,144,177,197,272-279`, `FragmentTareasPendientes.kt:29`,
`FragmentPgPrincipal.kt:46,78-99,177,197-215,312-316`, `FragmentTareas.kt:56-57,78,155,205,226,397`,
`nav_graph.xml:80-84`, `NotificationScheduler.kt:74-80`.

Line-drift note: every `file:line` below was re-verified against the current source. The
canonical spec's older citations (`MainActivity.kt:255-275`,
`TareaRepositorioFirebase.kt:162-213`, `TareasHomeAdapter.kt:39,145-210`) are superseded; the
corrected rows are recorded under "Non-requirement updates".

## MODIFIED Requirements

### Requirement: Auto-login MUST require verified email before navigating

The system MUST only navigate to `fragment_PgPrincipal` if `FirebaseAuth.currentUser` is
non-null AND `isEmailVerified` is true. If not verified, the system MUST sign out and
remain on the auth flow. The `Presentación → Login` transition MUST be owned by a single
coordinator: the system MUST NOT register more than one active navigator for that transition,
and the registration MUST be recreation-safe so that an Activity recreation during the auth
window (no `configChanges` is declared for `MainActivity`; `AndroidManifest.xml:21-31`) cannot
produce a second navigation to `fragment_Login`. The `Login → PgPrincipal` step MUST remain
conditional on the verified session and MUST occur only after the single coordinator observes
the destination. The observable destination sequence and the presentation message sequence
MUST be preserved.
(Previously: the transition was driven by two independent navigators — the Activity's one-shot
`OnDestinationChangedListener` and `FragmentPresentacion`'s delayed navigation — and the
listener removed itself only after the first `fragment_Presentacion` hit, so a configuration
change during the window could race two navigators into Login.)

#### Scenario: User is logged in but email not verified

- GIVEN `FirebaseAuth.currentUser != null` and `isEmailVerified == false`
- WHEN `verificarSesionActiva` runs
- THEN the system MUST call `auth.signOut()` and show a verification Toast
- AND the system MUST NOT navigate past `fragment_Login`
- Evidence: `MainActivity.kt:238-247`

#### Scenario: User is logged in and email verified

- GIVEN `FirebaseAuth.currentUser != null` and `isEmailVerified == true`
- WHEN `verificarSesionActiva` runs
- THEN the system MUST load the user's group via `ParejaViewModel.cargarGrupoPorUsuario`
- AND the single coordinator MUST chain `Presentacion → Login → PgPrincipal` only after the
  destination listener fires
- AND no second navigator MUST navigate to `fragment_Login`
- Evidence: `MainActivity.kt:248-277`

#### Scenario: Configuration change during the auth window must not duplicate Login

- GIVEN the auth window is active and the single coordinator is registered
- WHEN the Activity is recreated (configuration change) before the transition fires
- THEN exactly one navigation to `fragment_Login` MUST occur
- AND the recreated Activity MUST NOT register a second active navigator for the same transition
- Evidence: `MainActivity.kt:94,232-286` (listener `:257-277`); `AndroidManifest.xml:21-31`
  (no `configChanges`)

#### Scenario: Presentación message sequence is preserved under the single coordinator

- GIVEN the app starts unauthenticated on `fragment_Presentacion`
- WHEN the presentation messages run
- THEN the three messages MUST still display in order
- AND the transition to `fragment_Login` MUST still occur exactly once
- Evidence: `FragmentPresentacion.kt:36,39-57`

### Requirement: Listeners MUST be released in `awaitClose` or equivalent

Every Flow-returning repository method MUST release its Firestore listener inside
`awaitClose` (or in the coroutine cancellation block). ViewModels MUST cancel their
collector jobs in `onCleared`. Activities MUST cancel activity-scoped jobs in `onDestroy`
or on the next `onResume` if the user changes. Fragments MUST cancel view-scoped jobs in
`onDestroyView` so that a view-scoped job does not outlive the view it renders into.
(Previously: the requirement covered repository `awaitClose`, ViewModel `onCleared`, and
Activity `onDestroy`/`onResume`. The Fragment `onDestroyView` clause is added, and
`MainActivity.avatarDrawerJob` (`:56,486-500`) — an activity-scoped job the old text did not
name — is now covered by the Activity clause.)

#### Scenario: ViewModel-owned group listener

- GIVEN `ParejaViewModel` holds a `grupoObserverJob`
- WHEN the group id changes (via `setGrupoLocal`) or the ViewModel is cleared
- THEN the previous job MUST be cancelled before the new collector starts
- Evidence: `ParejaViewModel.kt:83-105,344-350`

#### Scenario: Activity-owned notifications listener

- GIVEN `MainActivity.notificacionesJob` is active
- WHEN the activity is destroyed or the user logs out
- THEN the job MUST be cancelled and `notificacionesUidObservado` reset
- Evidence: `MainActivity.kt:214-219,583-585`

#### Scenario: Activity-owned avatar job

- GIVEN `MainActivity.avatarDrawerJob` is active
- WHEN the activity is destroyed
- THEN the job MUST be cancelled
- AND no avatar load MUST run against a destroyed activity
- Evidence: `MainActivity.kt:56,486-500,216-221`

#### Scenario: Fragment-owned recent-tasks job

- GIVEN `FragmentPgPrincipal.tareasHomeJob` is active
- WHEN the Fragment view is destroyed
- THEN the job MUST be cancelled in `onDestroyView`
- Evidence: `FragmentPgPrincipal.kt:46,197-215`

### Requirement: Notification intent extras MUST route to the task

The system MUST read `openTaskId` from the launching intent as a raw intent extra and
navigate to `fragment_Tareas` with the typed `taskId` navigation argument. The handler MUST
run on `onCreate` and on `onNewIntent`. Tapping a notification while the current destination
is already `fragment_Tareas` MUST NOT push a duplicate `fragment_Tareas` destination
(single-top / `popUpTo`). If the bounded duplicate-stack fix cannot be shown safe within this
change, the change MUST record it as an explicit follow-up rather than leave it undocumented.
(Previously: the requirement navigated with a manual `taskId` bundle and did not address the
duplicate-stack case.)

#### Scenario: User taps a notification with `openTaskId`

- GIVEN a `PendingIntent` carries `Intent.putExtra("openTaskId", taskId)`
- WHEN the activity is launched (or relaunched)
- THEN the activity MUST navigate to `fragment_Tareas` with the typed `taskId` argument
- AND the destination fragment MUST be able to read the typed `taskId` argument
- Evidence: `MainActivity.kt:133,204-230`; `NotificationScheduler.kt:74-80`;
  `FragmentTareas.kt:56-72`

#### Scenario: Notification tapped while already on `fragment_Tareas`

- GIVEN the current destination is `fragment_Tareas`
- WHEN the user taps a notification carrying `openTaskId`
- THEN the system MUST NOT place two `fragment_Tareas` entries on the back stack
- Evidence: `MainActivity.kt:223-230`; `NotificationScheduler.kt:74-80`

## ADDED Requirements

### Requirement: Task observation state MUST stay consistent across observer updates

`TareaRepositorioFirebase.observarTareas` MUST keep its shared result map consistent with the
query snapshots it subscribes to: when a document is no longer present in a query snapshot,
its key MUST be removed from the shared map before the combined list is emitted, with parity
to `observarTareasPorGrupo`. The shared map MUST have a single logical writer discipline so
that a task reachable through more than one query (for example `creadoPor` and `grupoId`)
appears exactly once in the emitted list. Firestore snapshot listener callbacks are treated as
main-thread-confined `[UNVERIFIED]`; the pruning behavior MUST be correct under that
assumption and the assumption MUST be stated explicitly rather than silently assumed. The
group id is resolved once when the flow starts; a group change while the same flow instance is
active MUST NOT be expected to re-resolve within that instance and MUST be documented as a
known limitation. This change resolves TD-5 by stale-key pruning plus single-writer discipline;
a typed `combine` rewrite is not required by this change.

#### Scenario: Stale key is pruned

- GIVEN a task is present in the emitted list through a listener snapshot
- WHEN a later snapshot for that listener no longer contains the task
- THEN the task MUST NOT be present in the next emitted list
- Evidence: parity pruning `TareaRepositorioFirebase.kt:224-225`; target map `:186`

#### Scenario: Cross-listener duplicate is emitted once

- GIVEN a task matches both the `creadoPor` listener and the `grupoId` listener
- WHEN both snapshots are processed
- THEN the task MUST appear exactly once in the emitted list
- Evidence: shared map `TareaRepositorioFirebase.kt:186`; listeners `:188,194,202`

#### Scenario: Group resolved once is documented

- GIVEN the observer flow is active for group A
- WHEN the user's group changes to B while the same flow instance stays active
- THEN the flow MUST NOT be required to re-resolve the group within that instance
- AND the limitation MUST be documented
- Evidence: group resolution `TareaRepositorioFirebase.kt:171-183`

#### Scenario: Threading assumption is explicit

- GIVEN a reviewer audits `observarTareas`
- WHEN they read the main-thread-confinement assumption
- THEN it MUST be stated explicitly and marked `[UNVERIFIED]`, not silently assumed
- Evidence: snapshots registered with no custom executor `TareaRepositorioFirebase.kt:188,194,202`

### Requirement: Adapter asynchronous work MUST use a lifecycle-safe scope owner

`TareasHomeAdapter` MUST NOT receive an external `CoroutineScope` from its host Fragment. Its
asynchronous loads (assignee-name resolution and the assignment dialog) MUST run on an
adapter-owned, lifecycle-safe mechanism that survives view recreation, or behind the
ViewModel with proper cancellation; the loads MUST be cancelled when the host view is
destroyed. Consuming Fragments MUST create the adapter against the current view lifecycle
owner and MUST NOT capture a stale scope through `by lazy`. The adapter MUST NOT call
`fragment.findNavController()`; it MUST invoke a host-provided navigation callback instead.

#### Scenario: Loads survive view recreation

- GIVEN the adapter is attached and an assignee-name load is pending
- WHEN the Fragment view is recreated (for example a rotation)
- THEN a later load MUST complete rather than become a silent no-op
- Evidence (defect): `FragmentTareasPendientes.kt:29`; `TareasHomeAdapter.kt:38,144`

#### Scenario: Loads are cancelled with the view

- GIVEN the adapter scope is active
- WHEN the host view is destroyed
- THEN the adapter's jobs MUST be cancelled and MUST NOT leak past the view
- Evidence: `TareasHomeAdapter.kt:144,177,197`

#### Scenario: No external scope in the constructor

- GIVEN a developer reads the `TareasHomeAdapter` constructor
- WHEN they check its parameters
- THEN it MUST NOT declare a `CoroutineScope` parameter
- Evidence: current `scope` parameter `TareasHomeAdapter.kt:34-39`

#### Scenario: Navigation is decoupled from the Fragment

- GIVEN the user taps a task row
- WHEN the adapter navigates
- THEN it MUST call the host-provided navigation callback and MUST NOT call
  `fragment.findNavController()`
- Evidence: `TareasHomeAdapter.kt:272-279`

### Requirement: Navigation arguments MUST be typed via AndroidX Safe Args

The `androidx.navigation.safeargs.kotlin` Gradle plugin MUST be applied and version-matched
to the Navigation Component (2.9.6). `fragment_Tareas` MUST declare typed navigation
arguments (`taskId`, `modo`, `categoria`) in `nav_graph.xml`. Every manual `Bundle` writer and
reader for these arguments MUST be replaced by the generated directions and argument
accessors. The `"openTaskId"` intent extra contract MUST remain a raw intent extra and MUST
NOT be converted to a navigation argument.

#### Scenario: No manual bundle sites remain

- GIVEN the change is applied
- WHEN production code is searched for manual reads/writes of the task navigation keys
- THEN there MUST be zero manual `Bundle` sites for `taskId`, `modo`, and `categoria`
- Evidence (current writers): `MainActivity.kt:225`; `TareasHomeAdapter.kt:274`;
  `FragmentPgPrincipal.kt:78-99,312-316`; (current readers):
  `FragmentTareas.kt:56-57,78,155,205,226,397`

#### Scenario: Arguments are declared

- GIVEN the navigation graph is reviewed
- WHEN `fragment_Tareas` is inspected
- THEN its `taskId`, `modo`, and `categoria` arguments MUST be declared as `<argument>`
  elements
- Evidence: current absence in `nav_graph.xml:80-84`

#### Scenario: `openTaskId` contract unchanged

- GIVEN a notification is delivered
- WHEN its intent is read
- THEN `"openTaskId"` MUST still be read as a raw intent extra
- AND it MUST NOT be declared as a navigation argument
- Evidence: `MainActivity.kt:133,206`; `NotificationScheduler.kt:74-80`

## Non-requirement updates (apply at archive)

The archive step MUST apply the following replacements to
`openspec/specs/navigation-lifecycle/spec.md`. They are not requirement-shaped, so they are
recorded here instead of as delta requirement blocks (established pattern).

### Current State (observable) — corrected evidence rows (line drift)

"MainActivity overrides the graph" table:

- Auto-login row: Evidence changes from `MainActivity.kt:91,230-283` to
  `MainActivity.kt:94,232-286`; the implementation note becomes "chains Presentacion → Login →
  PgPrincipal via a single recreation-safe coordinator (listener `:257-277`)".
- Back row: Evidence changes from `MainActivity.kt:154-194` to `MainActivity.kt:156-196`.
- Open task from notification intent row: Evidence changes from `MainActivity.kt:130-133,202-228`
  to `MainActivity.kt:133,204-230`; `navigate(fragment_Tareas, bundle)` becomes the typed
  `taskId` argument.

"Listener ownership and cancellation" table:

- `TareaRepositorioFirebase.observarTareas` row: Evidence changes from
  `TareaRepositorioFirebase.kt:162-213` to `TareaRepositorioFirebase.kt:163-214`.
- `TareaRepositorioFirebase.observarTareasPorGrupo` row: Evidence changes from
  `TareaRepositorioFirebase.kt:215-228` to `TareaRepositorioFirebase.kt:216-229`.
- `TareasHomeAdapter` external scope row is REPLACED by:

  | Component | Where the listener lives | Cancellation |
  |---|---|---|
  | `TareasHomeAdapter` adapter-owned scope | The adapter owns its lifecycle-safe load mechanism; the host supplies a navigation callback, not a scope | Loads are cancelled when the host view is destroyed; Fragments build the adapter per view (`TareasHomeAdapter.kt:34-39,144,177,197`; `FragmentTareasPendientes.kt:29`; `FragmentPgPrincipal.kt:177`) |

### Future Convergence Work — resolved rows removed

The following rows are closed by this change and removed from the table. The remaining rows
(double-back in the nav graph, single-place `openTaskId` documentation, `LogoutController`)
are unchanged.

- "Replace ad-hoc `mutableMapOf` merge in `TareaRepositorioFirebase.observarTareas` with a
  typed `combine` | Med" — resolved by TD-5 (stale-key pruning + single-writer discipline; no
  typed `combine` rewrite in this change).
- "Pass an explicit `CoroutineScope` to `TareasHomeAdapter` or refactor to suspend | Low" —
  resolved by TD-6 (the external scope is removed, not passed explicitly).
- "Add `androidx.navigation:navigation-safe-args` | Low" — resolved by TD-15.

### Known Risks — resolved bullets removed

The bullets for the one-shot auto-login listener (TD-4), the `observarTareas` stale-entry race
(TD-5), the silent manual-bundle key typos (TD-15), and the externally-owned
`TareasHomeAdapter` scope (TD-6) are REMOVED as resolved by this change. The Activity-level
back-handling risk is preserved, as is this marker:

[UNVERIFIED] Whether any future change will introduce Hilt, which would alter DI patterns.

### Manual Verification (rows added)

The preserved behavior matrix is the verification net for this change (no automated suite).
The following rows are added to the existing table.

| Check | How | Expected |
|---|---|---|
| Single auto-login navigator | Start a verified session and rotate the device during the Presentación window | Exactly one `fragment_Login` entry; lands on `fragment_PgPrincipal` |
| Observer stale-key pruning | Open the task list, then remove a task from the queries (delete/unassign) | The removed task disappears; no stale row remains |
| Adapter load after rotation | Rotate on a screen using `TareasHomeAdapter` with an uncached assignee | The assignee name resolves after rotation (not stuck on "Cargando...") |
| Adapter scope cancellation | Rotate away while an assignee-name load is in flight | No leaked job; loads are cancelled with the old view |
| Safe Args migration | `grep -rn "putString(\"taskId\"\|getString(\"taskId\"\|putString(\"categoria\"\|getString(\"categoria\"\|getString(\"modo\"" app/src/main/java` | Zero manual navigation-bundle sites for the typed arguments |
| Notification duplicate stack | While already on `fragment_Tareas`, tap a reminder notification | No duplicate `fragment_Tareas` is pushed |
| Collateral job cleanup | Rotate/recreate on the dashboard and open/close the drawer with an avatar load pending | `avatarDrawerJob` and `tareasHomeJob` are cancelled; no leaked-job warnings |

## Unchanged content (preserved)

The following sections are NOT changed by this delta and MUST be preserved verbatim at
archive: the "Current State (observable)" rows not listed above, the requirements "Back
behavior MUST follow declared rules per destination", "Logout MUST clear local state and
rewind the back stack", and "Lifecycle-aware collection SHOULD be the default" (with all their
scenarios), the "Future Convergence Work" rows not listed above, the "Known Risks" bullet for
Activity-level back handling, and this marker:

[UNVERIFIED] Whether any future change will introduce Hilt, which would alter DI patterns.
