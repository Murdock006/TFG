# Proposal: TeamTask navigation/lifecycle convergence

## Intent

TeamTask's navigation and lifecycle orchestration carries four open debt items — **TD-4**
(duplicate auto-login navigation), **TD-5** (inconsistent task observer state), **TD-6**
(adapter owning an external `CoroutineScope`), and **TD-15** (manual navigation bundles)
(`docs/architecture/TEAMTASK_GUIDE.md:148-159`; `openspec/specs/implementation-recipes/spec.md:205-216`).
Each is a known robustness gap that survives only because there is no test net to catch it:
a configuration change can race two navigators into Login (`MainActivity.kt:257-277` vs
`FragmentPresentacion.kt:39-57`), `observarTareas` never prunes stale keys while its sibling
observer does (`TareaRepositorioFirebase.kt:186,190-213` vs `:224-225`), and
`TareasHomeAdapter` holds a `viewLifecycleOwner.lifecycleScope` that is cancelled when its
host view is recreated, turning later loads into silent no-ops
(`TareasHomeAdapter.kt:38,144-235`; `FragmentTareasPendientes.kt:29`).

This change is convergence roadmap step 6 (`docs/architecture/TEAMTASK_GUIDE.md:175`;
`implementation-recipes/spec.md:227`) and resolves TD-4, TD-5, TD-6, and TD-15. It is a
robustness/convergence refactor, **not a behavior redesign**: the current user-visible
navigation behavior must be preserved exactly (drawer close → double-back on
`fragment_PgPrincipal` → secondary destinations redirect to `fragment_PgPrincipal` →
pop/finish; the `"openTaskId"` intent extra contract stays unchanged).

## Resolved Decisions

These are settled for this change; the proposal records them and does not re-open them.

1. **One coordinated auto-login/Presentación→Login path (TD-4).** Exactly one component owns
   the Presentación→Login transition, and the redundant independent navigator is removed. The
   Login→PgPrincipal step remains conditional on a verified session. The mechanism MUST be
   robust across Activity recreation (an idempotent registration guard or equivalent
   one-shot/lifecycle-scoped cleanup), and MUST preserve the current observable sequence and
   destinations. The exact ownership split (Activity-owned listener vs Fragment-signalled
   transition) is selected in `design.md`; the proposal fixes only the invariant "single
   navigator, recreation-safe".
2. **Observer consistency (TD-5).** `observarTareas` gains stale-key pruning with parity to
   `observarTareasPorGrupo` (`TareaRepositorioFirebase.kt:224-225`) and single-writer
   discipline for the shared `combinado` map. The main-thread confinement of Firestore
   snapshot listeners and the group-resolved-once limitation are documented as explicit
   assumptions in the delta spec, not silently assumed.
3. **No external `CoroutineScope` in the adapter (TD-6).** The adapter stops receiving a host
   scope. Its asynchronous loads run on a lifecycle-safe, adapter-owned mechanism that
   survives view recreation and is cancelled when the view is destroyed (or the loads move
   behind the ViewModel/Fragment with proper cancellation). The adapter's direct
   `fragment.findNavController()` coupling is replaced by a host-provided navigation callback
   where that is feasible within this scope. Consuming Fragments create the adapter with the
   current view's owner, not via `by lazy` capturing a stale scope.
4. **AndroidX Safe Args (TD-15).** Adopt the `androidx.navigation.safeargs.kotlin` Gradle
   plugin, version-matched to the Navigation Component (2.9.6), because it is the standard
   AndroidX mechanism and the repo already uses the Navigation Component. A hand-written
   typed bundle wrapper was considered and rejected as bespoke. Every manual bundle site and
   reader is migrated and the `<argument>` declarations are added to `nav_graph.xml`. The
   `"openTaskId"` intent extra contract is unchanged.
