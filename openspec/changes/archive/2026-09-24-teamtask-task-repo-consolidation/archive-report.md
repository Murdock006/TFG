# Archive Report: TeamTask Task Repository Consolidation

Terminal record of the `teamtask-task-repo-consolidation` SDD cycle. This report describes the
state of the change **at close**, per the archive Final-State Authority hierarchy (persisted
`tasks.md` > orchestrator final-state facts > `verify-report`/`apply-progress` snapshots).

## Metadata

| Field | Value |
|---|---|
| Change | `teamtask-task-repo-consolidation` |
| Repo | `C:\Users\Victor\AndroidStudioProjects\TFG2` |
| Archived to | `openspec/changes/archive/2026-09-24-teamtask-task-repo-consolidation/` |
| Archive date | 2026-09-24 |
| Artifact store | hybrid (files under `openspec/` + Engram mirrors) |
| Delivery | solo developer, direct commits to `master`, **no PRs** |
| TDD mode | Standard (`openspec/config.yaml` → `strict_tdd: false`; template tests only) |

## Final State (at close)

- **17 / 18 tasks complete.** The single unfinished task is **4.3 (manual verification matrix)** —
  left unchecked in `tasks.md` and recorded as **NOT EXECUTED (pending manual verification)**.
  Checkbox state was preserved as-is: the archived `tasks.md` contains 17 `[x]` and 1 `[ ]`.
- **Task 4.3 was not executed by design.** It requires a device plus Firebase emulators, which are
  outside this change's execution scope. No matrix result is claimed. Rows 1-9 (8 design rows + the
  A1 guard row) remain the only behavioral proof of the points-leak fix.
- **Bounded addition A1 landed** (orchestrator-accepted; task 1.3): the `pendiente`-state guard on
  the confirm-required branch of `TareaRepositorioFirebase.marcarCompletada` was restored
  (`if (tarea.estado != "pendiente") return Result.failure(...)`), placed after the auto-assign guard
  and before the `requiereConfirmacion` branch. It is safety parity with the deleted
  `RepositorioTareas.kt:72` and with the no-confirm in-transaction check; it is not a product change.
- **No CRITICAL verify findings.** The change is statically verified (compile gate, five grep audits,
  docs gate, stack purity, contract-scenario review). Behavioral runtime proof is absent.

## Commits (corroborated from `git log`)

| Commit | Subject |
|---|---|
| `71bfe79` | fix(tasks): consume reservation and guard state on completion |
| `2765e42` | refactor(tasks): consolidate completion onto the canonical task repository |
| `7e7c4fd` | docs(openspec): update guide and record consolidation apply |
| `dfd2d6d` | docs: refresh README after task repo consolidation |
| `17d5d36` | docs(openspec): add consolidation verify report |

The orchestrator's launch prompt asserted five commits; repository evidence confirms all five.
`dfd2d6d` fixed the stale `README.md` package-tree pointer (`RepositorioTareas.kt` →
`TareaRepositorio.kt`), which resolves `verify-report.md` SUGGESTION 1. `17d5d36` added the verify
report itself.

### Snapshot vs. final state

`verify-report.md` and `apply-progress.md` are intermediate snapshots. They observed three commits
(`71bfe79`, `2765e42`, `7e7c4fd`) and flagged the canonical specs as "not yet synced (expected
mid-pipeline)". Both observations are valid history, not final state:

- The two later commits (`dfd2d6d`, `17d5d36`) are part of the change and are recorded above.
- The canonical-spec sync was completed by **this archive phase** (see Specs Synced).

No unrankable contradiction was found between sources.

## Verification Summary

From `verify-report.md` (intermediate snapshot, at verification time) and the launch-prompt
final-state facts:

**CRITICAL:** none.

**WARNING:**
1. **Behavioral verification gap for the points-leak fix.** The only behavioral proof is manual
   matrix task 4.3, which was NOT executed (no device/emulator). With no automated test net, rows 1-9
   remain the sole behavioral evidence; the change MUST NOT be treated as behaviorally proven until a
   human runs the matrix.
2. **Compile gate was an up-to-date check, not a from-clean rebuild.** The verify run returned
   `40 actionable tasks: 40 up-to-date`. `apply-progress.md` records the originating run as
   `BUILD SUCCESSFUL in 29s` with both compile tasks executed. For from-clean evidence, run
   `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --rerun-tasks`.

**SUGGESTION (status at close):**
- Stale `README.md` pointer → **resolved** by `dfd2d6d`.
- Canonical specs not yet synced → **resolved** by this archive phase.
- `design.md` not back-updated for A1 → documentation nit only; implementation follows the
  authoritative `tasks.md`. Not changed here (outside archive scope).
