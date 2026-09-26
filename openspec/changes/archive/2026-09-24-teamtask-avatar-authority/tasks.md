# Tasks: TeamTask Avatar Authority Convergence

Implementation breakdown for `teamtask-avatar-authority` (convergence roadmap step 3; debt items
TD-2 and TD-3). Baseline is the approved `design.md`: its forks (a)-(g), its `AvatarRepositorio`
contract, the `AvatarImagen` helper object, the atomic-write upload, the hint-keyed cache, its
File Changes table, and its Verification Plan (compile gate + 6 grep audits + 12-row manual
matrix). The design's Threat Matrix is `N/A`, so no RED-test tasks are generated.

Delivery context: solo developer, commits go directly to `master`, **no pull requests**. The
Review Workload Forecast below is **informational only** — no PR slicing, chain strategy, or
size-exception decision applies.

One orchestrator adjustment is folded in (see
[Orchestrator Adjustments](#orchestrator-adjustments)): the design mentions a pure-JVM unit test
for the `AvatarImagen` helpers but omits it from its File Changes table. It is made an explicit
deliverable here (task 1.3).

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~650-700 raw additions+deletions; dominated by the full rewrite of `data/firebase/AvatarRepositorioFirebase.kt` (~174 lines removed) plus three new files (`AvatarRepositorio.kt` ~40, `AvatarImagen.kt` ~80, `AvatarImagenTest.kt` ~50) and three display-site rewrites |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Not applicable — single direct-to-`master` change (no PRs) |
| Delivery strategy | auto-chain |
| Chain strategy | pending (no chain needed; no-PR delivery) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

**Informational note (no-PR delivery).** The raw count exceeds the 400-line review budget
primarily because one file is rewritten wholesale (`AvatarRepositorioFirebase.kt`, ~174 lines
removed, ~120 added) and because three new files land. There is no reviewer-facing PR: commits
land directly on `master`. The forecast therefore carries no gating weight, and no chain or
size-exception decision is requested. Per `openspec/config.yaml`, `strict_tdd: false`, so no
RED-GREEN ceremony applies.

### Suggested Work Units

Each unit is compile-coherent at its end, independently verifiable, and has its own revert
boundary. "Likely PR" is recorded for traceability only; there is no PR in this delivery.

| Unit | Goal | Likely PR | Focused proving command | Runtime harness | Rollback boundary |
|------|------|-----------|-------------------------|-----------------|-------------------|
| WU1 | Contract + image helpers + JVM test | slice 1 (informational) | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` | N/A — pure JVM (no Android runtime needed) | Delete the three new files (`AvatarRepositorio.kt`, `AvatarImagen.kt`, `AvatarImagenTest.kt`) |
| WU2 | Data + authority + ViewModel + profile | slice 2 (informational) | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` | Emulator/device manual matrix rows 1-6, 8-11 | Revert `Usuario.kt`, `AuthRepositorioFirebase.kt`, `AvatarRepositorioFirebase.kt`, `AvatarRepositorioLocal.kt`, `LocalizadorServicios.kt`, `AvatarViewModel.kt`, `FragmentPerfil.kt`, `firestore.rules` |
| WU3 | Display sites (drawer + member cards) | slice 3 (informational) | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` | Emulator/device manual matrix rows 7, 12 | Revert `MainActivity.kt` and `FragmentPgPrincipal.kt` |
| WU4 | Verification (global) | N/A | Global Gates 1-5 | Full manual matrix | N/A — verification only |

WU1 is standalone (additive files, no callers). WU2 and WU3 are mutually coupled only through the
locator/contract: WU2 must land (at least tasks 2.1-2.7) before WU3 compiles, because the display
sites resolve through `LocalizadorServicios.repositorioAvatar`. WU1 may land first in its own
session; WU2 and WU3 may be one or two sessions.

## Global Gates

These gates apply to the whole change and are re-run at the end (Phase 4). `strict_tdd: false`
(`openspec/config.yaml:6`); `app/src/test` currently holds only the template `ExampleUnitTest.kt`
(read-only), so the compile gate plus the new focused JVM test plus the grep audits plus the
manual matrix are the whole verification surface.

1. **Compile gate** (build types `emulator` `app/build.gradle.kts:28-35` (read-only) and
   `release` `:36-46` (read-only); `debug` is the implicit third build type, no flavors):

   ```powershell
   .\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain
   ```

   Expected: `BUILD SUCCESSFUL`, no unresolved references.

2. **Focused JVM test gate** (new): run the `AvatarImagen` unit test off-device.

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
   ```

   Expected: `BUILD SUCCESSFUL`; `AvatarImagenTest` executes and passes. **Verify the exact task
   name during apply**: with build types `debug`/`emulator`/`release` and no product flavors, the
   expected task is `:app:testDebugUnitTest` (`testDebugUnitTest`), and `:app:test` runs all three.
   If the name differs, use the actual `test*UnitTest` task and record it in the apply report.

3. **Grep audits** (required; commands are PowerShell, scoped to `app/src/main`):

   | Audit | Command | Expected |
   |---|---|---|
   | `avatar_prefs` retired | `Get-ChildItem -Recurse app\src\main | Select-String "avatar_prefs"` | Zero matches |
   | No Storage on the avatar path | `Get-ChildItem -Recurse app\src\main\java\com\example\tfg\data\firebase | Select-String "FirebaseStorage\|putBytes\|downloadUrl\|StorageMetadata"` | Zero matches in `AvatarRepositorioFirebase.kt`; only `AuthRepositorioFirebase.kt` account-cleanup may remain |
   | No direct avatar construction outside the boundary | `Get-ChildItem -Recurse app\src\main\java | Select-String "AvatarRepositorioLocal(\|AvatarRepositorioFirebase("` | Matches only `service\LocalizadorServicios.kt`, `data\local\AvatarRepositorioLocal.kt`, `data\firebase\AvatarRepositorioFirebase.kt` |
   | ViewModel does not build a repository | `Get-ChildItem app\src\main\java\com\example\tfg\viewmodel\AvatarViewModel.kt | Select-String "data.local\|data.firebase"` | Zero matches |
   | `avatarUrl` has no producer/reader | `Get-ChildItem -Recurse app\src\main\java | Select-String "avatarUrl"` | Only `modelo\Usuario.kt` |
   | No delete affordance wired | `Get-ChildItem -Recurse app\src\main | Select-String "eliminarAvatar"` | Only `AvatarViewModel.kt` (declaration + KDoc), no caller |

4. **Manual verification matrix** (emulator build; Phase 4.4, 12 rows mapped to spec scenarios).

5. **Scope gate**: only the thirteen authorized edit targets (twelve application/rules targets from
   the design's File Changes table plus the new `AvatarImagenTest.kt`). No Gradle change, no
   canonical `openspec/specs/**` edit (archive-time only), no `storage.rules` edit, no Firebase
   Console/deployment action, no commit by this phase.

## Phase 1 — WU1: Foundation (contract, helpers, JVM test)

Goal: land the pure, additive foundation with no callers, so it compiles and is unit-testable on
its own. Ends with the compile gate + focused JVM test.

- [x] **1.1 Create the canonical contract `app/src/main/java/com/example/tfg/repositorio/AvatarRepositorio.kt`.**
  - `interface AvatarRepositorio` with exactly two operations, matching `design.md` §Interfaces:
    `suspend fun subirAvatar(imageUri: Uri): Result<Timestamp>` and
    `suspend fun obtenerAvatar(uid: String, avatarUpdatedAt: Timestamp?): Bitmap?`.
  - Imports: `android.graphics.Bitmap`, `android.net.Uri`, `com.google.firebase.Timestamp`. No
    delete operation. Carry the KDoc from the design (self-only upload; nullable read; no
    Firestore read when the hint is null; never throws).
  - **Verify**: compile gate; the interface matches the `avatar-management` delta
    `openspec/changes/teamtask-avatar-authority/specs/avatar-management/spec.md` (read-only)
    Requirement 1.
  - **Rollback**: delete the file; no other file references it yet.
  - **Depends/Parallel**: none. Blocks 1.3 and 2.4. Parallel with 1.2.

- [x] **1.2 Create `app/src/main/java/com/example/tfg/util/AvatarImagen.kt`.**
  - `object AvatarImagen` with constants `LADO_LARGO_PX = 256`, `CALIDAD_JPEG = 75`,
    `MAX_BASE64_CHARS = 700_000`, `CONTENT_TYPE_JPEG = "image/jpeg"`, and functions
    `comprimirJpeg(resolver: ContentResolver, uri: Uri, ladoLargo: Int = LADO_LARGO_PX, calidad: Int = CALIDAD_JPEG): ByteArray?`,
    `codificarBase64(bytes: ByteArray): String`, `decodificarBase64(base64: String): ByteArray?`,
    `decodificarJpeg(bytes: ByteArray): Bitmap?`.
  - Compression is a **two-pass** decode: `BitmapFactory.Options.inJustDecodeBounds = true` for the
    dimensions, then a power-of-two `inSampleSize` so the decoded long edge is ≥ 256, then
    `Bitmap.createScaledBitmap` to exactly 256 on the long edge, then `compress(JPEG, 75, …)`.
  - Use `java.util.Base64` (standard, no line wrapping) — **NOT** `android.util.Base64`, which is
    stubbed in plain JVM tests. This is a deliberate refinement of the proposal's loose "Android
    `Base64`" note (design decision). Pure helpers must be Robolectric-free.
  - **Verify**: compile gate; exercised by 1.3.
  - **Rollback**: delete the file.
  - **Depends/Parallel**: none. Blocks 1.3 and 2.4. Parallel with 1.1.

- [x] **1.3 Create the focused JVM test `app/src/test/java/com/example/tfg/util/AvatarImagenTest.kt` (orchestrator adjustment; explicit deliverable).**
  - Create the new `app/src/test/java/com/example/tfg/util/` directory. Plain JUnit 4
    (`org.junit.Test` / `org.junit.Assert`), **no Robolectric** — `java.util.Base64` keeps the
    helpers pure-JVM. `strict_tdd: false`, so this is a plain focused test, not a RED-GREEN cycle.
  - Cover, at minimum:
    - `codificarBase64` / `decodificarBase64` round-trip: arbitrary bytes (including an embedded
      `0x00`) decode back byte-identical.
    - `decodificarBase64` on a malformed string returns `null` (the designed failure shape), not an
      exception.
    - The size-cap gate: assert the predicate the upload uses rejects an encoded payload over the
      cap, e.g. `assertTrue((AvatarImagen.MAX_BASE64_CHARS + 1) > AvatarImagen.MAX_BASE64_CHARS)` and
      document that `AvatarRepositorioFirebase.subirAvatar` reuses exactly this `length >
      MAX_BASE64_CHARS` check (the threshold constant is the contract).
    - Only call **pure-JVM** helpers. Do **not** call `comprimirJpeg`/`decodificarJpeg` in the
      default test path (they touch `ContentResolver`/`Bitmap`, which throw `Stub!` off-device); if
      a `decodificarJpeg` case is added, gate it so it does not fail the JVM run.
  - **Verify**: Global Gate 2 — `AvatarImagenTest` executes and passes.
  - **Rollback**: delete the file and the new `util/` test directory.
  - **Depends/Parallel**: requires 1.2. Parallel with 1.1. Confirm the Gradle task name here (Global
    Gate 2 note).

- [x] **1.4 Run the WU1 gate.**
  - Run Global Gate 1 (compile) and Global Gate 2 (focused JVM test).
  - **Verify**: `BUILD SUCCESSFUL`; `AvatarImagenTest` green.
  - **Rollback**: n/a (verification task); on failure, fix 1.1-1.3 before proceeding.
  - **Depends/Parallel**: requires 1.1, 1.2, 1.3. Blocks Phase 2.

## Phase 2 — WU2: Data, authority, ViewModel, profile

Goal: make Firebase the single authority, add the hint, atomically write the blob, demote the local
repository to a cache, wire the locator, and move the ViewModel/profile to `Bitmap?`. Tasks 2.1-2.7
must all land before the end-of-phase gate; 2.8 (local demotion) is sequenced last so the compile
stays green mid-phase.

- [x] **2.1 Add the avatar hint to `app/src/main/java/com/example/tfg/modelo/Usuario.kt` and mark `avatarUrl` legacy.**
  - Immediately after `avatarUrl` (line 18) insert `val avatarUpdatedAt: Timestamp? = null` with the
    hint comment (existence flag + cache version key). Reword the `avatarUrl` comment to mark it
    legacy (no producer/reader after this change). Keep `avatarUrl` in place (design decision (b)).
  - All eight `Usuario(...)` construction sites use **named** arguments, so inserting a field is
    positionally safe (design verified this).
  - **Verify**: compile gate; Global Gate 3 "`avatarUrl` has no producer/reader" holds once 2.2 lands.
  - **Rollback**: revert the two comment/field lines.
  - **Depends/Parallel**: none. Must land together with 2.2 (reverting one half reintroduces the
    silent-drop gap). Parallel with 2.2-2.9.

- [x] **2.2 Surface the hint at the four `AuthRepositorioFirebase` mapper sites.**
  - In `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt`, populate
    `avatarUpdatedAt = doc.getTimestamp("avatarUpdatedAt")` at: `login` (`:125-136`),
    `loginConTokenProveedor` (`:194-205`), and `observarUsuarios` (`:404-412`).
  - `usuarioActual()` (`:370-384`): the cached branch returns `_usuarioCache` and must preserve the
    hint; the no-document fallback construction relies on the model default `null` (no drop).
  - Do not populate `avatarUrl` anywhere (it stays inert). Do not add a new Firestore read: the hint
    travels on the documents already being mapped.
  - **Verify**: `avatar-management` delta scenarios "Live `usuarios` stream carries the hint",
    "Login mapping carries the hint", and "Absent hint maps to null"
    (`openspec/changes/teamtask-avatar-authority/specs/avatar-management/spec.md`, read-only);
    manual matrix rows 3-5.
  - **Rollback**: revert the four call sites.
  - **Depends/Parallel**: requires 2.1. Parallel with 2.3-2.9.

- [x] **2.3 Add the last-known cache API to `app/src/main/java/com/example/tfg/data/local/AvatarRepositorioLocal.kt` (additive step).**
  - Keep the class name/path, the `tfg_prefs` namespace, the key `avatar_path_$uid`, and the
    `filesDir/avatars/$uid.jpg` layout. **Add** `guardarUltimoConocido(uid: String, jpegBytes: ByteArray): Boolean`,
    `obtenerUltimoConocido(uid: String): ByteArray?`, and `limpiar(uid: String)`.
  - This is a pure **addition**: keep the existing authority methods (`subirAvatar`,
    `obtenerAvatarPathActual`, `obtenerAvatarPath`, `eliminarAvatarActual`, `determinarExtension`)
    for now so the file compiles while `AvatarViewModel` still calls them. They are removed in 2.8.
  - **Verify**: compile gate.
  - **Rollback**: remove the three added methods.
  - **Depends/Parallel**: none. Blocks 2.4. Parallel with 2.1-2.2, 2.9.

- [x] **2.4 Rewrite `app/src/main/java/com/example/tfg/data/firebase/AvatarRepositorioFirebase.kt` as the Firestore base64 implementation.**
  - Repurpose in place (design decision (g)); implement `AvatarRepositorio`. Remove **every**
    Storage reference (`storage.reference.child`, `StorageMetadata`, `putBytes`, `downloadUrl`,
    `StorageException`, the `usuarios.avatarUrl` writes) and the old `obtenerAvatarUrl*`/`eliminarAvatar`
    members.
  - Constructor: `firestore: FirebaseFirestore = FirebaseComposition.firestore()`,
    `auth: FirebaseAuth = FirebaseComposition.auth()`, `context: Context`,
    `cacheLocal: AvatarRepositorioLocal = AvatarRepositorioLocal(context)`. Own a
    `LruCache<String, Bitmap>(32)`.
  - `subirAvatar(imageUri)`: `withContext(Dispatchers.IO)`; uid from `auth.currentUser?.uid` else
    `Result.failure` with **no write**; `AvatarImagen.comprimirJpeg(resolver, uri)` →
    `AvatarImagen.codificarBase64(bytes)`; if `length > AvatarImagen.MAX_BASE64_CHARS` return
    `Result.failure` naming the size/quality limit with **no write**; capture
    `val ahora = com.google.firebase.Timestamp.now()` **once**; write both docs in one `WriteBatch`:
    `avatares/{uid}` = `{base64, contentType = "image/jpeg", updatedAt = ahora}` and
    `usuarios/{uid}` set `{avatarUpdatedAt = ahora}` with `SetOptions.merge()`; `commit()`. On
    success seed `LruCache["$uid#${ahora.seconds}:${ahora.nanoseconds}"]` with the decoded bitmap
    and call `cacheLocal.guardarUltimoConocido(uid, jpegBytes)`; return `Result.success(ahora)`.
  - `obtenerAvatar(uid, avatarUpdatedAt)`: `withContext(Dispatchers.IO)`. If the hint is `null`,
    perform **no** Firestore read — return `cacheLocal.obtenerUltimoConocido(uid)` decoded, else
    `null`. If the hint is non-null, check the memory cache by `claveCache`; a hit performs no read.
    On a miss, `firestore.collection("avatares").document(uid).get()`: base64 present → decode →
    put memory cache + `cacheLocal.guardarUltimoConocido` → return; base64 absent → `cacheLocal` or
    `null`; read throws → `cacheLocal` or `null`. **Never throws.**
  - `private fun claveCache(uid, hint) = "$uid#${hint.seconds}:${hint.nanoseconds}"`.
  - **Verify**: Global Gate 3 "No Storage on the avatar path" and "No direct avatar construction
    outside the boundary"; manual matrix rows 1-6, 9-10.
  - **Rollback**: `git revert` this one file; the `avatares` collection may remain inert data.
  - **Depends/Parallel**: requires 1.1, 1.2, 2.3. Parallel with 2.1-2.2, 2.9.

- [x] **2.5 Add `repositorioAvatar` to `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt`.**
  - Add `val repositorioAvatar: AvatarRepositorio by lazy { FirebaseComposition.requireContext(); val contexto = requireNotNull(TFGApplication.appContext) { "TFGApplication.appContext no inicializado" }; AvatarRepositorioFirebase(firestore = FirebaseComposition.firestore(), auth = FirebaseComposition.auth(), context = contexto) }`.
  - The `Context` comes from `TFGApplication.appContext` (precedent: `TareaRepositorioFirebase.kt:538`),
    **not** a new `FirebaseComposition` accessor; `FirebaseComposition.kt` (read-only) stays
    untouched. `TFGApplication.kt` is read-only and already sets `appContext` in `onCreate`
    (`:9,14`). Add the needed imports (`AvatarRepositorio`, `AvatarRepositorioFirebase`,
    `TFGApplication`).
  - **Verify**: compile gate; Global Gate 3 "No direct avatar construction outside the boundary"
    lists `LocalizadorServicios.kt` as an allowed match.
  - **Rollback**: remove the property + imports.
  - **Depends/Parallel**: requires 1.1, 2.4. Blocks 2.6, 3.1, 3.2.

- [x] **2.6 Rewire `app/src/main/java/com/example/tfg/viewmodel/AvatarViewModel.kt` to `AvatarRepositorio` through the locator.**
  - Signature per design: primary constructor
    `(application: Application, repositorioAvatar: AvatarRepositorio = LocalizadorServicios.repositorioAvatar, repositorioAuth: AuthRepositorio = LocalizadorServicios.repositorioAuth)`
    plus a secondary `constructor(application: Application) : this(application)` so `by viewModels()`
    keeps working; remove the `AvatarRepositorioLocal` import and the direct construction (line 16).
  - Public surface per design: `avatarUrlActual: StateFlow<String?>` → `avatarActual: StateFlow<Bitmap?>`;
    `avatarState: StateFlow<Result<Unit>?>` (was `Result<String>?`); `cargando: StateFlow<Boolean>`.
    `subirAvatar(uri)`: on success re-resolve `obtenerAvatar(uid, ahora)` so `avatarActual` emits.
    `cargarAvatarActual()`: read the current uid and `repositorioAuth.usuarioActual()?.avatarUpdatedAt`,
    then `obtenerAvatar(uid, hint)`. `eliminarAvatar()` retained as an **unwired state-clear** (no
    remote delete exists). `resetAvatarState()` unchanged.
  - **Verify**: Global Gate 3 "ViewModel does not build a repository" (zero `data.local`/`data.firebase`);
    manual matrix row 11 and the `architecture-map` delta scenario "Resolving `AuthRepositorio` from a
    ViewModel" (`openspec/changes/teamtask-avatar-authority/specs/architecture-map/spec.md`, read-only).
  - **Rollback**: revert the file.
  - **Depends/Parallel**: requires 2.4, 2.5. Must land with 2.7 before 2.8 or the compile breaks
    (`FragmentPerfil` reads `avatarUrlActual`).

- [x] **2.7 Update `app/src/main/java/com/example/tfg/vista/FragmentPerfil.kt` for the `Bitmap?` avatar.**
  - Replace the `avatarVM.avatarUrlActual.collect { url -> … }` block (`:189-205`) with
    `avatarVM.avatarActual.collect { bmp -> if (bmp != null) Glide.with(this).load(bmp).circleCrop().placeholder(R.drawable.perfil).into(ivAvatarPerfil) else { Glide.with(this).clear(ivAvatarPerfil); ivAvatarPerfil.setImageResource(R.drawable.perfil) } }`.
  - The picker preview (`:49-62`) and the `avatarState` collect (`:171-187`) stay; the latter still
    compiles with `Result<Unit>?`. `configurarAvatarUI()`/`cargarAvatarActual()` calls (`:86`) unchanged.
  - **Verify**: compile gate; manual matrix rows 1, 8, 9 (profile site).
  - **Rollback**: revert the file.
  - **Depends/Parallel**: requires 2.6. Parallel with 2.8-2.9 only after 2.6 lands.

- [x] **2.8 Remove the `AvatarRepositorioLocal` authority methods (demote to cache).**
  - In `app/src/main/java/com/example/tfg/data/local/AvatarRepositorioLocal.kt`, delete `subirAvatar`,
    `obtenerAvatarPathActual`, `obtenerAvatarPath`, `eliminarAvatarActual`, `determinarExtension`, and
    the now-unused imports (`android.net.Uri`, `com.example.tfg.service.firebase.FirebaseComposition`,
    `android.util.Log` if unused). Keep the class, the `tfg_prefs` namespace, the `avatar_path_$uid`
    key, `filesDir/avatars/$uid.jpg`, and the three cache methods from 2.3.
  - Apply **after** 2.6 and 2.7 so no caller remains; this keeps the mid-phase compile green.
  - **Verify**: Global Gate 3 "`avatar_prefs` retired" (zero) and "No direct avatar construction
    outside the boundary"; compile gate.
  - **Rollback**: restore the removed methods and imports.
  - **Depends/Parallel**: requires 2.6, 2.7. Parallel with 2.9.

- [x] **2.9 Add the `avatares/{uid}` block to `firestore.rules`.**
  - After the `usuarios` match (`:27-32`), add
    `match /avatares/{userId} { allow read: if signedIn(); allow write: if isSelf(userId); }`,
    reusing the existing `signedIn()`/`isSelf()` helpers (`:16-22`); add the `avatares` block to the
    header comment (`:3-11`). Local file only — **no** deployment, **no** Console change. Leave
    `storage.rules` (read-only) untouched (deferred to the rules/indexes change).
  - **Verify**: manual matrix row 10; `firestore-contracts` delta scenario "Writer is self only" and
    "Reader is any signed-in user"
    (`openspec/changes/teamtask-avatar-authority/specs/firestore-contracts/spec.md`, read-only).
  - **Rollback**: remove the added block + comment line.
  - **Depends/Parallel**: none. Parallel with 2.1-2.8.

- [x] **2.10 Run the WU2 gate.**
  - Run Global Gate 1 (compile) and Global Gate 3 (all six audits).
  - **Verify**: `BUILD SUCCESSFUL` for both build types; every audit matches its expected result.
  - **Rollback**: n/a (verification task); on failure, fix the specific Phase 2 task.
  - **Depends/Parallel**: requires 2.1-2.9. Blocks Phase 3.

## Phase 3 — WU3: Display sites (drawer + member cards)

Goal: route the two remaining display sites (drawer header and dashboard member cards) through the
canonical repository with the recycling guard and placeholder fallback. Both depend on the locator
and contract from Phase 2.

- [x] **3.1 Route the drawer header through the repository in `app/src/main/java/com/example/tfg/vista/MainActivity.kt`.**
  - In `refrescarHeaderDrawer()` (`:451-509`), replace the `tfg_prefs` read (`:484-497`) with a
    resolve through `LocalizadorServicios.repositorioAvatar.obtenerAvatar(usuario.id, usuario.avatarUpdatedAt)`.
    Add an `avatarDrawerJob: Job?` field (the `Job` import already exists, `:35`); cancel any prior
    job before launching, and launch on `lifecycleScope`. On a non-null bitmap render with
    `Glide.with(this).load(bitmap).circleCrop().placeholder(R.drawable.perfil).into(ivAvatar)`; on
    `null` `Glide.clear(ivAvatar)` + `setImageResource(R.drawable.perfil)`. Keep the existing
    null-`usuario` fallback (`:498-505`).
  - **Verify**: compile gate; manual matrix rows 7, 8, 9, 12 (drawer site).
  - **Rollback**: revert the file.
  - **Depends/Parallel**: requires 2.5. Parallel with 3.2.

- [x] **3.2 Route the member cards through the repository in `app/src/main/java/com/example/tfg/vista/FragmentPgPrincipal.kt`.**
  - In `MiembrosHorizontalAdapter.onBindViewHolder` (`:370-389`), delete the `avatar_prefs` read and
    the `File`-on-Glide path (`:376-388`). Set `holder.ivAvatar.tag = u.id`, reset the holder to
    `R.drawable.perfil` on every bind, then
    `viewLifecycleOwner.lifecycleScope.launch { val bmp = LocalizadorServicios.repositorioAvatar.obtenerAvatar(u.id, u.avatarUpdatedAt); if (holder.ivAvatar.tag == u.id && bmp != null) Glide.with(holder.itemView.context).load(bmp).circleCrop().into(holder.ivAvatar) }`.
  - Remove the now-unused `java.io.File` import (`:36`) — its only use was `:380`.
  - **Verify**: compile gate; manual matrix rows 7, 8, 9, 12 (member-card site); Global Gate 3
    "`avatar_prefs` retired" (zero).
  - **Rollback**: revert the file.
  - **Depends/Parallel**: requires 2.5. Parallel with 3.1.

- [x] **3.3 Run the WU3 gate.**
  - Run Global Gate 1 (compile) and Global Gate 3 "`avatar_prefs` retired".
  - **Verify**: `BUILD SUCCESSFUL`; zero `avatar_prefs` matches.
  - **Rollback**: n/a (verification task).
  - **Depends/Parallel**: requires 3.1, 3.2. Blocks Phase 4.

## Phase 4 — Verification (global)

Goal: prove the whole change. This is the design's Verification Plan. There is no pre-existing
automated avatar test net; state that explicitly in the apply report.

- [x] **4.1 Run the compile gate.**
  - Run Global Gate 1.
  - **Verify**: `BUILD SUCCESSFUL` for both build types.
  - **Rollback**: n/a.
  - **Depends/Parallel**: requires Phases 1-3. Parallel with 4.2-4.5.

- [x] **4.2 Run the focused JVM test gate.**
  - Run Global Gate 2 (`AvatarImagenTest`).
  - **Verify**: `BUILD SUCCESSFUL`; `AvatarImagenTest` passes. If the task name differs from
    `:app:testDebugUnitTest`, record the actual name.
  - **Rollback**: n/a.
  - **Depends/Parallel**: requires 1.2, 1.3. Parallel with 4.1/4.3-4.5.

- [x] **4.3 Run the grep audits.**
  - Run Global Gate 3 (all six audits).
  - **Verify**: every audit matches its expected result.
  - **Rollback**: n/a.
  - **Depends/Parallel**: requires Phases 2-3. Parallel with 4.1/4.2/4.4/4.5.

- [x] **4.4 Manual verification matrix — WAIVED by owner decision (2026-09-26).** The owner decided the app will be validated by real user testing instead of a hand-executed device matrix; no rows were claimed as passed. All executable checks for this change are green (compile gates, grep audits, JVM suite).
  - Execute the 12-row matrix below (the design's matrix, reproduced here for apply convenience).
  - **Verify**: every row matches its expected result.
  - **Rollback**: n/a (verification task). On failure, diagnose the specific Phase 1-3 task.
  - **Depends/Parallel**: requires Phases 1-3. Parallel with 4.1-4.3/4.5.

  | # | Scenario (spec) | Steps | Expected |
  |---|---|---|---|
  | 1 | Successful upload writes the documented fields | Upload a photo in `FragmentPerfil` | `avatares/{uid}` has `base64`, `contentType = image/jpeg`, `updatedAt`; `usuarios/{uid}.avatarUpdatedAt` set; no blob field in `usuarios/{uid}` |
  | 2 | Normal input is downscaled and encoded | Upload a >256px photo | Stored `base64` decodes to a ≤256px JPEG, not the original bytes |
  | 3 | Oversized input is rejected | Temporarily set `MAX_BASE64_CHARS` to a tiny value and upload | Toast/`tvErrorAvatar` shows the size-limit message; **no** `avatares/{uid}` write; `avatarUpdatedAt` unchanged |
  | 4 | Hint changes only when the blob changes | Upload avatar A, then avatar B | `avatarUpdatedAt` differs between the two; a forced failure in between does not change it |
  | 5 | Cache hit avoids a repeat read | Render a member's card twice with no hint change | Only one `avatares/{uid}` read (verify via emulator logging / offline test) |
  | 6 | Hint change invalidates the cache | Change a user's hint externally, rebind the card | The new blob is re-read and rendered, not the cached bitmap |
  | 7 | A group member's avatar renders on a member card | `uA` views the dashboard with `uB` having an avatar | `ivMiembroAvatar` shows `uB`'s avatar, not the placeholder |
  | 8 | Missing avatar falls back to the placeholder | Member with no `avatarUpdatedAt` | `R.drawable.perfil` at all three sites |
  | 9 | Offline degradation preserves a last-known avatar | Load once, kill the network (airplane mode), reopen | Cached avatar shown; with no cache, `R.drawable.perfil` |
  | 10 | Owner can write / another user cannot | Emulator: write `avatares/{self}` then `avatares/{other}` | Self write allowed; cross-user write denied; cross-user **read** allowed |
  | 11 | ViewModel resolves the contract through the locator | Inspect `AvatarViewModel.kt` | Constructor takes `AvatarRepositorio` (default from the locator); zero concrete construction |
  | 12 | No delete affordance | Inspect the UI | No control calls `eliminarAvatar`; the method remains uncalled |

- [x] **4.5 Run the scope gate.**
  - Confirm only the thirteen authorized edit targets changed; no Gradle change; no canonical
    `openspec/specs/**` edit; no `storage.rules` edit; no Firebase Console/deployment action; no
    commit by this phase.
  - **Verify**: `git status` shows only the authorized targets plus `tasks.md`; the diffs match the
    design's File Changes table.
  - **Rollback**: n/a.
  - **Depends/Parallel**: requires Phases 1-3. Parallel with 4.1-4.4.

## Orchestrator Adjustments

Recorded for traceability; accepted by the orchestrator and do not expand the proposal's product
scope.

- **A1 — Explicit JVM test deliverable.** The design mentions an optional pure-JVM test at
  `app/src/test/java/com/example/tfg/util/AvatarImagenTest.kt` for the compression/base64 helpers
  but omits it from its File Changes table (a validator-flagged inconsistency). Task 1.3 makes it an
  explicit deliverable: a small focused JVM test using `java.util.Base64` (no Robolectric) that
  exercises `codificarBase64`/`decodificarBase64` and the size-cap gate. `strict_tdd: false`, so no
  RED-GREEN ceremony — a plain focused test with the correct runner command (Global Gate 2;
  confirm the exact Gradle task name during apply).

## Out of Scope (do not implement here)

- Avatar delete/removal affordance (the ViewModel keeps `eliminarAvatar` as an unwired state-clear
  only).
- Firebase Storage use and `storage.rules` pruning (deferred to the rules/indexes change).
- Account deletion/cleanup (TD-13); navigation/lifecycle/listener/auto-login refactors (TD-4/TD-5/TD-6);
  TD-7 (`resolverReclamo` split writes).
- Deploying rules/indexes/App Check or any Firebase Console change (all `[UNVERIFIED]`).
- Editing canonical `openspec/specs/**` (this change's deltas do that at archive time).
- Batch migration of pre-existing on-device local avatars (best-effort only).
- `MiembrosHorizontalAdapter` extraction out of `FragmentPgPrincipal` (design Open Question; future
  convergence work, not a task of this change).
- Pruning the stale Storage exercise in `tools/firebase/emulator-verify.mjs:169-181` (read-only;
  deferred).
- No Hilt, Room, Retrofit, Compose, new dependency, or Gradle change.

## Notes on Task Format

- The design's Threat Matrix is `N/A` (no routing/shell/VCS/regex-injection/process boundary), so
  **no RED-test tasks** are generated.
- `strict_tdd: false` (`openspec/config.yaml:6`); testing is the compile gate, the focused JVM test,
  the six grep audits, and the 12-row manual matrix.
- Every task is completable in one session; WU1, WU2, and WU3 can each be a single work session.
- No commit is created by this phase; the apply phase owns commits.
