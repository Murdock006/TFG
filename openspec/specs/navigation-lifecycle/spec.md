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
| Auto-login | `verificarSesionActiva()` checks `FirebaseAuth.currentUser`, requires `isEmailVerified`, loads `ParejaViewModel.cargarGrupoPorUsuario`, then chains Presentacion → Login → PgPrincipal via a one-shot `OnDestinationChangedListener` | `MainActivity.kt:91,230-283` |
| Back | `OnBackPressedCallback` (1) closes drawer, (2) on `PgPrincipal` requires double-back within `DOUBLE_BACK_TIMEOUT_MS=2000L`, (3) on a secondary set navigates explicitly to `PgPrincipal`, else `popBackStack`/finish | `MainActivity.kt:154-194`; `Constants.kt:6` |
| Open task from notification intent | `intent.getStringExtra("openTaskId")` → `navigate(fragment_Tareas, bundle)` | `MainActivity.kt:130-133,202-228` |
| Logout | `repositorioAuth.logout()` + `tfg_prefs.remove("grupoId")` + `cancel` notifications job + `popUpTo(fragment_Presentacion, inclusive=true)` + `navigate(fragment_Login)` | `MainActivity.kt:571-595` |
| Notification system prompt | API 33+ requests `POST_NOTIFICATIONS`; granted/denied shows Toast | `MainActivity.kt:336-361,57-65` |
| Drawer | `setupDrawer` wires `topAppBar.setNavigationOnClickListener`, item clicks, and `refrescarHeaderDrawer` on open; logout lives in the footer's `NavigationView` | `MainActivity.kt:363-428` |
| Drawer header | `observarUsuarioDrawerHeader` runs in `lifecycleScope.launch { repeatOnLifecycle(STARTED) { LocalizadorServicios.repositorioAuth.observarUsuarios().collect { refrescarHeaderDrawer() } } }`; avatar path resolved from `tfg_prefs.avatar_path_<uid>` | `MainActivity.kt:430-508` |

### Listener ownership and cancellation

| Component | Where the listener lives | Cancellation |
|---|---|---|
| `TareaRepositorioFirebase.observarTareas` | `callbackFlow` opening 1-3 `addSnapshotListener` (creadoPor, asignadoA, grupoId) combined in a `mutableMapOf`; `awaitClose` removes all three | `awaitClose { subCreado.remove(); subAsignado.remove(); subGrupo?.remove() }` (`TareaRepositorioFirebase.kt:162-213`) |
| `TareaRepositorioFirebase.observarTareasPorGrupo` | one `addSnapshotListener` + `removeAll` on stale keys | `awaitClose { sub.remove() }` (`TareaRepositorioFirebase.kt:215-228`) |
| `AuthRepositorioFirebase.observarUsuarios` | `callbackFlow` over `usuarios` collection; cache refreshes `_usuarioCache` for current uid | `awaitClose { listener.remove() }` (`AuthRepositorioFirebase.kt:386-422`) |
| `RepositorioPareja.observarGrupos` / `observarGrupoPorId` | `callbackFlow` with `addSnapshotListener` | `awaitClose { sub.remove() }` (`RepositorioPareja.kt:169-191`) |
| `RepositorioNotificaciones.observarNotificaciones` | `callbackFlow` over `whereEqualTo("destinatario", uid)` | `awaitClose { sub.remove() }` (`RepositorioNotificaciones.kt:35-43`) |
| `MainActivity.notificacionesJob` | Activity-scoped `lifecycleScope.launch`; re-armed on `onResume`; cancelled in `onDestroy` | `MainActivity.kt:53,128,211-219,300,583-585` |
| `ParejaViewModel.grupoObserverJob` | `viewModelScope.launch`; cancelled on group change and `onCleared` | `ParejaViewModel.kt:42-43,83-105,344-350` |
| `TareasHomeAdapter` external scope | Adapter receives a `CoroutineScope` from the fragment (scope ownership not visible inside the adapter) | `TareasHomeAdapter.kt:39,145-210` |

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
remain on the auth flow.

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
- AND it MUST chain `Presentacion → Login → PgPrincipal` only after the destination
  listener fires
- Evidence: `MainActivity.kt:248-275`

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
or on the next `onResume` if the user changes.

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

### Requirement: Notification intent extras MUST route to the task

The system MUST read `openTaskId` from the launching intent and navigate to
`fragment_Tareas` with a `taskId` bundle argument. The handler MUST run on `onCreate`
and on `onNewIntent`.

#### Scenario: User taps a notification with `openTaskId`

- GIVEN a `PendingIntent` carries `Intent.putExtra("openTaskId", taskId)`
- WHEN the activity is launched (or relaunched)
- THEN the activity MUST navigate to `fragment_Tareas` with `bundle.putString("taskId", taskId)`
- AND the destination fragment MUST be able to read the `taskId` argument
- Evidence: `MainActivity.kt:130-133,202-228`; `NotificationScheduler.kt:74-80`; `FragmentTareas.kt:56-72`

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

## Future Convergence Work

| Work item | Severity | Notes |
|---|---|---|
| Encode the `PgPrincipal` double-back and secondary-set redirect in the nav graph (avoid Activity-level `OnBackPressedCallback`) | Med | Today centralized in Activity; risk if a Fragment overrides back |
| Document the `openTaskId` contract in a single place and share with `NotificationScheduler` | Med | Today duplicated across `MainActivity.kt:130,202` and `NotificationScheduler.kt:74-80` |
| Move the logout flow into a `LogoutController` and call it from the drawer footer | Low | Today inline in Activity |
| Replace ad-hoc `mutableMapOf` merge in `TareaRepositorioFirebase.observarTareas` with a typed `combine` | Med | Race window between listeners and group change (`TareaRepositorioFirebase.kt:184-212`) |
| Pass an explicit `CoroutineScope` to `TareasHomeAdapter` or refactor to suspend | Low | `TareasHomeAdapter.kt:39,145-210` |
| Add `androidx.navigation:navigation-safe-args` | Low | Manual bundle keys (`taskId`, `modo`, `categoria`) are error-prone |

## Known Risks

- **Activity-level back handling** conflicts with the `BottomNavigation.setupWithNavController`
  default behavior (`MainActivity.kt:98,154-194`); any change to the nav graph could break
  the secondary-set redirect.
- **`TareaRepositorioFirebase.observarTareas` listener race** between `grupoId` lookup and
  snapshot listener registration (`TareaRepositorioFirebase.kt:170-206`); group change while
  the flow is active may leave stale entries.
- **One-shot destination listener in `verificarSesionActiva`** is registered but only
  removed after the first `fragment_Presentacion` destination hit; on rapid config
  changes the listener may fire twice (`MainActivity.kt:255-275`).
- **Manual bundle keys** (`taskId`, `modo`, `categoria`) are read in
  `FragmentTareas.onCreateView` (`FragmentTareas.kt:56-72`); typos in keys fail silently.
- **External `CoroutineScope` in `TareasHomeAdapter`** has no obvious ownership and can outlive
  the Fragment view.

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
