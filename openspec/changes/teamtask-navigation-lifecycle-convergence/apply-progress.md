# Apply Progress: TeamTask navigation/lifecycle convergence

**Change**: `teamtask-navigation-lifecycle-convergence`
**Mode**: Standard (strict_tdd: false, per `openspec/config.yaml`)
**Branch**: `master` (worktree only — commits are authored by the orchestrator)
**Artifact store**: hybrid (files + Engram mirror)

## Completed Tasks

38 / 39 tasks complete. Only task **8.4** (manual 21-scenario matrix) is pending — it requires a
device/emulator and is explicitly outside this run's execution scope.

- [x] Phase 1 — TD-5 observer convergence (1.1–1.5)
- [x] Phase 2 — Safe Args foundation (2.1–2.4)
- [x] Phase 3 — TD-6 adapter scope + navigation callback (3.1–3.6)
- [x] Phase 4 — TD-4 single Presentación→Login coordinator (4.1–4.6)
- [x] Phase 5 — TD-15 typed navigation sites (5.1–5.5)
- [x] Phase 6 — collateral lifecycle cleanup + notification duplicate-stack (6.1–6.4)
- [x] Phase 7 — documentation (7.1–7.4)
- [x] Phase 8 — global gates: 8.1, 8.2, 8.3, 8.5
- [ ] Phase 8 — 8.4 manual navigation matrix (NOT EXECUTED — device required)

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` | Modified | TD-5: `idsPorFuente` + single-writer `aplicar()` + union-based pruning; three listeners delegate to `aplicar`; `[UNVERIFIED]` main-thread assumption + group-resolved-once limitation documented |
| `app/src/main/java/com/example/tfg/vista/TareasHomeAdapter.kt` | Modified | TD-6: dropped external `CoroutineScope` param; added `onTareaClick: (String) -> Unit`; adapter-owned `SupervisorJob` scope + `destroy()`; per-bind `cargaAsignadoJob` cancel; removed `findNavController()` |
| `app/src/main/java/com/example/tfg/vista/FragmentTareasPendientes.kt` | Modified | Per-view adapter field built in `onViewCreated` with nav callback; `adapter?.destroy()` in `onDestroyView` |
| `app/src/main/java/com/example/tfg/vista/FragmentPgPrincipal.kt` | Modified | Adapter promoted to per-view field with nav callback; six category buttons + `setupCategoriaAsignacion` migrated to `FragmentTareasArgs`; `onDestroyView` cancels `tareasHomeJob` + `adapter.destroy()` |
| `app/src/main/java/com/example/tfg/vista/MainActivity.kt` | Modified | TD-4: `autoLoginListener` field + remove-then-add; private funnel `navegarDePresentacionALogin()`; public `onPresentacionMensajesCompletados()`; listener removed in `onDestroy`; `avatarDrawerJob` cancelled in `onDestroy`; Safe Args in `handleOpenTaskId` + conditional `popUpTo` duplicate-stack fix |
| `app/src/main/java/com/example/tfg/vista/FragmentPresentacion.kt` | Modified | Replaced `findNavController().navigate(...)` with `(activity as? MainActivity)?.onPresentacionMensajesCompletados()`; dropped unused imports |
| `app/src/main/java/com/example/tfg/vista/FragmentTareas.kt` | Modified | Typed `navArgs` reader; all raw key reads migrated; bounded `onDestroyView` clearing `listaBinding`/`crearBinding` |
| `app/src/main/res/navigation/nav_graph.xml` | Modified | Declared nullable `taskId`, `modo`, `categoria` `<argument>` on `fragment_Tareas` |
| `gradle/libs.versions.toml` | Modified | Added `navigation-safeargs` plugin alias version-matched to `navigationFragment` (2.9.6) |
| `app/build.gradle.kts` | Modified | Applied `alias(libs.plugins.navigation.safeargs)` |
| `docs/architecture/TEAMTASK_GUIDE.md` | Modified | §6 TD-4/5/6/15 → resolved; §7 step 6 → done; §9 change → done |

Read-only files confirmed untouched: `FragmentLogin.kt`, `NotificationScheduler.kt`, all canonical
specs under `openspec/specs/`, and both delta specs. `.idea/**` was already modified before this
run (pre-existing dirty worktree).

## Gates

| Gate | Command | Observed result |
|---|---|---|
| Compile (both flavors) | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` | BUILD SUCCESSFUL in 19s; `generateSafeArgsEmulator`/`generateSafeArgsRelease` ran; `FragmentTareasArgs.kt` generated under both `app/build/generated/java/generateSafeArgs*/com/example/tfg/vista/` |
| Safe Args resolution (2.4) | same as above | Plugin resolved on AGP 9.3.3 / Kotlin 2.2.10 — closes the design Open Question; Fallback TD-15-2 not required |
| JVM suite | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` | BUILD SUCCESSFUL; `ExampleUnitTest` tests=1 failures=0, `AvatarImagenTest` tests=3 failures=0 → **4 tests / 0 failures** |

## Grep Audits (8.3, design verification table 3)

