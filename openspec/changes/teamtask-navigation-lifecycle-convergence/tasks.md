# Tasks: TeamTask navigation/lifecycle convergence

Robustness/convergence refactor that resolves **TD-4** (duplicate auto-login navigation),
**TD-5** (task-observer state consistency), **TD-6** (adapter-owned external `CoroutineScope`),
and **TD-15** (manual navigation bundles). Destinations, back-stack rules, the verified-session
condition, and the raw `"openTaskId"` intent extra are preserved exactly.

Source of truth: `design.md` (7 decisions, file-level contract, verification plan),
`proposal.md`, and the two delta specs
(`specs/navigation-lifecycle/spec.md`, `specs/implementation-recipes/spec.md`).
Every `file:line` below was re-verified against the design; if implementation finds drift,
re-verify against the current source before editing.

## Review Workload Forecast

> **Informational only.** This project is a solo-developer repository that commits directly to
> `master`; **no pull requests are opened**. The slice/chain fields below are recorded solely to
> satisfy the downstream guard contract and MUST NOT be read as a request to open, split, chain,
> or size-exception any PR. No PR slicing, chain strategy, or `size:exception` decision applies.

| Field | Value |
|-------|-------|
| Estimated changed lines | ~300–450 (`additions + deletions`, generated code excluded) |
| 400-line budget risk | Medium (borderline; bundle-site migration counts both deletes and adds) |
| Chained PRs recommended | No (no-PR delivery; kept as informational) |
| Suggested split | Two independently revertible commit sets — **A** (TD-5) and **B** (navigation unit) — plus a docs commit |
| Delivery strategy | auto-chain |
| Chain strategy | pending (not applicable: direct-to-master, no PRs) |

```text
Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium
```

Rationale: `auto-chain` resolves the decision gate to `No`. Because delivery is direct-to-master,
the "chained PR" machinery is neutralized; the value it still carries is the **work-unit and
rollback shape** below, which keeps each part independently revertible.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| A (commit set A) | TD-5 observer convergence | N/A — direct commit A | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` (4 tests, 0 failures) | Manual scenarios 11–14 (delete/unassign a task; cross-listener duplicate; read-once limitation; threading assumption) — device required, N/A in automation | `TareaRepositorioFirebase.kt` only; reverts with no navigation impact |
| B (commit set B) | Navigation unit: Safe Args plugin/sites + TD-6 adapter + TD-4 coordinator + collateral cleanup | N/A — direct commit B | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` | Manual matrix scenarios 1–10, 15–21 — device required, N/A in automation | All navigation/lifecycle files (Phases 2–6); independent of TD-5 |
| C (commit set C) | Guide §6/§7/§9 status | N/A — direct commit C | `git diff --stat -- docs/architecture/TEAMTASK_GUIDE.md` | Doc review only (no runtime) | `docs/architecture/TEAMTASK_GUIDE.md` only |

Unit B edits the same files as Unit A only in none — no overlap; the two are independent and
revertible separately (design "Migration / Rollout"). Unit B's sub-parts (TD-4/TD-6/TD-15/
cleanup) touch overlapping files, so they land together to avoid intermediate compile states.

## Apply Order & Dependency Graph

```
Phase 1 (TD-5, commit set A) ── independent ──────────────────────────────┐
                                                                           │
Phase 2 (Safe Args foundation) ──► Phase 3 (TD-6 adapter) ──► Phase 4 (TD-4 coordinator) ──► Phase 5 (TD-15 sites) ──► Phase 6 (cleanup) ──► Phase 7 (docs) ──► Phase 8 (global gates)
```

- **Phase 1 has no dependency** on any other phase and may run first or in parallel.
- **Phases 2→5 are strictly ordered**: the plugin must resolve before any typed-args site is
  written; the adapter's navigation callback (Phase 3) must exist before its host lambda can
  build typed args (Phase 5).
