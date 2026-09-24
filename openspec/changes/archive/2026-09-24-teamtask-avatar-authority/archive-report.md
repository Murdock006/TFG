# Archive Report: TeamTask Avatar Authority Convergence

Change: `teamtask-avatar-authority` · Repo: `C:\Users\Victor\AndroidStudioProjects\TFG2` ·
Branch: `master` · Store: hybrid (files under `openspec/` + Engram mirrors) · Archived:
`2026-09-24`. Delivery: solo developer, direct commits to `master`, no pull requests.

This report is the terminal record of the cycle. It describes the state of the change AT
CLOSE, not at earlier points. `apply-progress.md` and `verify-report.md` are intermediate
snapshots; where they disagree with the persisted `tasks.md` artifact or the archive launch
context, the latter rank higher.

## Final State (authoritative at close)

**21 / 22 tasks complete. Task 4.4 (12-row manual verification matrix) NOT EXECUTED.**

Task 4.4 requires an emulator/device build plus a live Firebase emulator and was explicitly
outside this change's execution scope. No row of the matrix was executed and none is claimed
passing. Per the `tasks.md` artifact (highest-ranked source), every task is checked except
4.4. No later evidence in the launch context reports task 4.4 executed or the verify warnings
fixed in later commits.

`verify-report.md` (intermediate snapshot, written at verification time) recorded
**PARTIAL**: the executable checks available at that time passed — a forced fresh compile of
the `debug`/`emulator`/`release` Kotlin variants, 4/4 JVM tests, six grep audits, and a static
contract review — while runtime behavior stayed NOT verified.

### Verify findings carried forward

- **CRITICAL: none.**
- **WARNING (unresolved at close):**
  1. **Runtime behavior is unverified.** Firestore write/read round-trips, base64 decode of
     real uploads, hint invalidation, offline fallback, and rules enforcement (matrix rows
     1-10) have no runtime evidence. Task 4.4 is open.
  2. **No automated test net for the substantive behavior.** The only new automated coverage
     is `AvatarImagenTest` (3 pure-JVM cases: base64 round-trip incl. `0x00`, malformed →
     `null`, and the `length > MAX_BASE64_CHARS` predicate). It does not exercise
     `comprimirJpeg`/`decodificarJpeg` (they touch `ContentResolver`/`Bitmap` and throw
     `Stub!` off-device), the Firestore write/read path, the mappers, the caches, the display
     sites, or `firestore.rules`. `ExampleUnitTest` (1) is the template.
  3. **Incremental-build caveat.** The verbatim compile/test commands both returned
     `UP-TO-DATE`; the PASS verdict rests on a forced `--rerun-tasks` run (69 tasks executed,
     `BUILD SUCCESSFUL`, test XML read).
- **SUGGESTION carried as an open follow-up (not a blocker):** stale tooling
  `tools/firebase/emulator-verify.mjs:169-181` still exercises the old **Storage** avatar
  path (`avatares/{uid}/probe.png`: unauth 403, own 200, other 403) and `checkStorageRules()`.
  After this change the client no longer uses that Storage path, so the check is stale.
  `proposal.md`/`design.md` defer pruning to the rules/indexes change; `storage.rules` was not
  touched. Recorded here as a follow-up for that change.

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `avatar-management` | **Created** (new capability) | Full canonical spec (purpose + `## Requirements`): 8 requirements, 21 scenarios. Requirements/scenarios are byte-identical to the delta section after the `## ADDED Requirements` → `## Requirements` heading rename; title and purpose were lightly composed (see below). |
| `firestore-contracts` | Updated | 1 ADDED requirement (`avatares/{uid}` collection contract) + 8 non-requirement table replacements. |
| `architecture-map` | Updated | 1 MODIFIED requirement (service locator loses the avatar bypass exception) + 6 non-requirement table replacements. |
| `implementation-recipes` | Updated | 1 ADDED requirement (binary avatar / preference-namespace convention) + 3 non-requirement table replacements. |

### Native composition (requirement blocks)

All three existing canonical specs were composed with the native tool; each exited `0` and
its `.compose-tmp` output was read back before being moved into place:

