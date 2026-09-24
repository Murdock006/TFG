# Proposal: TeamTask Avatar Authority Convergence

## Intent

TeamTask has two avatar implementations and no single authority. A live local-file repository
(`AvatarRepositorioLocal`, namespace `tfg_prefs`) is wired directly by `AvatarViewModel`, while a
Firebase Storage repository (`AvatarRepositorioFirebase`) is dead code with zero callers, and the
dashboard member cards read a second namespace (`avatar_prefs`) that nothing ever writes
(`FragmentPgPrincipal.kt:377-378`). The result is that avatars are local-only and non-sharable: a
user cannot see another group member's avatar, and the model field meant to carry that information
(`usuarios.avatarUrl`) is dropped by every mapper.

This change is convergence roadmap step 3 (`docs/architecture/TEAMTASK_GUIDE.md` §7) and resolves
debt items TD-2 (avatar authority split) and TD-3 (dead Firebase avatar repository) from §6. The
fixed product requirement is that avatars MUST live in Firebase and be visible to the users of the
group. The fixed constraint is that no paid Firebase Storage may be used, so avatars are stored as
compressed base64 bytes in a dedicated Firestore collection.

## Resolved Decisions

These are fixed upstream and MUST NOT be re-opened during spec, design, or apply.

1. **Firebase is the authority.** Avatars MUST be persisted in Firebase and MUST be resolvable by
   every member of a group, not only by the uploader.
2. **No paid Storage.** Cloud Storage for Firebase requires the Blaze billing plan; this project
   stays on the free tier. `[UNVERIFIED]` console/billing state and quota figures.
3. **Firestore base64 storage.** Avatar bytes are compressed client-side, encoded as base64, and
   stored in a dedicated collection `avatares/{uid}` with fields `base64`, `contentType`, and
   `updatedAt`. A strict client-side cap (downscale to roughly 256px on the long edge, JPEG, and a
   hard rejection threshold well below the 1 MiB document limit) MUST be enforced.
4. **Blobs never live in `usuarios`.** `observarUsuarios()` re-transmits the full `usuarios`
   document on every snapshot (`AuthRepositorioFirebase.kt:386-422`), so avatar bytes MUST NOT be
   added to `usuarios` documents.
5. **Avatar delete affordance is out of scope.** No UI calls `AvatarViewModel.eliminarAvatar`
   (`AvatarViewModel.kt:74-94`), so no delete flow is implemented in this change.

### Pointer / hint strategy (decision)

**Decision:** store the avatar bytes only in `avatares/{uid}`, and add a **distinct lightweight
hint field** to `usuarios` (recommended: `avatarUpdatedAt: Timestamp?`, surfaced through the model
and the four mappers) that tells consumers whether an avatar exists and doubles as a cache
invalidation key. Consumer reads then fetch the blob on demand from `avatares/{uid}` and cache it
in memory keyed by `(uid, avatarUpdatedAt)`.

Trade-offs considered:

| Option | Pros | Cons |
|---|---|---|
| Reuse `usuarios.avatarUrl` as the reference | No new model field; the field already exists (`Usuario.kt:18`) | Overloads a name that historically meant a Storage download URL; invites confusion between a URL, a version marker, and a hint; keeps a misleading field alive |
| Distinct hint field (recommended) | Clear semantics; enables cache invalidation; a few bytes on a widely-streamed doc; separates "exists/versioned" from the blob | Adds a field to the model plus the mapper gap fix |
| Load-on-demand with no hint | Zero metadata; simplest model | One Firestore read per group member per render even when nobody has an avatar; wastes free-tier reads and adds latency |

The distinct-hint option is recommended because it avoids unnecessary reads for members without
avatars and keeps `usuarios` documents small while giving the client a correct cache key. The
exact field name and null/default semantics are refinement points for the spec/design phases.

## Scope

### In Scope

- Introduce a single canonical avatar contract (`AvatarRepositorio` interface under `repositorio/`
  with a Firebase-backed implementation under `data/firebase/`), wired through
  `LocalizadorServicios` / `FirebaseComposition` instead of being instantiated inside
  `AvatarViewModel` (`AvatarViewModel.kt:14-16`).
- Firebase-backed upload: read the picked image, compress/downscale, base64-encode, enforce the
  size cap, and write `avatares/{uid}` for the current user only.
