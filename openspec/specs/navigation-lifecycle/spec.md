# navigation-lifecycle

## Purpose

Factual specification of the navigation graph, imperative navigation, listener and
coroutine ownership, and cancellation policy in TeamTask. Documents CURRENT observable
behavior and the lifecycle-safe patterns already present in the codebase, plus the
uneven spots that future work should converge on.

## Current State (observable)

### Navigation graph

`res/navigation/nav_graph.xml:1-98`:

- `fragment_Presentacion` is the start destination (`nav_graph.xml:6`).
- `fragment_Presentacion` → `fragment_Login` (id `action_fragment_Presentacion_to_fragment_Login`).
- `fragment_Login` → `fragment_PgPrincipal` and `fragment_Login` → `fragment_Registro`.
- `fragment_PgPrincipal` has six actions to `Calendario`, `Recompensas`, `Perfil`, `Tareas`,
  `Pareja`, `TareasPendientes` (`nav_graph.xml:35-52`).
- All secondary fragments have no return action declared; navigation back uses ids.

### `MainActivity` overrides the graph

| Concern | Implementation | Evidence |
|---|---|---|
| Bottom nav and drawer chrome | Visibility toggled per destination (hide on Presentacion/Login/Registro) | `MainActivity.kt:136-151` |
| Auto-login | `verificarSesionActiva()` checks `FirebaseAuth.currentUser`, requires `isEmailVerified`, loads `ParejaViewModel.cargarGrupoPorUsuario`, then chains Presentacion → Login → PgPrincipal via a single recreation-safe coordinator (listener `:257-277`) | `MainActivity.kt:94,232-286` |
| Back | `OnBackPressedCallback` (1) closes drawer, (2) on `PgPrincipal` requires double-back within `DOUBLE_BACK_TIMEOUT_MS=2000L`, (3) on a secondary set navigates explicitly to `PgPrincipal`, else `popBackStack`/finish | `MainActivity.kt:156-196`; `Constants.kt:6` |
| Open task from notification intent | `intent.getStringExtra("openTaskId")` → `navigate(fragment_Tareas, typed taskId argument)` | `MainActivity.kt:133,204-230` |
| Logout | `repositorioAuth.logout()` + `tfg_prefs.remove("grupoId")` + `cancel` notifications job + `popUpTo(fragment_Presentacion, inclusive=true)` + `navigate(fragment_Login)` | `MainActivity.kt:571-595` |
| Notification system prompt | API 33+ requests `POST_NOTIFICATIONS`; granted/denied shows Toast | `MainActivity.kt:336-361,57-65` |
| Drawer | `setupDrawer` wires `topAppBar.setNavigationOnClickListener`, item clicks, and `refrescarHeaderDrawer` on open; logout lives in the footer's `NavigationView` | `MainActivity.kt:363-428` |
| Drawer header | `observarUsuarioDrawerHeader` runs in `lifecycleScope.launch { repeatOnLifecycle(STARTED) { LocalizadorServicios.repositorioAuth.observarUsuarios().collect { refrescarHeaderDrawer() } } }`; avatar path resolved from `tfg_prefs.avatar_path_<uid>` | `MainActivity.kt:430-508` |

### Listener ownership and cancellation

