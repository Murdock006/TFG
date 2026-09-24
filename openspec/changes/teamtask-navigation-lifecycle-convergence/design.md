# Design: TeamTask navigation/lifecycle convergence

## Technical Approach

Four independent, bounded refactors that remove lifecycle/navigation debt without changing
user-visible behavior. All are grounded in the approved delta specs:
`specs/navigation-lifecycle/spec.md` (3 MODIFIED + 3 ADDED, 21 scenarios) and
`specs/implementation-recipes/spec.md` (1 ADDED + non-requirement table updates).

| Debt | Mechanism (this design) | Spec requirement |
|---|---|---|
| **TD-4** | One Activity-owned coordinator; the Fragment **signals** message completion; the coordinator is the only caller of `Presentacion → Login`; the destination listener is stored and removed in `onDestroy` (kills the stale listener left on the retained `NavController`). | MODIFIED "Auto-login MUST require verified email…" |
| **TD-5** | Per-source key sets + one shared map with a single logical writer and union-based pruning in `observarTareas`, mirroring `observarTareasPorGrupo`. | ADDED "Task observation state MUST stay consistent…" |
| **TD-6** | Adapter owns its `SupervisorJob` scope; hosts build the adapter per view and call `destroy()` from `onDestroyView`; `findNavController()` is replaced by a host navigation callback; per-bind job cancellation. | ADDED "Adapter asynchronous work MUST use a lifecycle-safe scope owner" |
| **TD-15** | AndroidX Safe Args plugin 2.9.6; `<argument>` declarations; every writer/reader migrated to `FragmentTareasArgs` (generated typed arg class). | ADDED "Navigation arguments MUST be typed via AndroidX Safe Args" |

The change is a robustness refactor: destinations, conditions, back-stack rules, and the raw
`"openTaskId"` intent extra contract are preserved exactly. Routing/shell subprocess boundaries
do not exist here, so the threat matrix is not applicable (see below).

## Architecture Decisions

### Decision TD-4-1: Activity-owned coordinator with a Fragment completion signal

**Choice**: `MainActivity` owns a private one-way funnel
`navegarDePresentacionALogin()` that is the **only** code path that performs the
`Presentacion → Login` navigation. It is driven by two inputs:

1. The verified-session `OnDestinationChangedListener` (already present) calls the funnel when
   it observes `fragment_Presentacion`.
2. `FragmentPresentacion` calls a new public `MainActivity.onPresentacionMensajesCompletados()`
   after its three messages, instead of calling `findNavController().navigate(...)` itself.

The funnel is idempotent by a live-destination guard:
`if (navController.currentDestination?.id != R.id.fragment_Presentacion) return`.

**Alternatives considered**:
- *Fragment-signalled-only* (the Fragment decides and navigates): keeps navigation logic split
  across Fragment and Activity — the current problem shape — and leaves the verified fast-path
  uncoordinated. Rejected.
- *Move the whole timed message sequence into the Activity*: the messages render into the
  Fragment's `binding.textoPresentacion`; moving display ownership is a larger, behavior-touching
  change. Rejected.
- *Persistent one-shot boolean guard*: needs a reset when the user re-enters Presentación (back
  from Login), otherwise back-to-Presentación would no longer re-forward to Login. The
  live-destination guard needs no reset and is strictly simpler. Rejected.

**Rationale**: `MainActivity` already owns `navController` and the central back-handling
callback, so single-ownership of the transition lands in the component that already
orchestrates navigation. The Fragment keeps its visible responsibility (show the messages) and
loses only the navigate call. The live-destination guard makes every invocation — including a
stale duplicate listener firing on the same destination change — a no-op after the first
navigation updated `currentDestination` synchronously.

### Decision TD-4-2: Recreation safety = remove the listener in `onDestroy`

**Choice**: Store the auto-login listener in a field (`autoLoginListener`) and
`navController.removeOnDestinationChangedListener(...)` it in `onDestroy` (and after it fires).
Remove-then-add before registering, so one Activity instance can never register two.

