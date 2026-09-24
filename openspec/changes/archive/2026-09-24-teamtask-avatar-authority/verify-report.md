# Verify Report: TeamTask Avatar Authority Convergence

Change: `teamtask-avatar-authority` · Repo: `C:\Users\Victor\AndroidStudioProjects\TFG2` ·
Branch: `master` · Store: hybrid (file + Engram) · Verification mode: explicit, read-only.

Verification commit range: `b8e6fa0..HEAD` (feature commits `8ee3136`, `d9a9593`, `69cd7eb`,
docs commit `f4f9f7a`). Baseline: `proposal.md`, `exploration.md`, `design.md`, `tasks.md`,
`apply-progress.md`, and the four delta specs under
`openspec/changes/teamtask-avatar-authority/specs/`.

## Overall Status

**PARTIAL — code-level verification passed; runtime behavior NOT verified.**

All executable checks available in this environment passed: fresh compile of both build types,
the focused JVM unit test, and all six grep audits. The contract review against the
`avatar-management` delta found every requirement satisfied at the code level. The single
unfinished item is task 4.4 (the 12-row manual matrix), which requires a device/emulator build
plus a live Firebase emulator and is explicitly outside this run's scope. No row of that matrix
was executed and none is claimed passing.

## Artifacts Inspected

- Change docs: `proposal.md`, `exploration.md`, `design.md`, `tasks.md`, `apply-progress.md`.
- Delta specs: `specs/avatar-management/spec.md` (primary), `specs/firestore-contracts/spec.md`,
  `specs/architecture-map/spec.md`, `specs/implementation-recipes/spec.md`.
- Implementation: `repositorio/AvatarRepositorio.kt`, `util/AvatarImagen.kt`,
  `test/java/com/example/tfg/util/AvatarImagenTest.kt`,
  `data/firebase/AvatarRepositorioFirebase.kt`, `data/local/AvatarRepositorioLocal.kt`,
  `viewmodel/AvatarViewModel.kt`, `service/LocalizadorServicios.kt`,
  `data/firebase/AuthRepositorioFirebase.kt`, `modelo/Usuario.kt`,
  `vista/MainActivity.kt`, `vista/FragmentPgPrincipal.kt`, `vista/FragmentPerfil.kt`,
  `firestore.rules`, `tools/firebase/emulator-verify.mjs`.

## Executed Checks

### 1. Compile gate

Command (as specified):

```
.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain
```

Observed: `BUILD SUCCESSFUL in 11s` (exit 0). Both `compileEmulatorKotlin` and
`compileReleaseKotlin` reported `UP-TO-DATE` (incremental build cache).

Because an `UP-TO-DATE` result does not recompile the current sources, the gate was re-run
forced to obtain hard evidence:

```
.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin :app:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

Observed: `BUILD SUCCESSFUL in 39s` (exit 0), `69 actionable tasks: 69 executed`. All three
`compile{Debug,Release,Emulator}Kotlin` tasks executed and succeeded. Only pre-existing
deprecation warnings were emitted (`AuthRepositorioInMemory.kt:145`,
`FragmentRegistro.kt:46`); **no unresolved references, no warnings on any avatar file.**

### 2. JVM test gate

Command (exact task name confirmed as `:app:testDebugUnitTest`):

```
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Observed (incremental): `BUILD SUCCESSFUL in 8s` (exit 0), `testDebugUnitTest UP-TO-DATE`.

Forced re-execution via the combined command above: `:app:testDebugUnitTest` executed,
`BUILD SUCCESSFUL`. Result XML observed
(`app/build/test-results/testDebugUnitTest/`):

| Test class | tests | failures | errors | skipped |
|---|---|---|---|---|
| `com.example.tfg.ExampleUnitTest` | 1 | 0 | 0 | 0 |
| `com.example.tfg.util.AvatarImagenTest` | 3 | 0 | 0 | 0 |

Executed test count = 4, failures = 0. Matches the expected `AvatarImagenTest` 3 +
`ExampleUnitTest` 1.

### 3. Grep audits

Tooling note: `ripgrep` is **not installed**; audits were run with `git grep` (case-sensitive)
against `app/src/main`.