- **Phase 6** depends on Phase 3 (adapter `destroy()`) and Phase 4 (`MainActivity` edits).
- **Phase 8** gates archive and MUST run last.

---

## Phase 1: TD-5 — Task observer convergence

Isolated, independently revertible (commit set A). Edits only
`app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt`.
No other file.

- [ ] 1.1 Re-verify the current observer shape before editing. Inspect `TareaRepositorioFirebase.kt:163-229` (read-only) and confirm: shared map `:186`, the three listener registrations `:188,:194,:202`, `awaitClose` `:209-213`, and the pruning parity target `observarTareasPorGrupo` `:224-225`.
  - **Verify**: the read confirms the map/listeners/awaitClose/parity line numbers used by 1.2–1.4; record any drift in the apply notes.
  - **Rollback**: read-only — no change to revert.
  - **Depends / Parallel**: none; independent of all other phases.
- [ ] 1.2 In `TareaRepositorioFirebase.kt` (~`:186`), add `idsPorFuente = mutableMapOf<Int, Set<String>>()` and a single local `aplicar(fuente: Int, snap: QuerySnapshot?)` function inside the `callbackFlow` that (a) copies snapshot docs into `combinado` (single logical writer), (b) records `idsPorFuente[fuente]`, (c) prunes `combinado.keys` to the **union** of all recorded id sets, and (d) `trySend(combinado.values.toList())` (design Decision TD-5-1).
  - **Verify**: reading `aplicar` shows one writer to `combinado`, per-source id recording, union-based pruning, and a single emit; the union (not a single snapshot) is the pruning set.
  - **Rollback**: revert commit set A (`TareaRepositorioFirebase.kt` restored to pre-pruning behavior).
  - **Depends / Parallel**: depends on 1.1; parallel-safe with Phases 2–7 (different files).
- [ ] 1.3 Replace the three ad-hoc `combinado[it.id] = it` blocks at `TareaRepositorioFirebase.kt:188,:194,:202` with `addSnapshotListener { snap, error -> if (error != null) { close(error); return@addSnapshotListener }; aplicar(N, snap) }`, preserving the existing `error`-close behavior.
  - **Verify**: `grep -n "addSnapshotListener" app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` shows three registrations, each delegating to `aplicar`; no direct `combinado[...] =` writes remain outside `aplicar`.
  - **Rollback**: revert commit set A.
  - **Depends / Parallel**: depends on 1.2.
- [ ] 1.4 Document the two explicit assumptions at the `observarTareas` listener-registration site in `TareaRepositorioFirebase.kt`: the `[UNVERIFIED]` main-thread-confinement assumption (no custom executor is passed) and the group-resolved-once limitation (group resolved at flow start `:171-183`; a group change while the instance is active does not re-resolve).
  - **Verify**: `grep -n "UNVERIFIED" app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` returns ≥1 match inside `observarTareas`; the group-resolved-once limitation is stated in a nearby comment (delta spec scenarios 13–14).
  - **Rollback**: revert commit set A.
  - **Depends / Parallel**: depends on 1.2.
- [ ] 1.5 Focused check for TD-5 (focused test command for Unit A). Run the compile gate and the JVM suite; then walk manual scenarios 11–14.
  - **Verify**: `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` succeeds; `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` reports 4 tests / 0 failures; scenarios 11 (stale key pruned), 12 (cross-listener duplicate emitted once), 13 (group-resolved-once documented), 14 (threading assumption explicit) are inspected/passed.
  - **Rollback**: revert commit set A (file-local; no navigation impact).
  - **Depends / Parallel**: depends on 1.3, 1.4.

## Phase 2: Safe Args foundation (TD-15)

Resolves the `[UNVERIFIED]` plugin-compatibility question at the earliest safe point. Edits
`gradle/libs.versions.toml`, `app/build.gradle.kts`, and
`app/src/main/res/navigation/nav_graph.xml`.

