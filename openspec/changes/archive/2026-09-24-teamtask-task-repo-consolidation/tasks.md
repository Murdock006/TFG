# Tasks: TeamTask Task Repository Consolidation

Implementation breakdown for `teamtask-task-repo-consolidation`. Baseline is the design's
Sequenced Work Units (WU1 canonical dependency switch + reservation fix, WU2 dead code removal,
WU3 docs update), plus the two orchestrator-accepted adjustments recorded under
[Bounded Additions and Orchestrator Adjustments](#bounded-additions-and-orchestrator-adjustments).

Delivery context: solo developer, commits go directly to `master`, **no pull requests**. The
Review Workload Forecast below is **informational only** — no PR slicing, chain strategy, or
size-exception decision applies.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~470 raw additions+deletions (≈407 deletions, ≈63 additions); authored logic change ≈35-40 lines |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Not applicable — single direct-to-`master` change (no PRs) |
| Delivery strategy | auto-chain |
| Chain strategy | pending (no chain needed; no-PR delivery) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

**Informational note (no-PR delivery).** The raw additions+deletions figure exceeds 400 only
because two confirmed-dead files are deleted wholesale (`RepositorioTareas.kt` ≈202 lines,
`TareaRepositorioInMemory.kt` ≈205 lines). The reviewable authored logic — the ViewModel
dependency switch, the reservation-consumption fix, the bounded guard, and the guide edits — is
≈35-40 lines. The approved `proposal.md` (Dependencies) and `design.md` (Rollback Boundary)
both state the change is "dominated by deletions" and low-risk. Since commits land directly on
`master` with no reviewer-facing PR, this forecast carries no gating weight.

### Suggested Work Units

Each unit is independently verifiable and has its own revert boundary. "Likely PR" is recorded
for traceability only; there is no PR in this delivery.

| Unit | Goal | Likely PR | Focused proving command | Runtime harness | Rollback boundary |
|------|------|-----------|-------------------------|-----------------|-------------------|
| WU1 | Canonical dependency switch + reservation fix + bounded guard | slice 1 (informational) | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` | Emulator/device manual matrix rows 1-6, 8-9 | Revert `TareasViewModel.kt` and `TareaRepositorioFirebase.kt` edits |
| WU2 | Dead code removal | slice 2 (informational) | `rg -n "RepositorioTareas\|TareaRepositorioInMemory\|tareaCreada\|resetTareaCreada" app/src/main` (expect zero) | Recompile both build types after deletion | Restore the two deleted files, the import, and the members (revert) |
| WU3 | Docs update (`TEAMTASK_GUIDE.md`) | slice 3 (informational) | Manual row/status check against `implementation-recipes` delta | N/A — docs only; no runtime | Revert the guide edits |

WU1 and WU2 may land together (WU2 depends on WU1 only for a clean compile); WU3 is independent.

## Global Gates

These gates apply to the whole change and are re-run at the end (Phase 4). They are the only
verification available: `app/src/test` and `app/src/androidTest` contain only the template
`ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt` (read-only), and roadmap step 5 (tests) is a
separate change. `openspec/config.yaml` sets `strict_tdd: false`.

1. **Compile gate** (both build types exist: `app/build.gradle.kts:27-47` — `emulator` at
   `:28-35`, `release` at `:36-46`):

   ```powershell
   .\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain
   ```

   Expected: `BUILD SUCCESSFUL`, no unresolved references.

2. **Grep audits** under `app/src/main`:

   | Audit | Command | Expected |
   |---|---|---|
   | Simplified repo removed | `rg -n "RepositorioTareas" app/src/main` | Zero matches |
   | In-memory task repo removed | `rg -n "TareaRepositorioInMemory" app/src/main` | Zero matches |
   | Dead VM members removed | `rg -n "tareaCreada\|resetTareaCreada" app/src/main` | Zero matches |
   | No new direct Firebase client | `rg -n "FirebaseFirestore.getInstance\|Firebase\.firestore" app/src/main` | Only `FirebaseComposition.kt:96` (unchanged posture) |
   | Stack purity preserved | `rg -n "androidx.compose\|dagger.hilt\|androidx.room\|retrofit2" app/` | No new matches |

3. **Docs gate**: guide `Status` columns match `openspec/changes/teamtask-task-repo-consolidation/specs/implementation-recipes/spec.md`
   (read-only) statuses for TD-1/TD-9/TD-11/TD-14 and roadmap steps 1/2/4.

4. **Scope gate**: no application code changed outside the six authorized targets; no Gradle,
   Firestore-contract, or UI-signature change. No commit is made by this phase.

## Phase 1 — WU1: Canonical dependency switch + reservation fix

Goal: point the completion flow at the canonical `TareaRepositorio` interface and close the
latent points leak before the switch goes live. Ends with the compile gate.

- [x] **1.1 Repoint `TareasViewModel` to the canonical interface.**
  - Change line 11 of `app/src/main/java/com/example/tfg/viewmodel/TareasViewModel.kt` from
    `class TareasViewModel(private val repo: RepositorioTareas = RepositorioTareas()) : ViewModel()`
    to `class TareasViewModel(private val repo: TareaRepositorio = LocalizadorServicios.repositorioTarea) : ViewModel()`.
  - Imports: remove `com.example.tfg.repositorio.RepositorioTareas` (line 6); add
    `com.example.tfg.repositorio.TareaRepositorio` and `com.example.tfg.service.LocalizadorServicios`.
    Keep the `com.example.tfg.modelo.Tarea` import (line 5) for now — it is only removed in 2.4
    together with the `crearTarea` member that uses it, otherwise the file will not compile.
  - Do NOT touch `marcarCompletada`/`confirmarTarea`/reset signatures — UI callers
    (`TareasHomeAdapter.kt:37,167,256`, `FragmentTareas.kt:46,497,512,519,585`) stay unchanged.
  - **Verify**: Global Gate 1 (compile) passes; the default-argument locator pattern matches the
    precedent at `app/src/main/java/com/example/tfg/viewmodel/VistaModeloAuth.kt:14-16` (read-only).
  - **Rollback**: revert this single-file edit; the concrete `RepositorioTareas` dependency returns.
  - **Depends/Parallel**: none. Independent of 1.2/1.3 (different file); all three share the
    Phase 1 compile gate and can be done in one session.

- [x] **1.2 Consume the creator's `puntosReservados` in the no-confirmation transaction.**
  - In `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt`, replace the
    transaction body at lines 405-435 with the design's shape (design.md §Detailed Changes 2):
    derive `creadorRef` from `tareaTx.creadoPor` (guard `!isNullOrBlank()`), read it **before any
    write**, and after the existing task-state and executor-credit writes apply
    `t.update(creadorRef, "puntosReservados", (reservados - tareaTx.puntos).coerceAtLeast(0))`.
  - Preserve unchanged: the pre-transaction read at `:394`, the `esAutoasignada` /
    `requiereConfirmacion` guards at `:396-402`, the executor credit and reward floor at
    `:411-432`. Mirror confirm-path semantics exactly (`:491-495`).
  - Invariants: every read before any write; executor credit follows the `ejecutorUid` argument
    and is NOT overridden by `tarea.asignadoA` (resolves TD-11); blank `creadoPor` ⇒ no creator
    write and no creator document created.
  - Do NOT add the confirmation-path in-transaction auto-assignment re-check (`:460`) here —
    proposal decision 2 bounds this branch to the reservation-consumption fix only.
  - **Verify**: manual matrix rows 2, 3, 4, 5, 6 (Phase 4.3) after WU1 lands.
  - **Rollback**: revert the transaction block to the pre-change shape; no Firestore data rollback
    is implied (the fix only corrects future writes).
  - **Depends/Parallel**: none functionally; shares the Phase 1 compile gate with 1.1/1.3.

- [x] **1.3 Restore the `pendiente`-state guard on the confirm-required branch (bounded addition).**
  - In `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt`, immediately
    after the auto-assign guard at line 396 and before the `requiereConfirmacion` branch at
    `:399-402`, insert a pre-branch check:
    `if (tarea.estado != "pendiente") return Result.failure(Exception("Tarea no está en estado pendiente"))`.
  - Rationale: safety parity with the deleted `RepositorioTareas.kt:72` (read-only, deleted by
    2.1) and with the no-confirm in-transaction check at `TareaRepositorioFirebase.kt:408`.
    Consolidation must not lose an existing protection; this is safety parity, not a product
    change (see [Bounded Additions](#bounded-additions-and-orchestrator-adjustments)).
  - **Verify**: manual matrix row 9 (Phase 4.3) — a non-`pendiente` task with
    `requiereConfirmacion=true` returns `Result.failure` and writes no state.
  - **Rollback**: remove the inserted line; the branch returns to the pre-change behavior.
  - **Depends/Parallel**: none; same file as 1.2, apply after 1.2 to keep the transaction edit
    distinct from the pre-branch guard.

- [x] **1.4 Run the WU1 compile gate.**
  - Run Global Gate 1 and confirm both `emulator` and `release` Kotlin compilation succeed.
  - **Verify**: `BUILD SUCCESSFUL`, zero unresolved references.
  - **Rollback**: n/a (verification task); on failure, fix 1.1-1.3 before proceeding.
  - **Depends/Parallel**: requires 1.1, 1.2, 1.3. Blocks Phase 2.

## Phase 2 — WU2: Dead code removal

Goal: remove the simplified repository and never-wired artifacts. Ends with grep audits + compile.

- [x] **2.1 Delete `app/src/main/java/com/example/tfg/repositorio/RepositorioTareas.kt`.**
  - Whole-file removal (≈202 lines). Its only references were `TareasViewModel.kt:6,11`, both
    removed by 1.1.
  - **Verify**: Global Gate 2 audit "Simplified repo removed" returns zero matches.
  - **Rollback**: restore the file via `git revert` of the change commit(s).
  - **Depends/Parallel**: requires 1.1 (import/dependency removed). Parallel with 2.2/2.3/2.4.

- [x] **2.2 Delete `app/src/main/java/com/example/tfg/data/inmemory/TareaRepositorioInMemory.kt`.**
  - Whole-file removal (≈205 lines). It implements `TareaRepositorio` but is never instantiated;
    `LocalizadorServicios` always wires `TareaRepositorioFirebase` (`LocalizadorServicios.kt:24-27`).
  - Keep `data/inmemory/` and its other files (`AuthRepositorioInMemory.kt`,
    `GrupoRepositorioInMemory.kt`).
  - **Verify**: Global Gate 2 audit "In-memory task repo removed" returns zero matches.
  - **Rollback**: restore the file via `git revert`.
  - **Depends/Parallel**: requires 2.3 (import removed) for a clean compile; parallel with 2.1/2.4.

- [x] **2.3 Remove the unused import at `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt:7`.**
  - Delete `import com.example.tfg.data.inmemory.TareaRepositorioInMemory`. Leave the canonical
    `repositorioTarea` wiring at `:24-27` unchanged.
  - **Verify**: Global Gate 2 audit "In-memory task repo removed" returns zero matches; compile
    gate still passes.
  - **Rollback**: re-add the import line via `git revert`.
  - **Depends/Parallel**: pairs with 2.2; parallel with 2.1/2.4.

- [x] **2.4 Remove the unused `TareasViewModel` members and the now-unused `Tarea` import.**
  - In `app/src/main/java/com/example/tfg/viewmodel/TareasViewModel.kt` delete: `crearTarea`
    (lines 22-27), `_tareaCreada` (line 13), `tareaCreada` (line 14), `resetTareaCreada`
    (lines 52-54), and the `import com.example.tfg.modelo.Tarea` (line 5).
  - Keep `marcarCompletada`, `confirmarTarea`, `marcarCompletadaState`, `confirmarTareaState`,
    `resetMarcarCompletadaState`, `resetConfirmarTareaState`.
  - **Verify**: Global Gate 2 audit "Dead VM members removed" returns zero matches; compile gate
    passes (no dangling `Tarea` reference).
  - **Rollback**: restore the members and import via `git revert`.
  - **Depends/Parallel**: requires 1.1; parallel with 2.1-2.3.

- [x] **2.5 Run the WU2 grep + compile gate.**
  - Run Global Gate 2 (all five audits) and Global Gate 1.
  - **Verify**: all audits match expectations; `BUILD SUCCESSFUL`.
  - **Rollback**: n/a (verification task); on failure, resolve dangling references before proceeding.
  - **Depends/Parallel**: requires 2.1-2.4. Blocks Phase 3.

## Phase 3 — WU3: Documentation update (`docs/architecture/TEAMTASK_GUIDE.md`)

Goal: make the guide honest after consolidation. This work unit also folds in the design's
Discovered Drift (§0/§2 stale sentences), per the orchestrator-accepted adjustment A2. Evidence
cells are retained (including deleted-file pointers) so the historical trail survives.

- [x] **3.1 Update the §6 technical debt register (add `Status` column).**
  - In `docs/architecture/TEAMTASK_GUIDE.md`, append a `Status` column to the header/separator
    (lines 140-141) and every row (142-156).
  - TD-1 (line 142) → `Resolved (teamtask-task-repo-consolidation)`; TD-11 (line 152) →
    `Resolved (teamtask-task-repo-consolidation)`. Evidence cells unchanged.
  - Consistency alignment with the canonical spec: TD-9 (line 150) →
    `Partially resolved (teamtask-testing-emulator-strategy)`; TD-14 (line 155) →
    `Resolved (teamtask-testing-emulator-strategy)`. All other rows → `Open`.
  - **Verify**: Global Gate 3 — statuses match
    `openspec/changes/teamtask-task-repo-consolidation/specs/implementation-recipes/spec.md`
    (read-only) lines 29-43.
  - **Rollback**: revert the guide edit via `git revert`.
  - **Depends/Parallel**: independent of Phases 1-2. Parallel with 3.2/3.3/3.4.

- [x] **3.2 Update the §7 convergence roadmap (add `Status` column).**
  - Append `Status` to header/separator (lines 165-166) and rows (167-173): step 1 → `Done`;
    step 2 (line 168) → `Done (teamtask-task-repo-consolidation)`; step 4 (line 170) →
    `Partially done`; steps 3, 5-7 → `Pending`.
  - Update the trailing note (lines 175-176): step 2 is now done, so "Steps 2-7 require their own
    baseline" and "None is part of this docs-only apply phase" are stale — reword to reflect that
    step 2 landed in this change.
  - **Verify**: Global Gate 3 — matches the `implementation-recipes` delta roadmap table (lines 50-58).
  - **Rollback**: revert the guide edit via `git revert`.
  - **Depends/Parallel**: parallel with 3.1/3.3/3.4.

- [x] **3.3 Update the §9 follow-up changes table (add `Status` column).**
  - Append `Status` to header/separator (lines 216-217) and rows (218-223):
    `teamtask-task-repo-consolidation` (line 219) → `Done (this change)`;
    `teamtask-testing-emulator-strategy` (line 221) → `Done (teamtask-testing-emulator-strategy)`;
    all other rows → `Pending`.
  - **Verify**: Global Gate 3 — statuses match the delivered-change record in the
    `implementation-recipes` delta (lines 52-58).
  - **Rollback**: revert the guide edit via `git revert`.
  - **Depends/Parallel**: parallel with 3.1/3.2/3.4.

- [x] **3.4 Fold in the Discovered Drift (§0/§2 stale sentences).**
  - §0 (lines 10-12): "This change is **docs-only**", "No application code is touched", and the
    dirty-worktree line are scoped to the architecture-guide change and read as stale once a code
    change edits the guide. Reword so the note does not describe this change as docs-only.
  - §2 (line 49): "two task repositories have divergent financial rules" becomes false — reword
    to the single canonical repository.
  - §2 (lines 57-59): state-index rows cite the deleted `RepositorioTareas.kt:44-52`, `:73-129`,
    `:141-200`; drop those pointers, keep the `TareaRepositorioFirebase` evidence.
  - §2 (line 69): "The asymmetry behind TD-1 ... are deliberate debt signals" no longer applies —
    reword to reflect TD-1 resolved.
  - Leave line 43 (`ViewModel → service locator: TareasViewModel.kt:11`) unchanged — it stays
    accurate because the ViewModel still reaches the locator through its default argument.
  - **Verify**: no remaining sentence asserts two task repositories or a docs-only scope for this
    change; `rg -n "RepositorioTareas" docs/architecture/TEAMTASK_GUIDE.md` shows only retained
    §6/§7 historical evidence cells.
  - **Rollback**: revert the guide edit via `git revert`.
  - **Depends/Parallel**: parallel with 3.1-3.3.

- [x] **3.5 Run the docs gate.**
  - Run Global Gate 3.
  - **Verify**: every added `Status` value matches the canonical `implementation-recipes` delta;
    headings `## 0` through `## 9` still present.
  - **Rollback**: n/a (verification task).
  - **Depends/Parallel**: requires 3.1-3.4. Blocks Phase 4.

## Phase 4 — Verification (global)

Goal: prove the consolidated path and the guide. This is the design's Verification Plan, plus the
new guard row (A1). There is no automated test net; state that explicitly in the apply report.

- [x] **4.1 Run the compile gate.**
  - Run Global Gate 1.
  - **Verify**: `BUILD SUCCESSFUL` for both build types.
  - **Rollback**: n/a.
  - **Depends/Parallel**: requires Phases 1-2. Parallel with 4.2/4.3/4.4.

- [x] **4.2 Run the grep audits.**
  - Run Global Gate 2 (all five audits).
  - **Verify**: all expected results met.
  - **Rollback**: n/a.
  - **Depends/Parallel**: requires Phases 1-2. Parallel with 4.1/4.3/4.4.

- [x] **4.3 Manual verification matrix — WAIVED by owner decision (2026-09-26).** The owner decided the app will be validated by real user testing instead of a hand-executed device matrix; no rows were claimed as passed. All executable checks for this change are green (compile gates, grep audits, JVM suite).
  - Execute the matrix below. Rows 1-8 are the design's matrix; row 9 is the A1 guard row.
  - **Verify**: every row matches its expected result.
  - **Rollback**: n/a (verification task). On failure, diagnose the specific Phase 1 task.
  - **Depends/Parallel**: requires Phase 1 (and Phase 2 for the "no direct repo" rows). Parallel
    with 4.1/4.2/4.4.

  | # | Check | How | Expected |
  |---|---|---|---|
  | 1 | Reservation at create | Create a task with `puntos>0` from the UI, inspect `usuarios/{creador}` | `puntos` decreased, `puntosReservados` increased |
  | 2 | No-confirmation reservation consumption | Create a `requiereConfirmacion=false` task with `puntos=100`, mark complete, inspect `usuarios/{creador}` | `puntosReservados` decreased by 100 (coerced `>= 0`) |
  | 3 | No-confirmation executor credit | Same task, inspect `usuarios/{ejecutorUid}` | `puntos` +100; `puntosRecompensa` +10 |
  | 4 | Reservation decrement coercion | Set creator `puntosReservados=30`, complete a 100-point no-confirm task | `puntosReservados` becomes 0, never negative |
  | 5 | Executor credit follows argument | Call `marcarCompletada(tareaId, "uX")` with `asignadoA="uE"` | `uX.puntos` increases; `uE.puntos` unchanged |
  | 6 | No creator document created | Complete a task with blank `creadoPor` | Executor credited; no `usuarios` doc created for a creator |
  | 7 | Confirmation regression | Confirm a `pendiente_confirmacion` task | `puntosReservados -= puntos` (coerced); `puntos`/`puntosRecompensa`/`rachaDias` per confirm rules |
  | 8 | Auto-assign rejection | Complete a task with `creadoPor == asignadoA` | `Result.failure`; no state/points written |
  | 9 | Confirm-required branch state guard (A1) | Call `marcarCompletada` on a non-`pendiente` task (e.g. `pendiente_confirmacion` or `confirmada`) with `requiereConfirmacion=true` | `Result.failure("Tarea no está en estado pendiente")`; no state written |

- [x] **4.4 Run the stack-purity audit.**
  - Run Global Gate 2 "Stack purity preserved" and "No new direct Firebase client".
  - **Verify**: no new Compose/Hilt/Room/Retrofit matches; the only direct client match remains
    `FirebaseComposition.kt:96` (read-only).
  - **Rollback**: n/a.
  - **Depends/Parallel**: parallel with 4.1-4.3.

## Bounded Additions and Orchestrator Adjustments

Recorded for traceability; these were accepted by the orchestrator and do not expand the
proposal's product scope.

- **A1 — `pendiente`-state guard on the confirm-required branch (bounded addition).** Task 1.3
  restores a pre-branch check that the deleted `RepositorioTareas.kt:72` enforced and that the
  canonical `marcarCompletada` confirm-required branch (`:399-402`) dropped. Rationale: the
  consolidation must not lose an existing protection; this is safety parity with the no-confirm
  in-transaction check at `:408`, not a product change. Verified by manual matrix row 9.
- **A2 — Docs work unit folds in Discovered Drift.** Task 3.4 updates the guide's §0/§2 stale
  sentences (design.md "Discovered Drift") in addition to the proposal-scoped §6/§7/§9 edits
  (tasks 3.1-3.3).

## Out of Scope (do not implement here)

- TD-7 (`resolverReclamo` split write), avatar authority, navigation/lifecycle convergence,
  account deletion, Firestore rules/indexes, `Tarea.estado` typing.
- No Hilt, Room, Retrofit, Compose, new dependency, or Gradle change.
- No automated tests (roadmap step 5 is a separate change).
- No change to Firestore collection/field contracts or UI caller signatures.
- Streak, `multiplicadorPuntos`, recurrence spawn, and reminder scheduling stay
  confirmation-path behaviors and are not added to the no-confirmation path.

## Notes on Task Format

- No threat-matrix rows exist in this design, so no RED-test tasks are generated.
- Every task is completable in one session; WU1, WU2, and WU3 can each be a single work session.
- No commit is created by this phase; the apply phase owns commits.
