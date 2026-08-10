# TeamTask architecture guide

This is the **scan-friendly index** for TeamTask's current architecture. Use it to choose the
right canonical spec, then follow that spec's evidence paths before changing behavior. The guide
does not replace the five specs or claim a target architecture that the application does not yet
implement.

## 0. How to read this guide

- This change is **docs-only**.
- The worktree is dirty (**54 modified + 10 untracked** at apply time).
- **No application code is touched** by this change.
- The source-of-truth order is: **real source code > verified repository configuration > README/docs > historical Engram**.

The five linked specs are the detailed, reviewable source for responsibilities, invariants,
contracts, evidence, and uncertainty. This guide supplies orientation and short decision cues;
it intentionally does not duplicate their detailed invariant tables.

| Need | Start here |
|---|---|
| Package ownership or an exception to the intended layer flow | [Architecture map](../../openspec/specs/architecture-map/spec.md) |
| Task states, points, groups, recurrence, or disputes | [Task and group domain](../../openspec/specs/task-domain/spec.md) |
| Firestore collections, fields, queries, or Storage paths | [Firestore contracts](../../openspec/specs/firestore-contracts/spec.md) |
| Navigation, listeners, scopes, or cancellation | [Navigation and lifecycle](../../openspec/specs/navigation-lifecycle/spec.md) |
| Adding a feature within the current stack | [Implementation recipes](../../openspec/specs/implementation-recipes/spec.md) |

`[UNVERIFIED]` is an intentional uncertainty marker, not an implementation decision. Do not turn
an unverified backend assumption into a requirement without confirming it first.

## 1. Architecture map

The declared direction is `UI → ViewModel → Repository → Data Source → Model`, but the observed
application is hybrid: Fragments and adapters also reach the service locator, concrete
repositories, Firebase SDKs, and parts of the domain logic. The [architecture-map spec](../../openspec/specs/architecture-map/spec.md)
owns the complete package matrix, dependency evidence, and allowed exceptions.

Observed deviations to check before adding a new path:

- `UI → LocalizadorServicios`: `FragmentTareas.kt:370,410,452`; `FragmentTareasPendientes.kt:49-175`; `MainActivity.kt:287-302,440`.
- `UI → concrete repositorio`: `FragmentTareas.kt:47,96,381`; `FragmentRecompensas.kt:33-34`; `MainActivity.kt:302`.
- `UI → Firebase SDK`: `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74`.
- `UI → domain logic`: `FragmentTareas.kt:354-368,415-525`; `TareasHomeAdapter.kt:158-267`.
- `ViewModel → service locator`: `VistaModeloPrincipal.kt:36-42`; `TareasViewModel.kt:11`; `ParejaViewModel.kt:19-22`; `AvatarViewModel.kt:14-16`.
- `Repository → service locator`: `TareaRepositorioFirebase.kt:87,124,376-377`.

## 2. Task and group domain

The domain is observable behavior, not a promise of a sealed-state model. `Tarea.estado` is a
raw `String`, two task repositories have divergent financial rules, and group/dispute behavior
is distributed across repositories and UI. Read the [task-domain spec](../../openspec/specs/task-domain/spec.md)
before relying on a transition or points calculation.

Compact state index:

| State/transition | Evidence |
|---|---|
| Create → `pendiente` | `TareaRepositorioFirebase.kt:76-120`; `RepositorioTareas.kt:44-52` |
| `pendiente` → `pendiente_confirmacion` or `confirmada` | `TareaRepositorioFirebase.kt:390-435`; `RepositorioTareas.kt:73-129` |
| `pendiente_confirmacion`/`completada` → `confirmada` | `TareaRepositorioFirebase.kt:441-497`; `RepositorioTareas.kt:141-200` |
| `reclamada` → `confirmada` or `pendiente` | `TareaRepositorioFirebase.kt:362-388` |
| Any state → `eliminada` | `FragmentTareas.kt:492` |

Three critical checks are specified in detail by the canonical spec:

1. Creation with positive points reserves the creator's points.
2. Confirmation credits points/rewards and releases the reservation transactionally.
3. `personalizada`/`personalizado` normalizes to `PUNTOS_FIJOS_PERSONALIZADA=200` in the full implementation.

The asymmetry behind TD-1 and the non-atomic reclamo path behind TD-7 are deliberate debt signals,
not behavior to reproduce in new code.

## 3. Firestore data contracts

The [Firestore contracts spec](../../openspec/specs/firestore-contracts/spec.md) is the only index for
client-observed collections, fields, access patterns, and Storage paths. The eight Firestore
collections currently indexed are:

