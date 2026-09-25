# Proposal: Harden Account Deletion and Security Cleanup

## Intent

Deleting a TeamTask account is currently a **silent best-effort** operation: every cleanup step is
wrapped in `try/catch` that only logs, so the user always sees "cuenta eliminada" even when data was
left behind, and the avatar document is never deleted at all
(`data/firebase/AuthRepositorioFirebase.kt:265-363`; TD-13). Two additional problems follow from the
same flow: cleanup runs *before* `FirebaseAuth.delete()` (`:246` then `:249`), so a failed Auth
deletion leaves a live account whose data was already wiped; and the group path only removes the
uid, leaving a surviving member with an active group and untouched balances instead of the intended
dissolution.

This change implements convergence roadmap step 7 (`docs/architecture/TEAMTASK_GUIDE.md` §7) and
closes TD-13. It makes the client cleanup **complete, verifiable, and honest**, implements the
**group dissolution + remaining-member reset** semantics the owner decided, and documents the
remaining order risk and its mitigation.

## Scope

### In Scope
- Delete `avatares/{uid}` (the avatar Firestore document) as part of cleanup; keep every existing
  deletion (usuarios doc, group removal path, user-created tasks, unassign assigned tasks,
  invitaciones, notificaciones, recompensas, canjes, disputas + Storage evidence).
- **Group dissolution** when the deleted user is one of exactly two members: delete `grupos/{gid}`,
  clear the remaining member's `grupoId` in Firestore, and rely on / document the remaining member's
  live observer to clear its local `tfg_prefs` `grupoId` (`viewmodel/ParejaViewModel.kt:83-105`).
- **Remaining-member balance reset**: `puntos`, `puntosReservados`, `puntosRecompensa` to zero.
- **Verifiable cleanup**: per-step failure collection + bounded retry; never report success when a
  step failed; surface a clear partial-failure message.
- **Order hardening**: gate `FirebaseAuth.delete()` on a completed cleanup report; document the
  residual inverse risk and the accepted partial-cleanup behavior.
- Delta specs for the affected capabilities; manual verification matrix (no automated tests today,
  `openspec/config.yaml:6`).

### Out of Scope
- Google Sign-In deletion specifics.
- Storage redesign — avatars are Firestore documents since `teamtask-avatar-authority`; the dead
  `avatares/{uid}/{uuid}.{ext}` Storage path is not pruned here (`firestore-contracts` defers it).
- Account-deletion scheduling or server-side Cloud Functions.
- TD-7 (non-atomic reclamo) and any task-domain state change.
- Avatars, navigation, and lifecycle convergence.
- Redesigning the re-auth password dialog UX or the `EliminacionCuentaActivity` info page
  (preserved as-is; `vista/FragmentPerfil.kt:214-275`; `vista/EliminacionCuentaActivity.kt:10-31`).
- Deploying rules/indexes to the Firebase Console (TD-9 partially resolved; console parity stays
  `[UNVERIFIED]`).

## Resolved Decisions

| # | Decision | Status |
|---|---|---|
| 1 | Tasks created by the deleted user are **deleted** (current behavior kept). The partner loses those tasks — this cascade MUST be documented. | Fixed by owner |
| 2 | When one member remains, the **group is dissolved**: delete the `grupos` doc and clear the remaining member's `grupoId` in Firestore and in local `tfg_prefs`. | Fixed by owner |
| 3 | The remaining member's balances are **reset to zero**: `puntos`, `puntosReservados`, `puntosRecompensa`. | Fixed by owner |
| 3a | `rachaDias`: **recommended to reset to zero too** for a coherent clean slate. Flagged for confirmation; if the owner declines, the reset excludes `rachaDias`. | Confirmed by owner — reset includes `rachaDias` |
| 4 | **CONFIRMED by the owner (2026-09-24)**: the dissolved group's remaining tasks are **deleted too** (tasks whose `grupoId` is the dissolved group, not already removed by decisions 1/3). Rationale: a coherent clean slate; the group no longer exists, so its tasks would otherwise be orphaned. This is inferred from decisions 2+3, **not** an explicit owner instruction. | Confirmed |

> Decision 4 is destructive and irreversible without a Firebase point-in-time restore
> ([UNVERIFIED] console capability). Confirmed by the owner on 2026-09-24.

## Capabilities

