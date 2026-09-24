# Apply Progress: TeamTask Avatar Authority Convergence

Change: `teamtask-avatar-authority` · Repo: `C:\Users\Victor\AndroidStudioProjects\TFG2` · Branch: `master`
Mode: **Standard** (`strict_tdd: false`, `openspec/config.yaml:6`) · Store: hybrid (file + Engram)
Apply state at start: `ready` · Batch: fresh (no previous apply-progress)

## Status

**21 / 22 tasks complete.** Task 4.4 (manual verification matrix) is **NOT EXECUTED** — it requires
a device/emulator build and a live Firebase emulator, which is explicitly outside this run's
execution scope. All code, compile, JVM-test, grep-audit, and scope gates passed.

## Completed Tasks

| Task | Description | Status |
|---|---|---|
| 1.1 | `repositorio/AvatarRepositorio.kt` contract (2 ops, no delete) | Done |
| 1.2 | `util/AvatarImagen.kt` helper object (two-pass decode, `java.util.Base64`) | Done |
| 1.3 | `app/src/test/java/com/example/tfg/util/AvatarImagenTest.kt` (focused JVM test) | Done |
| 1.4 | WU1 gate (compile + JVM test) | Done |
| 2.1 | `Usuario.avatarUpdatedAt: Timestamp? = null`; `avatarUrl` marked legacy | Done |
| 2.2 | `avatarUpdatedAt` surfaced at the login / provider / `observarUsuarios` mappers | Done |
| 2.3 | `AvatarRepositorioLocal` last-known cache API (`guardarUltimoConocido`/`obtenerUltimoConocido`/`limpiar`) | Done |
| 2.4 | `AvatarRepositorioFirebase` rewritten as the Firestore base64 impl | Done |
| 2.5 | `LocalizadorServicios.repositorioAvatar` wired via `TFGApplication.appContext` | Done |
| 2.6 | `AvatarViewModel` on `AvatarRepositorio` + `AuthRepositorio` through the locator | Done |
| 2.7 | `FragmentPerfil` collects `avatarActual: Bitmap?` | Done |
| 2.8 | `AvatarRepositorioLocal` authority methods + dead imports removed | Done |
| 2.9 | `firestore.rules` `avatares/{uid}` block (signed-in read, self-only write) | Done |
| 2.10 | WU2 gate (compile + six grep audits) | Done |
| 3.1 | `MainActivity` drawer header routed through the repository (`avatarDrawerJob` guard) | Done |
| 3.2 | `FragmentPgPrincipal` member cards routed through the repository (`tag` recycling guard) | Done |
| 3.3 | WU3 gate (compile + `avatar_prefs` audit) | Done |
| 4.1 | Compile gate | Done |
| 4.2 | Focused JVM test gate | Done |
| 4.3 | Grep audits | Done |
| 4.4 | Manual verification matrix | **NOT EXECUTED — pending manual verification** |
| 4.5 | Scope gate | Done |

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/src/main/java/com/example/tfg/repositorio/AvatarRepositorio.kt` | Created | Canonical contract: `subirAvatar(Uri): Result<Timestamp>`, `obtenerAvatar(uid, Timestamp?): Bitmap?` |
| `app/src/main/java/com/example/tfg/util/AvatarImagen.kt` | Created | `object` with constants + `comprimirJpeg`/`codificarBase64`/`decodificarBase64`/`decodificarJpeg` |
| `app/src/test/java/com/example/tfg/util/AvatarImagenTest.kt` | Created | 3 focused JUnit 4 tests (base64 round-trip incl. `0x00`, malformed → null, size-cap gate) |
| `app/src/main/java/com/example/tfg/data/firebase/AvatarRepositorioFirebase.kt` | Modified (full rewrite) | Firestore base64 impl; `WriteBatch` upload; hint-keyed `LruCache(32)`; last-known fallback |
| `app/src/main/java/com/example/tfg/data/local/AvatarRepositorioLocal.kt` | Modified | Demoted to cache-only (`guardarUltimoConocido`/`obtenerUltimoConocido`/`limpiar`); authority methods + dead imports removed |
| `app/src/main/java/com/example/tfg/viewmodel/AvatarViewModel.kt` | Modified | Locator-resolved `AvatarRepositorio`/`AuthRepositorio`; `avatarUrlActual: String?` → `avatarActual: Bitmap?`; `avatarState` → `Result<Unit>?` |
| `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` | Modified | Added `repositorioAvatar` (`by lazy`, `FirebaseComposition` + `TFGApplication.appContext`) |
| `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt` | Modified | `avatarUpdatedAt` populated at 3 mapper sites (`usuarioActual()` cache preserves it) |
| `app/src/main/java/com/example/tfg/modelo/Usuario.kt` | Modified | Added `avatarUpdatedAt`; `avatarUrl` marked legacy |
| `app/src/main/java/com/example/tfg/vista/MainActivity.kt` | Modified | Drawer header resolves via the repository; `avatarDrawerJob` cancel-guard | 
| `app/src/main/java/com/example/tfg/vista/FragmentPgPrincipal.kt` | Modified | Member cards resolve per member with `tag` recycling guard; `avatar_prefs` read + `java.io.File` import removed |
| `app/src/main/java/com/example/tfg/vista/FragmentPerfil.kt` | Modified | Collects `avatarActual` (`Bitmap?`) with Glide-on-Bitmap + placeholder |
| `firestore.rules` | Modified | Added `avatares/{uid}` block (signed-in read, self-only write) + header comment |
| `openspec/changes/teamtask-avatar-authority/tasks.md` | Modified | 21 task checkboxes marked `[x]` |
| `openspec/changes/teamtask-avatar-authority/apply-progress.md` | Created | This file |

## Gates — observed results

| Gate | Command | Observed result |
|---|---|---|
| Compile (Gate 1 / 1.4 / 2.10 / 3.3 / 4.1) | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` | `BUILD SUCCESSFUL in 29s` (exit 0); only pre-existing deprecation warnings in `AuthRepositorioInMemory.kt`/`FragmentRegistro.kt` |
| JVM test (Gate 2 / 1.4 / 4.2) | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` | `BUILD SUCCESSFUL in 29s` (exit 0); `AvatarImagenTest` tests=3 failures=0 errors=0; `ExampleUnitTest` tests=1 failures=0 |
| Grep audit 1 — `avatar_prefs` retired | `Get-ChildItem -Recurse app\src\main -File \| Select-String "avatar_prefs" -CaseSensitive` | ZERO MATCHES |
| Grep audit 2 — no Storage on the avatar path | `... app\src\main\java\com\example\tfg\data\firebase \| Select-String "FirebaseStorage\|putBytes\|downloadUrl\|StorageMetadata"` | Only `AuthRepositorioFirebase.kt:9,21` (allowed account-cleanup); ZERO in `AvatarRepositorioFirebase.kt` |
| Grep audit 3 — no direct construction outside the boundary | `... app\src\main\java \| Select-String "AvatarRepositorioLocal(\|AvatarRepositorioFirebase("` | Only `service\LocalizadorServicios.kt:34`, `data\firebase\AvatarRepositorioFirebase.kt:28,32`, `data\local\AvatarRepositorioLocal.kt:16` (exactly the allowed set) |
| Grep audit 4 — ViewModel does not build a repository | `... viewmodel\AvatarViewModel.kt \| Select-String "data.local\|data.firebase"` | ZERO MATCHES |
| Grep audit 5 — `avatarUrl` has no producer/reader | `... app\src\main\java \| Select-String "avatarUrl"` | Only `modelo\Usuario.kt:18` |
| Grep audit 6 — no delete affordance wired | `... app\src\main \| Select-String "eliminarAvatar"` | Only `viewmodel\AvatarViewModel.kt:101 (KDoc),104 (declaration)`; no caller |
| Scope (4.5) | `git status --short` / `git diff --stat` | Only the 13 authorized targets + `tasks.md`; `.idea/**` were already modified before this batch and were not touched. No Gradle change, no canonical `openspec/specs/**` edit, no `storage.rules` edit, no Console/deploy action |
| Manual matrix (4.4) | emulator build + device/emulator | **NOT EXECUTED** |

## Manual verification matrix — NOT EXECUTED (pending manual verification)

Requires an emulator/device build and a running Firebase emulator; explicitly outside this run's
scope. **No row below has been executed; none of these are claimed as passing.**

| # | Scenario (spec) | Status |
|---|---|---|
| 1 | Successful upload writes the documented fields | NOT EXECUTED |
| 2 | Normal input is downscaled and encoded | NOT EXECUTED |
| 3 | Oversized input is rejected | NOT EXECUTED |
| 4 | Hint changes only when the blob changes | NOT EXECUTED |
| 5 | Cache hit avoids a repeat read | NOT EXECUTED |
| 6 | Hint change invalidates the cache | NOT EXECUTED |
| 7 | A group member's avatar renders on a member card | NOT EXECUTED |
| 8 | Missing avatar falls back to the placeholder | NOT EXECUTED |
| 9 | Offline degradation preserves a last-known avatar | NOT EXECUTED |
| 10 | Owner can write / another user cannot | NOT EXECUTED |
| 11 | ViewModel resolves the contract through the locator | NOT EXECUTED (static audit 4 + 3 cover the code shape) |
| 12 | No delete affordance | NOT EXECUTED (static audit 6 covers the code shape) |

## Deviations from Design

1. **`AvatarViewModel` secondary constructor (design bug fixed).** `design.md` §Decision specified
   `constructor(application: Application) : this(application)`. Kotlin resolves that `this(...)`
   call to the same secondary constructor, producing a hard compile error:
   `There's a cycle in the delegation calls chain` (`AvatarViewModel.kt:24`). Fixed by passing the
   defaults explicitly (`this(application, LocalizadorServicios.repositorioAvatar, LocalizadorServicios.repositorioAuth)`),
   which is exactly `ParejaViewModel`'s proven pattern that the design cited as the rationale. No
   behavioral deviation — the intent (a single-`Application` constructor for `by viewModels()`) is
   preserved.
2. **Tasks 2.3 + 2.8 merged into one rewrite of `AvatarRepositorioLocal.kt`.** The design sequenced
   2.3 (additive cache API, keep authority methods) then 2.8 (remove authority methods) to keep the
   compile green mid-phase. No compile gate runs between them in this batch, so the file was written
   directly in its final cache-only state. The final state satisfies both tasks; no authority caller
   remains (audit 3). Recorded for traceability.

## Issues Found

- The `AvatarImagen` two-pass decode + guarded recycle is implemented as designed; `decodificarJpeg`
  and `comprimirJpeg` are intentionally not exercised by the JVM test (they touch
  `ContentResolver`/`Bitmap`, which throw `Stub!` off-device). No Robolectric was added.
- `LruCache` is `android.util.LruCache` sized by count (32), as designed.

## Remaining Tasks

- [ ] 4.4 — run the 12-row manual matrix on an emulator build with a live Firebase emulator.

## Rollback Boundary

- WU1 (new files only): delete `repositorio/AvatarRepositorio.kt`, `util/AvatarImagen.kt`,
  `test/java/com/example/tfg/util/`.
- WU2: revert `Usuario.kt`, `AuthRepositorioFirebase.kt`, `AvatarRepositorioFirebase.kt`,
  `AvatarRepositorioLocal.kt`, `LocalizadorServicios.kt`, `AvatarViewModel.kt`, `FragmentPerfil.kt`,
  `firestore.rules`.
- WU3: revert `MainActivity.kt`, `FragmentPgPrincipal.kt`.
- No commit was created; the orchestrator owns commits.

## Scope Gate Confirmation

Only the thirteen authorized edit targets changed, plus `tasks.md` and this `apply-progress.md`.
No Gradle change (JUnit 4.13.2 was already declared via `testImplementation(libs.junit)`). No
canonical spec, delta, or design edits. No `.idea/**` edits. No commits or pushes.
