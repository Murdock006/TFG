## Exploration: TeamTask Firebase Emulator strategy

### Current State

**Verified in the repository**

- Firebase is initialized in `TFGApplication.onCreate()` with `FirebaseApp.initializeApp(this)`. The exception is swallowed, so initialization failure is not a production-safe gate (`app/src/main/java/com/example/tfg/TFGApplication.kt:12-20`).
- `app/google-services.json` is present and identifies the configured Firebase project and production OAuth/bucket settings. There is no separate local Firebase project configuration in the repository.
- `LocalizadorServicios` has one compile-time `USAR_FIREBASE = true` flag. Its Firebase repositories are lazy, but it does not configure emulators and it does not own all Firebase instances (`app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt:14-42`).
- Auth and Firestore are used by `AuthRepositorioFirebase`, including email/password, verification email, Google credential sign-in, listeners, transactions, and account deletion. Auth/Firestore instances are obtained through `Firebase.auth` and `Firebase.firestore` (`data/firebase/AuthRepositorioFirebase.kt:20-23,30-217,219-368,386-430`).
- Firestore is created directly with default instances in `TareaRepositorioFirebase`, `RepositorioPareja`, `RepositorioDisputas`, `RepositorioNotificaciones`, `RepositorioRecompensas`, and the older `RepositorioTareas`. UI also accesses `Firebase.firestore` directly in `FragmentPareja` and `TareasHomeAdapter`.
- Storage is used by `RepositorioDisputas` and `AvatarRepositorioFirebase`; account cleanup also calls `FirebaseStorage.getInstance()` directly to delete dispute evidence. These paths must receive the same emulator-configured instance or they can escape to production.
- Firebase Auth is also read directly by `AvatarRepositorioLocal` and `MainActivity`. This is not only a repository concern.
- Realtime Database is declared as `firebase-database-ktx` and a database URL is present in `google-services.json`, but no `FirebaseDatabase`, `Firebase.database`, or database reference use was found. Realtime Database is therefore declared but not an observed runtime dependency.
- The only application build configuration has implicit `debug` and explicit `release`; there are no product flavors, emulator build type, build fields, host properties, or emulator config (`app/build.gradle.kts:7-42`).
- Tests are only template tests: one JUnit addition test and one AndroidX package-name test. There are no Firebase emulator tests, Firebase test dependencies, test fixtures, or integration orchestration (`app/src/test/.../ExampleUnitTest.kt`, `app/src/androidTest/.../ExampleInstrumentedTest.kt`).
- The Gradle wrapper exists and points to Gradle `9.5.0`; the app uses Firebase BoM `32.7.0`, Google Services plugin `4.4.4`, and Firebase Auth/Firestore/Storage/Realtime Database/Analytics dependencies. No Node manifest, `.firebaserc`, `firebase.json`, Firestore rules, Storage rules, indexes, Functions, or emulator files were found.
- `WorkManager` and local notifications are used for reminders. There is no Firebase Cloud Messaging implementation in the inspected source. Google Sign-In is configured in `FragmentLogin` and uses the production OAuth client resource.

**Implications verified against SDK documentation**

- Auth, Firestore, Storage, and Realtime Database clients support emulator endpoints. The Android Emulator reaches services on the host through `10.0.2.2`; a physical device must use a reachable host LAN address or another explicit tunnel/network route. The SDK documentation confirms Realtime Database's URL form and the `10.0.2.2` implication.
- Emulator configuration must happen before the relevant client performs work. Because TeamTask constructs clients in field initializers, direct construction makes ordering and coverage of a global emulator switch fragile.

**Not verified and requiring Firebase-console/user confirmation**

- Whether production Firestore/Storage/Auth rules, indexes, App Check, enabled providers, OAuth SHA registrations, and Storage bucket behavior match the repository assumptions.
- Whether any Cloud Functions, scheduled jobs, FCM server integration, or console-only triggers exist. No repository evidence for them was found.
- Whether the current production data model can be seeded without additional required fields or index deployments.

### Affected Areas

