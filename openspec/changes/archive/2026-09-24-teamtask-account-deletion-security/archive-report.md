# Archive Report: TeamTask account deletion & security cleanup

Change: `teamtask-account-deletion-security` · Repo: `C:\Users\Victor\AndroidStudioProjects\TFG2` · Branch: `master` · Store: hybrid (files under `openspec/` + Engram mirrors) · Archived: `2026-09-24`. Delivery: solo developer, direct commits to `master`, no pull requests.

This report is the terminal record of the cycle. It describes the state of the change AT CLOSE, not at earlier points. `apply-progress.md` and `verify-report.md` are intermediate snapshots; where they disagree with the persisted `tasks.md` artifact or the archive launch context, the latter rank higher (Final-State Authority hierarchy).

## Final State (authoritative at close)

**14 / 15 tasks complete. Task 6.2 / Global Gate G.7 (Manual Verification Matrix M1–M4) NOT EXECUTED.**

Task 6.2 requires a device/emulator and two accounts against the Firebase project; both were unavailable in this run. No matrix row was executed and none is claimed passing. Per the `tasks.md` artifact (highest-ranked source), every numbered task is checked except 6.2, and `G.7` is unchecked. The archive launch context supplies no later evidence that 6.2 was executed or that any earlier verify finding was fixed in a later commit; `22bc99d` is the last commit in range.

`verify-report.md` (intermediate snapshot, written 2026-09-25) recorded status **complete for all executable checks**: both Kotlin variants compile, the forced `--rerun` JVM suite is 8/0 (with the new `ResultadoLimpiezaCuentaTest` at 4/0), and grep audits G.3–G.6 pass. Its static contract review found all 12 `account-deletion` requirements (29 scenarios) satisfied by implementation paths. **No CRITICAL finding was recorded.** The only unresolved verification dimension is the manual Firebase matrix (G.7 / 6.2), which is `NOT EXECUTED`; no runtime PASS is claimed for it. Archive proceeds by **accepting the documented manual-verification debt**.

### Verify findings carried forward

- **CRITICAL: none.**
- **WARNING: none.**
- **Low / information (recorded as follow-ups, not blockers):**
  1. **Stale line citations** in the delta's recorded archive tables (`firestore-contracts`) and in the `account-deletion` delta's `observeUsuarios()` reference — corrected during this archive; see "Applied citation refresh" below.
  2. **O(all groups) read** — `limpiarGrupos` reads the whole `grupos` collection and filters client-side; correct and rule-permitted, but not a membership query. Future optimization only.
  3. **No-members branch interpretation limit** — the no-members branch deletes only the group document, not its tasks; the `account-deletion` requirements do not mandate that cascade.
  4. **Runtime gap** — the gate, retry convergence, dissolution writes, dispute-evidence deletion, and the reactive local clear are static observations only until the manual matrix runs.
  5. **Production/Console parity remains `[UNVERIFIED]`** for the cross-user `usuarios` update, the self `avatares/{uid}` delete, and the `grupos/{gid}` delete (`firestore.rules:27-32,37-40,43-48` allow them locally).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `account-deletion` | **Created** (new capability) | The delta was canonicalized into a complete spec: title `# Delta for account-deletion` → `# account-deletion`, `## ADDED Requirements` → `## Requirements`, and the `observeUsuarios()` citation refreshed. 12 requirements / 29 scenarios, all preserved byte-for-byte otherwise. |
| `firestore-contracts` | **Updated** | 2 MODIFIED requirements composed natively (1 added scenario each): "Query patterns MUST be listed per collection" gains the account-cleanup query set incl. `whereEqualTo("grupoId", gid)`; "Avatar bytes SHALL live in a dedicated `avatares/{uid}` collection" gains the cleanup-delete clause. Plus the recorded non-requirement table replacements (collections/access, `usuarios` field table, Storage, assumed server-side behaviour, Future Convergence Work, and 7 Manual Verification rows added). |
| `implementation-recipes` | **Updated** | 1 ADDED requirement ("Destructive account cleanup MUST be verifiable, idempotent, and gated"). Plus the recorded non-requirement table replacements (TD-13 → `Resolved (teamtask-account-deletion-security)` with refreshed evidence; convergence roadmap step 7 aligned with the guide and set to `Done (teamtask-account-deletion-security)`; 7 Manual Verification rows added). |

### Native composition (requirement blocks)

