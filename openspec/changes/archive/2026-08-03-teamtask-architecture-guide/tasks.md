# Tasks: TeamTask Architecture Guide

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 250–350 (1 new markdown file ~280 lines + Engram pointer ~20 lines) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR (docs-only, no code diff) |
| Delivery strategy | ask-on-risk |
| Chain strategy | not needed |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Create guide + Engram pointer + verify | PR 1 | Single docs-only PR; no code changes; rollback = delete file |

## Phase 1: Baseline Guards & Source-of-Truth Policy

- [x] 1.1 **Dirty-worktree guard** — Open `docs/architecture/TEAMTASK_GUIDE.md` §0 with a "How to read" preamble stating: (a) this change is docs-only, (b) the worktree is dirty (54 modified + 10 untracked), (c) no application code is touched, (d) source-of-truth tier: code > verified config > docs > historical Engram.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §0
  - **Depends on**: nothing
  - **Acceptance**: §0 contains the four guard statements verbatim.
  - **Verification**: `grep -c "source-of-truth" docs/architecture/TEAMTASK_GUIDE.md` ≥ 1.
  - **Rollback**: delete the file.

- [x] 1.2 **`[UNVERIFIED]` policy declaration** — In §8 (Source-of-truth & verification policy), document: every claim about Firestore rules, indexes, App Check, or Cloud Functions that lacks console confirmation is marked `[UNVERIFIED]` inline; future changes referencing an `[UNVERIFIED]` claim MUST confirm and strip the marker.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §8
  - **Depends on**: 1.1
  - **Acceptance**: §8 contains the `[UNVERIFIED]` rule and the audit command `grep -r "\[UNVERIFIED\]" openspec/`.
  - **Verification**: `grep -c "\[UNVERIFIED\]" docs/architecture/TEAMTASK_GUIDE.md` ≥ 1.
  - **Rollback**: delete the file.

## Phase 2: Guide — Thin Index over the Five Specs

- [x] 2.1 **§1 Architecture map** — One paragraph summarizing package responsibilities and dependency directions; link to `openspec/specs/architecture-map/spec.md` (Engram `sdd/teamtask-architecture-guide/spec/architecture-map`); list the 5 effective-flow deviations as a bullet table.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §1
  - **Depends on**: 1.1
  - **Acceptance**: Contains ≥1 link to spec and ≥5 deviation bullets with `file:line`.
  - **Verification**: open the linked spec; confirm ≥3 `file:line` references match source.
  - **Rollback**: delete the file.

- [x] 2.2 **§2 Task & group domain** — One paragraph on state machine + points invariants; link to `task-domain` spec; include the 6-state table and the 3 critical invariants (reservation at create, atomic confirm, personalizada normalization).
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §2
  - **Depends on**: 1.1
  - **Acceptance**: Contains ≥1 link and references TD-1, TD-7.
  - **Verification**: sample `TareaRepositorioFirebase.kt:466-494` — confirm points math matches spec.
  - **Rollback**: delete the file.

- [x] 2.3 **§3 Firestore data contracts** — One paragraph on collections and field contracts; link to `firestore-contracts` spec; list the 8 collections and the `[UNVERIFIED]` server-side assumptions table.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §3
  - **Depends on**: 1.2
  - **Acceptance**: Contains ≥1 link and ≥8 collection rows.
  - **Verification**: sample `AuthRepositorioFirebase.kt:48-53` — confirm `usuarios` fields match.
  - **Rollback**: delete the file.

- [x] 2.4 **§4 Navigation & lifecycle** — One paragraph on nav graph + auto-login + listener ownership; link to `navigation-lifecycle` spec; list the 5 back-press rules and the listener-cancellation table.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §4
  - **Depends on**: 1.1
  - **Acceptance**: Contains ≥1 link and references TD-4, TD-5.
  - **Verification**: sample `MainActivity.kt:154-194` — confirm back rules match.
  - **Rollback**: delete the file.

- [x] 2.5 **§5 Implementation recipes** — One paragraph on stack constraints (XML/Fragments/ViewBinding, no Hilt/Room/Retrofit/Compose); link to `implementation-recipes` spec; include the naming-convention table and the 6 recipe rules.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §5
  - **Depends on**: 1.1
  - **Acceptance**: Contains ≥1 link and the stack-purity grep command.
  - **Verification**: run `grep -r "androidx.compose\|dagger.hilt\|androidx.room\|retrofit2" app/` — confirm zero matches.
  - **Rollback**: delete the file.

## Phase 3: Debt Register, Roadmap & Follow-up Changes

- [x] 3.1 **§6 Technical debt register** — Reproduce the TD-1..TD-15 table from `implementation-recipes` spec with severity, evidence, and target-spec columns.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §6
  - **Depends on**: 2.1–2.5
  - **Acceptance**: 15 rows, each with `#`, issue, severity, evidence, target spec.
  - **Verification**: count rows = 15; spot-check TD-7 evidence path.
  - **Rollback**: delete the file.

- [x] 3.2 **§7 Convergence roadmap** — Reproduce the 7-step roadmap from `implementation-recipes` spec with depends-on and estimated-impact columns.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §7
  - **Depends on**: 3.1
  - **Acceptance**: 7 rows; step 1 = this change (docs-only).
  - **Verification**: step 1 description matches this tasks file.
  - **Rollback**: delete the file.