- [ ] 2.1 Add the Safe Args plugin alias to `[plugins]` in `gradle/libs.versions.toml` (`:37-39`), version-matched to the Navigation Component: `navigation-safeargs = { id = "androidx.navigation.safeargs.kotlin", version.ref = "navigationFragment" }` (`navigationFragment = "2.9.6"` at `:16`).
  - **Verify**: `gradle/libs.versions.toml` contains the `navigation-safeargs` alias with `version.ref = "navigationFragment"`.
  - **Rollback**: remove the added alias line (part of commit set B).
  - **Depends / Parallel**: none; can start while Phase 1 runs (different file).
- [ ] 2.2 Apply the plugin in `app/build.gradle.kts` `plugins` block (`:1-5`): add `alias(libs.plugins.navigation.safeargs)`.
  - **Verify**: the `plugins` block contains the alias; a Gradle sync exposes Safe Args tasks.
  - **Rollback**: remove the added alias line.
  - **Depends / Parallel**: depends on 2.1.
- [ ] 2.3 Declare the three typed arguments on `fragment_Tareas` in `app/src/main/res/navigation/nav_graph.xml` (`:80-84`): `taskId`, `modo`, `categoria` — all `app:argType="string"`, `app:nullable="true"`, `android:defaultValue="@null"`.
  - **Verify**: `nav_graph.xml` `fragment_Tareas` declares all three `<argument>` elements; `grep -n "argument" app/src/main/res/navigation/nav_graph.xml` shows them.
  - **Rollback**: remove the three `<argument>` elements (same revert boundary as 2.2).
  - **Depends / Parallel**: depends on 2.1 (plugin needed to validate the graph); parallel with 2.2.
- [ ] 2.4 Compile gate to prove Safe Args resolves on AGP 9.3.3 / Kotlin 2.2.10 (closes the design Open Question).
  - **Verify**: `.\gradlew.bat :app:compileEmulatorKotlin --no-daemon --console=plain` succeeds; the generated `FragmentTareasArgs` class is produced under the module's `build/generated` sources. If the plugin cannot resolve, STOP this phase and apply Fallback TD-15-2 (a `TareasNavKeys` key-owner object) **only after amending the delta spec's "plugin MUST be applied" clause** — escalate, do not silently substitute.
  - **Rollback**: revert Phase 2 files (commit set B).
  - **Depends / Parallel**: depends on 2.2, 2.3; gates Phases 3–6.

## Phase 3: TD-6 — Adapter-owned scope + navigation callback

Edits `TareasHomeAdapter.kt`, `FragmentTareasPendientes.kt`, `FragmentPgPrincipal.kt`.
Depends on Phase 2 (host lambdas build typed args).

- [ ] 3.1 In `TareasHomeAdapter.kt` drop the `scope: CoroutineScope` constructor parameter (`:34-39`) and add `onTareaClick: (String) -> Unit` (design Decision TD-6-1). Retain `fragment` only for resources/context (`getString`/`requireContext`).
  - **Verify**: the constructor declares no `CoroutineScope` parameter; `grep -n "TareasHomeAdapter(" app/src/main/java` shows call sites passing a nav callback (delta spec scenario 17).
  - **Rollback**: revert commit set B (adapter file).
  - **Depends / Parallel**: depends on Phase 2 (typed args for the callback); sequential within the navigation unit.
- [ ] 3.2 Add an adapter-owned scope `CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())` plus `fun destroy() { adapterJob.cancel() }` to `TareasHomeAdapter.kt`.
  - **Verify**: `grep -n "CoroutineScope" app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` shows **only** the adapter-owned scope/job; `destroy()` is present.
  - **Rollback**: revert commit set B.
  - **Depends / Parallel**: depends on 3.1.
- [ ] 3.3 Replace `fragment.findNavController().navigate(...)` in `TareasHomeAdapter.kt:272-279` with `onTareaClick(t.id)`; the host lambda owns the navigation (delta spec scenario 18).
  - **Verify**: `grep -n "findNavController" app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` returns zero matches.
  - **Rollback**: revert commit set B.
  - **Depends / Parallel**: depends on 3.1, 3.2.
