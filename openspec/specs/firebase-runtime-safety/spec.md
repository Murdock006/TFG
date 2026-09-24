# firebase-runtime-safety

## Purpose and current observable state

This capability defines a fail-closed Firebase runtime boundary for the existing XML,
Fragment, ViewBinding, manual-locator architecture. Today `TFGApplication` swallows
initialization errors (`app/src/main/java/com/example/tfg/TFGApplication.kt:12-20`),
`LocalizadorServicios` selects Firebase through `USAR_FIREBASE=true`
(`.../service/LocalizadorServicios.kt:14-42`), and no `emulator` build type exists
(`app/build.gradle.kts:22-29`). Firebase clients are also constructed outside the
locator: Auth/Firestore (`.../data/firebase/AuthRepositorioFirebase.kt:20-24`),
Firestore (`.../data/firebase/TareaRepositorioFirebase.kt:14`), plus UI and Storage
callers documented in `docs/architecture/TEAMTASK_GUIDE.md:39-43`.

## Requirements

### Requirement: Build selection SHALL identify runtime mode explicitly

The build/application policy MUST distinguish `emulator` from `release`; a mutable user
preference or ambiguous default MUST NOT select the backend. The emulator build MUST use
explicit host/ports and release MUST use production identity.

#### Scenario: Emulator selects local endpoints
- GIVEN an `emulator` build on the Android Emulator
- WHEN Firebase composition starts
- THEN Auth, Firestore, and Storage MUST target the declared local endpoints before use

#### Scenario: Production data escape is rejected
- GIVEN an `emulator` build with production project/options or a release build with emulator endpoints
- WHEN composition validates identity and mode
- THEN startup MUST fail closed and perform no Firebase-backed read or write

### Requirement: Firebase configuration SHALL be centralized and early

`TFGApplication` and one composition boundary MUST initialize and configure Auth,
Firestore, and Storage before repositories, UI, or listeners can obtain clients. Failed
initialization MUST be observable and MUST block Firebase-backed composition.

#### Scenario: Initialization ordering is safe
- GIVEN the process has not used Firebase
- WHEN `Application.onCreate` completes
- THEN all phase-one clients MUST be configured before `LocalizadorServicios` can create a repository

#### Scenario: Initialization failure is visible
- GIVEN Firebase options or endpoint configuration is invalid
- WHEN application startup runs
- THEN the app MUST expose a deterministic startup/composition error, not continue silently

### Requirement: All observed Firebase callers SHALL use the configured boundary

Repositories, account cleanup, `MainActivity`, `FragmentPareja`, `TareasHomeAdapter`,
and local Auth access MUST NOT create default Firebase clients. New direct SDK construction
outside the boundary MUST be rejected by review/audit. This does not require Hilt, Room,
Retrofit, Compose, or a service-locator replacement.

#### Scenario: Direct bypass audit
- GIVEN the implementation is reviewed
- WHEN production code is searched for default Auth/Firestore/Storage construction
- THEN every match MUST be inside the configured boundary or be removed

### Verification, risks, and boundary

Automated verification MUST assert mode, project/endpoint identity, initialization order,
and the direct-client audit; emulator-backed verification is defined by
`emulator-high-risk-verification`. Manual verification MUST run both mismatch directions
and inspect logs before a write. Production rules/indexes, App Check, enabled Auth
providers, OAuth/SHA, Storage behavior, Functions, FCM, and Console state are
`[UNVERIFIED]`. High risk is a missed caller or late field initializer writing production.
Rollback removes only this capability's build/composition changes and never attempts a
Firebase Console or data rollback. Scope ends at Auth/Firestore/Storage; Realtime Database,
Analytics, Google OAuth, WorkManager, FCM, and account-deletion redesign are out of scope.