| Component | Where the listener lives | Cancellation |
|---|---|---|
| `TareaRepositorioFirebase.observarTareas` | `callbackFlow` opening 1-3 `addSnapshotListener` (creadoPor, asignadoA, grupoId) combined in a `mutableMapOf`; `awaitClose` removes all three | `awaitClose { subCreado.remove(); subAsignado.remove(); subGrupo?.remove() }` (`TareaRepositorioFirebase.kt:163-214`) |
| `TareaRepositorioFirebase.observarTareasPorGrupo` | one `addSnapshotListener` + `removeAll` on stale keys | `awaitClose { sub.remove() }` (`TareaRepositorioFirebase.kt:216-229`) |
| `AuthRepositorioFirebase.observarUsuarios` | `callbackFlow` over `usuarios` collection; cache refreshes `_usuarioCache` for current uid | `awaitClose { listener.remove() }` (`AuthRepositorioFirebase.kt:386-422`) |
| `RepositorioPareja.observarGrupos` / `observarGrupoPorId` | `callbackFlow` with `addSnapshotListener` | `awaitClose { sub.remove() }` (`RepositorioPareja.kt:169-191`) |
| `RepositorioNotificaciones.observarNotificaciones` | `callbackFlow` over `whereEqualTo("destinatario", uid)` | `awaitClose { sub.remove() }` (`RepositorioNotificaciones.kt:35-43`) |
| `MainActivity.notificacionesJob` | Activity-scoped `lifecycleScope.launch`; re-armed on `onResume`; cancelled in `onDestroy` | `MainActivity.kt:53,128,211-219,300,583-585` |
| `ParejaViewModel.grupoObserverJob` | `viewModelScope.launch`; cancelled on group change and `onCleared` | `ParejaViewModel.kt:42-43,83-105,344-350` |
| `TareasHomeAdapter` adapter-owned scope | The adapter owns its lifecycle-safe load mechanism; the host supplies a navigation callback, not a scope | Loads are cancelled when the host view is destroyed; Fragments build the adapter per view (`TareasHomeAdapter.kt:34-39,144,177,197`; `FragmentTareasPendientes.kt:29`; `FragmentPgPrincipal.kt:177`) |

### Fragment lifecycle patterns

- Most Fragments collect flows inside `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle(STARTED)`:
  - `FragmentTareasPendientes.kt:48-175`
  - `FragmentCalendario.kt:90-113`
  - `FragmentPerfil.kt:115-190`
  - `FragmentRecompensas.kt:76-121`
- `MainActivity` collects in `lifecycleScope` and gates emissions on
  `lifecycle.currentState.isAtLeast(RESUMED)` for notifications
  (`MainActivity.kt:286-329`).
- `FragmentTareas` mixes `lifecycleScope` (form) with `viewLifecycleOwner.lifecycleScope`
  (detalle) (`FragmentTareas.kt:89-99,408-409`).

## Requirements

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

### Requirement: Back behavior MUST follow declared rules per destination

The system MUST implement the back-press rules declared in `MainActivity.kt:154-194`:

- If the drawer is open, close it.
- If on `fragment_PgPrincipal`, require double-back within `DOUBLE_BACK_TIMEOUT_MS=2000L`
  to finish.
- If on a known secondary fragment, navigate to `fragment_PgPrincipal`.
- Otherwise, `popBackStack` and finish if empty.

#### Scenario: User presses back on `fragment_PgPrincipal` once

- GIVEN the current destination is `fragment_PgPrincipal`
- WHEN the user presses back
- THEN the system MUST show a Toast prompting a second back
- AND it MUST NOT finish the activity
- Evidence: `MainActivity.kt:163-171`; `Constants.kt:6`

#### Scenario: User presses back twice within the timeout

- GIVEN the current destination is `fragment_PgPrincipal` and `ultimoRetrocesoMs` is within `2000L`
- WHEN the user presses back again
- THEN the activity MUST call `finish()`
- Evidence: `MainActivity.kt:165-167`

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

### Requirement: Logout MUST clear local state and rewind the back stack

The system MUST, on logout: (1) call `repositorioAuth.logout()`; (2) remove `grupoId`
from `tfg_prefs`; (3) cancel the notifications job; (4) `popUpTo(fragment_Presentacion,
inclusive=true)` and `navigate(fragment_Login)`.

#### Scenario: User logs out from the drawer footer

- GIVEN the user taps `menuCerrarSesion`
- WHEN they confirm the dialog
- THEN all four logout steps above MUST run
- Evidence: `MainActivity.kt:560-595`

### Requirement: Lifecycle-aware collection SHOULD be the default