- [ ] 3.4 Add per-bind load cancellation in `TareasHomeAdapter.kt`: add `var cargaAsignadoJob: Job? = null` to the `VH`; at the top of `onBindViewHolder` call `holder.cargaAsignadoJob?.cancel()`; assign `holder.cargaAsignadoJob = scope.launch { ... }` in the assignee-name load (`:144`) while keeping the existing `bindingAdapterPosition == posicionActual` write guard (design Decision TD-6-2).
  - **Verify**: reading the bind path shows the previous load cancelled before a new one starts; the position write guard is unchanged; loads at `:177,:197` also run on the adapter-owned scope.
  - **Rollback**: revert commit set B.
  - **Depends / Parallel**: depends on 3.2.
- [ ] 3.5 In `FragmentTareasPendientes.kt` replace the `by lazy` adapter (`:29`) with a per-view field built in `onViewCreated`, passing the nav callback that builds `FragmentTareasArgs(taskId = id, modo = null, categoria = null).toBundle()`; call `adapter?.destroy()` in `onDestroyView` (delta spec scenarios 15–16).
  - **Verify**: no `by lazy` adapter remains; `adapter?.destroy()` appears in `onDestroyView`; the adapter is constructed against the current view.
  - **Rollback**: revert commit set B (file-local).
  - **Depends / Parallel**: depends on 3.3, 3.4; depends on Phase 2 for the typed args.
- [ ] 3.6 In `FragmentPgPrincipal.kt` promote the adapter (`:177`) from a local construction to a per-view field built with the nav callback that constructs `FragmentTareasArgs(...)`, so the same instance can be torn down in Phase 6.
  - **Verify**: the adapter is a field built per view (in `onViewCreated`/`onCreateView`); it is constructed with a nav callback and no external `CoroutineScope`.
  - **Rollback**: revert commit set B (file-local).
  - **Depends / Parallel**: depends on 3.3, 3.4; depends on Phase 2.

## Phase 4: TD-4 — Single Presentación→Login coordinator

Edits `MainActivity.kt` and `FragmentPresentacion.kt`. `MainActivity.kt` is also edited in
Phases 5–6, so coordinate those edits within commit set B. Depends on Phases 2–3 (same unit).

- [ ] 4.1 In `MainActivity.kt` add an `autoLoginListener` field and store the `OnDestinationChangedListener` currently registered at `:257-277`; remove-then-add before registering so one Activity instance can never register two (design Decision TD-4-1/TD-4-2).
  - **Verify**: the listener is held in a field; registration is preceded by a removal attempt.
  - **Rollback**: revert commit set B (MainActivity edits).
  - **Depends / Parallel**: sequential within the navigation unit.
- [ ] 4.2 Add the private funnel `navegarDePresentacionALogin()` in `MainActivity.kt` as the **only** caller of the `action_fragment_Presentacion_to_fragment_Login` navigation, with the live-destination guard `if (navController.currentDestination?.id != R.id.fragment_Presentacion) return`; the existing destination listener (`:266`) calls the funnel, and it posts the `Login → PgPrincipal` continuation as today (`:268-273`).
  - **Verify**: `grep -n "action_fragment_Presentacion_to_fragment_Login"` in `MainActivity.kt` shows exactly one call site (inside the funnel); the guard is present.
  - **Rollback**: revert commit set B.
  - **Depends / Parallel**: depends on 4.1.
- [ ] 4.3 Add the public `onPresentacionMensajesCompletados()` signal to `MainActivity.kt`, delegating to `navegarDePresentacionALogin()`.
  - **Verify**: the public function exists and calls the funnel; it does not itself navigate to Login directly.
  - **Rollback**: revert commit set B.
  - **Depends / Parallel**: depends on 4.2.
