# Archive Report: TeamTask navigation/lifecycle convergence

Change: `teamtask-navigation-lifecycle-convergence` · Repo: `C:\Users\Victor\AndroidStudioProjects\TFG2` ·
Branch: `master` · Store: hybrid (files under `openspec/` + Engram mirrors) · Archived:
`2026-09-24`. Delivery: solo developer, direct commits to `master`, no pull requests.

This report is the terminal record of the cycle. It describes the state of the change AT
CLOSE, not at earlier points. `apply-progress.md` and `verify-report.md` are intermediate
snapshots; where they disagree with the persisted `tasks.md` artifact or the archive launch
context, the latter rank higher (Final-State Authority hierarchy).

## Final State (authoritative at close)

**38 / 39 tasks complete. Task 8.4 (the 21-scenario manual navigation matrix) NOT EXECUTED.**

Task 8.4 requires a device/emulator and was explicitly outside this change's execution scope.
No row of the matrix was executed and none is claimed passing. Per the `tasks.md` artifact
(highest-ranked source), every numbered task is checked except 8.4. No later evidence in the
launch context reports task 8.4 executed or any earlier verify finding fixed in a later commit
(the verify commit `1296201` is the last commit in range).

`verify-report.md` (intermediate snapshot, written at verification time) recorded status
**partial**: all executable automated gates passed — a forced fresh compile of both Kotlin
variants with Safe Args generation re-executing, the 4/0 JVM suite re-executed, seven grep
audits, and a static contract review of the 21 `navigation-lifecycle` scenarios — while the
authoritative behavioral net (the manual matrix, task 8.4) remained NOT verified. Archive
proceeds by **accepting the documented manual-verification debt**, which the verify report
explicitly offered as the alternative to a device run before archive.

### Verify findings carried forward

- **CRITICAL: none.**
- **WARNING: none.**
- **SUGGESTION (open, doc-only, recorded as follow-ups — not blockers):**
  1. **`tasks.md` decision-count label drift.** `tasks.md:8` describes `design.md` as
     containing "7 decisions", but the design file has **8** `### Decision` headings
     (TD-4-1, TD-4-2, TD-4-3, TD-5-1, TD-6-1, TD-6-2, TD-15-1, TD-15-2). Cosmetic; no
     behavioral impact.
  2. **Guide §6 retains pre-drift evidence citations.** `docs/architecture/TEAMTASK_GUIDE.md`
     §6 correctly marks TD-4/5/6/15 `Resolved`, but its evidence columns still cite the older
     pre-apply line ranges (e.g. TD-4 `MainActivity.kt:255-275`, TD-5
     `TareaRepositorioFirebase.kt:184-212`, TD-6 `TareasHomeAdapter.kt:39,145-210`, TD-15
     `MainActivity.kt:223`). The delta specs recorded the corrected citations, which were
     applied to the canonical specs during this archive; the **guide itself was not
     line-corrected** (the archive phase does not modify the guide).

### Apply deviations accepted

1. **TD-4 `Login → PgPrincipal` continuation placement.** The design's data-flow diagram drew
   the continuation inside the funnel; the implementation kept it in the verified-session
   listener path. Verified in source: the continuation lives in the destination-listener path
   and the funnel only performs `Presentación → Login`. This is **correct and required** —
   placing the continuation in the funnel would chain an *unauthenticated* user from Login into
   PgPrincipal, contradicting the delta-spec requirement that `Login → PgPrincipal` stays
   conditional on the verified session. Observable behavior is preserved (verified users chain;
   unauthenticated users stay on Login). **Accepted.**
2. **Spanish code comments.** New comments in the touched source files are in Spanish, matching
   the surrounding file convention (`openspec/config.yaml`: code Spanish, specs English). All
   SDD artifacts remain in English. **Accepted** (consistency with the existing codebase).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `navigation-lifecycle` | **Updated** | 3 MODIFIED requirement blocks replaced (auto-login single coordinator + recreation safety; listener `onDestroyView`/`avatarDrawerJob` clause; notification typed `taskId` + duplicate-stack clause) and 3 ADDED requirement blocks appended (TD-5 observer consistency; TD-6 adapter scope ownership; TD-15 typed Safe Args). Plus the recorded non-requirement table replacements (evidence-row corrections, `TareasHomeAdapter` row replacement, 3 Future Convergence Work rows removed, 4 Known Risks bullets removed, 7 Manual Verification rows added). |
| `implementation-recipes` | **Updated** | 1 ADDED requirement ("Navigation arguments MUST use AndroidX Safe Args"). Plus the recorded non-requirement table replacements (Technical debt register TD-4/5/6/15 → resolved with corrected evidence; Convergence roadmap step 6 → Done; Current State Navigation row → Safe Args). |

### Native composition (requirement blocks)

Both existing canonical specs were composed with the native tool; each exited `0` and its
`.compose-tmp` output was stripped of the delta's non-requirement tail and read back before
being moved into place:

