## Exploration: TeamTask avatar authority and preference namespaces

Capture of the completed exploration for `teamtask-avatar-authority` (convergence roadmap
step 3, `docs/architecture/TEAMTASK_GUIDE.md` §7; debt items TD-2 and TD-3, §6). Read-only
findings only; no application code was changed during exploration.

### Product decisions (fixed upstream, not re-opened here)

1. Avatars MUST live in Firebase and MUST be visible to the users of the group.
2. No paid Firebase Storage: Cloud Storage for Firebase requires the Blaze plan (billing
   account) since October 2024, with enforcement from February 2026. `[UNVERIFIED]` console
   and billing state. The free path is Firestore on the Spark tier (1 GiB storage,
   50K reads/day, no card). `[UNVERIFIED]` quota figures.
3. Target approach (from the product decision): compressed avatar bytes stored as base64 in a
   dedicated Firestore collection (`avatares/{uid}`), with a strict client-side size/quality cap.

### Current State (verified in the repository)

**Live authority**

- `AvatarRepositorioLocal` is the only wired avatar implementation. It stores files under
  `filesDir/avatars/$uid.ext` and records the absolute path in SharedPreferences namespace
  `tfg_prefs` under key `avatar_path_$uid`
  (`data/local/AvatarRepositorioLocal.kt:11,23-34,86`).
- There is **no** `AvatarRepositorio` interface. `AvatarViewModel` constructs the local
  repository directly, bypassing `LocalizadorServicios`
  (`viewmodel/AvatarViewModel.kt:14-16`).
- `AvatarViewModel.avatarUrlActual` holds a local filesystem **path**, not a URL
  (`viewmodel/AvatarViewModel.kt:24-25,41-42,62-63`).

**Dead implementation (TD-3)**

- `AvatarRepositorioFirebase` uploads to Storage at `avatares/$uid/UUID.ext`, reads the
  download URL, and writes `usuarios.avatarUrl` (`data/firebase/AvatarRepositorioFirebase.kt:49,61-71,163-165`).
  It has **zero callers** in `app/src/main/java`.
- `tools/firebase/emulator-verify.mjs:169-181` still exercises the Storage path
  `avatares/{uid}/...` independently of the app.
- The class constructs its own SDK clients instead of receiving them, and account cleanup
  reads `FirebaseStorage` directly (`data/firebase/AvatarRepositorioFirebase.kt:17-19`;
  `data/firebase/AuthRepositorioFirebase.kt:347-352`).

**Display sites (three consumers, three different sources)**

| Site | What it reads today | Evidence |
|---|---|---|
| Drawer header (current user only) | `tfg_prefs` key `avatar_path_$uid`, loads a `File` via Glide | `vista/MainActivity.kt:451-509` (read at `:484-492`) |
| Dashboard member cards (any group member) | namespace `avatar_prefs` key `avatar_$uid`, loads a `File` via Glide | `vista/FragmentPgPrincipal.kt:357-391` (read at `:376-388`) |
| Profile screen (current user) | picker at `:49-62`; display via Glide on a String path at `:190-205` | `vista/FragmentPerfil.kt:49-62,190-205` |
| `FragmentPareja` member list | text initials only, no image | `vista/FragmentPareja.kt:596,615` |

- `avatar_prefs` has a **reader with no writer**: `FragmentPgPrincipal.kt:377-378` reads
  `avatar_$uid`, but nothing writes that namespace, so member cards always fall back to the
  placeholder. This is the TD-2 namespace split.

**Read-mapper gap (blocks group-visible avatars)**

- `usuarios.avatarUrl` exists in the model and contract (`modelo/Usuario.kt:18`;
  `openspec/specs/firestore-contracts/spec.md:58`) but **no mapper reads it**. All four
  `AuthRepositorioFirebase` mapping sites drop it:
  `:125-136` (`login`), `:194-205` (`loginConTokenProveedor`), `:375-383` (`usuarioActual`),
  `:404-412` (`observarUsuarios`).
