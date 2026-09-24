# Design: TeamTask Avatar Authority Convergence

## Technical Approach

Make Firebase the single avatar authority behind one contract, without touching the existing
architecture (XML layouts, Fragments, ViewBinding, manual service locator, no Hilt/Room/Retrofit/Compose).

The change introduces `repositorio/AvatarRepositorio` as the only avatar contract, resolved through
`LocalizadorServicios.repositorioAvatar`. Its Firebase implementation stores compressed base64 bytes in
`avatares/{uid}` and, on success only, writes the `usuarios/{uid}.avatarUpdatedAt` hint that doubles as
the cache-invalidation key. All three display sites stop reading local files and instead resolve a
`Bitmap` through the contract using `(uid, hint)`, cached in memory and backed by a `tfg_prefs`/file
last-known cache for offline and legacy fallback. The dead Storage implementation is repurposed (not
duplicated) into the Firestore implementation, and the writer-less `avatar_prefs` reader is deleted.

The proposal fixed *what* (Firebase authority, Firestore base64, no Storage, no blobs in `usuarios`,
deletion out of scope). This design fixes *how* and closes the seven forks the spec phase left open.

### Fork resolution summary

| Fork | Decision |
|---|---|
| (a) `AvatarRepositorioLocal` | **Keep**, demoted to a last-known **cache** (`data/local`, file + `tfg_prefs`), no authority role |
| (b) `avatarUrl` | **Keep** as a legacy field with no producer and no reader; never used to resolve |
| (c) Size cap | Hard cap `MAX_BASE64_CHARS = 700_000` on the encoded string (~67% of the 1 MiB doc limit) |
| (d) Interface signatures | `suspend fun subirAvatar(Uri): Result<Timestamp>` + `suspend fun obtenerAvatar(uid, Timestamp?): Bitmap?` |
| (e) Hint encoding | `com.google.firebase.Timestamp.now()` captured once per upload, written to both docs in one `WriteBatch` |
| (f) Display + caching | Repository returns a decoded `Bitmap?`; render with **Glide-on-Bitmap**; in-memory `LruCache` keyed `(uid, seconds:nanos)` + `tfg_prefs`/file last-known |
| (g) Firebase impl class | **Repurpose** `AvatarRepositorioFirebase` (no new class) |

## Architecture Decisions

### Decision: One contract, two operations

**Choice**: `AvatarRepositorio` exposes exactly the upload and the read, in `repositorio/`:

```kotlin
suspend fun subirAvatar(imageUri: Uri): Result<Timestamp>
suspend fun obtenerAvatar(uid: String, avatarUpdatedAt: Timestamp?): Bitmap?
```

**Alternatives considered**:
- Returning the base64 `String` from the read and decoding in the UI (rejected: moves decode/cache to
  three call sites and onto the main thread — the spec requires decode/cache off the main thread).
- `Result<Bitmap?>` for the read (rejected: `Result.success(null)` and `Result.failure` are treated
  identically by every display site — `null` → `R.drawable.perfil` — so the extra wrapping buys nothing).
- Accepting `Long` epoch millis instead of `Timestamp` (rejected: forces a `Timestamp.toDate().time`
  conversion at three call sites and truncates sub-millisecond precision for no benefit).
- A `Flow<Bitmap?>` reactive read (rejected: no snapshot listener exists on `avatares`; the hint already
  drives invalidation, and an extra listener per member multiplies free-tier reads).

**Rationale**: `Result<T>` for the mutating operation matches the existing convention
(`AuthRepositorio`, `TareaRepositorio`, `RepositorioDisputas` all return `Result<T>`). A nullable return
for the read is the honest shape: `null` means "nothing to show", which is exactly the display-site
contract, and it collapses network error, missing blob, and absent avatar into the one outcome the UI
cares about. The `Timestamp?` parameter matches `Usuario.avatarUpdatedAt`'s type, so callers pass the
model field directly.

### Decision: Repurpose `AvatarRepositorioFirebase` instead of writing a new class

**Choice**: Rewrite `data/firebase/AvatarRepositorioFirebase.kt` in place as the Firestore base64
implementation of `AvatarRepositorio`.

**Alternatives considered**:
- New `data/firebase/AvatarRepositorioFirestore.kt` + delete the old class (rejected: churns a class
  the spec deltas reference by name and path — `firestore-contracts` "previously
  `AvatarRepositorioFirebase.kt:49,61-65`" and `implementation-recipes` TD-3 — and forces the archive
  step to reconcile a "deleted class" narrative for zero gain).