```
gentle-ai sdd-archive-compose --canonical "openspec/specs/firestore-contracts/spec.md" \
  --delta "openspec/changes/teamtask-avatar-authority/specs/firestore-contracts/spec.md" \
  --output "openspec/specs/firestore-contracts/spec.md.compose-tmp"     # exit 0
gentle-ai sdd-archive-compose --canonical "openspec/specs/architecture-map/spec.md" \
  --delta "openspec/changes/teamtask-avatar-authority/specs/architecture-map/spec.md" \
  --output "openspec/specs/architecture-map/spec.md.compose-tmp"        # exit 0
gentle-ai sdd-archive-compose --canonical "openspec/specs/implementation-recipes/spec.md" \
  --delta "openspec/changes/teamtask-avatar-authority/specs/implementation-recipes/spec.md" \
  --output "openspec/specs/implementation-recipes/spec.md.compose-tmp"  # exit 0
```

The compose output also appended the delta's `## Non-requirement updates (apply at archive)` /
`## Unchanged content (preserved)` prose blocks; those are archive instructions, not canonical
content, and were removed mechanically while every requirement block was preserved verbatim.
The recorded tables were then applied by hand.

### Applied non-requirement table replacements

**`firestore-contracts`** (delta section "Non-requirement updates (apply at archive)"):
1. Collections table — `usuarios` row updated (producer gains the canonical avatar repository
   writing `avatarUpdatedAt` only; `avatarUrl` marked legacy/no producer; consumer evidence
   `MainActivity.kt:441-448`).
2. Collections table — `avatares` row added (self-only writer; member readers; fields
   `base64`/`contentType`/`updatedAt`).
3. `usuarios/{uid}` field table — `avatarUrl` row replaced with the legacy statement.
4. `usuarios/{uid}` field table — `avatarUpdatedAt` row added.
5. New `avatares/{uid}` field table added after the `usuarios/{uid}` table.
6. Storage table — `avatares/{uid}/{uuid}.{ext}` row replaced with the "unused; pruning
   deferred" statement.
7. Assumed server-side behaviour — `usuarios` own read/write evidence updated to the local
   `firestore.rules:27-32`; the Storage `avatares/{uid}/*` row replaced and a new Firestore
   `avatares/{uid}` read/self-write row added.
8. Known Risks — avatar Storage bullet replaced with "Avatar storage path is unused
   (resolved)". Future Convergence Work — the "Decide avatar authority (local vs Storage)"
   row removed. Manual Verification — 3 rows added (`avatares` writer/reader rule; avatar not
   in `usuarios`; Storage avatar path unused).

**`architecture-map`** (delta section "Non-requirement updates (apply at archive)"):
1. Package inventory — `repositorio` row updated (`AvatarRepositorio` added to interfaces).
2. Package inventory — `data/firebase` row updated (Firestore base64 avatar impl via
   `FirebaseComposition`).
3. Package inventory — `data/local` row updated (last-known cache only; no authority role).
4. Effective flow — `ViewModel → service locator` row updated (avatar resolved through
   `LocalizadorServicios.repositorioAvatar`).
5. Future Convergence Work — the "Unify avatar authority" row removed.
6. Manual Verification — `repo` row updated to include `AvatarViewModel`; "Avatar authority
   audit" row added.

**`implementation-recipes`** (delta section "Non-requirement updates (apply at archive)"):
1. Technical debt register — `TD-2` and `TD-3` status set to
   `Resolved (teamtask-avatar-authority)`.
2. Convergence roadmap — step 3 status set to `Done (teamtask-avatar-authority)`.
3. Manual Verification — 5 avatar rows added (storage round-trip, oversize rejection,
   cross-user write denied, namespace audit, offline fallback).

### New capability composition (`avatar-management`)

No canonical spec existed. The delta was copied to `openspec/specs/avatar-management/spec.md`
with a mechanical shell copy verified by an empty `diff -r`, then composed into canonical
form:

- `# Delta for avatar-management` → `# avatar-management`.
- `## ADDED Requirements` → `## Requirements`.
- Purpose paragraph 1 reworded from "This is a NEW capability. It defines …" to
  "This capability defines …".
- The stale "Today there is no single authority …" paragraph was reframed as the pre-change
  observable state, because presenting the resolved split as current would contradict this
  terminal record. All evidence references (`viewmodel/AvatarViewModel.kt:14-16`,
  `data/local/AvatarRepositorioLocal.kt:11,23-34,86`,
  `data/firebase/AvatarRepositorioFirebase.kt:1-174`,
  `vista/FragmentPgPrincipal.kt:377-378`, `modelo/Usuario.kt:18`,
  `data/firebase/AuthRepositorioFirebase.kt:125-136,194-205,375-383,404-412`) were retained.

