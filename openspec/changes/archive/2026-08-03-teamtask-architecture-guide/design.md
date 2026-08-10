# Design: TeamTask Architecture Guide

## Technical Approach

Documentation-first convergence: produce one canonical guide (`docs/architecture/TEAMTASK_GUIDE.md`)
assembled from the five factual specs, plus a sequence of follow-up SDD changes that resolve
documented debt. No application code changes in this change. XML/Fragments/ViewBinding and the
mixed MVVM/service-locator are preserved. The design defines (a) the guide information
architecture and spec mapping, (b) source-of-truth + `[UNVERIFIED]` handling, (c) the
guide-to-Engram sync pattern, (d) the sequenced roadmap with safe boundaries, (e) the
deliverable map, and (f) decisions/alternatives/dependencies/rollback/verification/out-of-scope.

The guide is a **thin index**: it states the rule and links to the spec detail; specs remain the
verifiable units (evidence `file:line`). This keeps the guide review-light and prevents the
drift-prone duplication of invariants across two places.

## Architecture Decisions

### Decision: Guide is a thin index over the five specs

| Option | Tradeoff | Decision |
|---|---|---|
| Single monolithic guide | One bookmark; high drift risk; duplicating invariants | Rejected |
| Spec-only, no guide | Maximum fidelity; bad onboarding; no narrative order | Rejected |
| **Thin index guide + verifiable specs** | Narrative order + single evidence store; two hops to detail | **Chosen** |

**Rationale**: Specs already carry `file:line` evidence and `[UNVERIFIED]` markers. The guide adds
narrative + navigation, not a second source of invariants. Reviewers resolve a claim by following
the link to its spec, not by re-reading the guide.

### Decision: Source-of-truth tiering (verbatim from `implementation-recipes`)

1. Real source code in `app/src/main/`. 2. Verified repo config
(`build.gradle.kts`, `google-services.json`, `openspec/config.yaml`). 3. README/docs. 4. Historical
Engram. Any claim contradicting a higher tier MUST be corrected; unverifiable claims MUST be
marked `[UNVERIFIED]`. **Chosen over** README-as-truth (current) and Engram-as-truth.

### Decision: `[UNVERIFIED]` is a first-class label, not a footnote

| Option | Tradeoff | Decision |
|---|---|---|
| Inline prose "we are not sure" | Easy to miss in review | Rejected |
| Separate "assumptions" appendix | Detached from the claim | Rejected |
| **Inline `[UNVERIFIED]` tag on the exact claim + spec table row** | Greppable; review gate | **Chosen** |

A future change that relies on a `[UNVERIFIED]` claim MUST first confirm console/source state and
strip the marker in a delta spec. `grep -r "\[UNVERIFIED\]" openspec/` is the audit query.

### Decision: Documentation-to-Engram sync is "guide is canonical, Engram is recovery"

| Option | Tradeoff | Decision |
|---|---|---|
| Engram is canonical architecture memory | Fast; overwritten by upsert; not shareable | Rejected |
| Guide is canonical, Engram stores only topic keys + summaries pointing at the guide | Shareable + recoverable; tiny extra indirection | **Chosen** |