- [ ] 4.4 Remove `autoLoginListener` in `MainActivity.onDestroy` (and after it fires), so the armed one-shot listener's lifetime is bounded to the Activity (delta spec recreation-safety invariant).
  - **Verify**: `navController.removeOnDestinationChangedListener(autoLoginListener)` appears in `onDestroy`; the existing `notificacionesJob` cancellation at `:214-219` is preserved.
  - **Rollback**: revert commit set B.
  - **Depends / Parallel**: depends on 4.1.
- [ ] 4.5 In `FragmentPresentacion.kt` replace `findNavController().navigate(...)` (`:52`) with `(activity as? MainActivity)?.onPresentacionMensajesCompletados()`; drop the now-unused `findNavController` import; keep the three-message sequence (`:39-57`) unchanged (delta spec scenario 4).
  - **Verify**: `grep -n "findNavController" app/src/main/java/com/example/tfg/vista/FragmentPresentacion.kt` returns zero matches; the three messages still display in order and transition exactly once.
  - **Rollback**: revert commit set B (file-local).
  - **Depends / Parallel**: depends on 4.3.
- [ ] 4.6 Guard the untouched manual login path: confirm `FragmentLogin.kt:94-103` (read-only) still performs its own `Login → PgPrincipal` navigation and is **not** modified by this change (design note on TD-4 scope).
  - **Verify**: `git diff --name-only` does not list `FragmentLogin.kt`; reading `FragmentLogin.kt:94-103` (read-only) shows the original user-driven navigation.
  - **Rollback**: N/A (no edit intended; a stray edit is reverted with commit set B).
  - **Depends / Parallel**: after 4.5.

## Phase 5: TD-15 — Migrate navigation sites

Replaces every manual `Bundle` writer/reader with generated `FragmentTareasArgs`. The adapter
writer is already covered by 3.3 (host callback). Edits `MainActivity.kt`,
`FragmentPgPrincipal.kt`, `FragmentTareas.kt`. Depends on Phases 2–4.

- [ ] 5.1 In `MainActivity.handleOpenTaskId` (`:223-230`, writer `:225-226`) build `FragmentTareasArgs(taskId = taskId, modo = null, categoria = null).toBundle()`, and apply the duplicate-stack fix: only when `navController.currentDestination?.id == R.id.fragment_Tareas`, navigate with `NavOptions.Builder().setPopUpTo(R.id.fragment_Tareas, true).build()`; otherwise navigate as today (design Decision TD-4-3; delta spec scenario 10).
  - **Verify**: `grep -rn "putString(\"taskId\"" app/src/main/java` returns zero; the conditional `popUpTo` block is present; the raw `getStringExtra("openTaskId")` read at `:206` is unchanged.
  - **Rollback**: revert commit set B; `NotificationScheduler.kt` is never touched, so its behavior is unaffected.
  - **Depends / Parallel**: depends on 2.4, 4.2.
- [ ] 5.2 In `FragmentPgPrincipal.kt` (`:78-99`) replace the six `Bundle().apply { putString("categoria", …) }` category-button writes with `FragmentTareasArgs(taskId = null, modo = null, categoria = "cocina"|…).toBundle()`.
  - **Verify**: `grep -rn "putString(\"categoria\"" app/src/main/java` returns zero; all six buttons navigate with typed args.
  - **Rollback**: revert commit set B (file-local).
  - **Depends / Parallel**: depends on 2.4; parallel with 5.3/5.4.
- [ ] 5.3 In `FragmentPgPrincipal.kt` (`:312`, `:315`) migrate `setupCategoriaAsignacion`'s writes to typed args: `FragmentTareasArgs(taskId = null, modo = "crear", categoria = "Personalizada")` at `:312` and `FragmentTareasArgs(taskId = null, modo = null, categoria = categoriaId)` at `:315`.
  - **Verify**: `grep -rn "putString(\"modo\"\|putString(\"categoria\"" app/src/main/java/com/example/tfg/vista/FragmentPgPrincipal.kt` returns zero.
  - **Rollback**: revert commit set B (file-local).
  - **Depends / Parallel**: depends on 2.4; parallel with 5.2.