**Alternatives considered**:
- *Keep removal-on-first-Presentacion only* (current behavior): insufficient. `NavHostFragment`
  is retained across configuration changes, so its `NavController` is the **same instance** on
  the new Activity. A config change during the auth window (no `configChanges` is declared in
  `AndroidManifest.xml:21-31`) leaves the old Activity's listener still registered on the shared
  controller; the new Activity registers another one → two active navigators. Rejected.
- *Guard flag reset on Presentación*: works but adds state that must be reset at a second place;
  removal in `onDestroy` plus the live-destination guard already covers it. Rejected.

**Rationale**: The listener leak on the retained `NavController` is the actual TD-4 defect;
removing it in `onDestroy` is the minimum fix and matches the delta spec's recreation-safety
invariant ("MUST NOT register more than one active navigator").

### Decision TD-5-1: Per-source pruning with one shared writer

**Choice**: Replace the three ad-hoc `combinado[it.id] = it` blocks with a single local function
`aplicar(fuente, snap)` inside `callbackFlow` that (a) copies the snapshot docs into the shared
map (single logical writer), (b) records that source's current id set, (c) prunes
`combinado.keys` to the union of all recorded id sets, and (d) emits.

```kotlin
val combinado = mutableMapOf<String, Tarea>()
val idsPorFuente = mutableMapOf<Int, Set<String>>()

fun aplicar(fuente: Int, snap: QuerySnapshot?) {
    val docs = snap?.documents?.mapNotNull { docToTarea(it) } ?: emptyList()
    docs.forEach { combinado[it.id] = it }
    idsPorFuente[fuente] = docs.map { it.id }.toSet()
    val vigentes = idsPorFuente.values.flatten().toSet()
    combinado.keys.removeAll { it !in vigentes }
    trySend(combinado.values.toList())
}
```

Each listener becomes `addSnapshotListener { snap, error -> if (error != null) { close(error); return@addSnapshotListener }; aplicar(N, snap) }`.

**Alternatives considered**:
- *Naive per-snapshot pruning of `combinado`*: wrong. A snapshot for the `creadoPor` source does
  not contain tasks that arrive only via `grupoId`; removing "keys not in this snapshot" would
  delete live rows from other sources. Rejected.