- Firebase-backed read for **any** group member at all three display sites: drawer
  (`MainActivity.kt:451-509`), dashboard member cards (`FragmentPgPrincipal.kt:357-391`), and
  profile (`FragmentPerfil.kt:49-62,190-205`). Replace Glide-on-`File` with base64-to-`Bitmap`
  decoding plus caching.
- Fix the read-mapper gap so the avatar hint is surfaced by `AuthRepositorioFirebase` at all four
  mapping sites (`:125-136,194-205,375-383,404-412`).
- Unify preference namespaces: retire the write-dead `avatar_prefs` reader
  (`FragmentPgPrincipal.kt:377-378`); keep `tfg_prefs` only as a local **cache** (last-known
  avatar), never as the authority.
- Delete or repurpose the dead `AvatarRepositorioFirebase` (TD-3) and remove the local
  repository's authority role (TD-2), preserving sane offline behavior via a cached last-known
  avatar and the existing placeholder drawable.
- Add a local `firestore.rules` rule for `avatares/{uid}` (read: signed-in users / group scope;
  write: self only). Local rules file only; no deployment.

### Out of Scope

- Avatar delete/removal affordance (no UI caller today).
- Any use of Firebase Storage; the `storage.rules` avatar path becomes unused and is deferred to
  the rules/indexes change for pruning.
