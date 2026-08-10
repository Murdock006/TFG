# Proposal: TeamTask Architecture Guide

## Intent

TeamTask lacks a single source of truth for its architecture. README claims MVVM/StateFlow but the real codebase is a hybrid: service locator, direct UI-to-Firebase access, two task repositories with divergent financial rules, two avatar strategies, and imperative navigation layered on top of Navigation Component. This produces stale Engram memories, inconsistent implementation decisions, and slow onboarding. A canonical guide makes future implementation faster, reduces noisy/stale memory, and establishes a convergence roadmap.

## Scope

### In Scope
- Factual architecture map: packages, dependency directions, layer boundaries, allowed flow exceptions
- Domain contracts: task state machine, points/reservation/rewards invariants, group lifecycle, dispute states
- Firestore data contracts: collections, fields, access patterns, required indices/rules (marked as unverified)
- Navigation and lifecycle: nav graph + imperative flows, listener/scope ownership, cancellation policy
- Avatar authority decision: unify local vs Firebase, resolve `tfg_prefs`/`avatar_prefs` split
- Implementation recipes: how to add features within existing patterns (XML/Fragments/ViewBinding)
- Testing/build/security baseline: manual verification matrix, known technical debt register
- Source-of-truth policy: code > verified repo config > docs > historical Engram; evidence paths required; uncertainty markers for unverified claims

### Out of Scope
- No broad refactor (no Hilt/Room/Retrofit/Compose migration)
- No invented Firestore rules, indices, or Cloud Functions
- No deletion of historical Engram memories without source verification
- No mixing with the existing dirty worktree (54 modified + 10 untracked files)
- No Gradle/test execution
- No application code changes

## Capabilities

> Contract with sdd-spec. No existing specs in `openspec/specs/`.

### New Capabilities
- `architecture-map`: Package responsibilities, dependency directions, layer boundaries, and allowed exceptions for each layer
- `task-domain`: Task state machine, points/reservation/rewards invariants, assignment rules, recurrence, dispute lifecycle
- `firestore-contracts`: Collection schemas, field contracts, access patterns, required indices and rules (with uncertainty markers)
- `navigation-lifecycle`: Nav graph flows, imperative navigation, auth flow, listener/scope ownership, cancellation policy
- `implementation-recipes`: Feature addition patterns, testing matrix, technical debt register, convergence roadmap

### Modified Capabilities
None

## Approach

Documentation-first convergence: produce factual specs from source code, mark unverified claims with `[UNVERIFIED]`, and defer code changes to future SDD changes that reference these specs. Source-of-truth priority: code > `build.gradle.kts`/`config.yaml` > README > historical Engram. Each spec includes evidence paths (file:line) and uncertainty markers.

Incremental roadmap: (1) publish specs, (2) audit and fix avatar authority, (3) consolidate task repositories, (4) formalize Firestore rules/indices, (5) add focused tests per spec.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `openspec/specs/architecture-map/` | New | Canonical package/layer spec |
| `openspec/specs/task-domain/` | New | Domain invariants spec |
| `openspec/specs/firestore-contracts/` | New | Data contracts spec |
| `openspec/specs/navigation-lifecycle/` | New | Navigation and lifecycle spec |
| `openspec/specs/implementation-recipes/` | New | Patterns and debt register |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Firestore rules/indices unknown | High | Mark all rule/index claims `[UNVERIFIED]`; require Firebase console confirmation |
| Avatar authority unresolved | High | Spec documents both paths; decision deferred to future change |
| Duplicate task repos cause inconsistent behavior | High | Spec documents both; consolidation deferred to future change |
| Stale Engram memories contradict source | Med | Source-of-truth policy; evidence paths required |
| Dirty worktree conflicts with future apply | Med | Out of scope; future changes must resolve first |

## Rollback Plan

Specs are documentation-only. Delete `openspec/specs/{capability}/` and corresponding Engram artifacts. No code is modified.

## Dependencies

- Firebase console access to verify rules, indices, and App Check (currently unavailable)
- Resolution of dirty worktree before any future apply phase

## Success Criteria

- [ ] 5 specs published with evidence paths and uncertainty markers
- [ ] Source-of-truth policy documented and applied across all specs
- [ ] Technical debt register lists all known issues with severity
- [ ] Convergence roadmap with prioritized future changes
- [ ] Engram context reduced: future sessions reference specs instead of stale memories
