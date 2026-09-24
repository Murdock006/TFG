# Delta for avatar-management

## Purpose and current observable state

This is a NEW capability. It defines the canonical avatar domain for the existing XML,
Fragment, ViewBinding, mixed MVVM/service-locator architecture: a single `AvatarRepositorio`
contract, Firebase Firestore base64 storage, the upload flow, the member-resolution read
flow, the avatar hint that flows through the `usuarios` mappers, an in-memory cache with an
offline last-known fallback, and the preference-namespace rules.

Today there is no single authority. `AvatarViewModel` constructs `AvatarRepositorioLocal`
directly, bypassing `LocalizadorServicios` (`viewmodel/AvatarViewModel.kt:14-16`), and the
local repository stores files under `filesDir/avatars/` and records a path in the
SharedPreferences namespace `tfg_prefs` (`data/local/AvatarRepositorioLocal.kt:11,23-34,86`).
`AvatarRepositorioFirebase` is dead code with zero callers in `app/src/main/java`
(`data/firebase/AvatarRepositorioFirebase.kt:1-174`). The dashboard member cards read a
writer-less second namespace, `avatar_prefs` (`vista/FragmentPgPrincipal.kt:377-378`), so
member cards always fall back to the placeholder. `usuarios.avatarUrl` exists in the model
and contract (`modelo/Usuario.kt:18`; `openspec/specs/firestore-contracts/spec.md`) but no
mapper reads it, so the value is silently dropped at all four `AuthRepositorioFirebase`
mapping sites (`data/firebase/AuthRepositorioFirebase.kt:125-136,194-205,375-383,404-412`).

Fixed upstream decisions (from `proposal.md`): Firebase is the authority; no paid Firebase
Storage; avatars are compressed base64 in `avatares/{uid}`; blobs never live in `usuarios`;
avatar deletion is out of scope. Console/billing/quota/Storage claims remain `[UNVERIFIED]`.

Grounding: `proposal.md` (New Capabilities; Resolved Decisions 1-5; Pointer/hint strategy),
`exploration.md`, and source
`viewmodel/AvatarViewModel.kt:14-16,74-94`, `data/local/AvatarRepositorioLocal.kt:11,86`,
`data/firebase/AvatarRepositorioFirebase.kt:1-174`,
`data/firebase/AuthRepositorioFirebase.kt:125-136,194-205,375-383,404-412,386-422`,
`modelo/Usuario.kt:18`, `vista/MainActivity.kt:451-509`,
`vista/FragmentPgPrincipal.kt:357-391`, `vista/FragmentPerfil.kt:49-62,190-205`,
`service/LocalizadorServicios.kt:12-27`, `service/firebase/FirebaseComposition.kt:109-112`,
and `firestore.rules`.

## ADDED Requirements

### Requirement: A single avatar contract SHALL be the only avatar authority

The system MUST define one canonical `AvatarRepositorio` interface under `repositorio/` that
covers the avatar upload and read operations, and MUST resolve it through
`LocalizadorServicios`. `AvatarViewModel` MUST depend on that interface and MUST NOT
instantiate a concrete avatar repository directly (`viewmodel/AvatarViewModel.kt:14-16`). The
Firebase-backed implementation MUST receive its Firestore client through `FirebaseComposition`
(`service/firebase/FirebaseComposition.kt:109-112`), matching the existing composition
boundary, and MUST NOT construct Firebase SDK clients itself.

#### Scenario: ViewModel resolves the avatar contract through the locator

- GIVEN the app is composed and `AvatarViewModel` is created
- WHEN it needs avatar operations
- THEN it MUST obtain the repository through `LocalizadorServicios`
- AND no direct `AvatarRepositorioLocal(...)` or `AvatarRepositorioFirebase(...)` construction MUST remain in `viewmodel/` or `vista/`

#### Scenario: Direct-construction audit

- GIVEN the change is implemented
- WHEN production code is searched for avatar repository construction outside the composition boundary
- THEN every match MUST be inside `LocalizadorServicios`, `data/`, or a test
- AND `AvatarViewModel` MUST show a constructor-injected or locator-resolved `AvatarRepositorio`, not a concrete class

### Requirement: Avatar bytes SHALL be stored in Firestore as compressed base64 under `avatares/{uid}`

Avatar bytes MUST be persisted in the dedicated Firestore collection `avatares/{uid}` with
exactly the fields `base64` (String), `contentType` (String), and `updatedAt` (Timestamp).
Avatar bytes MUST NOT be stored in Firebase Storage and MUST NOT be written into `usuarios`
documents, because `observarUsuarios()` re-transmits the full `usuarios` document on every
snapshot (`data/firebase/AuthRepositorioFirebase.kt:386-422`) and the widely-streamed
collection MUST stay small. The chosen backend MUST NOT require the paid Blaze plan.
`[UNVERIFIED]` Firebase Console billing, quota, and deployment state.