- [ ] 5.4 In `FragmentTareas.kt` add the typed reader `private val navArgs: FragmentTareasArgs? get() = arguments?.let { FragmentTareasArgs.fromBundle(it) }` and replace every raw key read at `:56` (`modo` → `navArgs?.modo ?: "lista"`), `:57,:205,:397` (`taskId` → `navArgs?.taskId`), and `:78,:155,:226` (`categoria` → `navArgs?.categoria`).
  - **Verify**: `grep -rn "getString(\"taskId\"\|getString(\"modo\"\|getString(\"categoria\"" app/src/main/java/com/example/tfg/vista/FragmentTareas.kt` returns zero; the detail/create paths still read the same values.
  - **Rollback**: revert commit set B (file-local).
  - **Depends / Parallel**: depends on 2.4; parallel with 5.2/5.3.
- [ ] 5.5 Safe Args migration audit (delta spec scenarios 19–20; design grep table 3).
  - **Verify**: `grep -rn "putString(\"taskId\"\|getString(\"taskId\"\|putString(\"categoria\"\|getString(\"categoria\"\|putString(\"modo\"\|getString(\"modo\"" app/src/main/java` returns **zero** matches; `nav_graph.xml` declares the three arguments (from 2.3).
  - **Rollback**: N/A (audit only); failures are fixed by revisiting 5.1–5.4.
  - **Depends / Parallel**: depends on 5.1–5.4.

## Phase 6: Collateral lifecycle cleanup + notification duplicate stack

Edits `MainActivity.kt`, `FragmentPgPrincipal.kt`, `FragmentTareas.kt`. Depends on Phases 3–4.

- [ ] 6.1 In `MainActivity.onDestroy` (`:216-221`) cancel `avatarDrawerJob` (`:56`, reassigned `:486-500`) alongside the existing `notificacionesJob` cancellation (delta spec scenario 7).
  - **Verify**: `grep -n "avatarDrawerJob" app/src/main/java/com/example/tfg/vista/MainActivity.kt` shows a `.cancel()` inside `onDestroy`; no avatar load runs against a destroyed Activity.
  - **Rollback**: revert commit set B (behavior-preserving; file-local).
  - **Depends / Parallel**: depends on 4.4 (same `onDestroy` block).
- [ ] 6.2 Add `onDestroyView` to `FragmentPgPrincipal.kt` that cancels `tareasHomeJob` (`:46`) and calls `adapter.destroy()` (from 3.6) (delta spec scenario 8).
  - **Verify**: `onDestroyView` cancels `tareasHomeJob` and calls `adapter.destroy()`; no view-scoped job outlives the view.
  - **Rollback**: revert commit set B (file-local).
  - **Depends / Parallel**: depends on 3.6, 4.4.
- [ ] 6.3 Add a bounded `onDestroyView` to `FragmentTareas.kt` clearing `listaBinding`/`crearBinding` (`:43-44`), matching the pattern in `FragmentTareasPendientes.kt:224-228` and `FragmentCalendario.kt:337-338` (read-only references). No broader binding-null refactor.
  - **Verify**: `onDestroyView` nulls both bindings; the change is limited to the two touched bindings.
  - **Rollback**: revert `FragmentTareas.kt` (it is otherwise independent within commit set B).
  - **Depends / Parallel**: parallel with 6.1/6.2.
- [ ] 6.4 Confirm the notification duplicate-stack fix is fully covered by 5.1 and that `NotificationScheduler.kt` (`:74-80`, read-only) stays unchanged (proposal decision 5; delta spec scenario 10).
  - **Verify**: `git diff --name-only` does not list `NotificationScheduler.kt`; manual scenario 10 passes (notification tapped while on `fragment_Tareas` adds no duplicate entry).
  - **Rollback**: N/A (no edit intended).
  - **Depends / Parallel**: after 5.1.