### New Capabilities
- `account-deletion`: end-to-end account-deletion contract — re-authentication, verifiable per-step
  cleanup across all associated Firestore collections, the `avatares/{uid}` document, and Storage
  dispute evidence; group dissolution and remaining-member reset; ordering guarantees; and explicit
  failure reporting (no silent success).

### Modified Capabilities
- `firestore-contracts`: document the new cleanup writes (delete `avatares/{uid}`; clear
  `usuarios/{remainingUid}.grupoId`; reset remaining-member balances; delete the dissolved group and
  its remaining tasks) and the cleanup-completion evidence; refresh the TD-13 / "no Cloud Function"
  notes.
- `implementation-recipes`: mark TD-13 Resolved, update the convergence roadmap row, and add the
  account-deletion manual verification matrix required for new behavior.

## Approach

### 1. Verifiable cleanup (chosen approach)
- Wrap each cleanup step in a helper that captures success/failure instead of swallowing it, with a
  **bounded retry** (small fixed attempt count for transient network errors).
- Collect a structured result — a cleanup report listing which steps failed and why — rather than a
  bare boolean.
- **If any step still fails after retries, abort before `FirebaseAuth.delete()`** and return a
  failure whose message names the failed step(s). The deletion is not performed and the user can
  retry; **success is never reported when a step failed**.
- All cleanup operations are deletes/updates and therefore **idempotent**, so a retry re-runs the
  whole cleanup safely.
- Cleanup spans many documents and Storage, so it is **not atomic**. This is explicitly accepted and
  surfaced: an aborted run may have applied some steps; the retry converges.

### 2. Order hardening
- New order: re-authenticate → run cleanup with retries → **only if cleanup is complete** call
  `usuarioActual.delete()` → clear cache and `signOut()`.
- This removes the current failure mode where a live account survives with already-deleted data.
- **Residual, explicitly accepted and surfaced**: if `FirebaseAuth.delete()` itself fails after a
  fully successful cleanup (e.g. recent-login required), the account survives with cleaned data. The
  existing re-auth error path (`AuthRepositorioFirebase.kt:256-258`; `FragmentPerfil.kt:287-291`)
  is preserved and the user is told to retry; cleanup re-runs idempotently on retry.

### 3. Group dissolution + remaining-member reset (decisions 2 / 3 / 3a / 4)
For each group containing the deleted uid:
- **No remaining members** → delete the group doc (current behavior, `:290-294`).
- **Exactly one remaining member** → dissolve:
  - delete `grupos/{gid}`;
  - set `usuarios/{remainingUid}.grupoId = null` in Firestore (rules allow any signed-in update,
    `firestore.rules:27-32`, [UNVERIFIED] console parity);
  - reset the remaining member's `puntos`, `puntosReservados`, `puntosRecompensa` to `0` (and
    `rachaDias` to `0` if 3a is confirmed);
  - delete the dissolved group's remaining tasks — **only if decision 4 is confirmed**.
- **Two or more remaining members** (not the expected couple case) → keep the current membership
  map update, no dissolution.

