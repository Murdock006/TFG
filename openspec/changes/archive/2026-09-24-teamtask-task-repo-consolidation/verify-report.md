# Verify Report: TeamTask Task Repository Consolidation

## Scope

- Change: `teamtask-task-repo-consolidation`
- Repo: `C:\Users\Victor\AndroidStudioProjects\TFG2`
- TDD mode: **Standard** (`openspec/config.yaml` → `strict_tdd: false`; no test runner beyond the
  template `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt`)
- Artifact store: hybrid (this file + Engram mirror at
  `sdd/teamtask-task-repo-consolidation/verify`)
- Artifacts inspected: `proposal.md`, `exploration.md`, `design.md`, `tasks.md`,
  `apply-progress.md`; deltas `specs/task-domain/spec.md`, `specs/architecture-map/spec.md`,
  `specs/implementation-recipes/spec.md`; implementation
  `TareaRepositorioFirebase.kt`, `TareasViewModel.kt`, `LocalizadorServicios.kt`,
  `TareaRepositorio.kt`, `FirebaseComposition.kt`, `docs/architecture/TEAMTASK_GUIDE.md`.
- Change commits observed: `71bfe79` (reservation + state guard), `2765e42` (consolidation),
  `7e7c4fd` (docs/guide + apply record).
- Verification is READ-ONLY with respect to source: no application code, Gradle file, spec, delta,
  guide, or git state was modified. Only this report was written (plus the Engram mirror).

## Observed Progress

17 of 18 tasks complete. The only unfinished task is **4.3 (manual verification matrix)**, left
unchecked in `tasks.md` and reported NOT EXECUTED in `apply-progress.md`. Checkbox state was not
altered.

## Checks — Executed

### 1. Compile gate

Command:

```powershell
.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain
```

Observed result: `BUILD SUCCESSFUL in 8s`; `40 actionable tasks: 40 up-to-date`. Both
`:app:compileEmulatorKotlin` and `:app:compileReleaseKotlin` reported `UP-TO-DATE`.

Honest disclosure: this run performed **no fresh recompilation** — Gradle's input-hash check found
the tree unchanged since the last successful compile. It confirms the current tree is consistent
with a prior successful compilation of the same inputs (and would recompile on any source change),
but it is not a from-clean rebuild. `apply-progress.md` records the originating run as
`BUILD SUCCESSFUL in 29s` with both compile tasks executed.

### 2. Grep audits

ripgrep is not installed; `git grep` (case-sensitive by default) was used.

| Audit | Command | Observed result |
|---|---|---|
| Simplified repo removed | `git grep -n "RepositorioTareas" -- app/src/main` | Exit 1 — zero matches |
| In-memory task repo removed | `git grep -n "TareaRepositorioInMemory" -- app/src/main` | Exit 1 — zero matches |
| Dead VM members removed | `git grep -n "tareaCreada" -- app/src/main` | Exit 1 — zero matches |
| Dead VM members removed | `git grep -n "resetTareaCreada" -- app/src/main` | Exit 1 — zero matches |
| No new direct Firebase client | `git grep -n "FirebaseFirestore.getInstance\|Firebase\.firestore" -- app/src/main` | Single match `service/firebase/FirebaseComposition.kt:96` — unchanged posture |
| Stack purity preserved | `git grep -n "androidx.compose\|dagger.hilt\|androidx.room\|retrofit2" -- app/` | Exit 1 — zero matches |

Stronger repo-wide audit: `git grep -n "RepositorioTareas"` and `TareaRepositorioInMemory` return
**zero matches anywhere under `app/`** (including `test/` and `androidTest/`). Remaining matches are
confined to docs/openspec/README (see Findings).

### 3. Filesystem presence