## Phase 7: Documentation

Edits `docs/architecture/TEAMTASK_GUIDE.md` only (commit set C). Depends on Phases 1, 3–6
landing so status is truthful.

- [ ] 7.1 Guide §6 Technical debt register (`docs/architecture/TEAMTASK_GUIDE.md:138`) — mark TD-4, TD-5, TD-6, and TD-15 as `Resolved (teamtask-navigation-lifecycle-convergence)`.
  - **Verify**: reading §6 shows the four rows resolved with the change name.
  - **Rollback**: revert commit set C (doc-only).
  - **Depends / Parallel**: after Phases 1, 3, 4, 5.
- [ ] 7.2 Guide §7 Seven-step convergence roadmap (`:163`) — mark step 6 `Done (teamtask-navigation-lifecycle-convergence)`.
  - **Verify**: §7 step 6 row shows the resolved status.
  - **Rollback**: revert commit set C.
  - **Depends / Parallel**: after 7.1.
- [ ] 7.3 Guide §9 Follow-up SDD changes (`:218`) — update the change status to reflect this change's completion.
  - **Verify**: §9 records this change and its resolved debts.
  - **Rollback**: revert commit set C.
  - **Depends / Parallel**: after 7.2.
- [ ] 7.4 Record the archive hand-off (read-only reference): the delta specs' non-requirement tables (debt register, roadmap, current-state Navigation row) and the added Manual Verification rows are applied to the canonical specs at `sdd-archive`, **not now**. Verify the guide edits are consistent with `specs/implementation-recipes/spec.md` and `specs/navigation-lifecycle/spec.md` (both read-only here).
  - **Verify**: the guide's resolved rows match the archive-bound table content in both delta specs; no canonical spec was modified in this phase (`git diff --name-only` lists only the guide).
  - **Rollback**: N/A (no editing of canonical specs intended).
  - **Depends / Parallel**: after 7.3.

## Phase 8: Global Gates & Evidence

Mandatory before `sdd-archive`. Runs after every implementation phase. No device/emulator is
available in this run, so the manual matrix rows are executed on a device by apply/verify.

- [ ] 8.1 **Compile gate (authoritative).** `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain`
  - **Verify**: both flavors compile; Safe Args code generation resolved (`FragmentTareasArgs` generated). A plugin resolution failure routes to Fallback TD-15-2 (2.4), which requires a spec amendment.
  - **Rollback**: revert commit sets A/B as needed.
  - **Depends / Parallel**: after Phase 7.