- `app/src/main/java/com/example/tfg/TFGApplication.kt` — safest process-wide point to initialize Firebase and configure local endpoints before repositories are touched.
- `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` — current Firebase/in-memory switch; should become an explicit build-selected composition root with a production fail-closed guard.
- `app/build.gradle.kts` — currently has no emulator variant. A dedicated `emulator` build type or flavor should make accidental production use visibly and mechanically different from release.
- `app/google-services.json` — currently the only Firebase options source and points at the configured real project; local mode must not rely on silently changing this file.
- `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt` — Auth/Firestore/Storage creation, Google credential login, listeners, and high-blast-radius deletion path.
- `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` — Firestore queries, listeners, transactions, points invariants, notifications, and recursive task creation.
- `app/src/main/java/com/example/tfg/data/firebase/AvatarRepositorioFirebase.kt` — Storage plus Firestore avatar flow.
- `app/src/main/java/com/example/tfg/repositorio/RepositorioPareja.kt`, `RepositorioDisputas.kt`, `RepositorioNotificaciones.kt`, `RepositorioRecompensas.kt`, and `RepositorioTareas.kt` — direct Firestore/Storage default construction and likely first repository integration-test targets.
- `app/src/main/java/com/example/tfg/vista/MainActivity.kt`, `FragmentPareja.kt`, `FragmentLogin.kt`, and `TareasHomeAdapter.kt` — direct SDK use and Google Sign-In/UI paths that bypass the locator.
- `app/src/main/java/com/example/tfg/service/NotificationScheduler.kt` and `NotificationWorker.kt` — local WorkManager behavior to isolate from backend tests; emulator mode does not emulate Android notification delivery or server push.
- `openspec/config.yaml`, `gradle/wrapper/gradle-wrapper.properties`, `app/src/test/`, and `app/src/androidTest/` — test capability, runner, and dependency constraints.
- New repository-only tooling likely needed: `firebase.json`, `.firebaserc` (or an explicitly documented project alias), `firestore.rules`, `storage.rules`, `firestore.indexes.json`, deterministic seed/reset scripts, and rules/integration test sources. None currently exists.

### Approaches

1. **Dedicated emulator build type with centralized Firebase composition** — add an explicit `emulator` build type (or equivalent source-set configuration), configure all supported Firebase clients in `Application` before use, and inject the configured instances into repositories while making production the default only for release.
   - Pros: strong operator signal, no runtime toggle, fail-closed boundary, works with the existing single-module/manual locator, supports Android instrumentation and manual emulator runs.
   - Cons: requires touching many direct SDK construction sites and resolving the current mixed architecture; Google Sign-In still needs a separate decision.
   - Effort: Medium/High.

2. **Runtime host/flag switch inside the existing locator** — retain the current build graph and select emulator endpoints from a preference/system property.
   - Pros: smaller initial Gradle change and easy local experimentation.
   - Cons: unsafe because `USAR_FIREBASE` is already hard-coded true, preferences can be stale or user-controlled, direct SDK users bypass the switch, and a late configuration can leave clients connected to production.
   - Effort: Medium, but unacceptable as the safety boundary.

3. **Separate Firebase project configuration for a local/staging Android app** — use a second `google-services.json`/application identity and optionally point that project at non-production data, with emulator use layered on top.
   - Pros: useful for manual shared-device testing and console validation; reduces production-data exposure if emulator is unavailable.
   - Cons: still incurs network/cost/rules risk, does not replace local emulators, and requires Firebase-console credentials, OAuth registration, and project administration.
   - Effort: High; complementary rather than the first phase.

### Recommendation

Proceed with Approach 1 in phases:

1. **Safety foundation:** introduce an explicit `emulator` build type and a centralized Firebase runtime configuration. Configure Auth, Firestore, and Storage before any repository/UI access; use `10.0.2.2` for the Android Emulator and an explicit LAN host override for physical devices. Production/release must reject emulator endpoints, while emulator builds must reject production endpoints or fail closed when configuration is ambiguous.
2. **Composition convergence:** make every Firebase repository and direct caller use the configured app/service instances. Preserve the existing XML/Fragments/ViewBinding and manual locator; do not introduce Hilt, Room, Retrofit, or Compose. Keep Realtime Database out of phase 1 because no use was verified, but document the declared dependency and add it only after an actual consumer is confirmed.
3. **Local backend artifacts:** add `firebase.json`, Firestore/Storage rules, indexes, and a documented Firebase CLI/Node test harness. Use deterministic Auth users and minimal fixture documents across `usuarios`, `grupos`, `invitaciones`, `tareas`, `recompensas`, `canjes`, `disputas`, and `notificaciones`; reset between tests or emulator sessions.
4. **Verification layers:** use rules tests for allowed/denied access independently, emulator-backed repository/instrumentation tests for transactions/listeners/Storage, and a small manual matrix for two-user flows. First targets should be task reservation/confirmation, group invitation, reward redemption, dispute upload, listeners, and account deletion cleanup.
5. **Explicit exclusions/compatibility:** use email/password emulator accounts for automated Auth tests. Treat Google Sign-In as a separate manual or production-provider test because the external Google OAuth flow is not made safe merely by switching Firestore/Auth endpoints. Disable or clearly tag Analytics in emulator builds; keep WorkManager/notifications as local Android tests, not Firebase-emulator assertions.

