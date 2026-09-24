# Exploration: TeamTask navigation/lifecycle convergence

## Summary

This change is convergence roadmap step 6 (`docs/architecture/TEAMTASK_GUIDE.md:175`,
`openspec/specs/implementation-recipes/spec.md:227`) and resolves debt items **TD-4**,
**TD-5**, **TD-6**, and **TD-15** (`docs/architecture/TEAMTASK_GUIDE.md:148-159`;
`openspec/specs/implementation-recipes/spec.md:205-216`). It is a robustness/convergence
refactor, not a behavior redesign: the current user-visible navigation behavior must be
preserved.

This is a read-only exploration. No application code, Gradle files, canonical specs, or git
state were modified. The full exploration is mirrored in Engram under topic
`sdd/teamtask-navigation-lifecycle-convergence/explore` (observation #147, project
`TFG-TeamTask`).

**Line drift warning:** the canonical `navigation-lifecycle` spec cites older line numbers
(e.g. `MainActivity.kt:255-275`, `TareaRepositorioFirebase.kt:184-212`,
`TareasHomeAdapter.kt:39,145-210`). The current truth is recorded below
(`MainActivity.kt:257-277`, `TareaRepositorioFirebase.kt:186,190-213`,
`TareasHomeAdapter.kt:38,144-235`). The spec phase MUST re-verify every `file:line` against
the source before writing deltas.

## TD-4 — duplicate auto-login / Presentación→Login navigation

Two independent navigators drive the same Presentación→Login transition:

- `MainActivity.verificarSesionActiva()` (`MainActivity.kt:232-286`) registers a one-shot
  `OnDestinationChangedListener` (`MainActivity.kt:257-277`). When the destination is
  `fragment_Presentacion` it navigates to Login (`:266`), then posts a frame later to
  navigate Login→PgPrincipal if the current destination is Login (`:268-273`), and only then
  removes itself (`:274`). It is called from `onCreate` (`:94`).
- `FragmentPresentacion.mostrarMensajes()` (`FragmentPresentacion.kt:39-57`) runs its own
  delayed navigation after three 2 s messages (`:45-52`).

`AndroidManifest.xml:21-31` declares no `configChanges` for `MainActivity`, so any
configuration change recreates the Activity and re-runs `verificarSesionActiva()` (fresh
listener) while `FragmentPresentacion` restarts its handler (`onViewCreated` → `:36`). The
listener's self-removal only happens after it observes `fragment_Presentacion` (`:274`), so a
recreation during the window can leave two navigators racing into Login. Logout is separate
and already explicit (`MainActivity.kt:576-601`).

Evidence: `MainActivity.kt:94,232-286`; `FragmentPresentacion.kt:36,39-57`;
`AndroidManifest.xml:21-31`; spec drift: canonical cites `MainActivity.kt:255-275`.

## TD-5 — `observarTareas` shared-map consistency

`TareaRepositorioFirebase.observarTareas()` (`TareaRepositorioFirebase.kt:163-214`) opens up
to three Firestore snapshot listeners (`:188`, `:194`, `:202`) that all write into one shared
`combinado = mutableMapOf<String, Tarea>()` (`:186`) and emit `combinado.values.toList()`.

- No stale-key pruning: entries are only ever added (`:190`, `:196`, `:204`), never removed
  when a document leaves a query snapshot. `observarTareasPorGrupo()` already prunes
  (`:224-225`), so the two observers are inconsistent.
- No synchronization around the shared map. Firestore snapshot listeners are invoked on the
  main thread by default (no custom executor is passed), so the [UNVERIFIED] latent race is a
  main-thread-confinement assumption rather than an observed concurrent-write bug; the
  likely real defect is stale-key accumulation.
- The group is resolved once at flow start (`:171-183`); a group change while the flow is
  active is not re-resolved (limitation to document).

`awaitClose` removes all three listeners (`:209-213`). Consumers: `FragmentTareas.kt:189`,
`FragmentTareasPendientes.kt:114`, `FragmentCalendario.kt:106`.

Evidence: `TareaRepositorioFirebase.kt:163-214` (shared map `:186`, listeners
`:188,194,202`, `awaitClose` `:209-213`), parity target `:216-229` (pruning `:224-225`);
spec drift: canonical cites `:184-212`.

## TD-6 — `TareasHomeAdapter` external `CoroutineScope`

`TareasHomeAdapter` takes a host `Fragment` and an external `CoroutineScope` in its
constructor (`TareasHomeAdapter.kt:34-39`) and launches work on it (`:144`, `:177`, `:197`).

- In `FragmentTareasPendientes.kt:29` the adapter is `by lazy { ... viewLifecycleOwner.lifecycleScope }`.
  Because the adapter instance survives view recreation while the captured scope does not,
  after a rotation the scope is cancelled and `scope.launch { ... }` becomes a silent no-op
  (assignee-name resolution and the assign dialog stop working).