- Guide §8 residual phrasing → historical context for `teamtask-architecture-guide`; optional cleanup,
  not changed here.

**No automated test net exists.** `app/src/test` and `app/src/androidTest` contain only the template
`ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt`. Static inspection and the compile gate cannot
prove runtime points behavior.

## Specs Synced

Delta → canonical sync applied to exactly three capabilities. The `firebase-*` specs belong to a
different, unarchived change and were NOT touched.

| Domain | Action | Details |
|---|---|---|
| `task-domain` | Updated | Requirement blocks composed via `gentle-ai sdd-archive-compose` (exit 0): 1 RENAMED + 1 MODIFIED + 1 ADDED. Plus 6 recorded non-requirement table/bullet replacements. |
| `architecture-map` | Updated | No requirement blocks (documentation-status delta). 3 recorded table replacements. |
| `implementation-recipes` | Updated | No requirement blocks (documentation-status delta). 2 recorded table replacements (debt register, convergence roadmap). |

### Composition evidence

- `task-domain` — requirement blocks composed by the native command:

  ```bash
  gentle-ai sdd-archive-compose \
    --canonical "openspec/specs/task-domain/spec.md" \
    --delta "openspec/changes/teamtask-task-repo-consolidation/specs/task-domain/spec.md" \
    --output "openspec/specs/task-domain/spec.md.compose-tmp"
  # exit 0
  ```

  The compose command applies RENAMED before MODIFIED before ADDED and preserves unrelated
  requirements. It also appends the delta's non-requirement tail (the delta's own "apply at archive"
  section); that tail is delta meta-content, not canonical spec content, and was removed before the
  temp file was moved into place. The recorded non-requirement replacements were then applied in place.

- `architecture-map` and `implementation-recipes` — the compose command exited non-zero with
  `Error: sdd-archive-compose: DELTA: delta spec declares no ADDED, MODIFIED, REMOVED, or RENAMED
  requirements`. This is **not** the skill's blocking case (an unapplied requirement section): both
  deltas declare no requirement blocks by design — their "Change shape" sections state "There are no
  requirement block changes" and instruct the archive step to apply recorded table replacements. With
  no requirement sections to compose, those recorded replacements were applied directly.

### Applied table / non-requirement replacements

**`task-domain` → `openspec/specs/task-domain/spec.md`**
1. **State machine table** — removed `RepositorioTareas.kt` evidence from the 4 rows that cited it
   (`:44-52`, `:73-76`, `:79-129`, `:141-200`).
2. **Points invariants table** — `marcarCompletada` no-confirm row now shows creator
   `puntosReservados -= puntos` (coerced ≥ 0) and the canonical reward floor
   `max(1, floor(puntos*0.10))`; dropped the `RepositorioTareas.kt:97-113` evidence.
3. **Task creation rules** — removed the third bullet ("`RepositorioTareas.kt:44-52` does NOT
   normalize and does NOT auto-block assignment; it does not reserve points either").
4. **Future Convergence Work** — removed the "Unify `TareaRepositorioFirebase` and
   `RepositorioTareas`; pick the full impl as canonical | High" row.
5. **Known Risks** — removed the three two-repo bullets ("Two task repos, two rules",
   "Auto-assignment in `RepositorioTareas` is not blocked", "Custom task without 200 puntos cap");
   preserved the resolver-reclamo bullet and the `[UNVERIFIED]` marker.
6. **Manual Verification** — added 2 rows: "No-confirmation reservation consumption" and
   "No-confirmation executor credit".

**`architecture-map` → `openspec/specs/architecture-map/spec.md`**
1. **Package inventory** — `repositorio` row: removed `RepositorioTareas`; `data/inmemory` row:
   changed to "In-memory substitutes for `Auth` and `Grupo`" and dropped the stale
   `USAR_FIREBASE=false` selector reference and the `LocalizadorServicios.kt:17,32-42` evidence.
2. **Future Convergence Work** — removed the "Remove dual task repos (`TareaRepositorioFirebase` vs
   `RepositorioTareas`) | High" row.
3. **Manual Verification** — updated the "Dual task-repo usage split" row to
   `grep -r "RepositorioTareas()\|TareaRepositorioInMemory"` → "Zero matches (consolidation complete)".

**`implementation-recipes` → `openspec/specs/implementation-recipes/spec.md`**
1. **Technical debt register** — added a `Status` column: TD-1 `Resolved
   (teamtask-task-repo-consolidation)`; TD-11 `Resolved (teamtask-task-repo-consolidation)` with the
   line reference corrected (`RepositorioTareas.kt:85` → `:86`); TD-9 `Partially resolved
   (teamtask-testing-emulator-strategy)` with refreshed evidence; TD-14 `Resolved
   (teamtask-testing-emulator-strategy)` with refreshed evidence; all other rows `Open`.
