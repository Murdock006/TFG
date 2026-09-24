# Apply Progress: TeamTask Task Repository Consolidation

Cumulative apply record for `teamtask-task-repo-consolidation`. Fresh batch — no prior
apply-progress existed for this change.

- Change: `teamtask-task-repo-consolidation`
- Repo: `C:\Users\Victor\AndroidStudioProjects\TFG2`
- Branch: `master` (no commit/push performed by this phase; the orchestrator commits)
- Mode: **Standard** (`openspec/config.yaml` → `strict_tdd: false`; no test runner beyond the
  template `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt`)
- Artifact store: hybrid (this file + Engram mirror at
  `sdd/teamtask-task-repo-consolidation/apply-progress`)

## Completed Tasks (17/18)

- [x] 1.1 Repoint `TareasViewModel` to the canonical `TareaRepositorio` interface via
  `LocalizadorServicios.repositorioTarea`; imports updated.
- [x] 1.2 Consume the creator's `puntosReservados` in the no-confirmation transaction
  (`TareaRepositorioFirebase.marcarCompletada`); all reads before any write; creator write guarded
  on non-blank `creadoPor`.
- [x] 1.3 Restore the `pendiente`-state pre-branch guard on the confirm-required branch (A1).
- [x] 1.4 WU1 compile gate — passed.
- [x] 2.1 Delete `app/src/main/java/com/example/tfg/repositorio/RepositorioTareas.kt`.
- [x] 2.2 Delete `app/src/main/java/com/example/tfg/data/inmemory/TareaRepositorioInMemory.kt`
  (`data/inmemory/` retains `AuthRepositorioInMemory.kt` and `GrupoRepositorioInMemory.kt`).
- [x] 2.3 Remove the unused `TareaRepositorioInMemory` import in `LocalizadorServicios.kt`.
- [x] 2.4 Remove unused `TareasViewModel` members (`crearTarea`, `_tareaCreada`, `tareaCreada`,
  `resetTareaCreada`) and the now-unused `Tarea` import.
- [x] 2.5 WU2 grep + compile gate — passed.
- [x] 3.1 §6 debt register: `Status` column added; TD-1/TD-11 `Resolved`; TD-9 `Partially resolved`;
  TD-14 `Resolved`; others `Open`.
- [x] 3.2 §7 roadmap: `Status` column added; step 1 `Done`; step 2 `Done
  (teamtask-task-repo-consolidation)`; step 4 `Partially done`; steps 3, 5-7 `Pending`; trailing
  note reworded.
- [x] 3.3 §9 follow-up table: `Status` column added; this change `Done (this change)`;
  `teamtask-testing-emulator-strategy` `Done (teamtask-testing-emulator-strategy)`; others
  `Pending`.
- [x] 3.4 Discovered Drift folded in (§0/§2 stale sentences reworded; deleted-file pointers
  dropped from the §2 state index).