| Collection | Primary concern |
|---|---|
| `usuarios` | Identity, group membership, point balances, avatar metadata |
| `grupos` | Group membership, name, points, emoji, creation time |
| `invitaciones` | Invitation code, recipient, group, status, expiry |
| `tareas` | Task state, assignment, points, confirmation, recurrence |
| `recompensas` | Group rewards and costs |
| `canjes` | Reward redemptions and status |
| `disputas` | Task disputes and evidence URLs |
| `notificaciones` | Recipient, type, heterogeneous content, seen state |

The spec also covers `avatares/{uid}/...` and `disputas/{tareaId}/...` Storage paths. Claims about
rules, indexes, App Check, Crashlytics, or Cloud Functions remain `[UNVERIFIED]` where repository
or console evidence is unavailable. This guide does **not** claim that Firebase rules, indexes,
emulators, or server functions exist.

## 4. Navigation and lifecycle

The graph provides the main destinations, while `MainActivity` adds auto-login, custom back
behavior, notification intents, drawer state, and logout. The [navigation-lifecycle spec](../../openspec/specs/navigation-lifecycle/spec.md)
owns the complete graph and listener/scope evidence.

Back behavior index:

1. Close the drawer when it is open.
2. On `fragment_PgPrincipal`, require a second back within `DOUBLE_BACK_TIMEOUT_MS=2000L`.
3. From a known secondary destination, navigate explicitly to `fragment_PgPrincipal`.
4. Otherwise pop the back stack.
5. Finish the activity if the back stack cannot pop.

Listener ownership is equally important: repository `callbackFlow`s release listeners in
`awaitClose`; `ParejaViewModel` cancels its group job; `MainActivity` cancels notification work.
Prefer `viewLifecycleOwner.lifecycleScope` with `repeatOnLifecycle(STARTED)` in Fragments. TD-4
and TD-5 record lifecycle and listener risks that must be handled by follow-up changes.

## 5. Implementation recipes

Use the [implementation-recipes spec](../../openspec/specs/implementation-recipes/spec.md) as the
working checklist for new features and fixes. Preserve the current XML/Fragments/ViewBinding
stack, manual Navigation Component bundles, mixed StateFlow/LiveData reality, service locator,
Firebase data layer, SharedPreferences/local files, and WorkManager notifications.

| Artifact | Current convention |
|---|---|
| Fragment/layout | `Fragment<Name>.kt` + `fragment_<name>.xml`, with ViewBinding |
| ViewModel | `VistaModelo<Name>.kt` or `<Name>ViewModel.kt` |
| Adapter | `<Name>Adapter.kt` |
| Repository | `<Name>Repositorio.kt`; Firebase implementation under `data/firebase/` |
| Local repository | `<Name>RepositorioLocal.kt` |
| Constants | `util.Constants` |

Recipe rules: collect ViewModel flows in `viewModelScope`; expose StateFlow read-only; collect
Fragment flows with `repeatOnLifecycle`; use transactions for cross-document writes; keep new
state values compatible with current raw strings; include a manual verification matrix; and
record evidence paths for architectural claims. Do not introduce Hilt, Room, Retrofit, or Compose
in this change. Stack-purity audit command: `grep -r "androidx.compose\|dagger.hilt\|androidx.room\|retrofit2" app/`.

## 6. Technical debt register

This is a navigation aid to the 15 canonical debt items in the implementation-recipes spec;
the specs remain the detailed source of behavior and remediation constraints.

| # | Issue | Severity | Evidence | Target spec |
|---|---|---|---|---|
| TD-1 | Two task repos with divergent rules | H | `RepositorioTareas.kt:1-31,97-113,164-179`; `TareaRepositorioFirebase.kt:466-494` | task-domain |
| TD-2 | Avatar authority split | H | `AvatarRepositorioLocal.kt:11,86`; `FragmentPgPrincipal.kt:376-380`; `MainActivity.kt:484-485` | architecture-map |
| TD-3 | Firebase avatar repo is dead code | H | `AvatarViewModel.kt:14-16`; `AvatarRepositorioFirebase.kt:1-174` | architecture-map |
| TD-4 | Auto-login listener may fire twice | M | `MainActivity.kt:255-275` | navigation-lifecycle |
| TD-5 | Task observer mutates shared map without sync | M | `TareaRepositorioFirebase.kt:184-212` | navigation-lifecycle |
| TD-6 | Adapter receives external CoroutineScope | M | `TareasHomeAdapter.kt:39,145-210` | navigation-lifecycle |
| TD-7 | Reclamo state and points writes are split | H | `TareaRepositorioFirebase.kt:362-388` | task-domain |
| TD-8 | Task state is raw and incompletely declared | M | `Tarea.kt:15`; `TareaRepositorioFirebase.kt:397-400,448`; `FragmentTareas.kt:492` | task-domain |
| TD-9 | No rules/storage-rules/indexes artifacts | H | Repository absence; `firebase-database-ktx` has no consumer | firestore-contracts |
| TD-10 | UI performs direct Firebase reads | M | `MainActivity.kt:235,243,511-523`; `FragmentPareja.kt:364-371`; `TareasHomeAdapter.kt:74` | architecture-map |
| TD-11 | `ejecutorUid` is ignored in one completion path | M | `RepositorioTareas.kt:85` | task-domain |
| TD-12 | Dispute state machine has no resolver | M | `Disputa.kt:9`; `RepositorioDisputas.kt:17-34` | task-domain |
| TD-13 | Account cleanup is best effort | M | `AuthRepositorioFirebase.kt:259-357` | firestore-contracts |
| TD-14 | `USAR_FIREBASE` flag has no test | L | `LocalizadorServicios.kt:17` | architecture-map |
| TD-15 | Navigation uses manual bundles | L | `MainActivity.kt:223`; `FragmentTareas.kt:56-72,397-398` | navigation-lifecycle |