- Keep the Storage class and add a parallel Firestore class (rejected: reintroduces exactly the dual
  authority this change exists to kill).

**Rationale**: The rewrite is total — every Storage reference (`storage.reference.child`,
`StorageMetadata`, `putBytes`, `downloadUrl`, `StorageException`, the `usuarios.avatarUrl` writes) is
removed, so there is no risk of stale Storage code surviving. A class with zero external callers and a
contract-shaped replacement is best kept under its registered name.

### Decision: `AvatarRepositorioLocal` is demoted to the last-known cache, not deleted

**Choice**: `data/local/AvatarRepositorioLocal.kt` stays at its path and class name, but becomes a
bytes-only cache: `guardarUltimoConocido(uid, jpegBytes)`, `obtenerUltimoConocido(uid): ByteArray?`, and
`limpiar(uid)`. It keeps writing `filesDir/avatars/$uid.jpg` and the `tfg_prefs` key
`avatar_path_$uid`. Its authority methods (`subirAvatar`, `obtenerAvatarPathActual`,
`eliminarAvatarActual`, `determinarExtension`) are removed.

**Alternatives considered**:
- Delete the class and rely on memory + placeholder offline (rejected: the `architecture-map` delta
  explicitly keeps `data/local` as "Local last-known avatar cache under `filesDir/avatars/` + the
  `tfg_prefs` SharedPreferences namespace; no avatar authority role", and the `avatar-management` spec
  marks an offline last-known avatar as SHOULD).
- Reuse it as the authority under a Firebase-delegating wrapper (rejected: keeps two writers alive and
  contradicts "single authority").

**Rationale**: Retaining the `avatar_path_$uid` key and the `filesDir/avatars/` layout makes pre-existing
local-only avatars readable with no migration, satisfying the "Legacy local-only avatar" scenario as
best-effort display. Reusing the exact key also gives the archive evidence in the `data/local` row
literal accuracy.

### Decision: Keep `Usuario.avatarUrl` as an inert legacy field

**Choice**: Leave `avatarUrl: String?` in `modelo/Usuario.kt` unchanged, with a comment marking it
legacy. Add `avatarUpdatedAt: Timestamp? = null` immediately after it. Neither mapper populates
`avatarUrl`.

**Alternatives considered**:
- Delete `avatarUrl` (rejected: the `firestore-contracts` delta records the field as a retained legacy
  row with "no producer"; deletion is not required by any requirement and touches eight construction
  sites for zero behavioral gain).
- Repurpose `avatarUrl` as the hint (rejected upstream in the proposal: overloads a name that means
  "Storage download URL" and keeps a misleading field alive semantically even if reused).

**Rationale**: All eight `Usuario(...)` constructions use named arguments (verified across
`AuthRepositorioFirebase`, `AuthRepositorioInMemory`, `VistaModeloAuth`, `FragmentPgPrincipal`), so
inserting a field is positionally safe. Post-change, `grep -r "avatarUrl" app/src/main/java` must match
only the model declaration — a clean, auditable signal that the legacy reference is dead.

### Decision: Hint encoding is a client `Timestamp.now()`, written atomically with the blob

**Choice**: Capture `val ahora = com.google.firebase.Timestamp.now()` once per upload and write it to
**both** documents in a single `WriteBatch`:

```kotlin
batch.set(avatares/{uid}, mapOf("base64" to b64, "contentType" to "image/jpeg", "updatedAt" to ahora))
batch.set(usuarios/{uid}, mapOf("avatarUpdatedAt" to ahora), SetOptions.merge())
batch.commit()
```

**Alternatives considered**:
- `FieldValue.serverTimestamp()` (rejected: a locally-pending snapshot reports the sentinel as absent
  until the write is acknowledged, so the uploader's own avatar would flicker to the placeholder after
  every upload; client time avoids this and is immediately visible offline-queued).
- Two sequential writes (rejected: a failure between them would leave a blob with no hint — exactly the
  invisible-avatar failure this change removes).
- Server-time-driven hint via a Cloud Function (rejected: no Cloud Functions in this project, and
  deployment is out of scope).

**Rationale**: The batch makes the "failed upload MUST NOT change `avatarUpdatedAt`" scenario structural
rather than best-effort: either both documents change or neither does. Human-paced uploads make a
`Timestamp.now()` collision (same millisecond) effectively impossible, and the cache key uses
`seconds:nanoseconds` so even a same-millisecond collision cannot serve a stale bitmap. `SetOptions.merge()`
on `usuarios/{uid}` tolerates a missing document without needing a create branch.

### Decision: Hard cap on the encoded payload at 700,000 characters

