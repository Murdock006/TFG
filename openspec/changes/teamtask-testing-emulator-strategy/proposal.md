# Proposal: TeamTask Firebase Emulator Safety Strategy

## Intent

Make Firebase Emulator Suite a safe, repeatable execution target for TeamTask without
changing the app's XML/Fragments/ViewBinding stack or requiring a dependency-injection
migration. The first implementation change will establish an explicit emulator build,
configure Auth/Firestore/Storage before any client use, and make both emulator-to-production
and production-to-emulator mistakes fail closed. It will then route the existing manual
service-locator paths, repositories, UI callers, and account-cleanup code through the same
configured Firebase instances so a local-looking run cannot silently write production data.

## Decision Summary

Use a dedicated `emulator` build type derived from the development configuration and keep
`release` as the production build. Endpoint selection is a build-time/application policy,
not a mutable preference or ad-hoc runtime flag. `TFGApplication` owns early Firebase
initialization; a small Firebase composition boundary owns the configured Auth, Firestore,
and Storage instances; `LocalizadorServicios` remains the manual bootstrap/composition root
for repositories. Every Firebase caller must consume those configured instances.

Automated authentication uses deterministic email/password fixtures. Google Sign-In remains a
separate manual/production-provider concern, because switching Firebase endpoints does not
make Google's OAuth flow local. Realtime Database remains out of scope until an actual runtime
consumer is verified.

## Scope

### In Scope

- Add explicit `emulator` versus `release`/production build selection, with no user-controlled
  preference deciding the backend.
- Initialize Firebase and apply emulator endpoints before any Auth, Firestore, or Storage use.
- Add fail-closed guards:
  - an emulator build must reject production endpoints or ambiguous configuration;
  - a production build must reject emulator endpoints or ambiguous configuration;
  - initialization failure must be observable and must prevent Firebase-backed composition.
- Route Auth, Firestore, and Storage through the configured composition boundary, including:
  - `LocalizadorServicios` and its lazy repositories;
  - Firebase repository constructors and direct repository classes;
  - current UI callers in `MainActivity`, `FragmentPareja`, and `TareasHomeAdapter`;
  - direct Auth access in `AvatarRepositorioLocal` and `MainActivity`;
  - Storage access used by account cleanup.
- Add only the repository/tooling artifacts required for safe local execution: `firebase.json`,
  an explicit project alias policy (`.firebaserc` if appropriate), `firestore.rules`,
  `storage.rules`, `firestore.indexes.json` where observed queries require them, and the
  deterministic seed/reset harness.
- Define an idempotent fixture contract with namespaced deterministic UIDs, email/password
  accounts, required documents for the observed collections, and reset-before/after semantics.
- Add one representative emulator-backed high-risk verification seam covering a transactional
  task flow, preferably create/reserve then confirm and its points invariants, through the
  configured repository path. The seam must prove the selected endpoints and fixture reset,
  not merely exercise an in-memory fake.
- Keep a manual matrix for two-user flows and document that emulator state does not reset
  WorkManager, local notifications, or device files.

### Out of Scope

- Realtime Database until an actual consumer is found; its declared dependency alone is not
  evidence of a phase-one runtime requirement.
- Automated Google Sign-In, OAuth, SHA registration, or provider administration. Email/password
  fixtures are the automated Auth contract; OAuth remains manual and separate.
- Analytics assertions or production Analytics redesign. The emulator build must have an
  explicit collection policy, but Analytics is not treated as emulated Firebase state.
- WorkManager, notifications, FCM, or server-trigger assertions.
- Account-deletion redesign. The first seam may expose current best-effort cleanup failures,
  but changing deletion semantics is a separate security change.
- Task repository consolidation, avatar-authority redesign, Hilt, Room, Compose, unrelated UI,
  and broad architecture migration.

## Source Of Truth And Uncertainty Policy

Claims are resolved in this order:

1. Real source under `app/src/main/`.
2. Verified repository configuration: `app/build.gradle.kts`, `google-services.json`,
   `openspec/config.yaml`, and committed Firebase tooling files after this change.
3. Architecture and contract docs under `docs/` and `openspec/specs/`.
4. Historical Engram, used only to locate evidence and then cross-checked.

The repository is authoritative for client-observed collections, fields, paths, queries,
build selection, and composition wiring. Firebase Console state is not currently available.
Every proposal, spec, test, or implementation claim about production rules, Storage rules,
indexes already deployed, App Check, enabled Auth providers, OAuth/SHA registration, buckets,
Cloud Functions, scheduled jobs, FCM, or console-only triggers MUST carry `[UNVERIFIED]` until
the owner verifies it. A future implementation must not convert `[UNVERIFIED]` into an assumed
requirement; it must either verify the Console state and record evidence or keep the behavior
fail-closed and the marker.