On a successful upload the system MUST also make the new avatar version discoverable to
consumers by setting the signed-in user's `usuarios/{uid}.avatarUpdatedAt` hint to a value
that changes with each upload, so the hint can act as the cache-invalidation key. The exact
hint encoding is a design refinement point; the observable requirement is that the hint
changes when the blob changes and is absent when no avatar exists.

#### Scenario: Successful upload writes the documented fields

- GIVEN an authenticated user uploads an allowed image
- WHEN the upload completes successfully
- THEN a document `avatares/{uid}` MUST exist with a non-empty `base64` string, a `contentType` string, and an `updatedAt` timestamp
- AND the corresponding `usuarios/{uid}` document MUST NOT contain the avatar bytes
- AND `usuarios/{uid}.avatarUpdatedAt` MUST be set to a value reflecting the new avatar version

#### Scenario: Hint changes only when the blob changes

- GIVEN a user has an existing avatar and `usuarios/{uid}.avatarUpdatedAt` is set
- WHEN a new avatar is uploaded
- THEN `avatarUpdatedAt` MUST change so cached `(uid, oldHint)` entries are invalidated
- AND a failed upload MUST NOT change `avatarUpdatedAt`

#### Scenario: Storage is not used for avatars

- GIVEN the avatar implementation is reviewed
- WHEN production code is searched for `FirebaseStorage` usage on the avatar path
- THEN no avatar implementation MUST read from or write to Firebase Storage
- AND the only avatar-side effect MUST be the `avatares/{uid}` Firestore write

### Requirement: Avatar uploads SHALL compress client-side and enforce a hard size cap

The upload path MUST read the picked image, downscale it to roughly 256px on the long edge,
encode it as JPEG, and base64-encode the result before writing. The encoded payload MUST be
rejected before writing when it exceeds a hard cap chosen well below the Firestore 1 MiB
document limit, because base64 inflates payload size by roughly 33%. A rejected upload MUST
produce a failure result that the UI can surface, and MUST NOT write a partial document.

#### Scenario: Oversized input is rejected

- GIVEN a picked image that would encode above the hard cap
- WHEN the upload runs
- THEN it MUST return a failure result naming the size/quality limit
- AND no `avatares/{uid}` document MUST be written or updated

#### Scenario: Normal input is downscaled and encoded

- GIVEN a picked image larger than 256px on its long edge
- WHEN the upload runs
- THEN the stored `base64` payload MUST be the compressed JPEG, not the original bytes
- AND the stored `contentType` MUST be `image/jpeg`

### Requirement: Avatar uploads SHALL be self-only

The upload operation MUST resolve the target uid from the authenticated session and MUST
write only `avatares/{uid}` for that same user. The upload operation MUST NOT expose a way to
target another user's document. An upload attempted with no signed-in user MUST fail without
writing.

#### Scenario: Authenticated user writes only their own document

- GIVEN an authenticated user with uid `uA`
- WHEN the upload runs
- THEN it MUST write `avatares/uA`
- AND it MUST NOT write `avatares/` for any other uid

#### Scenario: Unauthenticated upload fails

- GIVEN no signed-in user
- WHEN the upload runs
- THEN it MUST return a failure result
- AND no `avatares` document MUST be written

### Requirement: Avatar reads SHALL resolve any group member at all three display sites

The read operation MUST resolve the avatar for any uid — the current user or any group member
— by reading `avatares/{uid}` and decoding `base64` to a `Bitmap`. All three display sites
MUST use this path: the drawer header (`vista/MainActivity.kt:451-509`), the dashboard member
cards (`vista/FragmentPgPrincipal.kt:357-391`), and the profile screen
(`vista/FragmentPerfil.kt:49-62,190-205`). A uid with no `avatares/{uid}` document MUST
render the placeholder drawable `R.drawable.perfil`.

#### Scenario: A group member's avatar renders on a member card

- GIVEN `avatares/uB` exists for a group member `uB` and the current user is `uA`
- WHEN the dashboard member card for `uB` is bound
- THEN it MUST render the avatar decoded from `avatares/uB`
- AND it MUST NOT render the placeholder while a valid document exists

#### Scenario: Missing avatar falls back to the placeholder

- GIVEN no `avatares/{uid}` document exists for a member
- WHEN that member's avatar is rendered at any display site
- THEN `R.drawable.perfil` MUST be shown

### Requirement: The avatar hint SHALL be surfaced by all four `usuarios` mappers

The model MUST carry a distinct lightweight hint field `avatarUpdatedAt: Timestamp?` on
`modelo/Usuario.kt`, separate from the legacy `avatarUrl` field. All four
`AuthRepositorioFirebase` mapping sites MUST surface the hint
(`data/firebase/AuthRepositorioFirebase.kt:125-136,194-205,375-383,404-412`) so that every
consumer already streaming `usuarios` receives it. Consumers MUST treat the hint as an
existence flag and cache version key, NOT as a blob location or download URL.

#### Scenario: Live `usuarios` stream carries the hint