5. **Bounded collateral lifecycle cleanup.** Cancel `MainActivity.avatarDrawerJob` in
   `onDestroy`; add `onDestroyView` cleanup where the touched jobs live (notably
   `FragmentPgPrincipal.tareasHomeJob`). The notification `taskId` duplicate-stack risk gets a
   cheap, behavior-preserving fix (`launchSingleTop` plus an explicit `popUpTo` to avoid
   duplicating `fragment_Tareas`) or, if that cannot be shown safe, is recorded as a
   follow-up with rationale. Broad binding-null refactors are not part of this change.

## Scope

### In Scope

- **TD-4**: eliminate the duplicate-navigation race between
  `MainActivity.verificarSesionActiva()`'s one-shot destination listener
  (`MainActivity.kt:232-286`, listener `:257-277`) and `FragmentPresentacion`'s delayed
  navigation (`FragmentPresentacion.kt:39-57`); make registration recreation-safe while
  preserving current behavior.
- **TD-5**: add stale-key pruning and single-writer discipline to
  `TareaRepositorioFirebase.observarTareas` (`:163-214`); document the main-thread
  confinement assumption and the group-resolved-once limitation.
- **TD-6**: remove the external `CoroutineScope` from `TareasHomeAdapter`
  (`:34-39,144,177,197`) and replace it with a lifecycle-safe mechanism; remove the direct
  `fragment.findNavController()` coupling (`:272-279`) where feasible; fix the stale-scope
  `by lazy` adapter in `FragmentTareasPendientes.kt:29`.
- **TD-15**: add the Safe Args Gradle plugin (`app/build.gradle.kts`, version catalog), add
  `<argument>` declarations to `nav_graph.xml`, and migrate every manual bundle site
  (`MainActivity.kt:225-226`, `TareasHomeAdapter.kt:274-275`,
  `FragmentPgPrincipal.kt:78-99,312-316`) and reader (`FragmentTareas.kt:56-57,78,155,205,226,397`).
- **Directly related cleanup in touched flows**: cancel `avatarDrawerJob` in
  `MainActivity.onDestroy` (`:216-221`); add `onDestroyView` cleanup for
  `FragmentPgPrincipal.tareasHomeJob` (`:46,184-185,197-215`); address the notification
  `taskId` duplicate-stack risk (`NotificationScheduler.kt:74-80`;
  `MainActivity.kt:223-230`) or record it as a follow-up.
- Update `docs/architecture/TEAMTASK_GUIDE.md` §6 (TD-4/5/6/15 status), §7 (roadmap step 6
  status), and §9 (change status).
- Provide a manual verification matrix for the preserved navigation behavior.
- Delta specs for the affected capabilities (spec phase).

### Out of Scope

- LiveData→StateFlow migration and broad binding-null refactors across all Fragments.
- Account deletion/security cleanup (TD-13); `resolverReclamo` split write (TD-7); avatar
  authority (TD-2/TD-3); Firestore rules/indexes/App Check (TD-9).
- Behavior redesign of back handling (the `OnBackPressedCallback` rules and secondary-set
  redirect stay as-is).
- Introducing Hilt, Room, Retrofit, Compose, or any non-Navigation new framework.
- Adding an automated test suite (roadmap step 5 is a separate change; no test net exists).

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `navigation-lifecycle`: requirements change around auto-login single-navigator coordination
  and recreation safety (TD-4), task-observer state consistency (TD-5), coroutine-scope
  ownership for `TareasHomeAdapter` (TD-6), and typed navigation arguments (TD-15). The
  "Manual bundles" allowance and the TD-4/5/6/15 "Future Convergence Work" rows move to
  resolved behavior.
- `implementation-recipes`: the debt register marks TD-4, TD-5, TD-6, and TD-15 resolved; the
  convergence roadmap records step 6 as done; the navigation recipe moves from manual bundles
  to Safe Args. (Documentation-status change; no behavioral requirement beyond the
  navigation-mechanism recipe.)

`architecture-map`: no requirement change. Any edit is reference-only line drift (the
`TareasHomeAdapter` evidence rows), and will be re-verified during the spec phase; it is not
listed as a modified capability.