**Choice**: `MAX_BASE64_CHARS = 700_000`, checked on the base64 string **before** any write. The primary
size control is the downscale (`LADO_LARGO_PX = 256`, JPEG `CALIDAD_JPEG = 75`); the cap is a defensive
backstop.

**Alternatives considered**:
- Cap on raw bytes instead of the encoded string (rejected: the spec requires the rejection decision on
  the encoded payload; checking the encoded size is the thing that actually bounds the document).
- A tighter cap such as 256 KB (rejected as needlessly aggressive: no observed payload approaches it,
  and a future 512px bump would need a cap change).
- Relying on the Firestore 1 MiB write rejection (rejected: fails opaquely at commit time and leaves no
  user-facing message).

**Rationale**: Firestore caps a document at 1,048,576 bytes, and base64 inflates raw bytes by ~4/3.
700,000 ASCII characters ≈ 700 KB = ~67% of the limit, leaving ~348 KB of headroom for field names,
`contentType`, `updatedAt`, and document overhead. A 256×256 JPEG at quality 75 is typically 8–20 KB
(~11–27 KB encoded), so the cap sits roughly 26–60× above the expected payload: it only fires on a
pathological input (downscale failure), never on a normal photo.

### Decision: Image helpers live in `util/AvatarImagen.kt` and use `java.util.Base64`

**Choice**: A new `object AvatarImagen` under `util/` owns the constants and the transforms:

```kotlin
object AvatarImagen {
    const val LADO_LARGO_PX = 256
    const val CALIDAD_JPEG = 75
    const val MAX_BASE64_CHARS = 700_000
    const val CONTENT_TYPE_JPEG = "image/jpeg"

    fun comprimirJpeg(resolver: ContentResolver, uri: Uri,
                      ladoLargo: Int = LADO_LARGO_PX, calidad: Int = CALIDAD_JPEG): ByteArray?
    fun codificarBase64(bytes: ByteArray): String
    fun decodificarBase64(base64: String): ByteArray?
    fun decodificarJpeg(bytes: ByteArray): Bitmap?
}
```

Compression is a **two-pass** decode: `BitmapFactory.Options.inJustDecodeBounds = true` to read the
dimensions, then a power-of-two `inSampleSize` so the decoded long edge is ≥ 256, then
`Bitmap.createScaledBitmap` to exactly 256 on the long edge, then
`bitmap.compress(JPEG, 75, ...)`.

**Alternatives considered**:
- Helpers as private members of `AvatarRepositorioFirebase` (rejected: hides a pure, JVM-testable
  function inside an Android/Firestore class).
- `android.util.Base64` (rejected: stubbed to throw in plain JVM unit tests, requiring Robolectric).
- Single-pass `readBytes()` then decode (rejected: a 50 MP photo would OOM before downscaling; this is
  also what the retired `AvatarRepositorioLocal` did).

**Rationale**: `java.util.Base64` is available at `minSdk 28` (API 26+) and is byte-identical for
standard base64 with no line wrapping, while making `codificarBase64`/`decodificarBase64` pure-JVM and
unit-testable without Robolectric. Extracting the object keeps the repository to Firestore + cache
orchestration. This is a deliberate, documented refinement of the proposal's loose "Android `Base64`"
dependency note.

### Decision: Render with Glide-on-Bitmap; the repository owns decode + caching

**Choice**: The repository returns a `Bitmap?`; display sites render it with
`Glide.with(ctx).load(bitmap).circleCrop().into(imageView)` and fall back to
`Glide.clear(iv)` + `iv.setImageResource(R.drawable.perfil)` when `null`.

**Alternatives considered**:
- `iv.setImageBitmap(bitmap)` directly (rejected: loses Glide's `circleCrop()` — the current visual
  contract — and needs a hand-rolled circular/bitmap-drawable path).
- A Glide custom `ModelLoader` for the base64/`avatares` doc (rejected: Glide decodes from a stream or
  file, not a Firestore document; the repo-side cache would still be needed for the no-reread scenario).
- Keeping Glide-on-`File` and only swapping the file source (rejected: the blob now lives in Firestore,
  not on disk).

**Rationale**: Glide can load a `Bitmap` model natively, so this keeps the exact rendering behavior
(`circleCrop`, `placeholder`, lifecycle-aware `clear`) with minimal churn while the heavy work — Firestore
read, base64 decode, bitmap construction — happens inside the repository on `Dispatchers.IO`. Glide's own
bitmap cache and the repository's `LruCache` serve different purposes (render vs. no-re-remote-read), so
the double cache is intentional, not redundant.