```
gentle-ai sdd-archive-compose --canonical "openspec/specs/navigation-lifecycle/spec.md" \
  --delta "openspec/changes/teamtask-navigation-lifecycle-convergence/specs/navigation-lifecycle/spec.md" \
  --output "openspec/specs/navigation-lifecycle/spec.md.compose-tmp"     # exit 0
gentle-ai sdd-archive-compose --canonical "openspec/specs/implementation-recipes/spec.md" \
  --delta "openspec/changes/teamtask-navigation-lifecycle-convergence/specs/implementation-recipes/spec.md" \
  --output "openspec/specs/implementation-recipes/spec.md.compose-tmp"   # exit 0
```

The compose output also appended the delta's `## Non-requirement updates (apply at archive)` /
`## Unchanged content (preserved)` prose blocks; those are archive instructions, not canonical
content, and were removed mechanically (BOM-less `.NET` string surgery) while every requirement
block was preserved verbatim. The recorded tables were then applied by hand. Unrelated
requirements (`Back behavior…`, `Logout…`, `Lifecycle-aware collection…`, and every existing
`implementation-recipes` requirement) were preserved byte-for-byte.

### Applied non-requirement table replacements

**`navigation-lifecycle`** (delta section "Non-requirement updates (apply at archive)"):
1. "MainActivity overrides the graph" table — Auto-login row evidence `MainActivity.kt:91,230-283`
   → `MainActivity.kt:94,232-286` and the transition clause now reads "chains Presentacion →
   Login → PgPrincipal via a single recreation-safe coordinator (listener `:257-277`)".
2. Same table — Back row evidence `MainActivity.kt:154-194` → `MainActivity.kt:156-196`.
3. Same table — Open task row evidence `MainActivity.kt:130-133,202-228` → `MainActivity.kt:133,204-230`;
   `navigate(fragment_Tareas, bundle)` → `navigate(fragment_Tareas, typed taskId argument)`.
4. "Listener ownership and cancellation" table — `observarTareas` evidence `:162-213` → `:163-214`.
5. Same table — `observarTareasPorGrupo` evidence `:215-228` → `:216-229`.
6. Same table — `TareasHomeAdapter` external-scope row **replaced** by the adapter-owned-scope row
   (host supplies a navigation callback, not a scope; loads cancelled on view destroy; per-view
   construction, evidence `TareasHomeAdapter.kt:34-39,144,177,197`; `FragmentTareasPendientes.kt:29`;
   `FragmentPgPrincipal.kt:177`).
7. Future Convergence Work — 3 resolved rows **removed** (typed `combine`; explicit
   `CoroutineScope`; `navigation-safe-args`). Remaining rows (double-back, single-place
   `openTaskId`, `LogoutController`) unchanged.
8. Known Risks — 4 resolved bullets **removed** (observer race, one-shot listener, manual bundle
   keys, external adapter scope). The Activity-level back-handling bullet and the closing
   marker are preserved.
9. Manual Verification — **7 rows added** (single auto-login navigator; observer stale-key
   pruning; adapter load after rotation; adapter scope cancellation; Safe Args migration;
   notification duplicate stack; collateral job cleanup).

**`implementation-recipes`** (delta section "Non-requirement updates (apply at archive)"):
1. Current State (observable) — Navigation row → "AndroidX Navigation Component with Safe Args
   (typed directions/arguments)", source `res/navigation/nav_graph.xml`; `app/build.gradle.kts:1-5,87-88`;
   `gradle/libs.versions.toml:16,37-39`.
2. Technical debt register — TD-4 (`MainActivity.kt:255-277`), TD-5
   (`TareaRepositorioFirebase.kt:163-214`), TD-6 (`TareasHomeAdapter.kt:34-39,144,177,197`), and
   TD-15 (all writer/reader sites) set to `Resolved (teamtask-navigation-lifecycle-convergence)`;
   every other row unchanged.
3. Convergence roadmap — step 6 status → `Done (teamtask-navigation-lifecycle-convergence)`.

**Archive-trail notes recorded here, not injected into the canonical specs** (the delta marked
them "for the archive trail"):
- *Resolution detail*: TD-4 resolved by a single recreation-safe coordinator; TD-5 by stale-key
  pruning plus single-writer discipline (not a typed `combine` rewrite); TD-6 by removing the
  external `CoroutineScope` and adding an adapter-owned, lifecycle-safe load mechanism; TD-15 by
  adopting AndroidX Safe Args.
- *Roadmap step 6 note*: the observers were converged via stale-key pruning plus single-writer
  discipline and a documented group-resolved-once limitation, not via a typed `combine` rewrite;
  the navigation/scope/safe-argument pieces of step 6 are delivered as stated.
- *Current State Navigation-row note*: the previous source citation `app/build.gradle.kts:69-70`
  pointed at the Firestore dependency block, not the Navigation dependency block; this change
  corrected it.
