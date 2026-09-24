# Design: TeamTask Firebase Emulator Safety Strategy

## Technical Approach

Choose a dedicated `emulator` build type derived from the development configuration. It
declares `BuildConfig.FIREBASE_MODE` and `BuildConfig.FIREBASE_HOST`; `release` is always
production. This is safer than a runtime flag (mutable and stale-prone) and safer than a
second Firebase project for phase one (still networked, credentialed, and dependent on
unverified console setup). The existing XML/Fragments/ViewBinding and manual
`LocalizadorServicios` remain; no Hilt, Room, Retrofit, or Compose.

`TFGApplication.onCreate` initializes one `FirebaseComposition` synchronously before the
locator or any repository can obtain a client. The composition validates mode, Firebase
options, project identity, and endpoint markers, then configures Auth, Firestore, and Storage
with `useEmulator(host, 9099/8080/9199)` when appropriate. Failure is observable and blocks
Firebase-backed composition. `10.0.2.2` is the Android Emulator default; physical-device
LAN/tunnel access is explicitly excluded from the automated path.

## Architecture Decisions

| Decision | Alternatives rejected | Rationale |
|---|---|---|
| Build-selected mode | Runtime preference; separate Firebase project | Mechanical, reviewable, fail-closed, and local without production credentials |
| `FirebaseComposition` before locator | Locator-only switch; broad DI migration | Covers direct callers while preserving the current composition root |
| Configured clients via accessors/constructors | New default SDK instances | Prevents late or missed emulator routing; enables a grep guard |

## Data Flow

```text
emulator/release BuildConfig -> TFGApplication -> FirebaseComposition
                                -> LocalizadorServicios -> existing repositories/UI
```

## Interfaces / Contracts

```kotlin
enum class FirebaseMode { EMULATOR, RELEASE }

data class CompositionContext(
    val mode: FirebaseMode, val host: String,
    val projectId: String, val applicationId: String
)

object FirebaseComposition {
    fun init(application: Application, mode: FirebaseMode, host: String)
    fun requireContext(): CompositionContext
    fun auth(): FirebaseAuth
    fun firestore(): FirebaseFirestore
    fun storage(): FirebaseStorage
    fun assertEmulator(); fun assertRelease()
}
```

All observed `Firebase.*`, `FirebaseAuth/Firestore/Storage.getInstance()` calls move inside
this boundary or consume its clients. The apply/verify guard is a zero-match `rg` audit under
`app/src/main`, excluding `FirebaseComposition.kt`. In-memory repositories remain available
only where the existing locator contract requires them; `USAR_FIREBASE` must not remain a
mutable backend selector.

## File-Level Changes

| Path | Action |
|---|---|
| `app/build.gradle.kts` | Add `emulator` build type, explicit fields, BuildConfig support |
| `TFGApplication.kt`, `service/firebase/FirebaseComposition.kt` | Early initialization, identity validation, fail-closed accessors |
| `service/LocalizadorServicios.kt` | Require initialized composition; wire configured clients |
| `data/firebase/*`, `repositorio/Repositorio{Pareja,Disputas,Notificaciones,Recompensas,Tareas}.kt` | Replace default SDK construction and pass Auth/Firestore/Storage clients |
| `vista/MainActivity.kt`, `FragmentPareja.kt`, `TareasHomeAdapter.kt`, `data/local/AvatarRepositorioLocal.kt` | Remove direct Firebase client access |
| `firebase.json`, `.firebaserc`, `firestore.rules`, `storage.rules`, `firestore.indexes.json` | Auth/Firestore/Storage-only local contract; production parity remains `[UNVERIFIED]` |
| `tools/firebase/*`, `app/src/androidTest/.../EmulatorTaskTransactionSeamTest.kt` | Deterministic reset/seed and one emulator-backed seam |

## Sequenced Work Units

1. **Foundation/composition safety.** Implement build identity, composition, early fail-closed
   startup, and every direct-caller route. Dependency: accepted clean-baseline decision against
   `68641c1`. Gate: audit is clean and both mismatch directions fail before a Firebase operation.
2. **Tooling/fixtures.** Add fixed emulator ports, local rules/indexes, prerequisites with
   `[UNVERIFIED]` until confirmed, and idempotent `reset -> seed`; deterministic namespaced UIDs,
   emails, and documents are mandatory. Dependency: WU1. Gate: only declared services start,
   unauthorized writes deny, and `seed; seed` converges.
3. **Verification seam.** Reset/seed two email-password users, assert emulator identity before
   writes, create a 100-point confirmable task, complete, confirm, and assert task state,
   `puntos`, `puntosReservados`, and `puntosRecompensa`. Insufficient points leave no task or
   balance mutation. Cleanup runs on success/failure and incomplete cleanup fails. Dependency:
   WU1 + WU2.

## Boundaries, Rollback, and Risk

Google Sign-In/OAuth, Analytics collection assertions, Realtime Database, WorkManager,
notifications/FCM, account-deletion redesign, physical-device networking, Functions, App Check,
and production rules/indexes are deferred or `[UNVERIFIED]`; manual checks document them rather
than implying emulator coverage. Roll back WU3, WU2, then WU1 by files only; never alter Console
state or production data. Forecast: WU1 220-320 lines, WU2 150-250, WU3 200-350; overall risk is
high until WU1's direct-client audit passes, so keep units reviewable and chained if needed.

## Verification

Unit-check mode/identity validation and fail-closed initialization; audit all direct SDK
construction; run emulator rules, ports, reset/seed isolation, and the WU3 transaction seam;
manually exercise both mismatch directions and the two-user matrix. Do not claim a clean build,
emulator result, or production parity until the permitted checks run from the accepted baseline.