- [x] 3.5 Docs gate — passed.
- [x] 4.1 Compile gate — passed.
- [x] 4.2 Grep audits — passed.
- [ ] 4.3 Manual verification matrix — **NOT EXECUTED** (see below).
- [x] 4.4 Stack-purity audit — passed.

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/src/main/java/com/example/tfg/viewmodel/TareasViewModel.kt` | Modified | Dependency switched to `TareaRepositorio = LocalizadorServicios.repositorioTarea`; unused members and `Tarea` import removed |
| `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` | Modified | No-confirmation transaction now reads the creator and consumes `puntosReservados`; A1 pre-branch `pendiente` guard added after the auto-assign guard |
| `app/src/main/java/com/example/tfg/repositorio/RepositorioTareas.kt` | Removed | Dead simplified repository deleted |
| `app/src/main/java/com/example/tfg/data/inmemory/TareaRepositorioInMemory.kt` | Removed | Never-wired in-memory task repository deleted |
| `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` | Modified | Unused `TareaRepositorioInMemory` import removed |
| `docs/architecture/TEAMTASK_GUIDE.md` | Modified | §0/§2 drift fixes; §6/§7/§9 `Status` columns added |
| `openspec/changes/teamtask-task-repo-consolidation/tasks.md` | Modified | 17 tasks marked `[x]`; 4.3 left pending (manual) |

No Gradle, Firestore-contract, UI-signature, `.idea`, canonical `openspec/specs/**`, delta, design,
or proposal change. No commit or push.

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | No automated test net exists (`app/src/test` and `app/src/androidTest` contain only the template `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt`; `strict_tdd: false`). The focused proving command for WU1/WU2 is the compile gate below; WU3 is docs-only (manual row/status check). Result: compile gate `BUILD SUCCESSFUL`; docs statuses verified against the `implementation-recipes` delta. |
| Runtime harness command/scenario and exact result | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` → `BUILD SUCCESSFUL in 29s` (40 actionable tasks: 2 executed, 38 up-to-date); `:app:compileReleaseKotlin` and `:app:compileEmulatorKotlin` both executed, zero unresolved references. Device/emulator runtime path is deferred (task 4.3). |
| Rollback boundary | Revert the change commit(s): restores `RepositorioTareas.kt`, `TareaRepositorioInMemory.kt`, the `LocalizadorServicios.kt` import, the original `TareasViewModel` dependency/members, the original `marcarCompletada` transaction, and the guide edits. No Firestore data rollback implied (the fix only corrects future writes). |

## Gates — Observed Results

| Gate | Command | Observed result |
|---|---|---|
| Compile (1.4 / 2.5 / 4.1) | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` | `BUILD SUCCESSFUL in 29s`; both compile tasks executed; zero unresolved references. (Run once; covers 1.4, 2.5 and 4.1.) |
| Grep — simplified repo removed (2.5 / 4.2) | `Select-String "RepositorioTareas"` over `app/src/main` (and `app/src`) | Zero matches. |
| Grep — in-memory task repo removed (2.5 / 4.2) | `Select-String "TareaRepositorioInMemory"` over `app/src/main` (and `app/src`) | Zero matches. |
| Grep — dead VM members removed (2.5 / 4.2) | `Select-String "tareaCreada\|resetTareaCreada"` over `app/src/main` (and `app/src`) | Zero matches. |
| Grep — no new direct Firebase client (4.2 / 4.4) | `Select-String -CaseSensitive "FirebaseFirestore\.getInstance\|Firebase\.firestore"` over `app/src/main` | Only `service/firebase/FirebaseComposition.kt:96` (`FirebaseFirestore.getInstance(app)`) — unchanged posture. Note: the default `Select-String` match is case-insensitive and over-matched `com.google.firebase.firestore.*` imports; the case-sensitive rerun is the authoritative result. |
| Stack purity preserved (4.4) | `Select-String "androidx\.compose\|dagger\.hilt\|androidx\.room\|retrofit2"` over `app/` excluding `build/`, plus Gradle files | Zero matches; no new matches; no new dependency. |
| Docs gate (3.5) | Status columns vs `specs/implementation-recipes/spec.md`; headings `## 0`–`## 9` | TD-1 `Resolved (teamtask-task-repo-consolidation)`, TD-9 `Partially resolved (teamtask-testing-emulator-strategy)`, TD-11 `Resolved (teamtask-task-repo-consolidation)`, TD-14 `Resolved (teamtask-testing-emulator-strategy)` — all match the delta. Roadmap step 1 `Done`, step 2 `Done (teamtask-task-repo-consolidation)`, step 4 `Partially done` — all match. Headings `## 0` through `## 9` present. `RepositorioTareas` appears only in the retained §6 TD-1/TD-11 evidence cells. |
| Scope gate | `git status --short` | Only the six authorized targets changed (plus the pre-existing `.idea/*` modifications and the untracked `tasks.md`, both present before this batch). No commit/push. |

## Manual Verification Matrix (task 4.3) — NOT EXECUTED

Status: **NOT EXECUTED — pending manual verification.** This run had no device and no Firebase
emulators; booting either is explicitly outside this change's execution scope. No matrix result is
claimed. The matrix (rows 1-8 from the design, row 9 the A1 guard row) must be run by a human on an
emulator/device before release:

| # | Check | How | Expected | Status |
|---|---|---|---|---|
| 1 | Reservation at create | Create a task with `puntos>0`, inspect `usuarios/{creador}` | `puntos` decreased, `puntosReservados` increased | NOT EXECUTED |
| 2 | No-confirmation reservation consumption | `requiereConfirmacion=false`, `puntos=100`, mark complete | `puntosReservados` decreased by 100 (coerced `>= 0`) | NOT EXECUTED |
| 3 | No-confirmation executor credit | Same task, inspect `usuarios/{ejecutorUid}` | `puntos` +100; `puntosRecompensa` +10 | NOT EXECUTED |
| 4 | Reservation decrement coercion | Creator `puntosReservados=30`, complete 100-point no-confirm task | `puntosReservados` → 0, never negative | NOT EXECUTED |
| 5 | Executor credit follows argument | `marcarCompletada(tareaId, "uX")` with `asignadoA="uE"` | `uX.puntos` increases; `uE.puntos` unchanged | NOT EXECUTED |
| 6 | No creator document created | Complete a task with blank `creadoPor` | Executor credited; no `usuarios` doc created for a creator | NOT EXECUTED |
| 7 | Confirmation regression | Confirm a `pendiente_confirmacion` task | `puntosReservados -= puntos` (coerced); confirm-path points/racha | NOT EXECUTED |
| 8 | Auto-assign rejection | Complete a task with `creadoPor == asignadoA` | `Result.failure`; no state/points written | NOT EXECUTED |
| 9 | Confirm-required branch state guard (A1) | `marcarCompletada` on a non-`pendiente` task with `requiereConfirmacion=true` | `Result.failure("Tarea no está en estado pendiente")`; no state written | NOT EXECUTED |

## Deviations from Design

- **None substantive.** Final file state matches `design.md` §Detailed Changes 1-4, including the
  exact transaction shape and the A1 guard placement (immediately after the auto-assign guard at
  `:396`, before the `requiereConfirmacion` branch).
- The `Tarea` import in `TareasViewModel.kt` was retained through task 1.1 and removed in 2.4, per
  the tasks contract (the design's §1 lists the import removal together; both paths converge to the
  same final state).

## Issues Found

- **Manual matrix cannot run here (4.3).** No device/emulators available; documented as NOT
  EXECUTED above. No automated coverage exists for the points paths (roadmap step 5 is a separate
  change), so rows 1-9 remain the only behavioral proof.
- **Grep case-sensitivity.** `Select-String` is case-insensitive by default, so the "no new direct
  Firebase client" pattern initially matched `com.google.firebase.firestore.*` imports. The
  case-sensitive rerun yields exactly the expected single match (`FirebaseComposition.kt:96`).
- **Residual stale sentence outside task scope.** `TEAMTASK_GUIDE.md` §8 "Archive boundary and
  rollback" still says "no application code is in scope" for the architecture-guide apply phase.
  This is historical context for the `teamtask-architecture-guide` change and was not in the
  tasks/design drift scope (§0/§2 only). Flagged as a candidate follow-up; not edited to avoid
  unauthorized scope expansion.
- **Skill conflict (resolved in favor of the project contract).** The `android-mvvm` skill mandates
  Hilt/Retrofit/Room/Compose, which this project explicitly forbids. The manual
  `LocalizadorServicios` locator, XML/Fragments/ViewBinding, and constructor-default injection were
  preserved per the design; no Hilt/Room/Retrofit/Compose was introduced.

## Remaining Tasks

- [ ] 4.3 Manual verification matrix — pending human execution on an emulator/device.

## Workload / PR Boundary

- Mode: solo dev, direct commits to `master`, **no PRs** — Review Workload Forecast is
  informational only.
- Current work unit: full change (WU1 + WU2 + WU3) delivered as one direct-to-`master` batch.
- Boundary: starts from the pre-change dual-repo state; ends with the canonical path wired, the
  reservation leak closed, dead code removed, and the guide updated. Rollback = `git revert` of the
  change commit(s).
- Estimated review budget impact: raw additions+deletions dominated by two whole-file deletions
  (`RepositorioTareas.kt`, `TareaRepositorioInMemory.kt`); authored logic change is small.

## Status

17/18 tasks complete. Task 4.3 (manual matrix) pending manual verification — outside this run's
execution scope. All in-scope work is done; compile, grep, docs, and stack-purity gates pass.