Both existing canonical specs were composed with the native tool; each exited `0`:

```
gentle-ai sdd-archive-compose --canonical "openspec/specs/firestore-contracts/spec.md" \
  --delta "openspec/changes/teamtask-account-deletion-security/specs/firestore-contracts/spec.md" \
  --output "openspec/specs/firestore-contracts/spec.md.compose-tmp"        # exit 0
gentle-ai sdd-archive-compose --canonical "openspec/specs/implementation-recipes/spec.md" \
  --delta "openspec/changes/teamtask-account-deletion-security/specs/implementation-recipes/spec.md" \
  --output "openspec/specs/implementation-recipes/spec.md.compose-tmp"    # exit 0
```

The compose output also appended the delta's `## Non-requirement updates (apply at archive)` / `## Unchanged content (preserved)` prose blocks; those are archive instructions, not canonical content, and were removed mechanically while every composed requirement block was preserved verbatim. The recorded tables were applied by literal table splicing before composition, anchored by heading; unrelated requirements and scenarios were preserved byte-for-byte.

`account-deletion` is a NEW capability, so the native compose tool has no canonical requirement to match against (it refuses an empty canonical). Its spec was produced by a mechanical shell copy of the delta plus the three deterministic line-level normalizations listed above; nothing else was altered.

### Applied non-requirement table replacements

**`firestore-contracts`**:
1. "Collections and access patterns" — `usuarios`, `avatares`, `grupos`, `invitaciones`, `tareas`, `recompensas`, `canjes`, `disputas` rows updated (account cleanup becomes a documented producer/consumer).
2. `usuarios/{uid}` field table — `puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias`, `grupoId` rows gain the dissolution write as a source of truth; `avatarUrl` marked producer-less.
3. Storage table — `disputas` cleanup citation refreshed.
4. Assumed server-side behaviour (UNVERIFIED) — 3 rows added (self `avatares` delete, cross-user `usuarios` update, `grupos` delete) and the Cloud Function row note updated.
5. Future Convergence Work — Cloud Function row note updated.
6. Manual Verification — 7 rows added (cleanup coverage, dissolution writes, dissolution task deletion, reactive local clear, Auth-deletion gate, residual Auth-delete failure, `avatares` cleanup rule).

**`implementation-recipes`**:
1. Technical debt register — TD-13 → `Resolved (teamtask-account-deletion-security)` with refreshed evidence; all other rows unchanged.
2. Convergence roadmap — step 7 title aligned with the guide §7 and set to `Done (teamtask-account-deletion-security)`.
3. Manual Verification — 7 rows added.

### Applied citation refresh

The verify report flagged pre-change line ranges in the recorded archive tables. At apply time these were refreshed to the current source positions:

| Stale citation (delta) | Refreshed to | Occurrences |
|---|---|---|
| `AuthRepositorioFirebase.kt:267-271` (self delete) | `AuthRepositorioFirebase.kt:361-362` | 1 |
| `AuthRepositorioFirebase.kt:274-295` (group dissolution) | `AuthRepositorioFirebase.kt:418-421` | 1 |
| `AuthRepositorioFirebase.kt:301-327` (task sweeps) | `AuthRepositorioFirebase.kt:298,307` | 1 |
| `AuthRepositorioFirebase.kt:344-362` (disputes cleanup) | `AuthRepositorioFirebase.kt:340-353` | 2 |
| `AuthRepositorioFirebase.kt:302,312` (task cleanup queries) | `AuthRepositorioFirebase.kt:298,307` | 3 |
| `AuthRepositorioFirebase.kt:393-432` (`observeUsuarios`, `account-deletion`) | `AuthRepositorioFirebase.kt:516-555` | 1 |

The avatar deletion line is `AuthRepositorioFirebase.kt:356-358` (recorded for reference; the applied tables carried no stale avatar code citation). Citations not included in the launch-provided refresh set — the `AuthRepositorioFirebase.kt:265-363` best-effort/Cloud-Function references in the `firestore-contracts` assumed-server-side and Future Convergence Work rows and in `implementation-recipes` TD-13 — were left as recorded and are listed as residual risks below.

## Archive Contents