- *Interpretation applied*: the delta's instruction "the implementation note becomes 'chains
  Presentacion → Login → PgPrincipal via a single recreation-safe coordinator (listener
  `:257-277`)'" was applied by replacing the transition clause of the Auto-login row while
  preserving the row's unchanged `verificarSesionActiva` verified-session lead-in, rather than
  dropping that lead-in; the recorded single-coordinator mechanism is present verbatim.

## Archive Contents

- `proposal.md` — present.
- `exploration.md` — present.
- `specs/` — present (two delta specs: `navigation-lifecycle`, `implementation-recipes`).
- `design.md` — present.
- `tasks.md` — present; **38/39 numbered tasks complete**, 1 unfinished (8.4). Original bytes
  preserved; checkboxes not repaired.
- `apply-progress.md` — present (intermediate snapshot).
- `verify-report.md` — present (intermediate snapshot; PARTIAL, no CRITICAL/WARNING).
- `archive-report.md` — this file (additive; did not exist in the pre-move source snapshot).

### Mechanical move evidence

The entire change folder was moved with `git mv` (8 files). A recursive snapshot of the source
was taken before the move, and `git diff --no-index` of the snapshot against the archived
destination was run as the mandatory readback:

```
# snapshot before move: 8 files
git mv openspec/changes/teamtask-navigation-lifecycle-convergence \
       openspec/changes/archive/2026-09-24-teamtask-navigation-lifecycle-convergence    # exit 0
git diff --no-index <snapshot>/source <destination>                                    # exit 0 (empty, no differences)
snapshot file count: 8   destination file count: 8
```

An empty readback diff is the only accepted passing evidence; there were no differences. The
destination did not previously exist (no collision); the active
`openspec/changes/teamtask-navigation-lifecycle-convergence/` no longer exists.

## Commits in this change's range

Baseline before the change: `112a095` (`docs(openspec): archive avatar authority change`).
Proposal through verify:

| Commit | Message |
|--------|---------|
| `2dad2bf` | docs(openspec): add navigation lifecycle convergence proposal |
| `ce4134c` | docs(openspec): add navigation lifecycle delta specs |
| `c26a559` | docs(openspec): add navigation lifecycle design |
| `e0bf287` | docs(openspec): correct navigation lifecycle design |
| `0026921` | docs(openspec): add navigation lifecycle tasks |
| `066f307` | fix(tasks): prune stale keys in the task observer with a single writer |
| `3bf35ad` | refactor(navigation): converge auto-login, adapter scope, and typed navigation args |
| `792f01b` | docs(openspec): update guide and record navigation lifecycle apply |
| `1296201` | docs(openspec): add navigation lifecycle verify report |

No commit was created by the archive phase. No application code, Gradle file, guide, or git
state was modified by archive. The only writes by this phase were the two canonical specs, the
folder move, this report, and the Engram mirror.

## `[UNVERIFIED]` markers preserved

All `[UNVERIFIED]` markers from the sources were carried through into the canonical specs and
this report:

- `navigation-lifecycle` retains the TD-5 Firestore main-thread-confinement assumption
  (`[UNVERIFIED]`, requirement text and its threading scenario) and the closing
  "Whether any future change will introduce Hilt…" marker.
- `implementation-recipes` retains its source-of-truth `[UNVERIFIED]` marker and the closing
  "No Firebase emulator, CI, or test-orchestration infrastructure was inspected" marker.
- No `[UNVERIFIED]` marker was resolved or removed by archive.

## Engram traceability

Artifacts were read from the file locators (hybrid store). Corresponding Engram mirrors
(project `TFG-TeamTask`), recorded for traceability, are: `#147` explore, `#148` proposal,
`#149` spec/navigation-lifecycle, `#150` spec/implementation-recipes, `#151` spec
(concatenated), `#152` design (corrected), `#153` tasks, `#154` apply-progress, `#155` verify.
This report is mirrored at `sdd/teamtask-navigation-lifecycle-convergence/archive-report`.

## Recorded follow-ups

1. **Task 8.4 — manual navigation matrix (21 scenarios) NOT EXECUTED.** Run it on a device
   before relying on any runtime navigation/lifecycle claim. This is the only behavioral net
   for the change; `strict_tdd: false` and roadmap step 5 (focused tests) is still pending.
2. **Guide §6 evidence citations remain pre-drift** (`docs/architecture/TEAMTASK_GUIDE.md`).
   The canonical specs now carry the corrected citations; an optional doc cleanup could
   realign the guide's evidence columns.
3. **`tasks.md:8` decision-count label drift** ("7 decisions" vs 8 `### Decision` headings).
   Cosmetic.

## Recommended Next Work

1. Execute the 21-scenario manual matrix (task 8.4) on a device against the emulator build.
2. Optional: realign `docs/architecture/TEAMTASK_GUIDE.md` §6 evidence columns to the corrected
   line ranges now recorded in the canonical specs.
3. Continue the convergence roadmap: step 5 (focused tests: `ParejaViewModel`,
   `TareaRepositorioFirebase.crearTarea`/`confirmarTarea`,
   `RepositorioRecompensas.canjearRecompensa`) and step 7 (logout/auto-login controllers).