2. **Convergence roadmap** — added a `Status` column: step 1 `Done`; step 2 `Done
   (teamtask-task-repo-consolidation)`; step 4 `Partially done` with refreshed impact text; steps 3,
   5, 6, 7 `Pending`.
3. Preserved verbatim per the delta's "Unchanged content" section: the "Current State (observable)"
   table, all Requirements blocks, the "Future Convergence Work" table, the "Known Risks" section,
   and the `[UNVERIFIED]` marker.

### Interpretation note

For `task-domain`, the delta's "Task creation rules (updated bullets)" block rendered its removal
instruction as a third bullet ("The previous third bullet ... is REMOVED because the file is
deleted."). That line is delta meta-instruction (consistent with the delta's other removal prose), so
the canonical section retains only the two live bullets (auto-assignment forbidden; personalizada
normalization). No other ambiguity was found in the recorded replacements.

## Archive Move

- Mechanism: `git mv` (the change folder is tracked), executed via Git Bash inside the skill's
  snapshot/guard/readback transaction.
- Destination collision check: destination did not exist before the move; no suffix, overwrite, or
  merge was chosen.
- Source after move: `openspec/changes/teamtask-task-repo-consolidation` no longer exists.

### Mandatory `diff -r` readback (verbatim)

Pre-move recursive snapshot vs. archived destination:

```
ARCHIVE_MOVE_OK diff_status=0
=== verbatim diff -r snapshot/source vs destination (BEGIN) ===
=== verbatim diff -r (END); exit=0 ===
```

The `diff -r` output is empty (exit 0) — byte-identity confirmed. `archive-report.md` is additive and
was not present in the pre-move snapshot, so it is excluded from the comparison.

### Archived contents

| Artifact | Present |
|---|---|
| `proposal.md` | present |
| `exploration.md` | present |
| `design.md` | present |
| `tasks.md` | present — 17/18 complete, 1 unfinished (4.3) |
| `apply-progress.md` | present |
| `verify-report.md` | present |
| `specs/task-domain/spec.md` | present (delta) |
| `specs/architecture-map/spec.md` | present (delta) |
| `specs/implementation-recipes/spec.md` | present (delta) |
| `archive-report.md` | present (this file, additive) |

## Source of Truth Updated

- `openspec/specs/task-domain/spec.md`
- `openspec/specs/architecture-map/spec.md`
- `openspec/specs/implementation-recipes/spec.md`

## Artifacts and Traceability

Filesystem artifacts (read/updated this phase):

- `openspec/changes/archive/2026-09-24-teamtask-task-repo-consolidation/{proposal,exploration,design,tasks,apply-progress,verify-report}.md`
- `openspec/changes/archive/2026-09-24-teamtask-task-repo-consolidation/specs/{task-domain,architecture-map,implementation-recipes}/spec.md`
- `openspec/specs/{task-domain,architecture-map,implementation-recipes}/spec.md` (canonical, updated)

Engram mirrors observed (project `TFG2` / `TFG-TeamTask`):

| Observation ID | Topic |
|---|---|
| #121 | `sdd/teamtask-task-repo-consolidation/explore` |
| #122 | `sdd/teamtask-task-repo-consolidation/proposal` |
| #123 | `sdd/teamtask-task-repo-consolidation/spec/task-domain` |
| #124 | `sdd/teamtask-task-repo-consolidation/spec/architecture-map` |
| #125 | `sdd/teamtask-task-repo-consolidation/spec/implementation-recipes` |
| #126 | `sdd/teamtask-task-repo-consolidation/design` |
| #127 | `sdd/teamtask-task-repo-consolidation/tasks` |
| #128 | `sdd/teamtask-task-repo-consolidation/apply-progress` |
| #129 | `sdd/teamtask-task-repo-consolidation/verify` |

Archive report Engram mirror: `sdd/teamtask-task-repo-consolidation/archive-report`.

## Unfinished Work and Open Findings

- **Task 4.3 (manual verification matrix) — NOT EXECUTED.** Run rows 1-9 on an emulator/device before
  release. This is the sole behavioral proof of the points-leak fix.
- **No automated test net** for the points paths (roadmap step 5 is a separate change).
- Optional follow-ups (not archive scope): re-run the compile gate with `--rerun-tasks` for from-clean
  evidence; back-update `design.md` for A1; clean up guide §8 residual phrasing.

## SDD Cycle Complete

The change is archived. Implementation: complete except manual matrix task 4.3 (pending human
execution). Verification: static checks pass; behavioral runtime proof absent. The three canonical
specs now reflect the post-consolidation state. Unfinished work and warnings are recorded above —
none was described as passed or completed.
