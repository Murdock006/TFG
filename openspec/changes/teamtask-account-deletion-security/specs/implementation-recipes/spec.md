# Delta for implementation-recipes

## Change shape

Mixed delta. One requirement is ADDED — the destructive account-cleanup implementation
convention. The technical debt register, the convergence roadmap, and the Manual Verification
matrix are non-requirement tables; their updated rows are recorded as exact replacements for
archive, following the established recorded-table pattern. All existing requirements and
scenarios are unchanged and MUST be preserved byte-for-byte.

Roadmap conflict resolved here: this change implements convergence roadmap step 7 as named in the
authoritative guide (`docs/architecture/TEAMTASK_GUIDE.md` §7: "Harden account deletion and
security cleanup"), which is also what `proposal.md` cites. The `implementation-recipes`
convergence roadmap row 7 currently reads "Encapsulate logout + auto-login flow into dedicated
controllers" — a stale title that does NOT describe this change. Marking that title `Done` would
be false, so the replacement aligns row 7 with the guide and marks it `Done
(teamtask-account-deletion-security)`.

Grounding: `proposal.md` (Modified Capabilities; Resolved Decisions 1-6; Approach sections 1-4;
Success Criteria), `exploration.md`, `docs/architecture/TEAMTASK_GUIDE.md` §6-§7, and source
`data/firebase/AuthRepositorioFirebase.kt:225-263,265-374`,
`vista/FragmentPerfil.kt:208-315`, `viewmodel/VistaModeloAuth.kt:102-124`,
`viewmodel/ParejaViewModel.kt:83-105`, and `openspec/config.yaml:6`.

## ADDED Requirements

### Requirement: Destructive account cleanup MUST be verifiable, idempotent, and gated

Account-deletion cleanup MUST collect a per-step success/failure result instead of swallowing
failures, MUST retry transient failures a bounded number of times, and MUST only delete the
Firebase Auth account after every cleanup step succeeded. Cleanup operations MUST be idempotent
so a retry re-runs the whole cleanup safely. A partial cleanup is accepted as non-atomic but MUST
be surfaced; success MUST NOT be reported when a step failed. The re-authentication dialog and
the elimination info page MUST be preserved.

#### Scenario: New destructive cleanup

- GIVEN a developer adds a cleanup that deletes or updates multiple documents
- WHEN they implement it
- THEN each step MUST produce a success/failure result
- AND the overall result MUST be a failure naming the failed step(s) when any step failed
- AND the Auth account deletion MUST be gated on an all-successful cleanup

#### Scenario: Failure is not swallowed

- GIVEN a cleanup step throws an exception
- WHEN the flow completes
- THEN the caller MUST receive a failure
- AND the UI MUST NOT show a success outcome

## Non-requirement updates (apply at archive)

### Technical debt register (updated)

TD-13 becomes `Resolved (teamtask-account-deletion-security)` and its evidence is refreshed to
the current cleanup lines; every other row is unchanged. Severity legend unchanged: **H**igh =
blocks a future spec's invariants; **M**ed = divergence between paths; **L**ow = cleanup.

| # | Issue | Severity | Evidence | Spec to fix it | Status |
|---|---|---|---|---|---|
| TD-1 | Two task repos with divergent rules | H | `RepositorioTareas.kt:1-31,97-113,164-179` vs `TareaRepositorioFirebase.kt:466-494` | `task-domain` convergence | Resolved (`teamtask-task-repo-consolidation`) |
| TD-2 | Avatar authority split (`tfg_prefs` + `avatar_prefs`) | H | `AvatarRepositorioLocal.kt:11,86`; `FragmentPgPrincipal.kt:376-380`; `MainActivity.kt:484-485` | `architecture-map` convergence | Resolved (`teamtask-avatar-authority`) |
| TD-3 | `AvatarRepositorioFirebase` is dead code | H | `AvatarViewModel.kt:14-16` (no caller); class is `class`, not `object`/`companion` | `architecture-map` convergence | Resolved (`teamtask-avatar-authority`) |
| TD-4 | Auto-login one-shot listener may fire twice on config change | M | `MainActivity.kt:255-277` | `navigation-lifecycle` | Resolved (`teamtask-navigation-lifecycle-convergence`) |
| TD-5 | `TareaRepositorioFirebase.observarTareas` mutates shared map without sync | M | `TareaRepositorioFirebase.kt:163-214` (map `:186`, listeners `:188,194,202`) | `navigation-lifecycle` | Resolved (`teamtask-navigation-lifecycle-convergence`) |
| TD-6 | `TareasHomeAdapter` external `CoroutineScope` | M | `TareasHomeAdapter.kt:34-39,144,177,197` | `navigation-lifecycle` | Resolved (`teamtask-navigation-lifecycle-convergence`) |
| TD-7 | `resolverReclamo` does state update then points transfer outside tx | H | `TareaRepositorioFirebase.kt:362-388` | `task-domain` | Open |
| TD-8 | `modelo.Tarea.estado` is raw `String`; not all states listed in comment | M | `Tarea.kt:15`; `TareaRepositorioFirebase.kt:397-400,448`; `FragmentTareas.kt:492` | `task-domain` | Open |
| TD-9 | No `firestore.rules` / `storage.rules` / `indexes.json` in repo | H | Local artifacts now exist: `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `tools/firebase/*` (committed by `teamtask-testing-emulator-strategy` WU2); deployment, console parity, and App Check remain pending; `firebase-database-ktx` declared but no consumer | `firestore-contracts` | Partially resolved (`teamtask-testing-emulator-strategy`) |
| TD-10 | UI direct Firebase reads | M | `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` | `architecture-map` | Open |
| TD-11 | `ejecutorUid` param ignored in `RepositorioTareas.marcarCompletada` no-confirm path | M | `RepositorioTareas.kt:86` (overrides with `tareaTx.asignadoA ?: ejecutorUid`) | `task-domain` | Resolved (`teamtask-task-repo-consolidation`) |
| TD-12 | Disputa state machine has no resolver | M | `Disputa.kt:9`; `RepositorioDisputas.kt:17-34` | `task-domain` | Open |
| TD-13 | Best-effort account cleanup | M | `AuthRepositorioFirebase.kt:265-363`; this change makes cleanup complete, verifiable, and gated | `firestore-contracts` | Resolved (`teamtask-account-deletion-security`) |
| TD-14 | `USAR_FIREBASE` flag in `LocalizadorServicios` has no test | L | Selector removed by `teamtask-testing-emulator-strategy` WU1; `LocalizadorServicios` now requires the initialized `FirebaseComposition` | `architecture-map` | Resolved (`teamtask-testing-emulator-strategy`) |
| TD-15 | Manual navigation bundles | L | `MainActivity.kt:225`; `TareasHomeAdapter.kt:274`; `FragmentPgPrincipal.kt:78-99,312-316`; `FragmentTareas.kt:56-57,78,155,205,226,397` | `navigation-lifecycle` | Resolved (`teamtask-navigation-lifecycle-convergence`) |

### Convergence roadmap (updated)

Step 7 is aligned with the authoritative guide (`docs/architecture/TEAMTASK_GUIDE.md` §7) and
becomes `Done (teamtask-account-deletion-security)`; every other row is unchanged. The prior row 7
title ("Encapsulate logout + auto-login flow into dedicated controllers") did not describe this
change and is replaced rather than falsely marked done.

| # | Title | Depends on | Estimated impact | Status |
|---|---|---|---|---|
| 1 | Publish `architecture-map`, `task-domain`, `firestore-contracts`, `navigation-lifecycle`, `implementation-recipes` specs | — | docs only | Done |
| 2 | Unify task repos (pick `TareaRepositorioFirebase`; remove `RepositorioTareas`) | 1 | refactor `FragmentTareas`, `TareasViewModel`, `VistaModeloPrincipal` | Done (`teamtask-task-repo-consolidation`) |
| 3 | Audit and fix avatar authority (decide local vs Firebase; unify `tfg_prefs`/`avatar_prefs`; delete dead Firebase impl) | 1 | changes `AvatarViewModel`, `FragmentPerfil`, `FragmentPgPrincipal`, `MainActivity` | Done (`teamtask-avatar-authority`) |
| 4 | Add `firestore.rules`, `storage.rules`, `firestore.indexes.json`; commit and deploy | 1 | Local artifacts committed by `teamtask-testing-emulator-strategy` WU2 and verified against emulators; deployment, console parity, and App Check pending | Partially done |
| 5 | Add focused tests per spec: `ParejaViewModel`, `TareaRepositorioFirebase.crearTarea`/`confirmarTarea`, `RepositorioRecompensas.canjearRecompensa` | 2 | 3-5 unit tests using `firebase emulators:exec` | Pending |
| 6 | Refactor `TareaRepositorioFirebase.observarTareas` to typed `combine` and reiniciar-on-group-change | 5 | safer observers | Done (`teamtask-navigation-lifecycle-convergence`) |
| 7 | Harden account deletion and security cleanup | 4, 5 | Highest-blast-radius code/server work | Done (`teamtask-account-deletion-security`) |

### Manual Verification (rows added)

The new convention and the account-deletion change require manual verification. Existing rows are
unchanged; the following rows are added.

| Check | How | Expected |
|---|---|---|
| Account cleanup coverage | Delete an account with data in every collection; inspect `usuarios`, `avatares`, `grupos`, `tareas`, `invitaciones`, `notificaciones`, `recompensas`, `canjes`, `disputas`, and the dispute Storage folder | No document remains for the deleted uid; `avatares/{uid}` is gone; dispute evidence is gone |
| Dissolution reset | Two-member group; delete one account; inspect the remaining member's `usuarios` doc and device | `grupoId` is `null`; `puntos`/`puntosReservados`/`puntosRecompensa`/`rachaDias` are `0`; the remaining device clears its local `tfg_prefs` `grupoId` |
| Dissolution task deletion | Two-member group with tasks; delete one account | All tasks with that `grupoId` are deleted |
| Auth-deletion gate | Force one cleanup step to fail; run deletion | Auth account is NOT deleted; failure names the failed step; success is not shown |
| Residual Auth-delete failure | Complete cleanup, then force `FirebaseAuth.delete()` to fail | Account survives with cleaned data; the re-auth message is shown; retry re-runs the idempotent cleanup |
| Re-auth and info UX preserved | Review the dialog and the elimination info page | `ELIMINAR` + password dialog and the `EliminacionCuentaActivity` info page are unchanged |
| Strict-TDD status | Inspect `openspec/config.yaml:6` | `strict_tdd: false` remains; the manual matrix is the only verification barrier |

## Unchanged content (preserved)

The following sections are NOT changed by this delta and MUST be preserved verbatim at archive:
the "Current State (observable)" table, all Requirements blocks, the "Future Convergence Work"
table, the "Known Risks" section, and this marker:

[UNVERIFIED] No Firebase emulator, CI, or test-orchestration infrastructure was inspected.