### Decision: Cache structure — in-memory `LruCache` in the repository + `tfg_prefs`/file last-known

**Choice**:

| Tier | Where | Key | Written | Read |
|---|---|---|---|---|
| In-memory | `LruCache<String, Bitmap>` inside `AvatarRepositorioFirebase` (process-scoped via the locator singleton) | `"$uid#${hint.seconds}:${hint.nanoseconds}"` | on a successful remote read **and** on a successful upload (seeded so the uploader's own re-resolution is instant) | first, whenever the hint is non-null; a hit performs **no** Firestore read |
| Last-known (persistent) | `AvatarRepositorioLocal` → `filesDir/avatars/$uid.jpg`, path recorded in `tfg_prefs` key `avatar_path_$uid` | `uid` | on a successful remote read and on a successful upload | only when the remote read fails (offline) or when the hint is `null` (legacy best-effort) |

`LruCache` is sized by count (32 entries) — 32 × ~256px ARGB_8888 ≈ 8 MB worst case, comfortably inside a
typical heap budget and far below the per-app limit.

**Alternatives considered**:
- A "latest bitmap per uid" map instead of a hint-keyed map (rejected: it cannot express
  `(uid, hint1)` vs `(uid, hint2)`, so the invalidation scenario becomes untestable and stale bitmaps
  survive a version change).
- Persisting the last-known copy as base64 in `tfg_prefs` (rejected: base64 in SharedPreferences inflates
  the XML and burns memory; the file + path layout already exists and is legacy-compatible).
- No in-memory cache (rejected: violates the "cache hit avoids a repeat read" scenario and re-reads
  `avatares/{uid}` on every rebind).

**Rationale**: The hint is the version key, so keying memory on `(uid, hint)` makes invalidation
automatic — a changed hint simply misses. The persistent tier is deliberately hint-agnostic ("last known")
because its job is offline degradation and legacy display, not currency. `tfg_prefs` remains a cache and is
never consulted to decide whether a remote avatar exists; the hint decides that.

### Decision: Context reaches the repository through `TFGApplication.appContext`, not a new `FirebaseComposition` accessor

**Choice**: `LocalizadorServicios.repositorioAvatar` supplies the Android `Context` from
`TFGApplication.appContext` (already the established precedent at `TareaRepositorioFirebase.kt:538`), and
the Firestore/Auth clients from `FirebaseComposition`:

```kotlin
val repositorioAvatar: AvatarRepositorio by lazy {
    FirebaseComposition.requireContext()
    val contexto = requireNotNull(TFGApplication.appContext) { "TFGApplication.appContext no inicializado" }
    AvatarRepositorioFirebase(
        firestore = FirebaseComposition.firestore(),
        auth = FirebaseComposition.auth(),
        context = contexto
    )
}
```

**Alternatives considered**:
- Add `Fun applicationContext()` / store the `Application` in `FirebaseComposition` (rejected: touches the
  prior change's composition contract for a need it was never scoped to serve — the `architecture-map`
  requirement only mandates that the **Firestore client** arrive through the composition boundary).
- Have `AvatarViewModel` pass `applicationContext` into the locator (rejected: an `object` with `lazy`
  accessors has no injection point, and the spec forbids the ViewModel from building the repository).
- Drop `Context` entirely by reading `Uri` bytes elsewhere (rejected: upload needs `ContentResolver` and the
  cache needs `filesDir`).

**Rationale**: `TFGApplication.appContext` is set in `onCreate()` before any repository can be touched and
is already an accepted seam. `FirebaseComposition` stays untouched, shrinking the blast radius and keeping
rollback to a single-file revert set.

### Decision: `AvatarViewModel` resolves through the locator with an explicit `(Application)` constructor

**Choice**:

```kotlin
class AvatarViewModel(
    application: Application,
    private val repositorioAvatar: AvatarRepositorio = LocalizadorServicios.repositorioAvatar,
    private val repositorioAuth: AuthRepositorio = LocalizadorServicios.repositorioAuth
) : AndroidViewModel(application) {

    /** Keeps the androidx AndroidViewModelFactory's single-Application lookup working. */
    constructor(application: Application) : this(application)
    ...
}
```

**Alternatives considered**:
- `repositorioAvatar: AvatarRepositorio = LocalizadorServicios.repositorioAvatar` without the secondary
  constructor (rejected: Kotlin only synthesises a no-arg constructor when *all* parameters have defaults;
  `application` does not, so `by viewModels()` would fail at runtime).
- A custom `ViewModelProvider.Factory` (rejected: more machinery than the project uses anywhere else).

**Rationale**: This mirrors `ParejaViewModel`'s proven pattern exactly (`constructor(application)` delegating
with explicit defaults), keeps `AvatarViewModel` constructible by `by viewModels()` in `FragmentPerfil`, and
gives tests a seam to pass fakes.