| # | Audit | Command | Observed |
|---|---|---|---|
| A1 | `avatar_prefs` retired | `git grep -n -- "avatar_prefs" -- app/src/main` | **ZERO MATCHES** |
| A2 | No Storage on the avatar path | `git grep -nE "FirebaseStorage\|putBytes\|downloadUrl\|StorageMetadata" -- app/src/main/java/com/example/tfg/data/firebase` | Only `AuthRepositorioFirebase.kt:9,21` (allowed account-cleanup). **Zero in `AvatarRepositorioFirebase.kt`.** |
| A3 | No direct avatar repo construction outside the boundary | `git grep -nE "AvatarRepositorioLocal\(\|AvatarRepositorioFirebase\(" -- app/src/main/java` | Exactly the allowed set: `service/LocalizadorServicios.kt:34`, `data/firebase/AvatarRepositorioFirebase.kt:28,32`, `data/local/AvatarRepositorioLocal.kt:16` |
| A4 | No delete affordance wired to UI | `git grep -n -- "eliminarAvatar" -- app/src/main` | Only `viewmodel/AvatarViewModel.kt:101` (KDoc), `:104` (declaration). **No caller.** |
| A5 | `avatarUrl` producer/reader audit | `git grep -ni -- "avatarUrl" -- app/src/main/java` | Only `modelo/Usuario.kt:18` (legacy declaration). **No producer or reader.** |
| A6 | ViewModel does not build a repository | `git grep -nE "data\.local\|data\.firebase" -- app/src/main/java/com/example/tfg/viewmodel/AvatarViewModel.kt` | **ZERO MATCHES** |

### 4. Test-count honesty note

`_usuarioCache` in `AvatarRepositorioFirebase` is process-scoped (the repository is a
`LocalizadorServicios` `by lazy` singleton); the memory `LruCache(32)` and the hint-keyed
invalidation are therefore shared across all display sites within a process. This is a static
observation, not a runtime result.

## Contract Review vs. `avatar-management` Delta

| Requirement | Status | Evidence |
|---|---|---|
| Single avatar contract is the only authority | Satisfied (static) | `repositorio/AvatarRepositorio.kt` defines exactly `subirAvatar`/`obtenerAvatar`; `LocalizadorServicios.kt:31-39` resolves it; `AvatarViewModel.kt:19-20` depends on the interface; audits A3/A6 pass |
| Bytes in Firestore base64 under `avatares/{uid}` | Satisfied (static) | `AvatarRepositorioFirebase.kt:59-66` writes `{base64, contentType, updatedAt}`; no blob in `usuarios`; no Storage (audit A2) |
| Compression + hard size cap | Satisfied (static, partially automated) | `AvatarImagen.comprimirJpeg` two-pass 256px/JPEG-75; `MAX_BASE64_CHARS = 700_000`; gate at `AvatarRepositorioFirebase.kt:48-55` before any write; base64 round-trip + cap predicate covered by `AvatarImagenTest` (3 passing) |
| Uploads self-only | Satisfied (static) | `AvatarRepositorioFirebase.kt:41-42` resolves uid from `auth.currentUser`, fails with no session; writes only `avatares/{uid}` |
| Reads resolve any member at all three sites | Satisfied (static) | `MainActivity.kt:487-499` (drawer), `FragmentPgPrincipal.kt:376-383` (member cards, `tag` recycling guard), `FragmentPerfil.kt:190-204` (profile); all fall back to `R.drawable.perfil` |
| Hint surfaced by all four mappers | Satisfied (static) | `AuthRepositorioFirebase.kt:136` (login), `:206` (loginConTokenProveedor), `:415` (observarUsuarios); `usuarioActual()` (`:376`) returns the cached `Usuario` which preserves the hint, else a fallback with the model default `null` |
| In-memory cache + offline last-known | Satisfied (static) | `AvatarRepositorioFirebase.kt:86-118`; `LruCache` keyed `(uid, seconds:nanos)`; null hint performs no Firestore read; decode/cache on `Dispatchers.IO`; last-known via `AvatarRepositorioLocal` |
| Preference namespaces unified | Satisfied (static) | Audit A1 zero `avatar_prefs`; `tfg_prefs` retained in `AvatarRepositorioLocal.kt:18` as cache only |
| `firestore.rules` `avatares/{uid}` | Satisfied (static) | `firestore.rules:37-40`: `allow read: if signedIn(); allow write: if isSelf(userId);` — local file only, no deployment |
| Avatar deletion out of scope | Satisfied (static) | Audit A4: `eliminarAvatar` declaration/KDoc only, no caller |

## Manual Matrix (task 4.4) — NOT EXECUTED

Reason: requires a device/emulator build and a live Firebase emulator, explicitly outside this
run's execution scope. **No row was executed; none is claimed passing.**

| # | Scenario | Status |
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
| 11 | ViewModel resolves the contract through the locator | NOT EXECUTED (static audits A3/A6 cover the code shape) |
| 12 | No delete affordance | NOT EXECUTED (static audit A4 covers the code shape) |

## Findings

### CRITICAL

None.

### WARNING

1. **Runtime behavior is unverified.** Firestore write/read round-trips, base64 decode of real
   uploads, hint invalidation, offline fallback, and the rules enforcement (matrix rows 1-10)
   have **no runtime evidence**. All such claims rest on source inspection only. Task 4.4 is
   open.
