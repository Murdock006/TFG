# Delta for implementation-recipes

## Change shape

Documentation-status delta. The technical debt register and the convergence roadmap are
non-requirement tables; this change alters no normative requirement. The requirements
("New code MUST remain compatible with the current stack", naming conventions, lifecycle
collection, transactional writes, source-of-truth policy, manual verification) are
unaffected.

**Choice recorded here:** because the update is not requirement-shaped, this delta records
the exact table replacements to apply at archive instead of a `## MODIFIED Requirements`
block. The archive step MUST apply the tables below to
`openspec/specs/implementation-recipes/spec.md`.

Grounding: `proposal.md` (Modified Capabilities), `exploration.md`, and
`docs/architecture/TEAMTASK_GUIDE.md:142,152,168`.

## Technical debt register (updated)

A `Status` column is added. TD-1 and TD-11 become `Resolved`; every other item stays `Open`.
Severity legend unchanged: **H**igh = blocks a future spec's invariants; **M**ed = divergence
between paths; **L**ow = cleanup.

| # | Issue | Severity | Evidence | Spec to fix it | Status |
|---|---|---|---|---|---|
| TD-1 | Two task repos with divergent rules | H | `RepositorioTareas.kt:1-31,97-113,164-179` vs `TareaRepositorioFirebase.kt:466-494` | `task-domain` convergence | Resolved (`teamtask-task-repo-consolidation`) |
| TD-2 | Avatar authority split (`tfg_prefs` + `avatar_prefs`) | H | `AvatarRepositorioLocal.kt:11,86`; `FragmentPgPrincipal.kt:376-380`; `MainActivity.kt:484-485` | `architecture-map` convergence | Open |
| TD-3 | `AvatarRepositorioFirebase` is dead code | H | `AvatarViewModel.kt:14-16` (no caller); class is `class`, not `object`/`companion` | `architecture-map` convergence | Open |
| TD-4 | Auto-login one-shot listener may fire twice on config change | M | `MainActivity.kt:255-275` | `navigation-lifecycle` | Open |
| TD-5 | `TareaRepositorioFirebase.observarTareas` mutates shared map without sync | M | `TareaRepositorioFirebase.kt:184-212` | `navigation-lifecycle` | Open |
| TD-6 | `TareasHomeAdapter` external `CoroutineScope` | M | `TareasHomeAdapter.kt:39,145-210` | `navigation-lifecycle` | Open |
| TD-7 | `resolverReclamo` does state update then points transfer outside tx | H | `TareaRepositorioFirebase.kt:362-388` | `task-domain` | Open |
| TD-8 | `modelo.Tarea.estado` is raw `String`; not all states listed in comment | M | `Tarea.kt:15`; `TareaRepositorioFirebase.kt:397-400,448`; `FragmentTareas.kt:492` | `task-domain` | Open |
| TD-9 | No `firestore.rules` / `storage.rules` / `indexes.json` in repo | H | Local artifacts now exist: `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `tools/firebase/*` (committed by `teamtask-testing-emulator-strategy` WU2); deployment, console parity, and App Check remain pending; `firebase-database-ktx` declared but no consumer | `firestore-contracts` | Partially resolved (`teamtask-testing-emulator-strategy`) |
| TD-10 | UI direct Firebase reads | M | `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` | `architecture-map` | Open |
| TD-11 | `ejecutorUid` param ignored in `RepositorioTareas.marcarCompletada` no-confirm path | M | `RepositorioTareas.kt:85` (overrides with `tareaTx.asignadoA ?: ejecutorUid`) | `task-domain` | Resolved (`teamtask-task-repo-consolidation`) |
| TD-12 | Disputa state machine has no resolver | M | `Disputa.kt:9`; `RepositorioDisputas.kt:17-34` | `task-domain` | Open |
| TD-13 | Best-effort account cleanup | M | `AuthRepositorioFirebase.kt:259-357` | `firestore-contracts` | Open |
| TD-14 | `USAR_FIREBASE` flag in `LocalizadorServicios` has no test | L | Selector removed by `teamtask-testing-emulator-strategy` WU1; `LocalizadorServicios` now requires the initialized `FirebaseComposition` | `architecture-map` | Resolved (`teamtask-testing-emulator-strategy`) |
| TD-15 | Manual navigation bundles | L | `MainActivity.kt:223`; `FragmentTareas.kt:56-72,397-398` | `navigation-lifecycle` | Open |

## Convergence roadmap (updated)

A `Status` column is added. Step 1 (published guide + specs) and step 2 (task-repo
consolidation) become `Done`; steps 3-7 stay `Pending`.

| # | Title | Depends on | Estimated impact | Status |
|---|---|---|---|---|
| 1 | Publish `architecture-map`, `task-domain`, `firestore-contracts`, `navigation-lifecycle`, `implementation-recipes` specs | — | docs only | Done |
| 2 | Unify task repos (pick `TareaRepositorioFirebase`; remove `RepositorioTareas`) | 1 | refactor `FragmentTareas`, `TareasViewModel`, `VistaModeloPrincipal` | Done (`teamtask-task-repo-consolidation`) |
| 3 | Audit and fix avatar authority (decide local vs Firebase; unify `tfg_prefs`/`avatar_prefs`; delete dead Firebase impl) | 1 | changes `AvatarViewModel`, `FragmentPerfil`, `FragmentPgPrincipal`, `MainActivity` | Pending |
| 4 | Add `firestore.rules`, `storage.rules`, `firestore.indexes.json`; commit and deploy | 1 | Local artifacts committed by `teamtask-testing-emulator-strategy` WU2 and verified against emulators; deployment, console parity, and App Check pending | Partially done |
| 5 | Add focused tests per spec: `ParejaViewModel`, `TareaRepositorioFirebase.crearTarea`/`confirmarTarea`, `RepositorioRecompensas.canjearRecompensa` | 2 | 3-5 unit tests using `firebase emulators:exec` | Pending |
| 6 | Refactor `TareaRepositorioFirebase.observarTareas` to typed `combine` and reiniciar-on-group-change | 5 | safer observers | Pending |
| 7 | Encapsulate logout + auto-login flow into dedicated controllers | 1 | smaller `MainActivity` | Pending |

## Unchanged content (preserved)

The following sections are NOT changed by this delta and MUST be preserved verbatim at
archive: the "Current State (observable)" table, all Requirements blocks, the "Future
Convergence Work" table, the "Known Risks" section, and this marker:

[UNVERIFIED] No Firebase emulator, CI, or test-orchestration infrastructure was inspected.