## Data Flow

### Upload (self-only)

    FragmentPerfil (picker)
        │  avatarVM.subirAvatar(uri)          ├─ immediate local preview via Glide-on-Uri (unchanged UX)
        ▼
    AvatarViewModel ──→ LocalizadorServicios.repositorioAvatar ──→ AvatarRepositorioFirebase
                                                                       │
        auth.currentUser?.uid  ─── none ──→ Result.failure (no write) ─┘
        AvatarImagen.comprimirJpeg(resolver, uri)  → 256px JPEG bytes
        AvatarImagen.codificarBase64(bytes)        → String
        base64.length > 700_000 ?  ── yes ──→ Result.failure(size limit)  (no write)
                                   └─ no ──→ WriteBatch
                                              ├─ avatares/{uid}: {base64, contentType, updatedAt=ahora}
                                              └─ usuarios/{uid}: {avatarUpdatedAt=ahora}   (merge)
                                            commit()
                                              ├─ seed LruCache[(uid, ahora)] = Bitmap
                                              └─ AvatarRepositorioLocal.guardarUltimoConocido(uid, jpeg)
                                             → Result.success(ahora)
        ▼
    AvatarViewModel re-resolves obtenerAvatar(uid, ahora)  → instant LruCache hit → avatarActual emits

### Read / resolve (any member)

    FragmentPgPrincipal adapter / MainActivity drawer / FragmentPerfil
        │  (uid, usuario.avatarUpdatedAt)   ← hint flows in from AuthRepositorioFirebase.observarUsuarios()
        ▼
    AvatarRepositorioFirebase.obtenerAvatar(uid, hint)   [Dispatchers.IO]

        hint != null ──→ LruCache["uid#s:n"] hit ? ── yes ─→ return Bitmap  (NO Firestore read)
                                    │ no
                                    ▼
                       firestore.avatares/{uid}.get()
                          base64 present ─→ decode → LruCache.put + cacheLocal.guardar → return Bitmap
                          base64 absent  ─→ cacheLocal (last-known) or null
                          read throws    ─→ cacheLocal (last-known) or null        ← offline path

        hint == null  ──→ NO Firestore read; cacheLocal (legacy last-known) or null

        null ──→ caller renders R.drawable.perfil

### Member-card recycling guard

    onBindViewHolder(holder, pos)
        ├─ holder.ivAvatar.tag = u.id
        ├─ holder.ivAvatar.setImageResource(R.drawable.perfil)      ← reset for a recycled holder
        └─ viewLifecycleOwner.lifecycleScope.launch {
               val bmp = repo.obtenerAvatar(u.id, u.avatarUpdatedAt)
               if (holder.ivAvatar.tag == u.id && bmp != null)      ← guard: holder still bound to u
                   Glide.with(holder.itemView.context).load(bmp).circleCrop().into(holder.ivAvatar)
           }

## File Changes