- [x] 3.3 **§9 Follow-up SDD changes** — List the 6 follow-up change names (`teamtask-avatar-authority` through `teamtask-account-deletion-security`) with one-line intent each; state that each is a separate SDD change with its own proposal/spec/design/tasks.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §9
  - **Depends on**: 3.2
   - **Acceptance**: 6 change names listed.
  - **Verification**: names match design.md "Follow-up SDD changes" section.
  - **Rollback**: delete the file.

## Phase 4: Engram Recovery Pointer

- [x] 4.1 **Create/update `architecture/teamtask-guide`** — Save an Engram observation (topic_key `architecture/teamtask-guide`, type `architecture`, project `TFG-TeamTask`) containing: (a) one-paragraph summary of the guide, (b) guide path `docs/architecture/TEAMTASK_GUIDE.md`, (c) the 5 spec topic keys, (d) today's date. Do NOT duplicate full spec content.
  - **Path**: Engram `architecture/teamtask-guide`
  - **Depends on**: 2.1–2.5
  - **Acceptance**: Observation exists with guide path + 5 spec keys + date.
  - **Verification**: `mem_search(query: "teamtask-guide", project: "TFG-TeamTask")` returns the observation.
  - **Rollback**: Engram upserts are non-destructive; overwrite with a "superseded" note if needed.

## Phase 5: Manual Documentation Verification

- [x] 5.1 **Link audit** — For every `→ spec` link in the guide, confirm the target spec exists in Engram (all 5 topic keys resolve).
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md`
  - **Depends on**: Phase 2 + 4.1
  - **Acceptance**: 5/5 links resolve.
  - **Verification**: `mem_search` for each spec topic key returns an observation.
  - **Rollback**: fix broken links in the guide.

- [x] 5.2 **Sampled `file:line` claims** — Pick 5 `file:line` references from the guide (one per spec section); open each source file and confirm the cited lines match the claim.
  - **Path**: source files under `app/src/main/java/com/example/tfg/`
  - **Depends on**: Phase 2
  - **Acceptance**: 5/5 claims match.
  - **Verification**: manual read of each cited line range.
  - **Rollback**: correct the guide's `file:line` references.

- [x] 5.3 **`[UNVERIFIED]` audit** — Run `grep -r "\[UNVERIFIED\]" openspec/` and confirm every `[UNVERIFIED]` marker in the specs is also reflected in the guide's §8 policy.
  - **Path**: `openspec/` (Engram specs) and `docs/architecture/TEAMTASK_GUIDE.md` §8
  - **Depends on**: Phase 2 + 3.1
  - **Acceptance**: guide §8 mentions the audit command; count of `[UNVERIFIED]` in specs > 0.
  - **Verification**: `grep -c "\[UNVERIFIED\]" docs/architecture/TEAMTASK_GUIDE.md` ≥ 1.
  - **Rollback**: add missing policy text to §8.

- [x] 5.4 **Stack-purity check** — Run `grep -r "androidx.compose\|dagger.hilt\|androidx.room\|retrofit2" app/` and confirm zero matches; document the result in the guide's §5.
  - **Path**: `app/src/main/`
  - **Depends on**: Phase 2
  - **Acceptance**: zero matches; guide §5 states "stack purity confirmed".
  - **Verification**: grep exit code 1 (no matches).
  - **Rollback**: if matches found, update §5 to list them (do NOT change code).

- [x] 5.5 **Artifact presence** — Confirm all 5 SDD artifacts exist: `proposal.md`, `design.md` in `openspec/changes/teamtask-architecture-guide/`; 5 specs in Engram; guide at `docs/architecture/TEAMTASK_GUIDE.md`; Engram pointer at `architecture/teamtask-guide`.
  - **Path**: filesystem + Engram
  - **Depends on**: all previous tasks
  - **Acceptance**: 9/9 artifacts present.
  - **Verification**: `Test-Path` for files; `mem_search` for Engram keys.
  - **Rollback**: create missing artifacts.

- [x] 5.6 **No application-code diff** — Run `git diff --stat app/` and confirm zero changes to application source.
  - **Path**: repo root
  - **Depends on**: all previous tasks
  - **Acceptance**: `git diff --stat app/` shows no new modifications beyond the pre-existing dirty worktree.
  - **Verification**: compare `git diff --stat app/` output against the known 54 modified files baseline.
  - **Rollback**: `git checkout -- app/` for any unintended changes (should be none).

## Phase 6: Archive Boundary & Rollback

- [x] 6.1 **Archive boundary** — Document in the guide's §8 that this change's archive boundary is: (a) the guide file + Engram pointer are the only deliverables, (b) the 5 specs remain in Engram as delta specs until a future `sdd-archive` promotes them to `openspec/specs/`, (c) no application code is in scope.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §8
  - **Depends on**: Phase 5
  - **Acceptance**: §8 contains the archive boundary statement.
  - **Verification**: read §8 and confirm the three statements.
  - **Rollback**: delete the file; Engram pointer is an upsert (non-destructive).

- [x] 6.2 **Rollback procedure** — Document in §8: to roll back this change, (a) delete `docs/architecture/TEAMTASK_GUIDE.md`, (b) delete `openspec/changes/teamtask-architecture-guide/` (or move to `archive/`), (c) overwrite the Engram `architecture/teamtask-guide` observation with a "superseded" marker. No application code rollback needed.
  - **Path**: `docs/architecture/TEAMTASK_GUIDE.md` §8
  - **Depends on**: 6.1
  - **Acceptance**: §8 contains the 3-step rollback procedure.
  - **Verification**: read §8 and confirm the procedure.
  - **Rollback**: N/A (this is the rollback doc itself).
