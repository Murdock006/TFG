# Tasks: TeamTask Firebase Emulator Safety Strategy

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 570–920 (WU1 220–320, WU2 150–250, WU3 200–350) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 (one per work unit) |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Foundation/composition safety: build identity, FirebaseComposition, fail-closed init, direct-caller audit | PR 1 | Base branch `68641c1`; depends on clean-baseline gate |
| 2 | Tooling/fixtures: firebase.json, rules, indexes, reset/seed, CLI prerequisites | PR 2 | Depends on PR 1; no production deployment |
| 3 | Verification seam: emulator-backed email/password users, task transaction invariants, cleanup | PR 3 | Depends on PR 1 + PR 2 |

## Global Gates

- Clean baseline: start from commit `68641c1`; do not absorb unrelated `.idea/` edits.
- No production deployment: never alter Firebase Console, production data, or deployed rules/indexes.
- Exclusions: Google Sign-In automation, Analytics assertions, Realtime Database, WorkManager/notifications, FCM, account-deletion redesign, physical-device networking, Functions, App Check.
- Rollback boundary: per work unit, files only; never Console or production data.
- Preservation: XML/Fragments/ViewBinding and manual `LocalizadorServicios` remain; no Hilt/Room/Retrofit/Compose assumptions.

## Phase 1: Foundation / Composition Safety

- [x] 1.1 Add `emulator` build type to `app/build.gradle.kts` with `BuildConfig.FIREBASE_MODE` and `BuildConfig.FIREBASE_HOST`; `release` remains production. Verify: `./gradlew :app:assembleEmulatorDebug` succeeds and BuildConfig fields exist. Rollback: revert `app/build.gradle.kts`. Parallel: no.
- [x] 1.2 Create `app/src/main/java/com/example/tfg/service/firebase/FirebaseComposition.kt` with `FirebaseMode` enum, `CompositionContext`, fail-closed `init()`, `requireContext()`, `auth()`, `firestore()`, `storage()`, `assertEmulator()`, `assertRelease()`. Verify: unit test for mode/identity mismatch fails before any Firebase operation. Rollback: delete file. Parallel: depends on 1.1.
- [x] 1.3 Update `app/src/main/java/com/example/tfg/TFGApplication.kt` to call `FirebaseComposition.init(this, BuildConfig.FIREBASE_MODE, BuildConfig.FIREBASE_HOST)` synchronously before any repository or locator access. Verify: app starts with emulator build targeting local endpoints; release build targets production. Rollback: revert `TFGApplication.kt`. Parallel: depends on 1.2.
- [x] 1.4 Update `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` to require initialized `FirebaseComposition` and wire configured Auth/Firestore/Storage clients to repositories; remove `USAR_FIREBASE` mutable selector. Verify: locator returns Firebase-backed repositories only after composition init. Rollback: revert `LocalizadorServicios.kt`. Parallel: depends on 1.3.
- [x] 1.5 Replace default SDK construction in `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt`, `TareaRepositorioFirebase.kt`, `AvatarRepositorioFirebase.kt` to consume `FirebaseComposition` clients. Verify: `rg "Firebase\.(auth|firestore|storage)|FirebaseAuth\.getInstance|FirebaseFirestore\.getInstance|FirebaseStorage\.getInstance" app/src/main` returns zero matches outside `FirebaseComposition.kt`. Rollback: revert these three files. Parallel: depends on 1.4.
- [x] 1.6 Replace default SDK construction in `app/src/main/java/com/example/tfg/repositorio/RepositorioPareja.kt`, `RepositorioDisputas.kt`, `RepositorioNotificaciones.kt`, `RepositorioRecompensas.kt`, `RepositorioTareas.kt` to consume `FirebaseComposition` clients. Verify: same `rg` audit returns zero matches. Rollback: revert these five files. Parallel: depends on 1.4; may run in parallel with 1.5.
- [x] 1.7 Remove direct Firebase client access in `app/src/main/java/com/example/tfg/vista/MainActivity.kt`, `FragmentPareja.kt`, `TareasHomeAdapter.kt`, `data/local/AvatarRepositorioLocal.kt`. Verify: same `rg` audit returns zero matches. Rollback: revert these four files. Parallel: depends on 1.4; may run in parallel with 1.5 and 1.6.
- [x] 1.8 Run full direct-client audit: `rg "Firebase\.(auth|firestore|storage)|FirebaseAuth\.getInstance|FirebaseFirestore\.getInstance|FirebaseStorage\.getInstance" app/src/main --glob '!**/FirebaseComposition.kt'` returns zero matches. Verify: both mismatch directions (emulator build + production endpoint, release build + emulator endpoint) fail closed before any Firebase operation. Rollback: revert all Phase 1 files. Parallel: depends on 1.5, 1.6, 1.7.