| File | Action | Description |
|---|---|---|
| `app/src/main/java/com/example/tfg/repositorio/AvatarRepositorio.kt` | Create | The canonical contract: `subirAvatar(Uri): Result<Timestamp>`, `obtenerAvatar(uid, Timestamp?): Bitmap?`. No delete operation. |
| `app/src/main/java/com/example/tfg/util/AvatarImagen.kt` | Create | `object` with `LADO_LARGO_PX`, `CALIDAD_JPEG`, `MAX_BASE64_CHARS`, `CONTENT_TYPE_JPEG`, `comprimirJpeg`, `codificarBase64`, `decodificarBase64`, `decodificarJpeg`. Two-pass decode + downscale. |
| `app/src/main/java/com/example/tfg/data/firebase/AvatarRepositorioFirebase.kt` | Modify (full rewrite) | Firestore base64 implementation. Storage entirely removed. Constructor takes `firestore`, `auth`, `context`, `cacheLocal`. Owns the `LruCache` and the hint write. |
| `app/src/main/java/com/example/tfg/data/local/AvatarRepositorioLocal.kt` | Modify | Demoted to last-known cache: `guardarUltimoConocido`, `obtenerUltimoConocido`, `limpiar`. Keeps `filesDir/avatars/$uid.jpg` + `tfg_prefs.avatar_path_$uid`. Authority methods deleted. |
| `app/src/main/java/com/example/tfg/viewmodel/AvatarViewModel.kt` | Modify | Resolve both repos via the locator; drop the `AvatarRepositorioLocal` import/construction. `avatarUrlActual: StateFlow<String?>` → `avatarActual: StateFlow<Bitmap?>`; `avatarState` → `Result<Unit>?`. `eliminarAvatar` retained as an unwired state-clear. |
| `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` | Modify | Add `repositorioAvatar: AvatarRepositorio by lazy { ... }` wiring `FirebaseComposition.firestore()/auth()` + `TFGApplication.appContext`. |
| `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt` | Modify | Surface `avatarUpdatedAt` at the mapper sites (login `:125-136`, loginConTokenProveedor `:194-205`, observarUsuarios `:404-412`; the `usuarioActual()` cache branch `:375-383` preserves it). |
| `app/src/main/java/com/example/tfg/modelo/Usuario.kt` | Modify | Add `avatarUpdatedAt: Timestamp? = null`; mark `avatarUrl` legacy. |
| `app/src/main/java/com/example/tfg/vista/MainActivity.kt` | Modify | Drawer (`:451-509`): resolve via the repo with `usuario.avatarUpdatedAt`, add an `avatarDrawerJob` cancel-guard, Glide-on-Bitmap, placeholder fallback. |
| `app/src/main/java/com/example/tfg/vista/FragmentPgPrincipal.kt` | Modify | Member cards (`:357-391`): delete the `avatar_prefs` read, resolve per member with a recycling `tag` guard. Optional `MiembrosHorizontalAdapter` extraction left as a follow-up. |
| `app/src/main/java/com/example/tfg/vista/FragmentPerfil.kt` | Modify | Collect `avatarVM.avatarActual` (`Bitmap?`) at `:190-205`; Glide-on-Bitmap + placeholder. Picker preview `:49-62` unchanged. |
| `firestore.rules` | Modify | Add the `avatares/{uid}` block (signed-in read, self-only write) and add `avatares` to the header comment. |

### Explicitly unchanged (recorded so apply does not drift)

| File | Why |
|---|---|
| `service/firebase/FirebaseComposition.kt` | The context decision avoids touching it. |
| `data/inmemory/AuthRepositorioInMemory.kt` | Not a Firestore mapper; the new field defaults to `null`. |
| `storage.rules` | Storage avatar-path pruning is deferred to the rules/indexes change (proposal scope). |
| `tools/firebase/emulator-verify.mjs:169-181` | The Storage avatar exercise becomes stale tooling; pruning is out of scope (see Open Questions). |
| Any layout XML | `ivMiembroAvatar`, `ivDrawerAvatar`, `ivAvatarPerfil` and `R.drawable.perfil` are reused as-is. |

## Interfaces / Contracts

### `repositorio/AvatarRepositorio.kt`

```kotlin
package com.example.tfg.repositorio

import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.Timestamp

interface AvatarRepositorio {

    /**
     * Uploads [imageUri] for the SIGNED-IN user only. Compresses client-side, base64-encodes,
     * enforces the size cap, then atomically writes `avatares/{uid}` and the
     * `usuarios/{uid}.avatarUpdatedAt` hint.
     *
     * @return the new version marker (also written as the hint) on success; `Result.failure`
     *         with no partial write when there is no session, the image cannot be decoded, or
     *         the encoded payload exceeds `AvatarImagen.MAX_BASE64_CHARS`.
     */
    suspend fun subirAvatar(imageUri: Uri): Result<Timestamp>

    /**
     * Resolves the avatar bitmap for ANY uid (current user or group member).
     *
     * Returns `null` when there is nothing to show, so callers render `R.drawable.perfil`.
     * Performs NO Firestore read when [avatarUpdatedAt] is null.
     * Never throws: a remote failure degrades to the local last-known avatar or `null`.
     */
    suspend fun obtenerAvatar(uid: String, avatarUpdatedAt: Timestamp?): Bitmap?
}
```

### `data/firebase/AvatarRepositorioFirebase.kt` (shape)

```kotlin
class AvatarRepositorioFirebase(
    private val firestore: FirebaseFirestore = FirebaseComposition.firestore(),
    private val auth: FirebaseAuth = FirebaseComposition.auth(),
    private val context: Context,
    private val cacheLocal: AvatarRepositorioLocal = AvatarRepositorioLocal(context)
) : AvatarRepositorio {

    private val coleccion = "avatares"
    private val cacheMemoria = LruCache<String, Bitmap>(CACHE_ENTRADAS)   // 32

    override suspend fun subirAvatar(imageUri: Uri): Result<Timestamp> = withContext(Dispatchers.IO) { ... }
    override suspend fun obtenerAvatar(uid: String, avatarUpdatedAt: Timestamp?): Bitmap? =
        withContext(Dispatchers.IO) { ... }

    private fun claveCache(uid: String, hint: Timestamp): String =
        "$uid#${hint.seconds}:${hint.nanoseconds}"
}
```