- Account deletion and cleanup behavior (TD-13).
- Navigation, lifecycle, listener, or auto-login refactors (TD-4/TD-5/TD-6).
- TD-7 (`resolverReclamo` split writes).
- Deploying rules, indexes, App Check, or any Firebase Console change.
- Editing canonical `openspec/specs/**` (this change's deltas do that at archive time).
- Batch migration of pre-existing on-device local avatars beyond best effort.

## Capabilities

### New Capabilities

- `avatar-management`: the canonical avatar domain contract — repository interface, Firebase
  Firestore base64 storage format and size discipline, upload flow, member-resolution read flow,
  hint/pointer semantics, in-memory cache, offline last-known fallback, and preference-namespace
  rules.

### Modified Capabilities

- `firestore-contracts`: add the `avatares/{uid}` collection contract (writer, reader, fields
  `base64`/`contentType`/`updatedAt`), record consumption of the `usuarios` avatar hint, and state
  the new collection's access rule; prune the now-unused Storage avatar-path claim.
- `architecture-map`: record the single avatar authority wired through `LocalizadorServicios`,
  remove the avatar service-locator bypass exception, and update the convergence entry for TD-2/TD-3.
- `implementation-recipes`: update the TD-2 and TD-3 rows to resolved, and add the avatar storage
  and preference-namespace convention and its manual verification step.

## Approach

1. **Contract.** Add an `AvatarRepositorio` interface in `repositorio/` with suspend operations for
   upload (self) and fetch (any uid), returning `Result`/cached values consistent with existing
   repository conventions. The interface depends only on Firestore; no Storage.
2. **Implementation.** Repurpose `AvatarRepositorioFirebase` into the Firestore base64
   implementation (or replace it): compress → base64 → `avatares/{uid}` write, and read → decode →
   cache for any uid. It receives its Firestore client through `FirebaseComposition`, matching the
   composition boundary introduced by `teamtask-testing-emulator-strategy`.
3. **Wiring.** Expose the canonical repository through `LocalizadorServicios` and inject it into
   `AvatarViewModel`, removing the direct `AvatarRepositorioLocal(...)` construction.
4. **Hint propagation.** Extend `Usuario` with the hint field (recommended `avatarUpdatedAt`) and
   read it in the four `AuthRepositorioFirebase` mappers so every consumer already streaming
   `usuarios` (`FragmentPgPrincipal.kt:110-158`, `MainActivity.kt:441`) receives it.
5. **Display.** At the three display sites, resolve the avatar through the canonical repository
   using the member's uid and hint, decode base64 to `Bitmap`, and cache; fall back to the cached
   last-known avatar, then to `R.drawable.perfil`.
6. **Namespaces.** Remove the `avatar_prefs` read in `FragmentPgPrincipal` (replacing it with the
   canonical read path) and keep `tfg_prefs` only as a cache store.
7. **Rules.** Add the `avatares/{uid}` match to `firestore.rules` with self-only writes and
   signed-in reads; leave `storage.rules` untouched (deferred pruning).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `repositorio/AvatarRepositorio.kt` | New | Canonical avatar contract (interface) |
| `data/firebase/AvatarRepositorioFirebase.kt` | Modified/Removed | Repurpose into Firestore base64 impl, or delete (TD-3) |
| `data/local/AvatarRepositorioLocal.kt` | Modified/Removed | Demote to cache or remove authority role (TD-2) |
| `viewmodel/AvatarViewModel.kt` | Modified | Depend on the canonical contract via the locator; drop direct instantiation |
| `service/LocalizadorServicios.kt` | Modified | Wire the avatar repository through `FirebaseComposition` |
| `data/firebase/AuthRepositorioFirebase.kt` | Modified | Surface the avatar hint at the four mapper sites |
| `modelo/Usuario.kt` | Modified | Add the avatar hint field |
| `vista/MainActivity.kt` | Modified | Drawer avatar via canonical repository (current user) |
| `vista/FragmentPgPrincipal.kt` | Modified | Member-card avatars via canonical repository; retire `avatar_prefs` |
| `vista/FragmentPerfil.kt` | Modified | Profile avatar via base64 decode and caching |
| `firestore.rules` | Modified | Add `avatares/{uid}` rule (self write, signed-in read) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Firestore 1 MiB document limit exceeded (base64 +33%) | Med | Strict client-side downscale (≈256px, JPEG) and a hard rejection threshold well below the limit |
| Free-tier read/bandwidth quota pressure from `avatares` reads | Med | Hint field avoids reads for members without avatars; in-memory cache keyed by `(uid, updatedAt)`; on-demand fetch only |
| Offline degradation after moving authority to Firestore | Med | Cached last-known avatar in `tfg_prefs`/files plus the existing placeholder drawable |
| Read-mapper gap silently keeps avatars invisible | Med | Explicit requirement that all four mapper sites surface the hint; manual verification matrix |
| Base64 decode cost / main-thread jank | Med | Decode and cache off the main thread; reuse a bounded cache |
| Rules not deployed, so behavior differs in production | High | Local-only rules; keep `[UNVERIFIED]` on console state; runtime degrade already handled |
| `firestore.rules:30` lets any signed-in user update any `usuarios` doc | Low | Blob writes are self-only in the new `avatares/{uid}` rule; hint is not a security boundary |
| Legacy local avatars not migrated | Low | Best-effort only; document that old local-only avatars may not appear remotely |

## Rollback Plan

Rollback is additive and requires no Firebase Console action:

1. Revert this change's application files and restore the prior local-authority wiring in
   `AvatarViewModel` and the display sites, so avatars are again local-only.
2. Remove the `avatares/{uid}` match from `firestore.rules` (local file). The collection may
   remain in Firestore as inert data or be cleared manually; no server deployment is implied.
3. Keep or discard the `Usuario` hint field and mapper change together; reverting only one half
   reintroduces the current silent-drop gap.
4. Delete or restore `AvatarRepositorioFirebase` as a single unit with its interface wiring.
5. Do not touch unrelated worktree changes, and do not roll back any other SDD change.

No production data rollback, rules deployment, or Storage rollback is implied, since deployment is
out of scope and all console claims remain `[UNVERIFIED]`.

## Dependencies

- `FirebaseComposition` and `LocalizadorServicios` contract from `teamtask-testing-emulator-strategy`
  (already landed): the new repository MUST receive its Firestore client through that boundary.
- Android `Base64`, `BitmapFactory`, and image decoding for compression/rendering.
- Firebase Console access is needed only to later confirm rules/quota/billing state; all such
  claims stay `[UNVERIFIED]` in this change.

## Success Criteria

- [ ] One canonical avatar path exists behind a single contract, wired through
      `LocalizadorServicios`; `AvatarViewModel` no longer instantiates a repository directly.
- [ ] Upload writes compressed base64 bytes to `avatares/{uid}` for the current user, with the
      size/quality cap enforced and oversized inputs rejected.
- [ ] All three display sites resolve the avatar of any group member from Firebase, decoding
      base64 to a `Bitmap` with caching and a last-known/placeholder fallback.
- [ ] The avatar hint is surfaced by all four `AuthRepositorioFirebase` mapper sites.
- [ ] `avatar_prefs` is retired and `tfg_prefs` is used only as a cache, never as authority.
- [ ] Dead `AvatarRepositorioFirebase` and the local repository's authority role are resolved
      (TD-2, TD-3), with offline behavior preserved via cached last-known avatar.
- [ ] `firestore.rules` contains a local `avatares/{uid}` rule (self write, signed-in read); no
      deployment is performed.
- [ ] Avatar deletion, Storage usage, account deletion, navigation/lifecycle, and TD-7 remain
      explicitly out of scope.