- `observarUsuarios()` already streams the **entire** `usuarios` collection to consumers
  (`AuthRepositorioFirebase.kt:386-422`); consumers such as `FragmentPgPrincipal.kt:110-158`
  and `MainActivity.kt:441` collect it. A small remote hint field would therefore flow to
  every consumer if the mapper surfaced it.

**Rules (local files, not deployed)**

- `firestore.rules:27-32` — `usuarios`: `read` any signed-in, `update` any signed-in
  (wide), `create`/`delete` self only. There is **no** `avatares` collection rule yet;
  `firestore.rules:91-93` denies anything outside the documented contract.
- `storage.rules:34-41` — `avatares/{uid}/...`: owner write, signed-in read. This becomes
  unused if avatars move to Firestore.

### Technical risks and constraints

- Firestore documents are limited to 1 MiB; base64 inflates payloads by roughly 33%, so any
  avatar bytes stored as base64 MUST be compressed client-side (downscale + JPEG quality cap)
  with a hard rejection threshold well below the limit.
- `observarUsuarios()` re-transmits the full `usuarios` document on every snapshot, so avatar
  blobs MUST NOT live in `usuarios` documents; a separate `avatares/{uid}` collection is
  required to keep the widely-streamed collection small.
- Glide cannot render base64 strings directly; the Firebase-backed path must decode to a
  `Bitmap` (or use a custom model) and cache it, replacing the current Glide-on-`File` display.
- There is no delete-avatar affordance anywhere in the UI; `AvatarViewModel.eliminarAvatar` has
  no caller (`viewmodel/AvatarViewModel.kt:74-94`). Avatar deletion is therefore out of scope.
- `firestore.rules:30` allows **any** signed-in user to `update` **any** `usuarios` document,
  so a hint field on `usuarios` is not itself a security boundary; the blob write must be
  self-only in the new `avatares/{uid}` rule.
- Offline behavior: the local path currently works offline. Moving authority to Firestore
  requires a cached last-known avatar so the UI degrades gracefully without network.
- Migration of existing local avatars to Firestore is best-effort at most; most existing local
  avatars are on a developer device and there is no batch migration path.

### Affected Areas

- `data/local/AvatarRepositorioLocal.kt` — demote from authority to optional local cache, or
  replace.
- `data/firebase/AvatarRepositorioFirebase.kt` — dead code; repurpose into the Firestore
  base64 implementation or delete.
- `viewmodel/AvatarViewModel.kt` — stop instantiating a repository directly; depend on the
  canonical contract.
- `vista/MainActivity.kt:451-509`, `vista/FragmentPgPrincipal.kt:357-391`,
  `vista/FragmentPerfil.kt:49-62,190-205` — display sites for member/current-user avatars.
- `data/firebase/AuthRepositorioFirebase.kt:125-136,194-205,375-383,404-412` — mappers must
  surface the avatar hint.
- `modelo/Usuario.kt:18` — avatar field/hint semantics.
- `service/LocalizadorServicios.kt:12-27`, `service/firebase/FirebaseComposition.kt:109-112`
  — canonical wiring of a Firestore-backed avatar repository.
- `firestore.rules`, `storage.rules` — new `avatares/{uid}` collection rule; prune the now
  unused Storage path (deferred).

### Ready for Proposal

Yes. Product intent and the free-tier technical constraint are settled, the live/dead
implementations and all three display sites are mapped with evidence, and the blocker (the
read-mapper gap plus the writer-less `avatar_prefs` reader) is identified. The proposal must
decide the pointer/hint strategy, define the canonical repository contract, bound avatar
deletion and Storage out of scope, and resolve the preference namespaces.

**Status:** success

**Artifacts:** Engram `sdd/teamtask-avatar-authority/explore` |
`openspec/changes/teamtask-avatar-authority/exploration.md`

**Next recommended:** `sdd-propose`