Seed/reset must be idempotent and namespaced with deterministic test UIDs. Account deletion tests must assert both Auth removal and cleanup of every affected Firestore/Storage artifact, and must expose the current best-effort cleanup behavior rather than silently treating partial cleanup as success.

### Risks

- **Production data escape (high):** `google-services.json` is real-project configuration, `USAR_FIREBASE` defaults true, and direct SDK construction bypasses the locator. A single missed instance can write production data while the UI appears local.
- **Initialization ordering (high):** `FirebaseApp` initialization currently swallows errors and repositories initialize lazily or eagerly in different places. Emulator endpoint configuration after first use may not cover all clients.
- **Rules uncertainty (high):** no rules/indexes artifacts are present, so emulator rule tests cannot yet prove production enforcement; console state is `[UNVERIFIED]`.
- **Data-model/index drift (medium/high):** queries use compound filters and ordered histories; a local seed may pass or fail differently until indexes and required fields are committed and verified.
- **Auth/Google Sign-In mismatch (medium):** automated emulator Auth should use email/password fixtures; Google OAuth, SHA certificates, and provider enablement require console confirmation and a separate path.
- **Analytics leakage (medium):** Analytics is declared and does not become local just because Auth/Firestore/Storage are local. Emulator builds need explicit collection suppression or test-project measurement policy.
- **Storage URL/cleanup behavior (medium/high):** account deletion derives a Storage reference from stored download URLs and suppresses individual cleanup errors. Emulator URL formats and partial failures need dedicated assertions.
- **WorkManager/notifications (medium):** scheduled local work can survive test state and notifications are device/system behavior; it is not reset by Firebase emulator resets. FCM/server delivery was not verified.
- **Account deletion blast radius (high):** current cleanup is best effort, spans many collections and Storage, then deletes Auth. Tests must distinguish complete cleanup from an Auth-only success.
- **Physical-device networking (medium):** `10.0.2.2` is for the Android Emulator, not a physical device. A LAN address, firewall rule, cleartext/network-security decision, and same-network setup will be needed.
- **Tooling availability (medium):** no Node/Firebase CLI indicators are committed. The team must confirm Node.js, Firebase CLI, Java compatibility, emulator ports, and CI support before making the harness mandatory.

### Ready for Proposal

Yes, with explicit scope and open confirmations. The proposal should require a dedicated emulator build selection, centralized early configuration, fail-closed production protection, Auth/Firestore/Storage as phase-one services, committed rules/indexes/seed/reset harness, and separate treatment of Google Sign-In, Analytics, WorkManager/notifications, Realtime Database, and account deletion. It should mark Firebase Console capabilities and CLI/CI availability as `[UNVERIFIED]` until the owner confirms them.

**Status:** success

**Executive summary:** TeamTask has Firebase production configuration and broad direct SDK usage but no emulator infrastructure, variant, rules, seed data, or meaningful tests. A dedicated emulator build type with centralized, early, fail-closed configuration is the only safe foundation; Auth/Firestore/Storage should be first scope, with Realtime Database excluded until actual use is confirmed.

**Artifacts:** Engram `sdd/teamtask-testing-emulator-strategy/explore` | `openspec/changes/teamtask-testing-emulator-strategy/exploration.md`

**Next recommended:** `sdd-propose`

**Risks:** High production-escape risk, unverified Firebase Console enforcement, direct SDK bypasses, account-deletion partial cleanup, and unresolved Google Sign-In/Analytics/device-network behavior.

**Skill resolution:** injected Project Standards plus `sdd-explore`; hybrid artifact store.