2. **No automated test net for the substantive behavior.** The only new automated coverage is
   `AvatarImagenTest` (3 pure-JVM cases: base64 round-trip incl. `0x00`, malformed → `null`,
   and the `length > MAX_BASE64_CHARS` predicate). It does **not** exercise `comprimirJpeg`/
   `decodificarJpeg` (they touch `ContentResolver`/`Bitmap` and throw `Stub!` off-device), the
   Firestore write/read path, the mappers, the caches, the display sites, or `firestore.rules`.
   `ExampleUnitTest` (1) is the template. Nothing here should be read as coverage of the avatar
   domain.
3. **Incremental-build caveat.** The verbatim compile and test commands both returned
   `UP-TO-DATE`. The PASS verdict rests on the forced `--rerun-tasks` run (69 tasks executed,
   `BUILD SUCCESSFUL`, test XML read); without it, the specified commands alone would not
   recompile/re-run anything.

### SUGGESTION

1. **Stale tooling (design-flagged, out of scope).** `tools/firebase/emulator-verify.mjs:169-181`
   still exercises the old **Storage** avatar path (`avatares/{uid}/probe.png`: unauth 403, own
   200, other 403) and `checkStorageRules()`. After this change the client no longer uses that
   Storage path, so this check is stale. The proposal/design defer pruning to the rules/indexes
   change; confirm at archive that leaving it is acceptable. No `storage.rules` file was touched.
2. **`tfg_prefs` namespace is shared.** The delta phrases `tfg_prefs` as a cache of the
   last-known avatar, but the namespace pre-exists and is also used for non-avatar keys
   (`ParejaViewModel.kt:39` `grupoId`, `FragmentRegistro.kt:51,114`, `MainActivity.kt:584`).
   The avatar-side use is cache-only and never authority, satisfying the requirement's intent;
   the archive wording should not be read as claiming the namespace is avatar-exclusive.
3. **`AvatarViewModel.eliminarAvatar()` remains present** as an unwired local state-clear
   (`AvatarViewModel.kt:104-106`), matching the design decision and the "deletion out of scope"
   requirement. If future convergence wants a hard signal of "no delete", the method could be
   removed; this change correctly leaves it uncalled.
4. **Design deviations recorded in `apply-progress.md` are benign.** (a) The
   `AvatarViewModel` secondary constructor was fixed to avoid the Kotlin
   "cycle in the delegation calls chain" error in the design snippet
   (`AvatarViewModel.kt:30-34`) — intent preserved, mirrors `ParejaViewModel`. (b) Tasks 2.3+2.8
   were merged into one final cache-only rewrite of `AvatarRepositorioLocal.kt`. Neither changes
   observable behavior.

## Scope Gate

Confirmed against `git diff --name-only b8e6fa0..HEAD`: only the **13 authorized edit targets**
(11 `app/src/main` files + `firestore.rules` + `AvatarImagenTest.kt`), the change's own SDD docs
(`proposal/design/exploration/tasks/apply-progress` + four delta specs), and `tasks.md` changed.
No canonical `openspec/specs/**` edit, **no `storage.rules` edit, no Gradle/build change, no
Firebase Console/deployment action.** The only uncommitted working-tree changes are pre-existing
unrelated `.idea/**` files (`.idea/compiler.xml`, `deploymentTargetSelector.xml`, `gradle.xml`,
`misc.xml`) that this verification did not touch.

## Unavailable / Unrun Checks

- Manual matrix rows 1-12 (task 4.4): device/emulator + live Firebase emulator — not available
  in this scope; deliberately not booted.
- Runtime Firestore/rules enforcement, offline behavior, cache-hit read counting, and cross-user
  write denial: require the emulator harness — not run.
- No deployment / Console verification of billing, quotas, or production rules parity:
  out of scope and remains `[UNVERIFIED]`.

## Recommended Next Work

1. Execute task 4.4 (12-row manual matrix) on the `emulator` build against the Firebase
   emulator before relying on any runtime claim.
2. At archive: apply the `firestore-contracts`, `architecture-map`, and `implementation-recipes`
   non-requirement table replacements; keep the legacy `avatarUrl` row and the stale-tooling
   deferral consistent.
3. Consider adding a Firestore-emulator-backed integration test for the upload/read/two-document
   atomicity in a later change (convergence roadmap step 5 covers focused Firestore tests).

## Limitations

Source inspection, task checkboxes, and unexecuted tests are **not** runtime proof. This report
distinguishes verified behavior (failed-free fresh compile, 4/4 JVM tests, six grep audits) from
static observations (contract review, scope gate) and unrun checks (manual matrix). No pass is
invented for anything that was not executed.