| Audit | Observed result |
|---|---|
| No external scope param | `FragmentPgPrincipal.kt:189` and `FragmentTareasPendientes.kt:47` both pass `(this, parejaVM, tareasVM) { id -> … }` — no `CoroutineScope` |
| Adapter scope internal-only | `TareasHomeAdapter.kt`: only `import kotlinx.coroutines.CoroutineScope` (line 26) and the adapter-owned `private val scope = CoroutineScope(Dispatchers.Main.immediate + adapterJob)` (line 50) |
| No raw navigation keys | **ZERO matches** for `putString/getString("taskId"\|"categoria"\|"modo")` |
| Jobs cancelled | `avatarDrawerJob?.cancel()` in `MainActivity.onDestroy` (:231-232); `tareasHomeJob?.cancel()` + `tareaAdapter?.destroy()` in `FragmentPgPrincipal.onDestroyView` (:380-382); `adapter?.destroy()` in `FragmentTareasPendientes.onDestroyView` (:236) |
| Intent contract intact | `openTaskId` still raw `putExtra` (`NotificationScheduler.kt:76`) and `getStringExtra` (`MainActivity.kt:137,210`) — never a nav argument |
| No manual fragment nav in adapter | `TareasHomeAdapter.kt`: **ZERO** `findNavController` matches |
| Uncertainty markers | `openspec/` still carries `[UNVERIFIED]` markers, incl. the delta spec's threading-assumption scenario (`specs/navigation-lifecycle/spec.md:205`) |

## Manual Navigation Matrix (8.4) — NOT EXECUTED

No device/emulator is available in this run. All 21 scenarios from the design Verification Plan §4
are **NOT EXECUTED — pending manual verification on a device**. No result is claimed.

| # | Spec scenario | Status |
|---|---|---|
| 1 | Logged in, email not verified | NOT EXECUTED |
| 2 | Logged in, email verified | NOT EXECUTED |
| 3 | Config change during auth window | NOT EXECUTED |
| 4 | Presentación message sequence preserved | NOT EXECUTED |
| 5 | ViewModel-owned group listener | NOT EXECUTED |
| 6 | Activity-owned notifications listener | NOT EXECUTED |
| 7 | Activity-owned avatar job | NOT EXECUTED |
| 8 | Fragment-owned recent-tasks job | NOT EXECUTED |
| 9 | Notification with `openTaskId` | NOT EXECUTED |
| 10 | Notification while on `fragment_Tareas` | NOT EXECUTED |
| 11 | Stale key is pruned | NOT EXECUTED |
| 12 | Cross-listener duplicate once | NOT EXECUTED |
| 13 | Group resolved once documented | NOT EXECUTED |
| 14 | Threading assumption explicit | NOT EXECUTED |
| 15 | Loads survive view recreation | NOT EXECUTED |
| 16 | Loads cancelled with the view | NOT EXECUTED |
| 17 | No external scope in constructor | NOT EXECUTED |
| 18 | Navigation decoupled | NOT EXECUTED |
| 19 | No manual bundle sites remain | NOT EXECUTED |
| 20 | Arguments are declared | NOT EXECUTED |
| 21 | `openTaskId` contract unchanged | NOT EXECUTED |

## Deviations from Design

1. **TD-4 `Login → PgPrincipal` continuation placement.** The design's Data Flow diagram draws the
   `binding.root.post { … Login → PgPrincipal }` inside `navegarDePresentacionALogin()`. That
   literal placement would chain an **unauthenticated** user from Login into `PgPrincipal` when
   `FragmentPresentacion` signals completion, contradicting the delta-spec requirement that
   `Login → PgPrincipal` "MUST remain conditional on the verified session and MUST occur only after
   the single coordinator observes the destination". Following task 4.2's wording ("the existing
   destination listener calls the funnel, and it posts the Login → PgPrincipal continuation as
   today"), the continuation stays in the **destination-listener (verified-session) path**; the
   funnel only performs `Presentación → Login`. Observable behavior is preserved exactly (verified
   users chain to PgPrincipal; unauthenticated users remain on Login).
2. **Code comments in Spanish.** The touched source files use Spanish comments throughout; the new
   comments (happy path + `[UNVERIFIED]`/limitation notes) were written in Spanish to match the
   surrounding file convention (`openspec/config.yaml` context languages: code Spanish, specs
   English). All SDD artifacts (this file, `tasks.md` updates, report) are in English. Flagged for
   reviewer preference.

## Issues Found

- None blocking. The Phase 2 compile gate resolved Safe Args on AGP 9.3.3 / Kotlin 2.2.10 on the
  first attempt, so no contingency was needed.
- Note: the worktree already contained modified `.idea/**` files before this run; they are not part
  of this change and were left untouched.

## Rollback Boundary

The change maps cleanly onto the three suggested commit sets, each independently revertible:

- **Commit set A (TD-5)**: `TareaRepositorioFirebase.kt` only — restores pre-pruning `observarTareas`.
- **Commit set B (navigation unit)**: `MainActivity.kt`, `FragmentPresentacion.kt`,
  `TareasHomeAdapter.kt`, `FragmentTareasPendientes.kt`, `FragmentPgPrincipal.kt`,
  `FragmentTareas.kt`, `nav_graph.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml` —
  restores original auto-login paths, adapter scope/nav coupling, all manual bundle sites, and
  removes the Safe Args plugin entry + `<argument>` declarations.
- **Commit set C (docs)**: `docs/architecture/TEAMTASK_GUIDE.md` only.

No Firestore data rollback is implied. Since this run does not commit, the sets are a plan for the
orchestrator's `git revert`; the worktree file→set mapping was dry-inspected and matches.

## Workload

- Authored additions 225 + deletions 65 = **290 changed lines** (11 files, excluding pre-existing
  `.idea/**`). Below the 400-line review budget; no `size:exception` needed.
- Delivery: direct-to-master, solo developer, no PRs (per tasks "Guard Notes").