## Phase 2: Tooling / Fixtures

- [x] 2.1 Create `firebase.json` declaring Auth (port 9099), Firestore (port 8080), Storage (port 9199) emulators with rules/index file references. Verify: `firebase emulators:start --only auth,firestore,storage` starts only declared services on documented ports. Rollback: delete `firebase.json`. Parallel: no.
- [x] 2.2 Create `.firebaserc` with local project alias policy; document that local tooling MUST NOT reuse production credentials. Verify: `firebase use` shows local alias, not production project ID. Rollback: delete `.firebaserc`. Parallel: may run in parallel with 2.1.
- [x] 2.3 Create `firestore.rules` covering observed collections and query patterns from `openspec/specs/firestore-contracts/spec.md:32-41,79-84`. Verify: emulator rejects unauthorized writes; test fixture user outside rule contract is denied. Rollback: delete `firestore.rules`. Parallel: depends on 2.1.
- [x] 2.4 Create `storage.rules` covering observed Storage paths. Verify: emulator rejects unauthorized uploads/deletes. Rollback: delete `storage.rules`. Parallel: depends on 2.1; may run in parallel with 2.3.
- [x] 2.5 Create `firestore.indexes.json` covering observed composite queries. Verify: emulator loads indexes without error; queries succeed. Rollback: delete `firestore.indexes.json`. Parallel: depends on 2.1; may run in parallel with 2.3 and 2.4.
- [x] 2.6 Create `tools/firebase/reset.sh` and `tools/firebase/seed.sh` with deterministic namespaced UIDs, emails, and documents; `seed` must be idempotent (`seed; seed` converges). Verify: reset leaves empty state; seed creates fixture users/documents; second seed produces identical state. Rollback: delete `tools/firebase/`. Parallel: depends on 2.1, 2.3, 2.4, 2.5.
- [x] 2.7 Document CLI prerequisites (Node.js, Firebase CLI, Java) and mark production rules/indexes parity as `[UNVERIFIED]` until confirmed. Verify: missing prerequisite fails with actionable error; documentation lists `[UNVERIFIED]` items. Rollback: revert documentation. Parallel: depends on 2.6.

## Phase 3: Verification Seam

- [ ] 3.1 Create `app/src/androidTest/java/com/example/tfg/EmulatorTaskTransactionSeamTest.kt` with endpoint identity assertion before any write; fail on mode/endpoint mismatch. Verify: test fails immediately when emulator build targets production endpoint or release build targets emulator endpoint. Rollback: delete test file. Parallel: no.
- [ ] 3.2 Implement fixture reset/seed in the test: reset emulator state, seed two deterministic email/password users (creator/executor) with required `usuarios` documents, verify baseline before flow. Verify: each run observes same baseline users/documents; no prior task residue. Rollback: revert test file. Parallel: depends on 3.1.
- [ ] 3.3 Implement two-user happy path: creator (1000/0 puntos) creates 100-point confirmable task, executor completes, creator confirms; assert task state transitions (`pendiente_confirmacion` → `confirmada`), creator `puntos` decreases by 100, `puntosReservados` increases then releases, executor `puntos`/`puntosRecompensa` credit. Verify: all invariants match current transaction contract in `TareaRepositorioFirebase.kt:441-497`. Rollback: revert test file. Parallel: depends on 3.2.
- [ ] 3.4 Implement insufficient-points negative path: creator with fewer points than task points attempts create; assert no task document or balance mutation. Verify: create fails; creator `puntos` and `puntosReservados` unchanged; no task document exists. Rollback: revert test file. Parallel: depends on 3.2; may run in parallel with 3.3.
- [ ] 3.5 Implement cleanup: run on success or failure; incomplete cleanup fails the test rather than silently passing. Verify: cleanup runs after 3.3 and 3.4; emulator state returns to known empty; failed cleanup marks test as failed. Rollback: revert test file. Parallel: depends on 3.3, 3.4.
- [ ] 3.6 Run full verification: execute seam test against emulator; manually exercise two-user matrix (create with/without confirmation, insufficient points, executor completion, creator confirmation, retry after reset, both mode mismatches). Verify: all scenarios pass; manual matrix documented. Rollback: revert all Phase 3 files. Parallel: depends on 3.5.