### 4. Remaining member's device (reactive local clear)
The deleting client clears the remaining member's Firestore `grupoId`. The remaining member's device
observes the group document via `ParejaViewModel.startObservingGrupo`; when the doc disappears it
emits `null` and the ViewModel clears the local `tfg_prefs` `grupoId`
(`viewmodel/ParejaViewModel.kt:83-105`, clear at `:91-98`). This change documents that reactive path
and MUST NOT duplicate it with a second mechanism. The deleting device still clears its own local
prefs as today (`vista/FragmentPerfil.kt:297-307`).

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt` | Modified | Add `avatares/{uid}` deletion; per-step report + retry; group dissolution + remaining-member reset; gate Auth deletion on cleanup success |
| `app/src/main/java/com/example/tfg/repositorio/AuthRepositorio.kt` | Modified | Cleanup report type / result surface (exact shape decided in design) |
| `app/src/main/java/com/example/tfg/viewmodel/VistaModeloAuth.kt` | Modified | Propagate the partial-failure message through `eliminacionCuenta` (`:102-120`) |
| `app/src/main/java/com/example/tfg/vista/FragmentPerfil.kt` | Modified | Surface partial-failure message; keep the dialog and success/navigation UX (`:214-314`) |
| `app/src/main/java/com/example/tfg/viewmodel/ParejaViewModel.kt` | Reused | Existing reactive local `grupoId` clear on group removal (no new code unless design finds a gap) |
| `app/src/main/java/com/example/tfg/repositorio/RepositorioPareja.kt` | Reused | `quitarMiembroGrupo` / `limpiarGrupoIdUsuario` semantics referenced; no change expected |
| `firestore.rules` | Unchanged | Evidence only; self-write on `avatares` and signed-in update on `usuarios` already permit the flow |
| `openspec/specs/firestore-contracts/spec.md` | Modified (delta) | Cleanup writes + completion evidence |
| `openspec/specs/implementation-recipes/spec.md` | Modified (delta) | TD-13 Resolved + verification matrix |
| `openspec/changes/teamtask-account-deletion-security/` | New | `exploration.md`, `proposal.md`, and later delta specs/design/tasks |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Non-atomic cleanup leaves partial state on abort | Med | Idempotent steps; abort surfaces failure; retry re-runs the whole cleanup; accepted and documented |
| A persistent cleanup failure blocks deletion | Med | Bounded retry + explicit failed-step message; failures expected to be transient network/permission; user retries. Console rule parity is [UNVERIFIED] |
| `usuarios` cross-user `grupoId` clear denied by production rules | Low | Local `firestore.rules:27-32` allows it; [UNVERIFIED] console parity flagged; failure is surfaced, not silently ignored |
| Deleting the remaining member's tasks (decision 4) destroys data | Med | **Derived decision, withheld until the owner confirms**; documented as irreversible |
| Resetting the remaining member's balances is user-visible and involuntary | Med | Fixed owner decision; documented; only applies on dissolution; `rachaDias` flagged separately |
| Partner loses tasks created by the deleted user (decision 1) | High (by design) | Documented cascade; irreversible; no re-creation |
| `FirebaseAuth.delete()` fails after successful cleanup | Low | Surfaced via existing re-auth message; retry re-runs idempotent cleanup; documented residual |
| No automated tests; regression risk concentrated in Auth/group flows | Med | Manual verification matrix in specs/PR; strict TDD is off (`openspec/config.yaml:6`) |

## Rollback Plan

- Client-side change only; no schema migration and no server artifact. Roll back by reverting the
  change's commit(s) on `master` (solo developer, direct-to-master, no PRs) — this restores the
  previous best-effort cleanup and the old group-removal behavior.
- Rollback boundary: `AuthRepositorioFirebase`, `AuthRepositorio`, `VistaModeloAuth`, `FragmentPerfil`,
  and the delta specs. No Gradle, navigation, or avatar changes are in scope.
- **Irreversible data caveat**: rollback does not restore data already deleted by a completed run;
  restoration would require a Firebase point-in-time restore ([UNVERIFIED]). Orphaned `avatares` docs
  created before this change are not retroactively cleaned.

## Dependencies

- Firebase Auth / Firestore / Storage client SDK only; **no Cloud Functions**.
- Owner confirmation of the **derived decision 4** and the **`rachaDias` recommendation (3a)** before
  apply.
- Delta updates to `firestore-contracts` and `implementation-recipes` via the normal SDD spec/archive
  flow.
- Preserve the re-auth dialog UX and `EliminacionCuentaActivity` info page (`[UNVERIFIED]` where only
  console evidence could confirm rule parity).

## Success Criteria

- [ ] `avatares/{uid}` is deleted during cleanup; no new orphaned avatar doc results from a successful deletion.
- [ ] Cleanup reports per-step results; a failed step produces a clear partial-failure message and is never reported as success.
- [ ] `FirebaseAuth.delete()` is only called after a complete cleanup; on partial failure the account is not deleted.
- [ ] Dissolution triggers when one member remains: group doc deleted, remaining member's Firestore `grupoId` cleared, local `tfg_prefs` cleared by the live observer.
- [ ] Remaining member's `puntos`, `puntosReservados`, `puntosRecompensa` reset to `0`; `rachaDias` handling matches the confirmed decision.
- [ ] Decision 4 (delete dissolved group's remaining tasks) is either confirmed and implemented, or explicitly declined and documented as not implemented.
- [ ] Cascade (decision 1: partner loses user-created tasks) is documented in the delta spec.
- [ ] Manual verification matrix exists for: successful deletion, partial failure, dissolution with reset, and the residual Auth-delete-failure path.
- [ ] Re-auth dialog UX and the account-deletion info page are unchanged.
- [ ] `[UNVERIFIED]` markers remain wherever only Firebase Console evidence could confirm rules/deployment.