- `proposal.md` — present. `exploration.md` — present. `specs/` — present (3 delta specs). `design.md` — present.
- `tasks.md` — present; **14/15 numbered tasks complete**, 1 unfinished (6.2 / G.7); original bytes preserved.
- `apply-progress.md` — present (intermediate snapshot). `verify-report.md` — present (intermediate; complete for executable checks, no CRITICAL, manual matrix NOT EXECUTED).
- `archive-report.md` — this file (additive).

### Mechanical move evidence

```
# snapshot before move: 9 files
git mv openspec/changes/teamtask-account-deletion-security \
       openspec/changes/archive/2026-09-24-teamtask-account-deletion-security    # exit 0
git diff --no-index <snapshot>/source <destination>                                # exit 0 (empty, no differences)
snapshot file count: 9   destination file count: 9
SHA256 per-file comparison: all 9 files byte-identical
```

Empty readback diff is the only accepted passing evidence; no differences. No destination collision; the active change folder no longer exists. The archive-report above is additive and was excluded from the source/destination comparison because it did not exist in the source snapshot.

## Commits in this change's range

Baseline before the change: `6f6616e` (`feat(tasks): mark recurrent tasks with a symbol and colored border`).

| Commit | Message |
|--------|---------|
| `19cb00b` | docs(openspec): add account deletion proposal |
| `4564fe1` | docs(openspec): add account deletion specs and confirm decisions |
| `9acfd56` | docs(openspec): add account deletion design |
| `f625cf7` | docs(openspec): add account deletion tasks |
| `7117379` | feat(account): harden account deletion with verifiable cleanup and group dissolution |
| `dc0a4cd` | docs(openspec): record account deletion apply |
| `22bc99d` | docs(openspec): add account deletion verify report |

No commit was created by the archive phase. No application code, Gradle file, guide, or git history was modified by archive.

## [UNVERIFIED] markers preserved

- `account-deletion` retains the production/Console parity `[UNVERIFIED]` marker for the cleanup writes.
- `firestore-contracts` retains every `[UNVERIFIED]` server-side claim, including the three new account-cleanup/dissolution rows and the closing rules/indexes/App Check/Crashlytics marker.
- `implementation-recipes` retains its source-of-truth `[UNVERIFIED]` marker and the closing "No Firebase emulator, CI, or test-orchestration infrastructure was inspected" marker.
- No `[UNVERIFIED]` marker was resolved or removed by archive.

## Engram traceability

Engram mirrors (project `TFG-TeamTask`): `#167` explore, `#168` proposal, `#169` spec/account-deletion, `#170` spec/firestore-contracts, `#171` spec/implementation-recipes, `#172` spec (index), `#173` design, `#174` tasks, `#175` apply-progress, `#176` verify. This report is mirrored at `sdd/teamtask-account-deletion-security/archive-report`.

## Risks

1. **Production/Console parity `[UNVERIFIED]`.** The cross-user `usuarios` update (clear `grupoId`, reset balances), the self `avatares/{uid}` delete, and the `grupos/{gid}` delete are allowed by local `firestore.rules` but their production/Console parity is unverified. If production denies any of them, the affected cleanup step fails, the Auth account is not deleted, and the failure is surfaced (accepted behavior).
2. **Stale-citation refresh is applied, not source-verified per line.** The refreshed ranges come from the verify report and the launch context; only the account-deletion `observeUsuarios` and the `firestore-contracts` table/query citations were updated here.
3. **Residual stale citations** left as recorded because they were not in the refresh set: `AuthRepositorioFirebase.kt:265-363` in the `firestore-contracts` assumed-server-side Cloud Function row and Future Convergence Work row, and in `implementation-recipes` TD-13 evidence.
4. **Manual matrix not executed** — see Recorded follow-ups.

## Recorded follow-ups

1. **Task 6.2 / G.7 — Manual Verification Matrix M1–M4 NOT EXECUTED.** The only behavioral net for the change (`strict_tdd: false`; roadmap step 5 pending). The developer must run it against the Firebase project and record per-row evidence before the change is considered runtime-verified.
2. **Residual `:265-363` citations** — optional doc cleanup when the cleanup span is next re-verified.
3. **`limpiarGrupos` reads all groups** — optional future membership-query optimization.

## Recommended Next Work

1. Execute the manual matrix M1–M4 (task 6.2 / G.7) on a device with two accounts against the Firebase project.
2. Optionally refresh the residual `AuthRepositorioFirebase.kt:265-363` citations.
3. Continue the convergence roadmap: step 5 (focused tests) remains pending.