- [ ] 8.2 **Existing JVM suite (regression gate for untouched code).** `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
  - **Verify**: 4 tests / 0 failures (`AvatarImagenTest` 3 + `ExampleUnitTest` 1; baseline from the previous change). The suite does not exercise the changed navigation/lifecycle paths; the manual matrix is the behavioral net.
  - **Rollback**: N/A (gate).
  - **Depends / Parallel**: after 8.1.
- [ ] 8.3 **Grep audits** (design verification table 3). Run all seven; each must match its expected result.

| Audit | Command | Expected |
|---|---|---|
| No external scope param | `grep -rn "TareasHomeAdapter(" app/src/main/java` | Both call sites pass `(this, parejaVM, tareasVM) { … }` — no `CoroutineScope` |
| Adapter scope internal-only | `grep -n "CoroutineScope" app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` | Only the adapter-owned `adapterJob`/scope |
| No raw navigation keys | `grep -rn "putString(\"taskId\"\|getString(\"taskId\"\|putString(\"categoria\"\|getString(\"categoria\"\|putString(\"modo\"\|getString(\"modo\"" app/src/main/java` | Zero matches |
| Jobs cancelled | `grep -rn "avatarDrawerJob\|tareasHomeJob\|\.destroy()" app/src/main/java/com/example/tfg/vista` | `avatarDrawerJob` cancelled in `onDestroy`; `tareasHomeJob` + adapter `destroy()` in `onDestroyView` |
| Intent contract intact | `grep -rn "openTaskId" app/src/main/java` | Still a raw `getStringExtra`/`putExtra`, never a nav argument |
| No manual fragment nav in adapter | `grep -rn "findNavController" app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` | Zero matches |
| Uncertainty markers | `grep -r "\[UNVERIFIED\]" openspec/` | New assumptions marked |

  - **Verify**: every row matches its expected result (delta spec scenarios 14, 17–21).
  - **Rollback**: N/A (audit); mismatches are fixed by revisiting the owning phase.
  - **Depends / Parallel**: after 8.2.
- [ ] 8.4 **Manual navigation matrix (authoritative behavioral net).** Execute the 21-scenario matrix referenced below on a device; record pass/fail per row.
  - **Verify**: all 21 rows pass with unchanged observable behavior. Failures block archive and are triaged against the owning phase.
  - **Rollback**: partial reverts per commit set (A, B, or C).
  - **Depends / Parallel**: after 8.1; runs alongside 8.3.
- [ ] 8.5 **Rollback rehearsal / boundary check.** Confirm the change can be reverted by `git revert` of commit sets A/B/C: the original auto-login paths, the pre-pruning `observarTareas`, the original adapter scope/navigation coupling, and every manual bundle site are restored; the Safe Args plugin entry and `nav_graph.xml` `<argument>` declarations are removed by the same revert. No Firestore data rollback is implied.
  - **Verify**: `git log --oneline` shows the commit sets; a dry inspection confirms each set's files map to one revertible unit.
  - **Rollback**: N/A (rehearsal).
  - **Depends / Parallel**: after 8.4.

---

## Manual Verification Matrix

The authoritative matrix is design `Verification Plan §4` (21 rows, one per spec scenario).
Reproduced here for execution convenience; if the design changes, the design wins.

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
| 19 | No manual bundle sites remain | Grep audit (8.3) | Zero raw key sites |
| 20 | Arguments are declared | Inspect `nav_graph.xml` | `taskId`, `modo`, `categoria` `<argument>` present |
| 21 | `openTaskId` contract unchanged | Read notification path | Still a raw intent extra, not a nav argument |

Scenarios 2, 3, 4, 7, 8, 9, 10, 15, 16 require a device/emulator and are **planned, not executed**
in this docs-only phase; apply/verify run them.

---

## Definition of Done (change-level)

- [ ] All four debts (TD-4, TD-5, TD-6, TD-15) implemented and independently revertible.
- [ ] Compile gate green for both flavors; Safe Args resolved (or Fallback TD-15-2 + spec amendment).
- [ ] Existing JVM suite green (4 tests, 0 failures).
- [ ] All seven grep audits match expected; zero raw navigation-key sites remain.
- [ ] Manual matrix (21 scenarios) passes with unchanged observable behavior.
- [ ] Guide §6/§7/§9 updated; delta non-requirement tables recorded for archive.

## Guard Notes

- **No PR slicing.** Delivery is direct-to-master (solo developer). The work-unit table is a
  rollback/review shape, not a PR plan. Do not open, chain, or size-exception any PR.
- **Bounded scope.** Only `FragmentTareas`' own two bindings are cleared (6.3); no broad
  `_binding`-null refactor. Hilt/Room/Retrofit/Compose are NOT introduced.
- **`[UNVERIFIED]` markers.** The Safe Args / AGP compatibility question is closed by 2.4; the
  Firestore main-thread-confinement assumption stays marked `[UNVERIFIED]` in code (1.4).
- **Edit authority.** Paths marked `(read-only)` are inspection targets only; every backticked
  path without that marker on a checkbox line is an intended edit target within the repo root.