### `data/local/AvatarRepositorioLocal.kt` (shape)

```kotlin
class AvatarRepositorioLocal(private val context: Context) {

    private val prefs = context.getSharedPreferences("tfg_prefs", Context.MODE_PRIVATE)

    /** Persists compressed JPEG bytes as the last-known avatar for [uid]; replaces any prior file. */
    fun guardarUltimoConocido(uid: String, jpegBytes: ByteArray): Boolean

    /** Reads the last-known compressed JPEG bytes, or null when nothing is cached. */
    fun obtenerUltimoConocido(uid: String): ByteArray?

    /** Clears the cached copy for [uid]. Does NOT touch the remote document. */
    fun limpiar(uid: String)

    private fun clave(uid: String): String = "avatar_path_$uid"   // retained legacy key
}
```

### `modelo/Usuario.kt` (changed region)

```kotlin
val avatarUrl: String? = null,          // Legacy: no producer/reader after teamtask-avatar-authority
val avatarUpdatedAt: Timestamp? = null, // Avatar hint: existence flag + cache version key
val fechaCreacion: Timestamp? = null
```

### `firestore.rules` (added block, after `usuarios`)

```
    // avatares/{uid}: avatar bytes as base64 (fields base64, contentType, updatedAt).
    // Readable by any signed-in user (group members render each other's avatars);
    // writable only by the owner.
    match /avatares/{userId} {
      allow read: if signedIn();
      allow write: if isSelf(userId);
    }
```

### `viewmodel/AvatarViewModel.kt` (public surface)

```kotlin
val avatarState: StateFlow<Result<Unit>?>      // upload outcome (success/failure message)
val avatarActual: StateFlow<Bitmap?>           // currently displayed avatar, null => placeholder
val cargando: StateFlow<Boolean>
fun subirAvatar(imageUri: Uri)
fun cargarAvatarActual()
fun eliminarAvatar()                            // unwired state-clear only; no remote delete exists
fun resetAvatarState()
```

## Testing Strategy

Reality check: the only tests in the repository are `ExampleUnitTest.kt` and
`ExampleInstrumentedTest.kt`. There is no avatar test net and `strict_tdd: false` in `openspec/config.yaml`.
The primary evidence for this change is therefore the **manual matrix** below plus grep audits; one focused
JVM unit test is cheap and worth adding because `AvatarImagen`'s base64 helpers are pure JVM.

| Layer | What to Test | Approach |
|---|---|---|
| Unit (JVM) | `AvatarImagen.codificarBase64` / `decodificarBase64` round-trip; that an oversized base64 string is rejected by the `length > MAX_BASE64_CHARS` gate | New `app/src/test/java/com/example/tfg/util/AvatarImagenTest.kt` (JUnit 4). `java.util.Base64` keeps this Robolectric-free. |
| Unit (JVM) | Hint gating (`null` hint ⇒ no Firestore read) and cache-key invalidation | Not feasible without a Firestore seam; covered by the manual matrix instead. Do not claim coverage. |
| Integration | Emulator round-trip against `firestore.rules` | Manual: `firebase emulators:exec` / `tools/firebase/emulator-verify.mjs`, then the rules checks below. |
| E2E | Three display sites | Manual on the emulator build. |

### Compile gate (required)

```
.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain
```

### Grep audits (required)

| Audit | Command (PowerShell) | Expected |
|---|---|---|
| `avatar_prefs` retired | `Get-ChildItem -Recurse app\src\main | Select-String "avatar_prefs"` | Zero matches |
| No Storage on the avatar path | `Get-ChildItem -Recurse app\src\main\java\com\example\tfg\data\firebase | Select-String "FirebaseStorage\|putBytes\|downloadUrl\|StorageMetadata"` | Zero matches in `AvatarRepositorioFirebase.kt` (only `AuthRepositorioFirebase.kt` cleanup may remain) |
| No direct avatar construction outside the boundary | `Get-ChildItem -Recurse app\src\main\java | Select-String "AvatarRepositorioLocal(\|AvatarRepositorioFirebase("` | Matches only `service\LocalizadorServicios.kt`, `data\local\AvatarRepositorioLocal.kt`, `data\firebase\AvatarRepositorioFirebase.kt` |
| ViewModel does not build a repository | `Get-ChildItem app\src\main\java\com\example\tfg\viewmodel\AvatarViewModel.kt | Select-String "data.local\|data.firebase"` | Zero matches |
| `avatarUrl` has no producer/reader | `Get-ChildItem -Recurse app\src\main\java | Select-String "avatarUrl"` | Only `modelo\Usuario.kt` |
| No delete affordance wired | `Get-ChildItem -Recurse app\src\main | Select-String "eliminarAvatar"` | Only `AvatarViewModel.kt` (declaration + KDoc), no caller |