Severity: **H** blocks a future invariant, **M** is a path divergence, **L** is cleanup.

## 7. Seven-step convergence roadmap

Follow-up work is intentionally sequenced: publish facts first, then mutate the application only
through separately proposed SDD changes.

| # | Step | Depends on | Estimated impact |
|---|---|---|---|
| 1 | Publish this guide and the five canonical specs | — | Docs only; this change |
| 2 | Unify task repositories, selecting `TareaRepositorioFirebase` | 1 | Refactor task callers and behavior |
| 3 | Decide and unify avatar authority and preference namespaces | 1 | Avatar ViewModel, profile, dashboard, drawer |
| 4 | Add and verify Firestore/Storage rules, indexes, and App Check | 1 | Firebase console/config; emulator-safe write checks |
| 5 | Establish focused repository/ViewModel tests and emulator strategy | 2 | Tests only; no current coverage is claimed |
| 6 | Converge observers, navigation, logout, auto-login, and safe arguments | 5 | Lifecycle/navigation refactor |
| 7 | Harden account deletion and security cleanup | 4, 5 | Highest-blast-radius code/server work |

Steps 2-7 require their own clean or explicitly accepted baseline. None is part of this docs-only
apply phase.

## 8. Source-of-truth and verification policy

### Policy

1. Read `app/src/main/` first.
2. Confirm configuration in `app/build.gradle.kts`, `google-services.json`, and `openspec/config.yaml`.
3. Use README and docs as context, not authority.
4. Use historical Engram only to recover locations; cross-check it against the repository.

Every claim about Firestore rules, indexes, App Check, Cloud Functions, or other unavailable
backend enforcement MUST carry `[UNVERIFIED]` inline. A future change that relies on such a claim
MUST confirm the console/source state first and remove the marker in its delta spec. Audit with
`grep -r "\[UNVERIFIED\]" openspec/`.

### Manual verification matrix

| Check | Expected result |
|---|---|
| Five relative spec links | All targets exist under `openspec/specs/` |
| Sections | Headings `## 0` through `## 9` are present |
| Evidence | Guide pointers retain `file:line` paths; detailed evidence remains in specs |
| Uncertainty | `[UNVERIFIED]` policy is explicit; no unavailable Firebase capability is asserted |
| Application scope | No application-code files changed; Gradle/tests are not required or run |
| Stack | The guide preserves XML/Fragments/ViewBinding and does not prescribe Hilt/Room/Retrofit/Compose |

### Archive boundary and rollback

For this apply phase, the guide file and Engram recovery pointer are the only deliverables. The
five specs remain canonical files under `openspec/specs/`; no application code is in scope. If the
change must be rolled back: (1) delete `docs/architecture/TEAMTASK_GUIDE.md`; (2) delete or move
`openspec/changes/teamtask-architecture-guide/` to its archive; and (3) overwrite the Engram
`architecture/teamtask-guide` pointer with a `superseded` marker. No application rollback is needed.

## 9. Follow-up SDD changes

Each item below is a separate SDD change with its own proposal, specs, design, tasks, and
verification. Do not implement them while editing this guide.

| Change | Intent |
|---|---|
| `teamtask-avatar-authority` | Decide local versus Firebase avatar authority and unify namespaces. |
| `teamtask-task-repo-consolidation` | Remove divergent task-repository behavior behind one canonical path. |
| `teamtask-firestore-rules-indexes` | Confirm and commit rules, Storage rules, indexes, and App Check configuration. |
| `teamtask-testing-emulator-strategy` | Add focused tests and a Firebase emulator verification strategy. |
| `teamtask-navigation-lifecycle-convergence` | Reduce listener races and move navigation/lifecycle orchestration into safer boundaries. |
| `teamtask-account-deletion-security` | Make account cleanup and security enforcement reliable and auditable. |

### Recovery pointer

Engram topic `architecture/teamtask-guide` is only a recovery pointer. It should contain the guide
path, date, and the five spec topic keys, not a second copy of the specifications. Read the guide
and linked specs after locating them.