Local rules and indexes added for the emulator are executable test artifacts, not proof that
production has the same configuration. Deployment to production is not part of this change.

## Compatibility With Current Architecture

The change preserves the single-module Kotlin app, manual `LocalizadorServicios`, concrete
repository classes, mixed StateFlow/LiveData, XML layouts, Fragments, and ViewBinding. It does
not introduce Hilt, Room, Retrofit, or Compose. The composition boundary is an incremental
seam, not a replacement DI architecture: constructors may receive configured Firebase clients,
while the locator continues to provide the existing repository graph.

The boundary must cover both declared and effective dependency paths. A locator-only switch is
not acceptable because direct `Firebase.firestore`, `FirebaseAuth.getInstance()`, and
`FirebaseStorage.getInstance()` calls currently bypass it. The implementation should preserve
the two existing task repositories and the local avatar path; convergence of those domains is
separate work. New direct SDK construction outside the composition boundary should be prohibited
by review and a focused grep/audit check.

## Proposed Work Units

### Foundation: Build And Runtime Safety

- Add the explicit `emulator` build type and build constants/inputs needed to select endpoint
  mode and host. The Android Emulator default host is `10.0.2.2`; physical-device host access
  must be an explicit LAN/tunnel override, never an implicit fallback.
- Replace swallowed Firebase initialization errors with a deterministic startup failure or a
  composition-blocking error state.
- Configure Auth, Firestore, and Storage before repositories or UI can obtain them.
- Validate project/options/endpoint identity and fail closed in both directions.
- Keep the real `google-services.json` production configuration from being silently reused as
  emulator configuration.

### Tooling: Local Backend Contract

- Add `firebase.json` with only Auth, Firestore, and Storage emulator configuration required by
  observed clients, plus explicit import/export or reset behavior.
- Add rules and indexes based on repository-observed access and query patterns. Unknown
  production enforcement remains `[UNVERIFIED]`; local rules are not a production deployment.
- Add a deterministic seed/reset command contract. Seeds must be idempotent, namespace all
  fixture identities/data, avoid production credentials, and leave a known empty state on reset.
- Document required Node.js/Firebase CLI/Java compatibility and emulator ports only after the
  environment is confirmed; these dependencies are currently `[UNVERIFIED]` in the repository.

### Verification: One High-Risk Seam

- Add the smallest emulator-backed test/instrumentation seam that starts against the local
  emulators, resets and seeds, signs in a fixture user, executes task creation/reservation and
  confirmation, and asserts the Firestore documents and points/reservation invariants.
- Assert that the configured clients target the emulator before performing writes.
- Include a negative guard check for production build plus emulator endpoint and emulator build
  plus production endpoint. The exact test placement may be local unit/instrumentation or a
  CLI harness, but it must exercise the same composition contract as the app.
- Forecast the remaining risk areas (invitation, listeners, rewards, dispute Storage upload,
  deletion cleanup) without expanding this first seam.

If the implementation exceeds 400 changed lines, split delivery into three reviewable SDD
changes/work units: (1) foundation and composition safety, (2) Firebase tooling/rules/seed
contract, and (3) the representative emulator-backed seam. Keep tests with the code they verify.
If the foundation alone cannot safely route every direct caller, do not land tooling or tests
that could create false confidence; split by safety boundary instead of by file type.

## Likely Files And Dependencies

| Area | Likely paths | Expected change |
|---|---|---|
| Build selection | `app/build.gradle.kts`, possibly `app/src/emulator/` | `emulator` build type and explicit constants; no runtime preference switch |
| Early runtime | `app/src/main/java/com/example/tfg/TFGApplication.kt` | initialize and validate Firebase composition before use |
| Composition | `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` and a focused service/config class if needed | configured Auth/Firestore/Storage instances and fail-closed mode |
| Firebase data | `data/firebase/AuthRepositorioFirebase.kt`, `TareaRepositorioFirebase.kt`, `AvatarRepositorioFirebase.kt` | consume configured clients; remove default production-instance creation |
| Repositories | `repositorio/RepositorioPareja.kt`, `RepositorioDisputas.kt`, `RepositorioNotificaciones.kt`, `RepositorioRecompensas.kt`, `RepositorioTareas.kt` | constructor wiring through composition boundary |
| Direct callers | `vista/MainActivity.kt`, `FragmentPareja.kt`, `TareasHomeAdapter.kt`, `data/local/AvatarRepositorioLocal.kt` | route Auth/Firestore access through configured boundary |
| Backend tooling | `firebase.json`, `.firebaserc` if selected, `firestore.rules`, `storage.rules`, `firestore.indexes.json`, seed/reset scripts | local-only emulator contract |
| Verification | focused `app/src/test/` or `app/src/androidTest/` seam and/or tooling test source | one high-risk emulator-backed flow and guard assertions |