- The adapter also reaches navigation directly: `fragment.findNavController().navigate(...)`
  (`TareasHomeAdapter.kt:272-279`), coupling the adapter to the Fragment and its nav graph.
- `FragmentPgPrincipal.kt:177` constructs the adapter with `viewLifecycleOwner.lifecycleScope`
  on each view creation, so it is less exposed than the `by lazy` site but still relies on an
  externally owned scope.

Evidence: `TareasHomeAdapter.kt:34-39,144,177,197,272-279`; `FragmentTareasPendientes.kt:29`;
`FragmentPgPrincipal.kt:177`; spec drift: canonical cites `TareasHomeAdapter.kt:39,145-210`.

## TD-15 — manual navigation bundles

Manual `Bundle`/`putString` sites and readers, with the intent extra `"openTaskId"` as a
separate contract:

| Kind | Location | Keys |
|---|---|---|
| Writer | `MainActivity.kt:225-226` | `"taskId"` |
| Writer | `TareasHomeAdapter.kt:274-275` | `"taskId"` |
| Writer | `FragmentPgPrincipal.kt:78-99` | `"categoria"` (six category buttons) |
| Writer | `FragmentPgPrincipal.kt:312-316` | `"modo"`, `"categoria"` |
| Reader | `FragmentTareas.kt:56` | `"modo"` |
| Reader | `FragmentTareas.kt:57,205,397` | `"taskId"` |
| Reader | `FragmentTareas.kt:78,155,226` | `"categoria"` |
| Intent extra | `MainActivity.kt:133,206`; `NotificationScheduler.kt:74-80` | `"openTaskId"` |

No Safe Args plugin is present (`app/build.gradle.kts:1-5`, `gradle/libs.versions.toml`
`plugins` block `:37-39`), and `nav_graph.xml:1-98` declares no `<argument>` elements. Typo
in a raw key fails silently. The `"openTaskId"` intent extra contract must stay unchanged.

Evidence: sites and readers above; `nav_graph.xml:1-98`; `app/build.gradle.kts:1-5`;
`gradle/libs.versions.toml:37-39`.

## Directly related lifecycle cleanup (touched flows)

- `MainActivity.avatarDrawerJob` is declared (`MainActivity.kt:56`) and reassigned
  (`:486-500`) but `onDestroy` only cancels `notificacionesJob` (`:216-221`), so the avatar
  job is never cancelled.
- `FragmentPgPrincipal.tareasHomeJob` (`:46`) is cancelled/reassigned in the group collector
  (`:184-185`, `:197-215`) but `FragmentPgPrincipal` has no `onDestroyView`, so the job and
  the binding outlive the view.
- `FragmentTareas` stores `listaBinding`/`crearBinding` (`:43-44`) and has no `onDestroyView`
  (file ends at `:910`), unlike `FragmentTareasPendientes.kt:224-228` and
  `FragmentCalendario.kt:337-338`.
- Notification `taskId` duplicate stack: `NotificationScheduler.showImmediateNotification`
  builds a `PendingIntent` with `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`
  (`NotificationScheduler.kt:74-80`) so the existing Activity is reused and
  `onNewIntent` → `handleOpenTaskId` runs (`MainActivity.kt:204-209,223-230`). That
  `navController.navigate(fragment_Tareas, bundle)` (`:226`) has no `popUpTo`/single-top, so
  tapping the notification while already on `fragment_Tareas` pushes a duplicate destination.
- Back handling is centralized in an Activity `OnBackPressedCallback`
  (`MainActivity.kt:156-196`) alongside `setupWithNavController` (`:100`); the declared
  behavior (close drawer → double-back on PgPrincipal → secondary redirect → pop/finish) is
  the contract and must not change.

## Constraints and non-goals

- No automated test net exists; `strict_tdd: false` (`openspec/config.yaml:6`). Verification
  is compile + reference audit + manual matrix.
- Out of scope: LiveData→StateFlow migration, broad binding-null refactors, account deletion
  (TD-13), TD-7, avatar/Storage (TD-2/TD-3), rules/indexes (TD-9), behavior redesign of back
  handling, and introducing Hilt/Room/Retrofit/Compose.

## Open decisions

Technical, not product-level:

1. Safe Args plugin vs a hand-written typed bundle equivalent (`navigation-lifecycle` "Future
   Convergence Work" suggests Safe Args, `spec.md:186`).
2. The exact ownership split for the single Presentación→Login coordinator (Activity-owned
   listener vs Fragment-signalled) that preserves the current destination sequence.
3. Whether the notification duplicate-stack fix is in scope now or recorded as a follow-up.

These are resolved or bounded in `proposal.md`; the surviving mechanism choices belong to the
design phase.