- *Typed `combine` rewrite*: explicitly not required by the delta spec ("a typed `combine`
  rewrite is not required by this change"), and larger than the debt. Rejected.

**Rationale**: The union-of-sources pruning is exactly `observarTareasPorGrupo`'s behavior
generalized to N sources — for a single source the union equals that snapshot's ids, so parity is
exact. The single `aplicar` function also gives the "single logical writer" discipline the spec
requires. The group-resolved-once limitation and the `[UNVERIFIED]` main-thread-confinement
assumption are documented in code comments at the listener registration site (no custom executor
is passed).

### Decision TD-6-1: Adapter-owned scope, per-view construction, host navigation callback

**Choice**: The constructor drops `scope: CoroutineScope` and adds `onTareaClick: (String) -> Unit`.
The adapter owns `CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())` and exposes
`fun destroy() { adapterJob.cancel() }`. Hosts create the adapter against the current view and
call `destroy()` from `onDestroyView`.

**Alternatives considered**:
- *Move loads behind the ViewModel*: the loads are view-rendering concerns (assignee display
  text, a host dialog) and there is no view-model that owns them; introducing one is a larger
  architectural change than the debt, and the proposal's bounded scope rejects it. Rejected.
- *Keep receiving the host `viewLifecycleOwner.lifecycleScope`*: that is the defect — the scope
  dies with the old view while the adapter survives, turning later loads into silent no-ops.
  Rejected.
- *`by lazy` adapter with a fresh scope*: still captures one scope for the Fragment lifetime and
  violates "build the adapter per view". Rejected.

**Rationale**: An adapter-owned `SupervisorJob` scoped to the view, paired with per-view adapter
construction (as `FragmentPgPrincipal:177` already does), makes each recreation start from a live
scope, while `onDestroyView` guarantees no job outlives the view. `Dispatchers.Main.immediate`
preserves the current threading (the previous scope was `viewLifecycleOwner.lifecycleScope`, also
main-immediate).

### Decision TD-6-2: Per-bind load cancellation

**Choice**: Add `var cargaAsignadoJob: Job? = null` to the `VH`. At the top of
`onBindViewHolder`, `holder.cargaAsignadoJob?.cancel()`; the assignee-name load assigns
`holder.cargaAsignadoJob = scope.launch { ... }` and keeps the existing
`bindingAdapterPosition == posicionActual` write guard.

**Rationale**: Recycling can start many overlapping loads for the same holder; cancelling the
previous one per bind bounds the work and removes stale writes. The navigation callback
(`onTareaClick`) replaces `fragment.findNavController()` so the adapter no longer depends on the
nav graph; the `fragment` reference is retained only for `getString`/`requireContext` (not a
scope, not navigation).

### Decision TD-15-1: Safe Args via the generated Args class, uniformly

**Choice**: Apply `androidx.navigation.safeargs.kotlin` 2.9.6 (matching `navigationFragment`
2.9.6). Declare `taskId`, `modo`, `categoria` as nullable string arguments on `fragment_Tareas`.
Every current site navigates with `navigate(R.id.fragment_Tareas, …)` (no action is used today),
so the migration uses the generated **`FragmentTareasArgs`** at every site rather than per-action
directions classes. All files that need it are in `com.example.tfg.vista`, the same package Safe
Args generates into, so no imports are added.

**Alternatives considered**:
- *Add `<action>`/global actions to obtain directions classes*: changes the graph beyond the
  spec's `<argument>` scope and is unnecessary because no site uses an action today. Rejected.
- *Hand-written typed bundle wrapper*: explicitly rejected by the proposal as bespoke; kept only
  as a contingency (see Fallback below). Rejected as the primary mechanism.

**Rationale**: `FragmentTareasArgs` is the generated, typed argument accessor; using it uniformly
keeps the exact navigation mechanism (destination-id navigate) while eliminating every raw key
literal. Safe Args generates `Args` classes for destinations that declare arguments even when no
action targets them.

### Decision TD-15-2: Fallback if the plugin cannot resolve (contingency)

**Choice**: If `androidx.navigation.safeargs.kotlin:2.9.6` cannot be applied or generated on AGP
9.3.3 / Kotlin 2.2.10 `[UNVERIFIED]` (surfaced by the compile gate), the fallback is a single
hand-written key-owner object:

```kotlin
object TareasNavKeys {
    const val TASK_ID = "taskId"
    const val MODO = "modo"
    const val CATEGORIA = "categoria"
    fun bundle(taskId: String? = null, modo: String? = null, categoria: String? = null) =
        Bundle().apply { /* only place raw keys appear */ }
    fun taskId(b: Bundle?): String? = b?.getString(TASK_ID)
    fun modo(b: Bundle?): String? = b?.getString(MODO)
    fun categoria(b: Bundle?): String? = b?.getString(CATEGORIA)
}
```

**Caveat**: the delta spec requires the plugin ("MUST be applied and version-matched"). The
fallback is **only** valid if the plugin is genuinely unresolvable **and** the delta spec is
amended; it is not a silent substitution. It centralizes the keys (no scattered literals) but does
not meet the spec's plugin letter.

**Rationale**: Records a bounded contingency without changing the primary plan. The compile gate
is the deciding evidence.

### Decision TD-4-3: Notification duplicate-stack — fix now, proven safe by construction

**Choice**: Fix in `MainActivity.handleOpenTaskId` by applying `popUpTo(fragment_Tareas, true)`
**only when `navController.currentDestination?.id == fragment_Tareas`**; otherwise navigate
exactly as today. `NotificationScheduler.kt` stays unchanged.

```kotlin
val args = FragmentTareasArgs(taskId = taskId, modo = null, categoria = null).toBundle()
if (navController.currentDestination?.id == R.id.fragment_Tareas) {
    val opciones = NavOptions.Builder().setPopUpTo(R.id.fragment_Tareas, true).build()
    navController.navigate(R.id.fragment_Tareas, args, opciones)
} else {
    navController.navigate(R.id.fragment_Tareas, args)
}
```

**Rationale**: `popUpTo` is used only when the destination is the current one, so it is
guaranteed to be on the back stack — the "not found / no-op" uncertainty of applying it blindly
is avoided. Result: already on `fragment_Tareas` → the existing entry is popped (inclusive) and
one fresh entry with the new `taskId` is pushed → no duplicate and the detail refreshes. Not on
`fragment_Tareas` → unchanged behavior. Because it can be shown safe, the proposal's "or record
as follow-up" branch is not taken; the delta spec's conditional is satisfied by the fix.

## Data Flow

### TD-4 — Presentación → Login, single coordinator

```
onCreate ──► verificarSesionActiva() ──► (verified session?) ──► register autoLoginListener (field)
                                                   │
fragment_Presentacion reached ─────────────────────┤
                                                   ▼
                                   navegarDePresentacionALogin()   ◄── FragmentPresentacion
                                   guard: live currentDestination == Presentacion      (after 3 messages)
                                                   │
                                                   ▼
                                   action_fragment_Presentacion_to_fragment_Login
                                                   │
                                   binding.root.post { if (currentDestination == Login)
                                       action_fragment_Login_to_fragment_PgPrincipal }
onDestroy ──► remove autoLoginListener (kills stale listener on retained NavController)
```

### TD-5 — observer pruning (N sources, one map)

```
creadoPor  snapshot ─┐
asignadoA  snapshot ─┼─► aplicar( fuente, snap )
grupoId    snapshot ─┘        │  combinado[id] = tarea          (single writer)
                              │  idsPorFuente[fuente] = ids     (per-source truth)
                              │  pruna: combinado.keys ⊆ ⋃ idsPorFuente
                              ▼
                        trySend(combinado.values.toList())
```

### TD-6 — adapter scope ownership

```
Fragment.onViewCreated ──► adapter = TareasHomeAdapter(host, parejaVM, tareasVM, onTareaClick)
                                  │  owns CoroutineScope(Main.immediate + SupervisorJob)
Fragment.onDestroyView ──► tareasHomeJob.cancel(); adapter.destroy()   → scope cancelled
```

## File Changes

| File | Action | Description |
|---|---|---|
| `app/src/main/java/com/example/tfg/vista/MainActivity.kt` | Modify | Add `autoLoginListener` field + `navegarDePresentacionALogin()` funnel; remove listener in `onDestroy`; cancel `avatarDrawerJob` in `onDestroy`; `onPresentacionMensajesCompletados()`; Safe Args in `handleOpenTaskId`; conditional `popUpTo` duplicate-stack fix |
| `app/src/main/java/com/example/tfg/vista/FragmentPresentacion.kt` | Modify | Replace `findNavController().navigate(...)` with `(activity as? MainActivity)?.onPresentacionMensajesCompletados()`; drop the `findNavController` import |
| `app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` | Modify | Drop `scope` constructor param; add `onTareaClick: (String) -> Unit`; own `SupervisorJob` scope + `destroy()`; per-bind `cargaAsignadoJob` cancel; remove `findNavController()` |
| `app/src/main/java/com/example/tfg/vista/FragmentTareasPendientes.kt` | Modify | Replace `by lazy` adapter with a per-view field built in `onViewCreated`; pass nav callback; call `adapter?.destroy()` in `onDestroyView` |
| `app/src/main/java/com/example/tfg/vista/FragmentPgPrincipal.kt` | Modify | Promote adapter to a field built per view with nav callback; Safe Args for the six category buttons and `setupCategoriaAsignacion`; add `onDestroyView` cancelling `tareasHomeJob` + `adapter.destroy()` |
| `app/src/main/java/com/example/tfg/vista/FragmentTareas.kt` | Modify | Read `FragmentTareasArgs` instead of raw keys at `:56,:57,:78,:155,:205,:226,:397`; add bounded `onDestroyView` clearing `listaBinding`/`crearBinding` |
| `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` | Modify | `observarTareas`: per-source id sets + single-writer `aplicar()` + union pruning; document threading/once-resolved assumptions |
| `app/src/main/res/navigation/nav_graph.xml` | Modify | Declare `<argument>` `taskId`, `modo`, `categoria` (nullable string, `@null` default) on `fragment_Tareas` |
| `gradle/libs.versions.toml` | Modify | Add `[plugins] navigation-safeargs = { id = "androidx.navigation.safeargs.kotlin", version.ref = "navigationFragment" }` |
| `app/build.gradle.kts` | Modify | `alias(libs.plugins.navigation.safeargs)` in the `plugins` block |
| `docs/architecture/TEAMTASK_GUIDE.md` | Modify | §6 TD-4/5/6/15 → resolved, §7 step 6 → done, §9 change status (apply phase) |
| `app/src/main/java/com/example/tfg/service/NotificationScheduler.kt` | Unchanged | Duplicate-stack fix lives in `MainActivity.handleOpenTaskId`; existing `CLEAR_TOP \| SINGLE_TOP` PendingIntent flags are sufficient |

## Interfaces / Contracts

```kotlin
// MainActivity — the single Presentación→Login owner (public signal + private funnel)
fun onPresentacionMensajesCompletados()            // called by FragmentPresentacion after messages
private fun navegarDePresentacionALogin()          // the ONLY caller of the Presentación→Login action
   // guard: navController.currentDestination?.id == R.id.fragment_Presentacion

// TareasHomeAdapter — new constructor (no CoroutineScope; host navigation callback)
class TareasHomeAdapter(
    private val fragment: Fragment,                // resources/context only (not a scope, not nav)
    private val parejaVM: ParejaViewModel,
    private val tareasVM: TareasViewModel,
    private val onTareaClick: (String) -> Unit
) : ListAdapter<Tarea, TareasHomeAdapter.VH>(TareaDiffCallback()) {
    fun destroy()                                  // cancels the adapter-owned SupervisorJob
}

// FragmentTareas — typed argument reader
private val navArgs: FragmentTareasArgs?
    get() = arguments?.let { FragmentTareasArgs.fromBundle(it) }
// usages: navArgs?.modo ?: "lista" | navArgs?.taskId | navArgs?.categoria

// nav_graph.xml fragment_Tareas arguments (all nullable)
<argument android:name="taskId"    app:argType="string" app:nullable="true" android:defaultValue="@null"/>
<argument android:name="modo"      app:argType="string" app:nullable="true" android:defaultValue="@null"/>
<argument android:name="categoria" app:argType="string" app:nullable="true" android:defaultValue="@null"/>
```

### TD-15 site migration (exact before/after)

All writers currently call `navigate(R.id.fragment_Tareas, bundle)`; the generated
`FragmentTareasArgs` is constructed with all three named parameters (Java-generated constructors
do not support default arguments, so pass explicit `null`s).

| Site | Before | After |
|---|---|---|
| `MainActivity.kt:225-226` | `Bundle().apply { putString("taskId", taskId) }` + `navigate(R.id.fragment_Tareas, bundle)` | `FragmentTareasArgs(taskId = taskId, modo = null, categoria = null).toBundle()` (plus conditional `popUpTo`) |
| `TareasHomeAdapter.kt:272-279` | `Bundle().apply { putString("taskId", t.id) }` + `fragment.findNavController().navigate(...)` | `onTareaClick(t.id)`; the host lambda builds `FragmentTareasArgs(taskId = id, modo = null, categoria = null).toBundle()` |
| `FragmentPgPrincipal.kt:78-99` | six `Bundle().apply { putString("categoria", "cocina"\|…) }` | `FragmentTareasArgs(taskId = null, modo = null, categoria = "cocina"\|…)` |
| `FragmentPgPrincipal.kt:312` | `Bundle().apply { putString("modo","crear"); putString("categoria","Personalizada") }` | `FragmentTareasArgs(taskId = null, modo = "crear", categoria = "Personalizada")` |
| `FragmentPgPrincipal.kt:315` | `Bundle().apply { putString("categoria", categoriaId) }` | `FragmentTareasArgs(taskId = null, modo = null, categoria = categoriaId)` |
| `FragmentTareas.kt:56` | `arguments?.getString("modo") ?: "lista"` | `navArgs?.modo ?: "lista"` |
| `FragmentTareas.kt:57` | `arguments?.getString("taskId")` | `navArgs?.taskId` |
| `FragmentTareas.kt:78,:155,:226` | `arguments?.getString("categoria")` | `navArgs?.categoria` |
| `FragmentTareas.kt:205,:397` | `arguments?.getString("taskId")` | `navArgs?.taskId` |

## Testing Strategy

No automated safety net exists (`openspec/config.yaml: strict_tdd: false`; roadmap step 5
tests are pending). Layers are therefore:

| Layer | What to Test | Approach |
|---|---|---|
| Unit | _None available_ | No test suite exists; adding one is roadmap step 5 (out of scope). `./gradlew test` would compile/run zero tests. |
| Integration | _None available_ | No emulator/device in this run; no integration harness. |
| E2E / Manual | The 21 spec scenarios | Documented manual matrix below; executed by the apply/verify phases on a device. |

## Verification Plan

### 1. Compile gate (authoritative)

```
.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain
```

- Compiles both flavors and proves Safe Args code generation resolved (or surfaces the plugin
  failure → Fallback decision TD-15-2).
- `[UNVERIFIED]` until run: plugin compatibility with AGP 9.3.3 / Kotlin 2.2.10.

### 2. Focused JVM checks

None available without adding test infrastructure (out of scope). Not attempted; recorded
honestly rather than faked.

### 3. Grep audits

| Audit | Command | Expected |
|---|---|---|
| No external scope param | `grep -rn "TareasHomeAdapter(" app/src/main/java` | Both call sites pass `(this, parejaVM, tareasVM) { … }` — no `CoroutineScope` |
| Adapter scope is internal-only | `grep -n "CoroutineScope" app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` | Only the adapter-owned `adapterJob`/scope |
| No raw navigation keys | `grep -rn "putString(\"taskId\"\|getString(\"taskId\"\|putString(\"categoria\"\|getString(\"categoria\"\|putString(\"modo\"\|getString(\"modo\"" app/src/main/java` | Zero matches |
| Jobs cancelled | `grep -rn "avatarDrawerJob\|tareasHomeJob\|\.destroy()" app/src/main/java/com/example/tfg/vista` | `avatarDrawerJob` cancelled in `onDestroy`; `tareasHomeJob` + adapter `destroy()` in `onDestroyView` |
| Intent contract intact | `grep -rn "openTaskId" app/src/main/java` | Still a raw `getStringExtra`/`putExtra`, never a nav argument |
| No manual fragment nav in adapter | `grep -rn "findNavController" app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` | Zero matches |
| Uncertainty markers | `grep -r "\[UNVERIFIED\]" openspec/` | New assumptions marked |

### 4. Manual matrix → 21 spec scenarios

No device/emulator is available in this run, so every row is **planned, not executed here**
(apply/verify run them).

| # | Spec scenario | Method | Expected |
|---|---|---|---|
| 1 | Logged in, email not verified | Login with unverified account | `signOut()` + Toast; stays on `fragment_Login` |
| 2 | Logged in, email verified | Login with verified account | Group loads; `Presentacion → Login → PgPrincipal` once; no second Login |
| 3 | Config change during auth window | Rotate during the window (verified) | Exactly one `fragment_Login` entry; no double navigator |
| 4 | Presentación message sequence preserved | Start unauthenticated | Three messages in order; single transition to Login |
| 5 | ViewModel-owned group listener | Change group / clear VM | Previous collector cancelled first |
| 6 | Activity-owned notifications listener | Destroy / logout | `notificacionesJob` cancelled; uid reset |
| 7 | Activity-owned avatar job | Rotate/recreate with avatar load pending | `avatarDrawerJob` cancelled; no load on destroyed activity |
| 8 | Fragment-owned recent-tasks job | Destroy `FragmentPgPrincipal` view | `tareasHomeJob` cancelled in `onDestroyView` |
| 9 | Notification with `openTaskId` | Tap notification | Navigates to `fragment_Tareas` with typed `taskId`; detail reads it |
| 10 | Notification while on `fragment_Tareas` | Tap notification on that screen | No duplicate `fragment_Tareas` entry |
| 11 | Stale key is pruned | Delete/unassign a task in a query | Removed task disappears from the list |
| 12 | Cross-listener duplicate once | Task matching `creadoPor` + `grupoId` | Appears exactly once |
| 13 | Group resolved once documented | Read `observarTareas` | Limitation documented in code |
| 14 | Threading assumption explicit | Read `observarTareas` | `[UNVERIFIED]` main-thread assumption stated |
| 15 | Loads survive view recreation | Rotate with uncached assignee | Name resolves after rotation (not stuck "Cargando…") |
| 16 | Loads cancelled with the view | Rotate while a load is in flight | No leaked job; scope cancelled with the old view |
| 17 | No external scope in constructor | Read constructor | No `CoroutineScope` parameter |
| 18 | Navigation decoupled | Read adapter navigate path | Host callback used; no `findNavController()` |
| 19 | No manual bundle sites remain | Grep audit (table 3) | Zero raw key sites |
| 20 | Arguments are declared | Inspect `nav_graph.xml` | `taskId`, `modo`, `categoria` `<argument>` present |
| 21 | `openTaskId` contract unchanged | Read notification path | Still a raw intent extra, not a nav argument |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary exists in this change. It is an in-process Android lifecycle/
navigation refactor.

## Migration / Rollout

- **No data migration.** The observer fix changes in-memory key handling only; no Firestore
  document is rewritten.
- **No feature flag.** The change lands as direct commits to `master` (solo developer, no PRs).
- Apply order is independent per debt: TD-5 (repository) is isolated from TD-4/TD-6/TD-15.
  TD-15 touches the same navigation call sites as TD-4/TD-6, so those three are best applied as
  one work unit to avoid intermediate compile states.

## Risks and Rollback Boundary

| Risk | Likelihood | Mitigation |
|---|---|---|
| Behavior regression with no test net | High | Preserve destinations/conditions/back rules exactly; compile + grep audits + manual matrix |
| Safe Args plugin incompatible with AGP 9.3.3 / Kotlin 2.2.10 `[UNVERIFIED]` | Med | Compile gate first; documented Fallback (TD-15-2) requiring a spec amendment |
| Stale auto-login listener still leaks | Low | Remove in `onDestroy` **and** live-destination guard in the funnel (defense-in-depth) |
| `popUpTo` duplicate-stack fix changes back behavior | Low | Applied only when `fragment_Tareas` is the current destination (provably on the back stack); otherwise unchanged |
| Per-source pruning deletes live rows | Low | Prune to the **union** of all source id sets, never a single snapshot |
| `[UNVERIFIED]` Firestore threading assumption wrong | Low | Pruning is correct under the main-thread assumption; the assumption is stated, not silent |
| Scope creep into broad binding-null refactors | Med | Only `FragmentTareas`' own two bindings are cleared (bounded to one touched fragment); everything else is a follow-up |

**Rollback boundary**: `git revert` of this change's commit(s) restores the original auto-login
paths, the pre-pruning `observarTareas`, the original adapter scope/navigation coupling, and every
manual bundle site; the same revert removes the Safe Args plugin entry and the `nav_graph.xml`
`<argument>` declarations. No persisted data changes, so no data rollback. TD-4/TD-6/TD-15 and
TD-5 are independent and can be reverted separately.

## Open Questions

- [ ] `[UNVERIFIED]` Safe Args 2.9.6 compatibility with AGP 9.3.3 / Kotlin 2.2.10 — resolved by
      the compile gate; if it fails, apply Fallback TD-15-2 (which requires amending the delta
      spec's "plugin MUST be applied" clause).
- [ ] Whether `FragmentPgPrincipal`'s promoted adapter field warrants a full `_binding`-null
      `onDestroyView`. **Decided: no** — cancel jobs/scope only; a binding-null refactor is out of
      scope. Flagged for reviewer awareness.
- [ ] Whether Safe Args generates convenience overloads for defaulted arguments. **Decided: do
      not rely on them** — pass all three named parameters at every site.
