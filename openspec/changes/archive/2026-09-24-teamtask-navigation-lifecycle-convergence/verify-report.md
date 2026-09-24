# Verify Report: TeamTask navigation/lifecycle convergence

**Change**: `teamtask-navigation-lifecycle-convergence`
**Repository**: `C:\Users\Victor\AndroidStudioProjects\TFG2`
**Verification**: explicitly requested; read-only with respect to source, Gradle files, specs,
deltas, guide, and git state. Only this report and its Engram mirror were produced.
**Artifact store**: hybrid (file + Engram mirror `sdd/teamtask-navigation-lifecycle-convergence/verify`, project `TFG-TeamTask`).
**Verified HEAD**: `792f01b` (change commits `066f307` TD-5, `3bf35ad` navigation unit, `792f01b` docs).
**Overall status**: **partial** — every executable automated check passes; the authoritative
behavioral net (the 21-scenario manual matrix, task 8.4) is **NOT EXECUTED** (device required,
explicitly outside this change's execution scope).

## Executive Summary

The four debts (TD-4, TD-5, TD-6, TD-15) and the bounded collateral cleanup are implemented as
the design specifies, and all automated gates pass on a **fresh, forced** build:

- Compile gate green for both flavors with Safe Args generation provably executed.
- Existing JVM suite green: 4 tests, 0 failures (re-executed, not cached).
- All seven grep audits match their expected results; zero raw navigation-key sites remain.
- Static contract review against the 21 `navigation-lifecycle` scenarios finds the required
  invariants implemented: single recreation-safe Presentación→Login coordinator, union-based
  observer pruning with a single writer, adapter-owned scope with host navigation callback, and
  typed Safe Args arguments with the raw `openTaskId` intent extra preserved.

No CRITICAL or WARNING findings. Two documentation nits are recorded as SUGGESTIONs. The single
unfinished task is 8.4 (manual navigation matrix), which cannot be executed without a device; no
result is claimed for it.

## Checks Executed

### 1. Compile gate (authoritative) — PASS

Command:
```
.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain
```
Observed (first run, unmodified worktree): `BUILD SUCCESSFUL in 11s`; 42 tasks, all `UP-TO-DATE`;
`generateSafeArgsEmulator` / `generateSafeArgsRelease` present in the graph. Exit code `0`.

Because the first run was cached, a **forced** recompilation was then executed to obtain
non-cached evidence:
```
.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --rerun-tasks --no-daemon --console=plain
```
Observed: `generateSafeArgsEmulator`, `generateSafeArgsRelease`, `compileEmulatorKotlin`, and
`compileReleaseKotlin` all **re-executed** (no `UP-TO-DATE`), `BUILD SUCCESSFUL in 34s`, exit code `0`.
This proves Safe Args code generation actually runs on AGP 9.3.3 / Kotlin 2.2.10 and both flavors
compile against the generated class.

Generated artifact confirmed on disk:
```
app/build/generated/java/generateSafeArgsEmulator/com/example/tfg/vista/FragmentTareasArgs.kt
app/build/generated/java/generateSafeArgsDebug/com/example/tfg/vista/FragmentTareasArgs.kt
app/build/generated/java/generateSafeArgsRelease/com/example/tfg/vista/FragmentTareasArgs.kt
```
The generated class declares nullable `taskId`, `modo`, `categoria`, `toBundle()`, and
`fromBundle()` — exactly the typed argument contract the delta spec requires.

**Conclusion**: the Safe Args `[UNVERIFIED]` plugin-compatibility Open Question is closed; the
Fallback TD-15-2 was not needed.

### 2. JVM suite gate — PASS

Command:
```
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```
The first run was `UP-TO-DATE`; a forced re-execution was performed:
```
.\gradlew.bat :app:generateSafeArgsEmulator :app:testDebugUnitTest --rerun --no-daemon --console=plain
```
Observed: `> Task :app:testDebugUnitTest` executed (no `UP-TO-DATE`), `BUILD SUCCESSFUL in 12s`,
exit code `0`. Fresh test-result XML:
```
TEST-com.example.tfg.ExampleUnitTest.xml:   tests=1 failures=0 errors=0 skipped=0
TEST-com.example.tfg.util.AvatarImagenTest.xml: tests=3 failures=0 errors=0 skipped=0
```
**Result: 4 tests / 0 failures**, matching the baseline expectation.

### 3. Grep audits (8.3 / design verification table 3) — PASS

`ripgrep` is not installed; `git grep` was used. The worktree application files are committed
clean (`git status --short` lists only pre-existing `.idea/**` modifications).

| Audit | Command | Observed result | Expected | Verdict |
|---|---|---|---|---|
| No external scope param | `git grep -n "TareasHomeAdapter(" -- app/src/main/java` | `FragmentPgPrincipal.kt:189` and `FragmentTareasPendientes.kt:47` both pass `(this, parejaVM, tareasVM) { id -> … }`; no `CoroutineScope` argument | no external scope | PASS |
| Adapter scope internal-only | `git grep -n "CoroutineScope" -- …/TareasHomeAdapter.kt` | only `import kotlinx.coroutines.CoroutineScope` (`:26`) and the adapter-owned `private val scope = CoroutineScope(Dispatchers.Main.immediate + adapterJob)` (`:50`) | adapter-owned only | PASS |
| No raw navigation keys | `git grep -n -F -e 'putString("taskId"' -e 'getString("taskId"' -e 'putString("categoria"' -e 'getString("categoria"' -e 'putString("modo"' -e 'getString("modo"' -- app/src/main/java` | **zero matches** (exit 1) | zero | PASS |
| Jobs cancelled | `git grep -n -F -e "avatarDrawerJob" -e "tareasHomeJob" -e ".destroy()" -- …/vista` | `avatarDrawerJob?.cancel()`+`=null` in `MainActivity.onDestroy` (`:231-232`); `tareasHomeJob?.cancel()`+`tareaAdapter?.destroy()` in `FragmentPgPrincipal.onDestroyView` (`:380-382`); `adapter?.destroy()` in `FragmentTareasPendientes.onDestroyView` (`:236`) | cancelled on destroy | PASS |
| Intent contract intact | `git grep -n "openTaskId" -- app/src/main/java` | `NotificationScheduler.kt:76` raw `putExtra("openTaskId", …)`; `MainActivity.kt:137,210` raw `getStringExtra("openTaskId")`; never a nav argument | raw extra only | PASS |
| No manual fragment nav in adapter | `git grep -n "findNavController" -- …/TareasHomeAdapter.kt` | **zero matches** (exit 1) | zero | PASS |
| Uncertainty markers | `git grep -n "\[UNVERIFIED\]" -- openspec` | markers present incl. the delta spec's threading scenario (`specs/navigation-lifecycle/spec.md:172,205`); the code marker lives at `TareaRepositorioFirebase.kt:211` | present | PASS |

Additional audit (single-owner + touched-file boundary):

- `git grep -n "action_fragment_Presentacion_to_fragment_Login"` → **exactly one** call site:
  `MainActivity.kt:325` (inside the private funnel). PASS (TD-4 single navigator).
- `git grep -n "action_fragment_Login_to_fragment_PgPrincipal"` → `MainActivity.kt:296`
  (verified-session continuation) and `FragmentLogin.kt:100` (user-driven login path, unchanged).
  Matches the design note that the manual login path keeps its own navigation.
- `git grep -n "by lazy" -- FragmentTareasPendientes.kt FragmentPgPrincipal.kt` → zero matches
  (no stale `by lazy` scope). PASS.
- `git diff --name-only HEAD~3 HEAD -- …/NotificationScheduler.kt` and `…/FragmentLogin.kt` →
  **empty** for both: the design's read-only files were not modified. PASS.

## Static Contract Review (21 navigation-lifecycle scenarios)

Review is static (source inspection). Behavioral scenarios marked "device" below are covered by
task 8.4 and are **NOT EXECUTED** here; the static evidence shows the required structure exists.

### TD-4 — single coordinator, recreation safety, behavior preservation

- **Single navigator**: `navegarDePresentacionALogin()` (`MainActivity.kt:322-326`) is the only
  caller of the Presentación→Login action (verified by grep; exactly one call site). Its
  `if (currentDestination?.id != R.id.fragment_Presentacion) return` live-destination guard makes
  duplicate signals no-ops. `FragmentPresentacion` no longer navigates; it signals
  `onPresentacionMensajesCompletados()` (`FragmentPresentacion.kt:50-52`). Matches design
  TD-4-1; satisfies scenario 4 structurally.
- **Recreation safety**: the listener is stored in the `autoLoginListener` field; registration
  does remove-then-add (`MainActivity.kt:304-306`), and `onDestroy` removes it (`:226-229`) and
  nulls it after firing (`:299-300`). One Activity instance cannot register two active navigators;
  a recreated Activity gets a fresh controller and its own listener. Satisfies scenario 3
  structurally.
- **Verified-session condition**: the `Login → PgPrincipal` continuation stays in the
  verified-session listener path, gated by `currentDestination?.id == fragment_Login`
  (`MainActivity.kt:293-298`); the funnel never performs it. Unauthenticated users reach Login via
  the Fragment signal and remain there. Satisfies scenarios 1, 2, 4 structurally.

### TD-5 — union pruning, single writer, documented assumptions

- `aplicar(fuente, snap)` (`TareaRepositorioFirebase.kt:202-209`) is the single writer of
  `combinado`, records `idsPorFuente[fuente]`, prunes `combinado.keys` against the **union** of all
  recorded id sets (`:206-207`), and emits once. The three listeners (`:220,:225,:232`) delegate to
  it and preserve `close(error)` on error. `awaitClose` removes all three (`:238-242`).
- Union pruning (not single-snapshot) preserves live rows from other sources; for one source it
  equals that snapshot, giving parity with `observarTareasPorGrupo` (`:245-249`). Satisfies
  scenarios 11 and 12 structurally; scenarios 13 and 14 (documented once-resolved group and
  `[UNVERIFIED]` main-thread assumption) are satisfied by the comments at `:211-218`.

### TD-6 — adapter-owned scope + host callback + per-view construction

- Constructor drops `CoroutineScope` and adds `onTareaClick: (String) -> Unit`
  (`TareasHomeAdapter.kt:36-41`); the adapter owns `SupervisorJob` + `destroy()` (`:49-52`); the
  nav call is `onTareaClick(t.id)` (`:288`) with no `findNavController`. Per-bind cancellation at
  `onBindViewHolder` (`:101`). Hosts build the adapter per view
  (`FragmentTareasPendientes.kt:47-53`, `FragmentPgPrincipal.kt:189-195`) and destroy it in
  `onDestroyView`. Satisfies scenarios 15, 16, 17, 18 structurally.

### TD-15 — typed args everywhere; `openTaskId` preserved

- Plugin applied and version-matched (`app/build.gradle.kts:4`;
  `gradle/libs.versions.toml:40` `version.ref = "navigationFragment"` = `2.9.6` at `:16`).
- `nav_graph.xml:85-99` declares nullable `taskId`, `modo`, `categoria`.
- All writers use `FragmentTareasArgs(...)` (`MainActivity.kt:237`, `FragmentPgPrincipal.kt:82-336`,
  `FragmentTareasPendientes.kt:50`) and readers use `navArgs?.*`
  (`FragmentTareas.kt:56-57,60-61,82,159,209,230,401`). Zero raw-key sites (grep audit). Satisfies
  scenarios 19, 20, 21 structurally; `openTaskId` stays a raw extra (audit).

### Collateral cleanup + notification duplicate-stack

- `avatarDrawerJob` cancelled in `onDestroy` (`MainActivity.kt:231-232`); `tareasHomeJob` cancelled
  in `onDestroyView` (`FragmentPgPrincipal.kt:380-382`); `FragmentTareas` clears its two bindings
  in its own bounded `onDestroyView` (`FragmentTareas.kt:538-543`). `NotificationScheduler.kt`
  untouched.
- Duplicate-stack fix: `handleOpenTaskId` (`MainActivity.kt:235-251`) applies
  `popUpTo(fragment_Tareas, true)` only when the current destination is already `fragment_Tareas`,
  otherwise navigating as before. This is exactly design TD-4-3 and is structurally guaranteed (the
  destination is on the back stack when the guard holds). Behavior otherwise unchanged.

### Scenario coverage matrix (static)

| # | Scenario | Static status | Notes |
|---|---|---|---|
| 1 | Logged in, email not verified | STRUCTURE OK | `MainActivity.kt:263-268` `signOut()`+Toast, returns before nav |
| 2 | Logged in, email verified | STRUCTURE OK | group load `:274`; chain `:289-298` |
| 3 | Config change during auth window | STRUCTURE OK (device) | remove-then-add `:304-306`; remove in `onDestroy` `:226-229` |
| 4 | Presentación message sequence preserved | STRUCTURE OK | 3 messages then single signal `FragmentPresentacion.kt:38-56` |
| 5 | ViewModel-owned group listener | OUT OF CHANGE SCOPE | untouched (`ParejaViewModel`) |
| 6 | Activity-owned notifications listener | OUT OF CHANGE SCOPE | `notificacionesJob` logic preserved `MainActivity.kt:220-224,337-380` |
| 7 | Activity-owned avatar job | STRUCTURE OK | `:231-232` |
| 8 | Fragment-owned recent-tasks job | STRUCTURE OK | `FragmentPgPrincipal.kt:380-382` |
| 9 | Notification with `openTaskId` | STRUCTURE OK | typed args `MainActivity.kt:237` |
| 10 | Notification while on `fragment_Tareas` | STRUCTURE OK | conditional `popUpTo` `:238-247` |
| 11 | Stale key is pruned | STRUCTURE OK | union prune `TareaRepositorioFirebase.kt:206-207` |
| 12 | Cross-listener duplicate once | STRUCTURE OK | single map keyed by id `:196,204` |
| 13 | Group resolved once documented | CONFIRMED | comment `:216-218` |
| 14 | Threading assumption explicit | CONFIRMED | `[UNVERIFIED]` comment `:211-214` |
| 15 | Loads survive view recreation | STRUCTURE OK (device) | per-view adapter + fresh scope |
| 16 | Loads cancelled with the view | STRUCTURE OK | `destroy()` in `onDestroyView` |
| 17 | No external scope in constructor | CONFIRMED | constructor `TareasHomeAdapter.kt:36-41` |
| 18 | Navigation decoupled | CONFIRMED | `onTareaClick`; zero `findNavController` |
| 19 | No manual bundle sites remain | CONFIRMED | grep audit zero |
| 20 | Arguments are declared | CONFIRMED | `nav_graph.xml:85-99` |
| 21 | `openTaskId` contract unchanged | CONFIRMED | raw extra audit |

Rows 1-4, 7-12, 15-16 include runtime behavior that only a device can confirm; the static evidence
above shows the required code paths exist but is not runtime proof.

## Findings

### CRITICAL
None.

### WARNING
None.

### SUGGESTION

1. **Design decision-count label drift (doc-only).** `tasks.md:8` describes `design.md` as
   containing "7 decisions", but the file has **8** `### Decision` headings (TD-4-1, TD-4-2,
   TD-4-3, TD-5-1, TD-6-1, TD-6-2, TD-15-1, TD-15-2). Cosmetic; no behavioral impact.
2. **Guide §6 retains pre-drift evidence citations (doc-only).** `docs/architecture/TEAMTASK_GUIDE.md`
   §6 marks TD-4/5/6/15 `Resolved` (correct), but its evidence columns still cite the older
   pre-apply line ranges (e.g. TD-4 `MainActivity.kt:255-275`, TD-5
   `TareaRepositorioFirebase.kt:184-212`, TD-6 `TareasHomeAdapter.kt:39,145-210`, TD-15
   `MainActivity.kt:223`). The delta specs record the corrected citations for archive; the guide
   itself was not line-corrected. Optional cleanup during archive.

## Unfinished Task and Unavailable Checks

- **Task 8.4 — Manual navigation matrix**: **NOT EXECUTED**. A device/emulator is required and is
  explicitly outside this run's execution scope; no device was booted. All 21 rows are reported as
  **NOT EXECUTED — pending manual verification on a device**. No result is claimed. (This matches
  the apply-progress record.)
- **Task 8.5** (rollback rehearsal) was completed in apply and is supported here by inspection:
  the change maps to three revertible commit sets (`066f307` TD-5, `3bf35ad` navigation unit,
  `792f01b` docs); `git diff HEAD~3 HEAD` shows only the expected files plus the SDD artifacts, with
  `NotificationScheduler.kt` and `FragmentLogin.kt` untouched.
- **No automated behavioral net exists.** `openspec/config.yaml` sets `strict_tdd: false`; roadmap
  step 5 (focused tests) is still pending. The existing JVM suite exercises only the pure-JVM
  `AvatarImagen` helpers and `ExampleUnitTest`; it does **not** cover any navigation/lifecycle path
  changed here. The green suite is a regression/compile gate for untouched code, **not** evidence
  that TD-4/5/6/15 behave correctly. The manual matrix is the only behavioral net, and it is not
  executed here.

## Apply Deviations Noted (as required)

1. **TD-4 `Login → PgPrincipal` continuation placement (deviation #1).** The design's data-flow
   diagram drew the continuation inside the funnel; the implementation kept it in the
   verified-session listener path. Verified in source: the continuation lives at
   `MainActivity.kt:293-298` (listener path) and the funnel (`:322-326`) only performs
   Presentación→Login. This is **correct and required**: placing the continuation in the funnel would
   chain an *unauthenticated* user from Login into PgPrincipal, contradicting the delta-spec
   requirement that `Login → PgPrincipal` stays conditional on the verified session. Observable
   behavior is preserved (verified users chain; unauthenticated users stay on Login). **Accepted.**
2. **Spanish code comments.** New comments in the touched source files are in Spanish, matching the
   surrounding file convention (`openspec/config.yaml`: code Spanish, specs English). All SDD
   artifacts remain in English. **Accepted** (consistency with the existing codebase).

## Rollback Boundary (confirmed)

Rollback is a `git revert` of the three commit sets: set A (`TareaRepositorioFirebase.kt`, TD-5),
set B (navigation/lifecycle files + Gradle + `nav_graph.xml`, TD-4/6/15 + cleanup), set C
(guide). No Firestore data rollback is implied. TD-5 is independent of the navigation unit.

## Verification Limitations

- The compile and test gates were ultimately forced (`--rerun-tasks` / `--rerun`) to avoid relying
  on cached results; the initial plain runs reported `UP-TO-DATE`.
- No device/emulator, no integration harness, and no automated navigation test exist; all runtime
  behavior claims rest on static inspection only.
- The `[UNVERIFIED]` Firestore main-thread-confinement assumption is deliberate and documented; the
  pruning fix is correct under that assumption and remains an unconfirmed deployment assumption.

## Conclusion

Automated verification is **green and fresh**: both flavors compile with Safe Args generation
executing, the JVM suite is 4/0, and every grep audit matches. The static contract review confirms
the TD-4/5/6/15 invariants and the preserved `openTaskId` contract. The change is structurally
complete; it is **not** behaviorally certified because the 21-scenario manual matrix (task 8.4)
remains unexecuted. Status: **partial**. Recommend proceeding to a device run of task 8.4 before
archive, or accepting the documented manual-verification debt if archive must proceed now.
