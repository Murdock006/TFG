# Delta for implementation-recipes

## Change shape

Mixed delta. One requirement is ADDED — the avatar storage and preference-namespace
convention. The technical debt register and the convergence roadmap are non-requirement
tables; their updated rows are recorded as exact replacements for archive.

**Choice recorded here:** the table updates are not requirement-shaped, so this delta records
the exact table replacements to apply at archive instead of MODIFIED blocks, following the
`teamtask-task-repo-consolidation` pattern. The archive step MUST apply the tables below to
`openspec/specs/implementation-recipes/spec.md`. All existing requirements and scenarios are
unchanged and MUST be preserved byte-for-byte.

Grounding: `proposal.md` (Modified Capabilities; Resolved Decisions 1-4; Success Criteria),
`exploration.md`, the `avatar-management` spec, and source
`data/local/AvatarRepositorioLocal.kt:11,86`, `vista/FragmentPgPrincipal.kt:376-380`,
`vista/MainActivity.kt:484-485`, `viewmodel/AvatarViewModel.kt:14-16`, and `firestore.rules`.

## ADDED Requirements

### Requirement: Binary avatar assets and preference namespaces MUST follow the canonical convention

Avatar bytes MUST be stored as compressed base64 in the dedicated Firestore collection
`avatares/{uid}` (fields `base64`, `contentType`, `updatedAt`). Binary avatar data MUST NOT be
added to Firebase Storage and MUST NOT be added to the widely-streamed `usuarios` documents.
Avatar uploads MUST be self-only; avatar reads MUST resolve any group member. Client-side
compression (downscale to roughly 256px on the long edge, JPEG) and a hard size cap below the
Firestore 1 MiB limit MUST be enforced, with oversized input rejected before any write.
SharedPreferences namespaces MUST have a single owner and a single purpose: the retired
`avatar_prefs` namespace MUST NOT be reintroduced, and `tfg_prefs` MUST be used only as a
local cache of the last-known avatar, never as the authority. New binary assets MUST follow
the same pattern rather than adding blobs to widely-streamed documents.

#### Scenario: New binary asset feature

- GIVEN a developer adds an avatar-like binary asset
- WHEN they plan the storage
- THEN the plan MUST use a dedicated Firestore collection with client-side compression and a hard size cap
- AND the plan MUST NOT add the bytes to `usuarios` or Firebase Storage

#### Scenario: Retired namespace is not reintroduced

- GIVEN the change is implemented
- WHEN production code is searched for `avatar_prefs`
- THEN there MUST be zero matches

#### Scenario: Local preferences are cache-only

- GIVEN a developer needs to persist avatar state locally
- WHEN they choose a namespace
- THEN they MUST use `tfg_prefs` as a cache only
- AND the local value MUST NOT be treated as proof that a remote avatar exists or is current

## Non-requirement updates (apply at archive)

### Technical debt register (updated)

TD-2 and TD-3 become `Resolved (teamtask-avatar-authority)`; every other row is unchanged.
Severity legend unchanged: **H**igh = blocks a future spec's invariants; **M**ed = divergence
between paths; **L**ow = cleanup.