The requirement and scenario bodies were **not** altered: after the heading rename the
`## Requirements` section is byte-identical to the delta section (verified by string
comparison in the archive phase).

## Archive Contents

- `proposal.md` — present.
- `exploration.md` — present.
- `specs/` — present (four delta specs: `avatar-management`, `firestore-contracts`,
  `architecture-map`, `implementation-recipes`).
- `design.md` — present.
- `tasks.md` — present; **21/22 complete**, 1 unfinished (4.4). Original bytes preserved;
  checkboxes not repaired.
- `apply-progress.md` — present (intermediate snapshot).
- `verify-report.md` — present (intermediate snapshot; PARTIAL).
- `archive-report.md` — this file (additive; did not exist in the pre-move source snapshot).

### Mechanical move evidence

The entire change folder was moved with `git mv` (10 files). A recursive snapshot of the
source was taken before the move, and `diff -r` of the snapshot against the archived
destination was run as readback:

```
git mv openspec/changes/teamtask-avatar-authority \
       openspec/changes/archive/2026-09-24-teamtask-avatar-authority      # exit 0
diff -r <snapshot>/source <destination>                                   # exit 0 (empty, no differences)
snapshot file count: 10   destination file count: 10
```

An empty readback diff is the only accepted passing evidence; there were no differences.
The destination did not previously exist (no collision). The active
`openspec/changes/teamtask-avatar-authority/` no longer exists.

## Commits in this change's range

Baseline before the change: `b8e6fa0` (`docs(openspec): archive task repo consolidation
change`). Feature and documentation commits:

| Commit | Message |
|--------|---------|
| `a4fd934` | docs(openspec): add avatar authority proposal |
| `aae771a` | docs(openspec): add avatar authority delta specs |
| `46ee7b4` | docs(openspec): add avatar authority design |
| `ebe7721` | docs(openspec): add avatar authority tasks |
| `8ee3136` | feat(avatar): add canonical avatar contract, image helpers, and JVM test |
| `d9a9593` | feat(avatar): store avatars in Firestore base64 with atomic hint |
| `69cd7eb` | feat(avatar): resolve group avatars at all display sites |
| `f4f9f7a` | docs(openspec): record avatar authority apply |
| `a852537` | docs(openspec): add avatar authority verify report |

No commit was created by the archive phase. No application code, Gradle file, guide, or git
state was modified by archive.

## `[UNVERIFIED]` markers preserved

All `[UNVERIFIED]` markers from the sources were carried through into the canonical specs and
this report. In particular:

- Firebase Console billing, quota, deployment, and production rules parity remain
  `[UNVERIFIED]`.
- The local `firestore.rules` `avatares/{uid}` rule is a local file only; no deployment was
  performed.
- The `avatar-management` canonical spec retains its `[UNVERIFIED]` Firebase Console
  billing/quota/deployment markers; `firestore-contracts` retains its `[UNVERIFIED]`
  server-side assumption and Known Risks markers; `architecture-map` and
  `implementation-recipes` retain their closing `[UNVERIFIED]` markers verbatim.

## Engram traceability

Artifacts were read from the file locators (hybrid store). Corresponding Engram mirrors
(project `TFG-TeamTask`), recorded for traceability, are:
`#136` proposal, `#137` spec/avatar-management, `#138` spec/firestore-contracts, `#139`
spec/architecture-map, `#140` spec/implementation-recipes, `#141` design, `#143`
apply-progress, `#144` verify. No `sdd/teamtask-avatar-authority/tasks` observation exists
(`mem_search` returned none); the persisted `tasks.md` file was the authority for completion
state. This report is mirrored at `sdd/teamtask-avatar-authority/archive-report`.

## Recommended Next Work

1. Execute task 4.4 (the 12-row manual matrix) on the `emulator` build against the Firebase
   emulator before relying on any runtime avatar claim.
2. In the rules/indexes change, prune the stale Storage avatar exercise in
   `tools/firebase/emulator-verify.mjs:169-181` and the dead `storage.rules` avatar path.
3. Consider a Firestore-emulator-backed test for upload/read and the two-document atomicity
   (convergence roadmap step 5 covers focused Firestore tests).