- GIVEN `usuarios/uB` has a non-null `avatarUpdatedAt`
- WHEN `AuthRepositorioFirebase.observarUsuarios()` emits its list
- THEN the mapped `Usuario` for `uB` MUST expose the same `avatarUpdatedAt` value
- AND consumers reading the stream MUST see the hint without an extra read

#### Scenario: Login mapping carries the hint

- GIVEN the signed-in user's document has a non-null `avatarUpdatedAt`
- WHEN `login` or `loginConTokenProveedor` maps the document to `Usuario`
- THEN the returned `Usuario.avatarUpdatedAt` MUST be populated
- AND `usuarioActual()` MUST NOT drop the hint from the cached user

#### Scenario: Absent hint maps to null

- GIVEN `usuarios/{uid}` has no `avatarUpdatedAt` field
- WHEN any of the four mappers builds the `Usuario`
- THEN `avatarUpdatedAt` MUST be `null`
- AND the member MUST be treated as having no avatar for read purposes

### Requirement: Avatar resolution SHALL cache in memory and fall back to the last-known avatar offline

Avatar resolution MUST use an in-memory cache keyed by `(uid, avatarUpdatedAt)`. When the hint
is null or absent, the system MUST NOT perform a Firestore read for that member. When the hint
value changes, the cached entry MUST be invalidated and the blob re-read on demand. Decode and
cache work MUST run off the main thread. When Firestore is unreachable, the system SHOULD
render the cached last-known avatar and MUST fall back to `R.drawable.perfil` when no cached
avatar exists.

#### Scenario: Cache hit avoids a repeat read

- GIVEN the avatar for `(uB, hint1)` is already cached
- WHEN `uB`'s avatar is requested again and the hint is still `hint1`
- THEN no Firestore read for `avatares/uB` MUST occur
- AND the cached bitmap MUST be rendered

#### Scenario: Hint change invalidates the cache

- GIVEN the avatar for `(uB, hint1)` is cached
- WHEN `uB.avatarUpdatedAt` changes to `hint2`
- THEN the `(uB, hint1)` entry MUST NOT be served
- AND `avatares/uB` MUST be re-read on demand and the new result cached

#### Scenario: Offline degradation preserves a last-known avatar

- GIVEN a last-known avatar for `uB` is present in the local cache and Firestore is unreachable
- WHEN `uB`'s avatar is rendered
- THEN the last-known avatar SHOULD be shown
- AND if no cached avatar exists, `R.drawable.perfil` MUST be shown

### Requirement: Preference namespaces SHALL be unified

The `avatar_prefs` SharedPreferences namespace MUST be retired: no code MUST read or write
`avatar_prefs`. The `tfg_prefs` namespace MUST be used only as a local cache of the
last-known avatar, never as the avatar authority.

#### Scenario: No `avatar_prefs` reader or writer remains

- GIVEN the change is implemented
- WHEN production code is searched for `avatar_prefs`
- THEN there MUST be zero matches

#### Scenario: `tfg_prefs` is cache-only

- GIVEN an avatar was previously resolved or uploaded on this device
- WHEN the app runs offline
- THEN `tfg_prefs`-backed storage MAY supply the last-known avatar
- AND a `tfg_prefs` hit MUST NOT be treated as proof that a remote avatar exists or is current

#### Scenario: Legacy local-only avatar

- GIVEN a device has a pre-existing local-only avatar from the retired authority
- WHEN the app renders that user's avatar
- THEN best-effort display from the local cache is acceptable
- AND no batch migration of legacy local avatars MUST be required by this change

### Requirement: `firestore.rules` SHALL grant read to signed-in users and write to self only for `avatares/{uid}`

The local `firestore.rules` file MUST add a `avatares/{uid}` match: `read` allowed for any
signed-in user, `write` allowed only when `request.auth.uid == uid` (`isSelf(uid)`). The rule
is a local file only; this change MUST NOT deploy rules or require any Firebase Console
change. `[UNVERIFIED]` deployed production parity and Console state. The avatar hint remains
non-authoritative because `firestore.rules:30` already lets any signed-in user update any
`usuarios` document; the blob write MUST be self-only.

#### Scenario: Owner can write their own avatar document

- GIVEN a signed-in user with uid `uA`
- WHEN the client writes `avatares/uA`
- THEN the rule MUST allow the write

#### Scenario: Another signed-in user cannot write

- GIVEN a signed-in user with uid `uA`
- WHEN the client attempts to write `avatares/uB`
- THEN the rule MUST deny the write
- AND a signed-in user reading `avatares/uB` MUST still be allowed

### Requirement: Avatar deletion SHALL remain out of scope

This change MUST NOT add or expose an avatar delete/removal affordance.
`AvatarViewModel.eliminarAvatar` (`viewmodel/AvatarViewModel.kt:74-94`) has no caller and MUST
NOT be wired to UI by this change.

#### Scenario: No delete affordance is added

- GIVEN the change is implemented
- WHEN the UI is inspected for avatar removal
- THEN no user-facing control MUST call an avatar delete operation
- AND `eliminarAvatar` MUST remain uncalled by production UI