- `app\src\main\java\com\example\tfg\repositorio\RepositorioTareas.kt` → `False` (deleted)
- `app\src\main\java\com\example\tfg\data\inmemory\TareaRepositorioInMemory.kt` → `False` (deleted)
- `app\src\main\java\com\example\tfg\repositorio\TareaRepositorio.kt` → `True` (canonical interface retained)
- `data\inmemory\` still contains `AuthRepositorioInMemory.kt` and `GrupoRepositorioInMemory.kt` (correctly retained)

### 4. Change-scope audit

`git diff --stat 71bfe79~1 7e7c4fd` shows exactly the authorized targets:

```
app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt  |  21 +-
app/src/main/java/com/example/tfg/data/inmemory/TareaRepositorioInMemory.kt  | 205 ---
app/src/main/java/com/example/tfg/repositorio/RepositorioTareas.kt           | 202 ---
app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt            |   1 -
app/src/main/java/com/example/tfg/viewmodel/TareasViewModel.kt               |  20 +-
docs/architecture/TEAMTASK_GUIDE.md                                          | 103 ++--
openspec/changes/.../apply-progress.md                                       | 146 +++
openspec/changes/.../tasks.md                                                | 324 +++
```

No Gradle, canonical-spec, Firestore-contract, or UI-signature change. `git status --short` shows
only pre-existing `.idea/*` modifications; the worktree carries no uncommitted source edits.

## Checks — Contract Review (static, against the `task-domain` delta)

The `marcarCompletada` no-confirmation transaction (`TareaRepositorioFirebase.kt:406-444`) was
reviewed against each delta scenario. All are **statically satisfied**; none has runtime proof.

| Delta scenario | Requirement | Code evidence | Static result |
|---|---|---|---|
| No-confirmation completion consumes the reservation | estado→`confirmada`; credit `ejecutorUid` with `puntos`; `+max(1, floor(puntos*0.10))`; creator `puntosReservados -= puntos` coerced ≥0 | `:425` update estado; `:412` ref from `ejecutorUid`; `:421` `coerceAtLeast(1)`; `:438-441` creator decrement | PASS (static) |
| Reservation decrement coerced to zero | never negative | `:440` `(reservados - tareaTx.puntos).coerceAtLeast(0)` | PASS (static) |
| Executor credit follows the `ejecutorUid` argument (TD-11) | not overridden by `tarea.asignadoA` | `:412` `document(ejecutorUid)`; no `asignadoA` reference in this path | PASS (static) |
| Task without a creator does not create a creator document | no creator doc created when `creadoPor` blank | `:413-414` `creadorRef = null` when blank; `:438` guarded write | PASS (static) |
| Completion path rejects self-assignment | `Result.failure("Tarea inválida: autoasignación no permitida")` before writes | `:396` pre-check; `:469` in-tx re-check (`confirmarTarea`) | PASS (static) |
| Full impl rejects self-assignment | `crearTarea` rejects `creadoPor == asignadoA` | `:79-83` via `validarAutoasignacion` | PASS (static) |

Additional invariants verified by reading:

- **Transaction discipline**: all reads (`:407`, `:417`, `:418`) precede all writes (`:425`,
  `:427-435`, `:438-441`). Firestore's read-before-write rule is honored.
- **A1 bounded guard**: `:397` `if (tarea.estado != "pendiente") return Result.failure(...)` is
  placed after the auto-assign guard (`:396`) and before the `requiereConfirmacion` branch (`:400`),
  matching `tasks.md` 1.3. Its message is byte-identical to the in-transaction check at `:409`, so
  the no-confirmation path's outcome is unchanged; the confirm-required branch regains the
  pre-branch protection the deleted `RepositorioTareas.kt:72` enforced.
- **Scope bound respected**: streak, `multiplicadorPuntos`, recurrence spawn, and reminder
  scheduling remain confirmation-path-only; they are absent from the no-confirmation path as the
  delta requires.
- **ViewModel surface**: `TareasViewModel` depends on `TareaRepositorio = LocalizadorServicios.repositorioTarea`
  (`:11`), keeps the default-argument pattern (matches `VistaModeloAuth.kt` precedent), and retains
  `marcarCompletada` / `confirmarTarea` / reset signatures for UI callers. `crearTarea`,
  `_tareaCreada`, `tareaCreada`, `resetTareaCreada`, and the `Tarea` import are removed.
- **Docs gate**: guide §6/§7/§9 `Status` values match the `implementation-recipes` delta exactly
  (TD-1 `Resolved (teamtask-task-repo-consolidation)`, TD-9 `Partially resolved
  (teamtask-testing-emulator-strategy)`, TD-11 `Resolved (teamtask-task-repo-consolidation)`, TD-14
  `Resolved (teamtask-testing-emulator-strategy)`; roadmap step 1 `Done`, step 2 `Done
  (teamtask-task-repo-consolidation)`, step 4 `Partially done`; §9 `Done (this change)` /
  `Done (teamtask-testing-emulator-strategy)`). Headings `## 0` through `## 9` are present.
- **No-automated-test-net disclosure**: `app/src/test` and `app/src/androidTest` contain only the
  template `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt`. There is **no automated coverage**
  for the points paths. Static inspection and the compile gate cannot prove runtime points
  behavior.

## Checks — Not Executed / Unavailable

### Manual verification matrix (task 4.3) — NOT EXECUTED

A device plus Firebase emulators are explicitly outside this change's execution scope. No emulator
or device was booted. Rows 1-9 are reported as NOT EXECUTED (pending manual verification); no
result is claimed.

| # | Check | Expected | Status |
|---|---|---|---|
| 1 | Reservation at create | `puntos` decreased, `puntosReservados` increased | NOT EXECUTED |
| 2 | No-confirmation reservation consumption | `puntosReservados` decreased by 100 (coerced ≥0) | NOT EXECUTED |
| 3 | No-confirmation executor credit | `puntos` +100; `puntosRecompensa` +10 | NOT EXECUTED |
| 4 | Reservation decrement coercion | `puntosReservados` → 0, never negative | NOT EXECUTED |
| 5 | Executor credit follows argument | `uX.puntos` increases; `uE.puntos` unchanged | NOT EXECUTED |
| 6 | No creator document created | Executor credited; no creator `usuarios` doc | NOT EXECUTED |
| 7 | Confirmation regression | `puntosReservados -= puntos` (coerced); confirm-path points/racha | NOT EXECUTED |
| 8 | Auto-assign rejection | `Result.failure`; no state/points written | NOT EXECUTED |
| 9 | Confirm-required branch state guard (A1) | `Result.failure("Tarea no está en estado pendiente")`; no state written | NOT EXECUTED |

Consequence: the points-leak fix (the change's highest-risk behavioral delta) has **no runtime
proof** in this run. This is the central limitation of the verification.

## Findings

### CRITICAL

None. No compile failure, no unmet requirement detectable statically, no unauthorized scope change.

### WARNING

1. **Behavioral verification gap for the points-leak fix.** The only behavioral proof of the
   reservation-consumption fix is manual matrix task 4.3, which was not executed (no device /
   emulator). With no automated test net, rows 1-9 remain the sole behavioral evidence. The change
   must not be treated as behaviorally proven until a human runs the matrix on an emulator/device.
2. **Compile gate was an up-to-date check, not a fresh rebuild.** This run returned
   `40 actionable tasks: 40 up-to-date`. It validates the current tree against Gradle's incremental
   state, but a from-clean recompile was not performed. If stricter evidence is wanted before
   release, run `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --rerun-tasks`.

### SUGGESTION

1. **Stale `README.md` pointer (outside declared scope).** `README.md:62` still lists
   `RepositorioTareas.kt` in the package tree, now a deleted file. The proposal scoped docs edits to
   `TEAMTASK_GUIDE.md` only, so this was correctly left untouched — but it is now false and worth a
   small follow-up.
2. **Canonical specs not yet synced (expected mid-pipeline).** `openspec/specs/architecture-map/spec.md`
   (lines 15, 86, 109), `openspec/specs/implementation-recipes/spec.md` (169, 179, 190), and
   `openspec/specs/task-domain/spec.md` still carry pre-consolidation rows and `RepositorioTareas`
   evidence. The three deltas explicitly defer their table replacements to the archive step
   ("The archive step MUST apply the tables below"). This is by design, but archive MUST apply all
   three deltas or the canonical specs stay wrong.
3. **`design.md` not back-updated for the A1 guard.** The design's no-confirmation code sample and
   its risk table treat the confirm-required state guard as out-of-scope ("adding a guard is beyond
   proposal decision 2"). `tasks.md` 1.3 promoted it to an orchestrator-accepted bounded addition
   (A1), and the implementation follows `tasks.md`. Traceability would be cleaner if `design.md`
   recorded A1; the implementation is consistent with the authoritative `tasks.md`, so this is a
   documentation nit only.
4. **Guide §8 residual phrasing.** `TEAMTASK_GUIDE.md` §8 ("Archive boundary and rollback", lines
   212-216) still says "no application code is in scope". Line 199 explicitly scopes that boundary
   to the original `teamtask-architecture-guide` apply phase, so it is historical context rather
   than a live claim. Optional cleanup.

## Historical Context

No prior `verify-report.md` exists for this change. `apply-progress.md` (same change) reports the
same gate outcomes this run observed (compile, all five grep audits, docs gate, stack purity) and
the same single unfinished task (4.3). This report does not contradict it; it adds the repo-wide
audit, the explicit contract-scenario review, the compile up-to-date caveat, and the README/canonical
spec observations.

## Summary

- **Verified (static + tooling):** the canonical dependency switch, the no-confirmation
  reservation-consumption transaction shape, `ejecutorUid` credit (TD-11), the ≥0 coercion, the
  blank-creator guard, the A1 state guard, dead-code removal, stack purity, and the guide `Status`
  alignment with the deltas. All executed checks pass.
- **Not verified (runtime):** the points behavior itself (manual matrix task 4.3). No automated test
  net exists.
- **Recommended next work:** run the manual matrix (task 4.3) on an emulator/device before release;
  optionally re-run the compile gate with `--rerun-tasks` for from-clean evidence. Then proceed to
  archive, ensuring the three deltas are applied to the canonical specs.

No finding blocks archive; archive must record the actual state (17/18 tasks, matrix pending), never
a synthetic PASS.