## Approach

1. **TD-4 — single navigator.** Collapse the two Presentación→Login navigators into one
   coordinator. Keep the destination sequence and the verified-session condition unchanged;
   make the listener registration idempotent across `onCreate` recreation (guard flag and/or
   removal in a lifecycle callback, not only on the Presentación hit at
   `MainActivity.kt:274`). Defer the Activity-owned vs Fragment-signalled split to `design.md`.
2. **TD-5 — observer parity.** In `observarTareas`, prune keys not present in the current
   snapshot (mirroring `observarTareasPorGrupo`) and keep a single logical writer for
   `combinado`. Document the main-thread confinement assumption and the once-resolved group.
3. **TD-6 — scope ownership.** Give the adapter a lifecycle-safe load mechanism (adapter-owned
   `CoroutineScope`/supervisor cancelled from the host's `onDestroyView`, or move loads behind
   the ViewModel). Replace `fragment.findNavController()` with a host-provided navigation
   lambda, and construct the adapter per view in the consuming Fragments.
4. **TD-15 — Safe Args.** Add the plugin and typed directions/argument classes; declare
   arguments in `nav_graph.xml`; replace manual `Bundle` writes/reads with generated
   directions and argument accessors. Keep `"openTaskId"` as a raw intent extra.
5. **Collateral cleanup.** Cancel `avatarDrawerJob` in `onDestroy`; add `onDestroyView`
   cleanup where the touched jobs live; apply the bounded notification duplicate-stack fix or
   record the follow-up.
6. **Docs/deltas.** Update the guide (§6/§7/§9) and emit the delta specs
   `openspec/changes/teamtask-navigation-lifecycle-convergence/specs/{navigation-lifecycle,implementation-recipes}/spec.md`.