| # | Issue | Severity | Evidence | Spec to fix it | Status |
|---|---|---|---|---|---|
| TD-1 | Two task repos with divergent rules | H | `RepositorioTareas.kt:1-31,97-113,164-179` vs `TareaRepositorioFirebase.kt:466-494` | `task-domain` convergence | Resolved (`teamtask-task-repo-consolidation`) |
| TD-2 | Avatar authority split (`tfg_prefs` + `avatar_prefs`) | H | `AvatarRepositorioLocal.kt:11,86`; `FragmentPgPrincipal.kt:376-380`; `MainActivity.kt:484-485` | `architecture-map` convergence | Resolved (`teamtask-avatar-authority`) |
| TD-3 | `AvatarRepositorioFirebase` is dead code | H | `AvatarViewModel.kt:14-16` (no caller); class is `class`, not `object`/`companion` | `architecture-map` convergence | Resolved (`teamtask-avatar-authority`) |
| TD-4 | Auto-login one-shot listener may fire twice on config change | M | `MainActivity.kt:255-275` | `navigation-lifecycle` | Open |
| TD-5 | `TareaRepositorioFirebase.observarTareas` mutates shared map without sync | M | `TareaRepositorioFirebase.kt:184-212` | `navigation-lifecycle` | Open |
| TD-6 | `TareasHomeAdapter` external `CoroutineScope` | M | `TareasHomeAdapter.kt:39,145-210` | `navigation-lifecycle` | Open |
| TD-7 | `resolverReclamo` does state update then points transfer outside tx | H | `TareaRepositorioFirebase.kt:362-388` | `task-domain` | Open |
| TD-8 | `modelo.Tarea.estado` is raw `String`; not all states listed in comment | M | `Tarea.kt:15`; `TareaRepositorioFirebase.kt:397-400,448`; `FragmentTareas.kt:492` | `task-domain` | Open |
| TD-9 | No `firestore.rules` / `storage.rules` / `indexes.json` in repo | H | Local artifacts now exist: `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `tools/firebase/*` (committed by `teamtask-testing-emulator-strategy` WU2); deployment, console parity, and App Check remain pending; `firebase-database-ktx` declared but no consumer | `firestore-contracts` | Partially resolved (`teamtask-testing-emulator-strategy`) |
| TD-10 | UI direct Firebase reads | M | `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` | `architecture-map` | Open |
| TD-11 | `ejecutorUid` param ignored in `RepositorioTareas.marcarCompletada` no-confirm path | M | `RepositorioTareas.kt:86` (overrides with `tareaTx.asignadoA ?: ejecutorUid`) | `task-domain` | Resolved (`teamtask-task-repo-consolidation`) |
| TD-12 | Disputa state machine has no resolver | M | `Disputa.kt:9`; `RepositorioDisputas.kt:17-34` | `task-domain` | Open |
| TD-13 | Best-effort account cleanup | M | `AuthRepositorioFirebase.kt:259-357` | `firestore-contracts` | Open |
| TD-14 | `USAR_FIREBASE` flag in `LocalizadorServicios` has no test | L | Selector removed by `teamtask-testing-emulator-strategy` WU1; `LocalizadorServicios` now requires the initialized `FirebaseComposition` | `architecture-map` | Resolved (`teamtask-testing-emulator-strategy`) |
| TD-15 | Manual navigation bundles | L | `MainActivity.kt:223`; `FragmentTareas.kt:56-72,397-398` | `navigation-lifecycle` | Open |

### Convergence roadmap (updated)

Step 3 (avatar authority) becomes `Done (teamtask-avatar-authority)`; every other row is
unchanged.

| # | Title | Depends on | Estimated impact | Status |
|---|---|---|---|---|
| 1 | Publish `architecture-map`, `task-domain`, `firestore-contracts`, `navigation-lifecycle`, `implementation-recipes` specs | — | docs only | Done |
| 2 | Unify task repos (pick `TareaRepositorioFirebase`; remove `RepositorioTareas`) | 1 | refactor `FragmentTareas`, `TareasViewModel`, `VistaModeloPrincipal` | Done (`teamtask-task-repo-consolidation`) |
| 3 | Audit and fix avatar authority (decide local vs Firebase; unify `tfg_prefs`/`avatar_prefs`; delete dead Firebase impl) | 1 | changes `AvatarViewModel`, `FragmentPerfil`, `FragmentPgPrincipal`, `MainActivity` | Done (`teamtask-avatar-authority`) |
| 4 | Add `firestore.rules`, `storage.rules`, `firestore.indexes.json`; commit and deploy | 1 | Local artifacts committed by `teamtask-testing-emulator-strategy` WU2 and verified against emulators; deployment, console parity, and App Check pending | Partially done |
| 5 | Add focused tests per spec: `ParejaViewModel`, `TareaRepositorioFirebase.crearTarea`/`confirmarTarea`, `RepositorioRecompensas.canjearRecompensa` | 2 | 3-5 unit tests using `firebase emulators:exec` | Pending |
| 6 | Refactor `TareaRepositorioFirebase.observarTareas` to typed `combine` and reiniciar-on-group-change | 5 | safer observers | Pending |
| 7 | Encapsulate logout + auto-login flow into dedicated controllers | 1 | smaller `MainActivity` | Pending |

### Manual Verification (rows added)

The new convention requires manual verification. Existing rows are unchanged; the following
rows are added.

| Check | How | Expected |
|---|---|---|
| Avatar storage round-trip | Upload an image in the emulator/release build; inspect `avatares/{uid}` and a group member's dashboard card | Document contains `base64`/`contentType`/`updatedAt` under the cap; member card renders the decoded avatar; `usuarios/{uid}.avatarUpdatedAt` is set |
| Avatar oversize rejection | Upload an image that encodes above the hard cap | Failure surfaced in UI; no `avatares` document written |
| Avatar cross-user write denied | Attempt to write `avatares/{other}` from a signed-in user in the emulator | Write denied |
| Avatar namespace audit | `grep -r "avatar_prefs" app/src/main/java` | Zero matches |
| Avatar offline fallback | Disable network with a cached avatar present, then with none | Cached avatar shown when present; otherwise `R.drawable.perfil` |

## Unchanged content (preserved)

The following sections are NOT changed by this delta and MUST be preserved verbatim at
archive: the "Current State (observable)" table, all Requirements blocks, the "Future
Convergence Work" table, the "Known Risks" section, and this marker:

[UNVERIFIED] No Firebase emulator, CI, or test-orchestration infrastructure was inspected.