### Manual matrix (maps to spec scenarios)

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

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. This change touches Android UI rendering and Firestore documents only.

## Migration / Rollout

- **No server migration.** No Cloud Function, no Storage migration, no rules deployment — the
  `firestore.rules` edit is a local file only.
- **No data migration.** `avatares/{uid}` is a new collection populated on the next upload. Existing
  `usuarios.avatarUrl` values are abandoned in place (no producer, no reader).
- **Legacy local avatars**: the `tfg_prefs.avatar_path_$uid` key and the `filesDir/avatars/` layout are
  retained, so pre-existing local-only avatars still render as last-known where the hint is absent. No
  batch migration is performed or required (per the "Legacy local-only avatar" scenario).
- **Rollout order** (one change, committed directly to `master`): contract + helpers + implementation +
  rules + mappers + model, then the three display sites, then the ViewModel/locator wiring. The change is
  not independently feature-flaggable; the compile gate is the gate.
- **Rollback boundary** (per the proposal, additive, no Console action):
  1. Revert the application files listed above and restore the local-authority wiring, so avatars are
     local-only again. The `avatares` collection may remain as inert data.
  2. Remove the `avatares/{uid}` block from `firestore.rules`.
  3. Revert the `Usuario` hint field **and** the mapper changes together; reverting one half reintroduces
     the current silent-drop gap.
  4. Revert the `AvatarRepositorio` interface + `AvatarRepositorioFirebase` + `AvatarRepositorioLocal` +
     `LocalizadorServicios` + `AvatarViewModel` as a single unit.
  5. Do not touch unrelated worktree changes or any other SDD change.

## Risks

| Risk | Likelihood | Mitigation (design-level) |
|---|---|---|
| 1 MiB document limit exceeded | Low | Hard cap at 700,000 encoded chars **before** any write, on top of the mandatory 256px/q75 downscale; the cap is ~26–60× the expected payload |
| Free-tier read pressure from `avatares` reads | Med | Hint gating (no read when the hint is absent), hint-keyed `LruCache` (no re-read on repeat render), on-demand fetch only |
| Offline degradation after the authority moves | Med | `tfg_prefs`/file last-known cache + `R.drawable.perfil`, and the hint-keyed memory cache survives a transient network blip |
| RecyclerView recycling race leaves the wrong avatar | Med | `holder.ivAvatar.tag = u.id` guard plus a placeholder reset on every bind; `viewLifecycleOwner.lifecycleScope` cancels in-flight loads |
| Read-mapper gap silently keeps avatars invisible | Med | All mapper sites change together with the model; the "absent hint maps to null" and "live stream carries the hint" scenarios are in the manual matrix |
| Base64 decode cost / main-thread jank | Low | All decode/cache work happens inside the repository on `Dispatchers.IO`; Glide only renders the finished `Bitmap` |
| Hint is set but the blob is missing or unreadable | Low | The read path treats "base64 absent" as no avatar and falls back to last-known/placeholder; no crash path |
| `firestore.rules` not deployed, so production differs | High | Accepted and out of scope; the rule is local-only and production parity stays `[UNVERIFIED]`. Runtime behavior does not depend on the rule |
| `firestore.rules:30` lets any signed-in user update any `usuarios` doc | Low | The hint is not a security boundary; the blob write is self-only in the new rule, and a forged hint without a blob degrades to the placeholder |
| Legacy local-only avatars never appear remotely | Low | Documented best-effort; no migration required by the spec |

## Open Questions

- [ ] `tools/firebase/emulator-verify.mjs:169-181` still exercises the Storage avatar path, which becomes
  stale after this change. The proposal defers Storage pruning to the rules/indexes change, so this design
  leaves the script untouched — confirm at archive that the stale check is acceptable until then.
- [ ] Should `MiembrosHorizontalAdapter` be extracted out of `FragmentPgPrincipal` (the file is 393 lines
  and the fragment is already heading toward a god-object shape)? Not required by any spec requirement;
  recorded as future convergence work, not a task of this change.