Fragments collecting flows from a ViewModel or repository SHOULD do so inside
`viewLifecycleOwner.lifecycleScope` with `repeatOnLifecycle(STARTED)`. Direct
`lifecycleScope.launch { flow.collect { ... } }` MUST be limited to Activity-scoped
flows that require RESUMED state (e.g., notifications).

#### Scenario: New Fragment observes a flow

- GIVEN a developer adds a Flow collector in a Fragment
- WHEN they open a PR
- THEN the collector SHOULD be inside `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { ... } }`
- Direct `lifecycleScope.launch { flow.collect { ... } }` MUST be justified in the PR description
- Evidence (positive): `FragmentTareasPendientes.kt:48-175`, `FragmentCalendario.kt:90-113`,
  `FragmentPerfil.kt:115-190`, `FragmentRecompensas.kt:76-121`
- Evidence (boundary): `MainActivity.kt:286-329` (RESUMED gating)

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

## Future Convergence Work

| Work item | Severity | Notes |
|---|---|---|
| Encode the `PgPrincipal` double-back and secondary-set redirect in the nav graph (avoid Activity-level `OnBackPressedCallback`) | Med | Today centralized in Activity; risk if a Fragment overrides back |
| Document the `openTaskId` contract in a single place and share with `NotificationScheduler` | Med | Today duplicated across `MainActivity.kt:130,202` and `NotificationScheduler.kt:74-80` |
| Move the logout flow into a `LogoutController` and call it from the drawer footer | Low | Today inline in Activity |

## Known Risks

- **Activity-level back handling** conflicts with the `BottomNavigation.setupWithNavController`
  default behavior (`MainActivity.kt:98,154-194`); any change to the nav graph could break
  the secondary-set redirect.

[UNVERIFIED] Whether any future change will introduce Hilt, which would alter DI patterns.

## Manual Verification

| Check | How | Expected |
|---|---|---|
| Auto-login happy path | Set `isEmailVerified=true`, kill app, reopen | Lands on `fragment_PgPrincipal` |
| Auto-login unverified | Set `isEmailVerified=false`, kill app, reopen | Toast + stays on `fragment_Login` |
| Double-back | On `fragment_PgPrincipal`, press back twice within 2s | Activity finishes on second press |
| Secondary back | Navigate to `fragment_Tareas`, press back | Lands on `fragment_PgPrincipal`, not Login |
| Logout from drawer | Open drawer, tap "Cerrar sesión", confirm | `tfg_prefs.grupoId` removed; back at `fragment_Login` |
| Notification intent | Trigger a reminder with `openTaskId`, tap | Lands on `fragment_Tareas` with `taskId` arg |
| Listener leak | Rotate device while notifications pending; check `logcat` for unclosed listeners | No `addSnapshotListener` warnings, no leaked `Job` warnings |
| Single auto-login navigator | Start a verified session and rotate the device during the Presentación window | Exactly one `fragment_Login` entry; lands on `fragment_PgPrincipal` |
| Observer stale-key pruning | Open the task list, then remove a task from the queries (delete/unassign) | The removed task disappears; no stale row remains |
| Adapter load after rotation | Rotate on a screen using `TareasHomeAdapter` with an uncached assignee | The assignee name resolves after rotation (not stuck on "Cargando...") |
| Adapter scope cancellation | Rotate away while an assignee-name load is in flight | No leaked job; loads are cancelled with the old view |
| Safe Args migration | `grep -rn "putString(\"taskId\"\|getString(\"taskId\"\|putString(\"categoria\"\|getString(\"categoria\"\|getString(\"modo\"" app/src/main/java` | Zero manual navigation-bundle sites for the typed arguments |
| Notification duplicate stack | While already on `fragment_Tareas`, tap a reminder notification | No duplicate `fragment_Tareas` is pushed |
| Collateral job cleanup | Rotate/recreate on the dashboard and open/close the drawer with an avatar load pending | `avatarDrawerJob` and `tareasHomeJob` are cancelled; no leaked-job warnings |