Existing Firebase Auth, Firestore, Storage, coroutines Play Services, JUnit 4, and AndroidX Test
dependencies are the starting point. Additional Firebase emulator test tooling, Firebase CLI,
Node packages, or test orchestration dependencies require explicit justification and version
verification during design. Realtime Database must not be added to the seam merely because its
SDK is already declared.

## Production Escape Risks And Mitigations

| Risk | Why it exists | Required mitigation |
|---|---|---|
| Emulator build writes production | Real `google-services.json`, default Firebase instances, or a missed caller | Build-selected mode, early endpoint configuration, identity validation, and fail-closed rejection |
| Release uses emulator | Stale host/property or shared configuration | Release-only production policy rejects emulator endpoints and local host markers |
| Late configuration | Firebase clients are field-initialized in several classes | Configure before locator/repository access and eliminate default instance construction |
| Direct UI/Auth escape | Existing UI and local repository bypass locator | Route every observed Auth/Firestore/Storage caller through the boundary and audit imports |
| False rules confidence | No committed rules/indexes and Console state is unknown | Commit local artifacts, test them locally, and mark production claims `[UNVERIFIED]` |
| Fixture pollution or leakage | Non-deterministic UIDs and incomplete reset | Namespace deterministic fixtures and make reset idempotent and mandatory |
| OAuth/Analytics leakage | Google Sign-In and Analytics are not local emulator state | Email/password automation, explicit manual OAuth separation, and emulator Analytics policy |
| Partial account cleanup | Current cleanup is best effort across Firestore/Storage/Auth | Assert/document partial failure; do not redesign deletion in this change |

## Clean-Baseline Gate

Apply is blocked until the implementation branch has an explicit clean-baseline decision against
commit `68641c1`. The current `.idea` modifications and any unrelated user changes must not be
reverted or absorbed. Before application edits, reviewers must record which changes are accepted
as baseline and which are excluded. No Gradle or test execution is required or permitted in this
proposal phase. The future apply must not claim a green build or emulator test without running
the permitted checks from the clean/accepted baseline.

## Rollback

Rollback is additive and must be possible without touching production Firebase state:

1. Remove the emulator build/configuration and composition changes, restoring the pre-change
   production composition only after reviewing the escape risk.
2. Remove local `firebase.json`, rules, indexes, seed/reset tooling, and emulator tests as one
   tooling unit if they are not retained independently.
3. Revert only this change's files; do not delete or overwrite unrelated worktree changes.
4. No Firebase Console rollback or production data rollback is implied, because deployment is out
   of scope and any Console claims remain `[UNVERIFIED]`.

## Success Criteria

- [ ] `emulator` and `release` selection is explicit and visible in the build configuration.
- [ ] Firebase initialization/configuration occurs before any Auth, Firestore, or Storage client
      work.
- [ ] Emulator-to-production and production-to-emulator endpoint mismatches fail closed.
- [ ] All observed Auth, Firestore, and Storage callers use the configured composition boundary.
- [ ] Local rules/indexes/tooling and deterministic seed/reset behavior are committed, while
      production enforcement claims retain `[UNVERIFIED]` where not confirmed.
- [ ] One emulator-backed high-risk task seam proves fixture reset, endpoint selection, and
      points/reservation invariants.
- [ ] Google Sign-In, Analytics, WorkManager/notifications, Realtime Database, and account
      deletion redesign remain explicitly bounded or separately forecast.
- [ ] The change is split into foundation/tooling/verification units when the forecast exceeds
      400 changed lines.

## Dependencies And Open Confirmations

- Firebase Console access is needed to verify production rules, indexes, Storage behavior,
  providers, App Check, OAuth/SHA registration, and any Functions/FCM/triggers; all are
  `[UNVERIFIED]` until confirmed.
- The owner must confirm Node.js, Firebase CLI, Java, emulator ports, and CI support before a
  local harness becomes a mandatory developer or CI gate; availability is `[UNVERIFIED]`.
- Physical-device testing requires a reachable host LAN/tunnel and may require a documented
  cleartext/network-security decision; `10.0.2.2` is only the Android Emulator path.
- A future design must reconcile any new constructor wiring with the existing service locator
  without introducing a second competing composition root.