**Sync pattern**: when the guide/specs change, save an Engram architecture observation under the
stable topic key `architecture/teamtask-guide` with a one-paragraph summary + the guide path +
changed spec topic keys. Future agents MUST search Engram only to *locate* the guide, then read
the guide/specs as source of truth. Historical architecture observations that contradict the guide
are flagged, not deleted, until a delta spec resolves them (matches proposal's "no deletion without
verification").

### Decision: Roadmap sequence with safe boundaries (publish first, mutate later)

| # | Step | Touches code? | Boundary / gate |
|---|---|---|---|
| 1 | **Publish guide + 5 specs** (this change) | No | docs only; safe on dirty worktree |
| 2 | Resolve avatar authority (local vs Firebase; unify `tfg_prefs`/`avatar_prefs`; delete dead `AvatarRepositorioFirebase` per TD-2/TD-3) | Yes | requires clean/explicit baseline; small SDD change; ADR |
| 3 | Consolidate duplicate task repos (pick `TareaRepositorioFirebase` as canonical; remove `RepositorioTareas`) | Yes | clean baseline; touches `FragmentTareas`, `TareasViewModel`, `VistaModeloPrincipal`; likely chained PR |
| 4 | Formalize Firestore rules / `firestore.indexes.json` / `storage.rules` / App Check | Config + (optional) code | requires Firebase console access to strip `[UNVERIFIED]`; emulator-safe write test before deploy |
| 5 | Establish tests + emulator strategy (focused repo/VM tests; `firebase emulators:exec`) | Tests only | depends on 3 for canonical repo; does not mutate prod paths |
| 6 | Navigation/lifecycle convergence (typed `combine` in `observarTareas`; logout/auto-login controllers; safe-args) | Yes | depends on 5 for safety net; chained PR |
| 7 | Account deletion / security (atomic cleanup or Cloud Function; envelope TD-13) | Yes/Functions | depends on 4 (rules) and 5 (tests); highest blast radius, done last |

**Why this order**: factual artifacts first (unblocks all later steps with a shared vocabulary);
avatar authority is bounded to avatar code and its own ADR; repo consolidation is the biggest
behavioral refactor and goes *after* docs so the new canonical path is specced; rules/indexes
after consolidation so they describe the target, not the duplicate; tests before risky lifecycle
and deletion work so there is a regression net. Each non-doc step requires a clean/explicit
baseline (see Dirty Worktree handling).

### Decision: Dirty worktree forces gated apply, not free refactor

This change is docs-only and **safe on the dirty worktree** (no app code touched). Any future
application-code SDD change in steps 2-7 MUST start from a clean worktree OR a user-explicitly
accepted baseline (`size:exception`-style acknowledgment recorded in that change's proposal).
Design does not resolve the existing 54-modified+10-untracked state; it records the gate.

## Data Flow

Guide assembly and sync flow:

```
   specs (5)  ──evidence──▶  TEAMTASK_GUIDE.md (index)
      │                          │
      └─ Engram topic keys ◀─────┴─ architecture/teamtask-guide (summary only)
                                            │
   future agent ─search Engram─▶ locate guide ─▶ read guide + specs (source of truth)
```

No runtime/data-plane changes. The only "flow" is documentation retrieval.

## Information Architecture of the Guide

`docs/architecture/TEAMTASK_GUIDE.md` sections (thin index):

| Guide section | Maps to spec | Content policy |
|---|---|---|
| 0. How to read this guide | (meta) | Source-of-truth tiers; `[UNVERIFIED]` rule; sync pattern |
| 1. Architecture map | `architecture-map` | Package matrix + allowed exceptions table (link) |
| 2. Task & group domain | `task-domain` | State machine + points invariants (link) |
| 3. Firestore data contracts | `firestore-contracts` | Collections + access patterns (link) |
| 4. Navigation & lifecycle | `navigation-lifecycle` | Graph + listener ownership (link) |
| 5. Implementation recipes | `implementation-recipes` | Naming + lifecycle + tx rules (link) |
| 6. Technical debt register | consolidated from all specs' "Future Convergence Work" | Single TD table (TD-1..TD-15) |
| 7. Convergence roadmap | this design's roadmap section | Sequenced steps 1..7 |
| 8. Source-of-truth & verification policy | `implementation-recipes` + this design | Tier list + manual verification matrix |
| 9. Follow-up SDD changes | this design | Deliverable map (next section) |

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `docs/architecture/TEAMTASK_GUIDE.md` | Create | Canonical thin-index guide; sections 0-9 above |
| `openspec/changes/teamtask-architecture-guide/design.md` | Create | This file |
| `openspec/specs/architecture-map/spec.md` | (Archive step) | Promote delta to main spec when archived |
| `openspec/specs/task-domain/spec.md` | (Archive step) | Same |
| `openspec/specs/firestore-contracts/spec.md` | (Archive step) | Same |
| `openspec/specs/navigation-lifecycle/spec.md` | (Archive step) | Same |
| `openspec/specs/implementation-recipes/spec.md` | (Archive step) | Same |

No application code is modified or created. No Hilt/Room/Retrofit/Compose. The five spec files are
created in `openspec/specs/` during archive, not in this design step. Sanity package: `com.example.tfg`.

## Testing Strategy

This change is documentation-only; no automated tests apply (`strict_tdd: false`).
Verification is a manual drift-check matrix:

| Layer | What to verify | Approach |
|-------|---------------|----------|
| Docs | Every guide claim links to a spec; every spec claim has `file:line` | Manual cross-check + `grep -r "\[UNVERIFIED\]"` audit |
| Spec drift | 3 sampled spec claims match cited source lines | Manual |
| Stack purity | Guide asserts no Hilt/Room/Retrofit/Compose | `grep -r "androidx.compose\|dagger.hilt\|androidx.room\|retrofit2" app/` returns zero |
| Engram sync | `architecture/teamtask-guide` observation exists and points at guide path | `mem_search` |

Manual verification workflows for the *content* of each spec already exist inside the specs
themselves; the guide does not duplicate them.

## Migration / Rollout

No data migration. Rollout is: create guide + design; archive (promotes deltas to
`openspec/specs/`) only after verification in `sdd-verify`. Rollback is documentation-only:
delete the guide file and the change folder; Engram topic keys are upserts (no destructive
history). Specs are safe to roll back because no code depends on them yet.

## Deliverable Map (this change)

| Deliverable | Path | Owner phase |
|---|---|---|
| Canonical guide | `docs/architecture/TEAMTASK_GUIDE.md` | sdd-apply (docs) |
| Design | `openspec/changes/teamtask-architecture-guide/design.md` | this phase |
| Tasks | `openspec/changes/teamtask-architecture-guide/tasks.md` | sdd-tasks |
| Verify report | `openspec/changes/teamtask-architecture-guide/verify-report.md` | sdd-verify |
| Specs promoted | `openspec/specs/{architecture-map,task-domain,firestore-contracts,navigation-lifecycle,implementation-recipes}/spec.md` | sdd-archive |

## Follow-up SDD changes (future)

| Change (suggested name) | Step | Boundary |
|---|---|---|
| `teamtask-avatar-authority` | 2 | ADR + small code; clean baseline |
| `teamtask-task-repo-consolidation` | 3 | refactor; likely chained PR |
| `teamtask-firestore-rules-indexes` | 4 | rules/indexes/App Check; console access |
| `teamtask-testing-emulator-strategy` | 5 | tests only; emulator harness |
| `teamtask-navigation-lifecycle-convergence` | 6 | lifecycle/observers/controllers |
| `teamtask-account-deletion-security` | 7 | cleanup atomicity/Cloud Function |

## Interfaces / Contracts

No new code interfaces (documentation change). The only "contract" introduced is the
Engram sync contract `architecture/teamtask-guide`:

```
topic_key: architecture/teamtask-guide
content:   one-paragraph guide summary
           + guide path: docs/architecture/TEAMTASK_GUIDE.md
           + spec topic keys: sdd/teamtask-architecture-guide/spec/{name}
           + last-updated date
```

## Dependencies

- The five specs (already produced): `architecture-map`, `task-domain`, `firestore-contracts`,
  `navigation-lifecycle`, `implementation-recipes`.
- Exploration inventory `teamtask-architecture-inventory/explore`.
- Firebase console access is **not** required for this step (only for step 4 follow-ups).
- Clean/explicit worktree baseline is **not** required for this step (docs-only); it is a gate for
  steps 2-7.

## Verification

- `grep -rn "\[UNVERIFIED\]" openspec/changes/teamtask-architecture-guide/specs/` lists every
  uncertain claim (audit, not empty-check).
- Guide link-checking: every section links to exactly one spec file path.
- Stack purity grep (above) returns zero.
- Engram sync observation present.

## Open Questions

- [ ] None blocking this design. Avatar authority decision and Firestore rules confirmation are
      intentionally deferred to follow-up changes (steps 2 and 4), not open here.