7. **Verify.** Compile (`./gradlew :app:assembleDebug` or the project's build command),
   audit references, and run the manual navigation matrix (no automated suite).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `app/src/main/java/com/example/tfg/vista/MainActivity.kt` | Modified | Single recreation-safe auto-login coordinator; `avatarDrawerJob` cancelled in `onDestroy`; Safe Args for the `taskId` navigation; notification duplicate-stack handling |
| `app/src/main/java/com/example/tfg/vista/FragmentPresentacion.kt` | Modified | Remove/neutralize the redundant Presentación→Login navigator (keep message sequence) |
| `app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` | Modified | Drop external `CoroutineScope`; lifecycle-safe loads; replace `findNavController()` with a host callback; typed `taskId` navigation |
| `app/src/main/java/com/example/tfg/vista/FragmentTareasPendientes.kt` | Modified | Create the adapter from the current view owner (no stale `by lazy` scope) |
| `app/src/main/java/com/example/tfg/vista/FragmentPgPrincipal.kt` | Modified | Adapter construction; `onDestroyView` cleanup for `tareasHomeJob`; typed category/mode navigation |
| `app/src/main/java/com/example/tfg/vista/FragmentTareas.kt` | Modified | Read Safe Args arguments instead of raw bundle keys |
| `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` | Modified | `observarTareas` stale-key pruning + single-writer discipline |
| `app/src/main/res/navigation/nav_graph.xml` | Modified | Declare `<argument>` elements for `fragment_Tareas` |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | Modified | Add and version-match the `androidx.navigation.safeargs.kotlin` plugin |
| `app/src/main/java/com/example/tfg/service/NotificationScheduler.kt` | Modified (conditional) | Only if the duplicate-stack fix requires it; otherwise unchanged and recorded as follow-up |
| `docs/architecture/TEAMTASK_GUIDE.md` | Modified | §6/§7/§9 reflect TD-4/5/6/15 resolved and step 6 done |
| `openspec/changes/teamtask-navigation-lifecycle-convergence/specs/**` | New | Delta specs for `navigation-lifecycle` and `implementation-recipes` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Behavior regression with no test net | High | Preserve destinations and conditions exactly; compile + reference audit + manual navigation matrix; no behavior redesign |
| Safe Args plugin incompatible with AGP 9.3.3 / Kotlin 2.2.10 `[UNVERIFIED]` | Med | Version-match 2.9.6 to Navigation; verify with a clean build first; fall back to a typed bundle wrapper if the plugin cannot resolve (recorded decision) |
| Line drift in the canonical spec causes wrong deltas | High | Re-verify every `file:line` against the current source before writing deltas (exploration records current truth) |
| Scope creep into broad lifecycle/binding refactors | Med | Bounded cleanup list in decisions 1-5; anything else is a follow-up |
| Presentación message timing changes while removing the duplicate navigator | Med | Keep `FragmentPresentacion`'s message sequence and the verified-session condition; choose the ownership split that preserves the observed sequence in `design.md` |
| Notification duplicate-stack fix changes back-stack behavior | Low | Use `launchSingleTop` + explicit `popUpTo`; if it cannot be shown safe, record as a follow-up instead of forcing it |
| Firestore listener threading assumption is wrong `[UNVERIFIED]` | Low | Document the assumption explicitly in the delta spec and keep the pruning fix correct under either threading model |

## Rollback Plan

The change lands as direct commits to `master` in a solo-developer repository (no pull
requests). Rollback is a `git revert` of this change's commit(s):

1. Revert the commit(s). This restores the original `MainActivity`/`FragmentPresentacion`
   auto-login paths, the pre-pruning `TareaRepositorioFirebase.observarTareas`, the original
   `TareasHomeAdapter` scope/navigation coupling, and every manual bundle site/reader.
2. Remove the Safe Args plugin entry and the `nav_graph.xml` `<argument>` declarations
   (both are reverted by the same commit revert).
3. No Firestore data rollback is implied: the observer fix changes in-memory key handling
   only and does not rewrite persisted documents.
4. Revert the guide §6/§7/§9 edits and the delta specs, or leave the archive trail intact and
   mark the change superseded.

If only part of the change is problematic, the TD-4/TD-6/TD-15 pieces are independent of the
TD-5 observer fix and can be reverted separately.

## Dependencies

- Roadmap step 1 (guide + five canonical specs) is published; step 2 landed
  (`teamtask-task-repo-consolidation`). Roadmap step 5 (tests) is not done, so no automated
  safety net exists (`docs/architecture/TEAMTASK_GUIDE.md:174`).
- AndroidX Navigation Component is already present (`libs.versions.toml:16,33`); Safe Args is
  an additive plugin, version-matched to 2.9.6.
- Delivery context: solo developer, commits go directly to `master`; no PR slicing applies by
  policy. The change touches several files and may approach or exceed the 400-line review
  budget, so `sdd-tasks` MUST forecast the budget and recommend chained/sliced work units if
  the forecast is high.

## Success Criteria

- [ ] Exactly one coordinator drives Presentación→Login; the duplicate navigator is gone, and
      a config change during the window cannot produce a double Login entry.
- [ ] `TareaRepositorioFirebase.observarTareas` prunes stale keys (parity with
      `observarTareasPorGrupo`) and has documented single-writer/main-thread assumptions.
- [ ] `TareasHomeAdapter` no longer receives an external `CoroutineScope`; its loads survive
      view recreation and are cancelled with the view; no stale `by lazy` scope remains.
- [ ] All manual bundle sites/readers are migrated to Safe Args and `nav_graph.xml` declares
      the task arguments; `"openTaskId"` is still handled unchanged.
- [ ] `avatarDrawerJob` is cancelled in `onDestroy`; `FragmentPgPrincipal.tareasHomeJob` is
      cleaned up in `onDestroyView`; the notification `taskId` duplicate-stack risk is fixed
      or recorded as an explicit follow-up.
- [ ] The project compiles; the manual navigation matrix (auto-login, double-back, secondary
      back, notification deep link) passes with unchanged behavior.
- [ ] TD-4/5/6/15 are marked resolved and roadmap step 6 done in the guide and specs.
