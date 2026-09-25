# Exploration: teamtask-account-deletion-security

## Source

- Engram observation `#167` — topic `sdd/teamtask-account-deletion-security/explore`, project `TFG-TeamTask`
  (captured 2026-09-25). This file is a concise capture of that observation, re-verified against the
  current source tree during this proposal phase.
- Roadmap step 7 (`docs/architecture/TEAMTASK_GUIDE.md` §7) and debt item TD-13
  (`docs/architecture/TEAMTASK_GUIDE.md` §6; `openspec/specs/implementation-recipes/spec.md` TD-13 row).
- Canonical contracts: `openspec/specs/firestore-contracts/spec.md` (cleanup evidence, collection contracts).

> Line-number note: the raw exploration and the specs cite `AuthRepositorioFirebase.kt` cleanup at
> `:259-357` / `:261-359`. The current file has shifted; the **observed** lines are cited below.
> This drift is cosmetic (the `teamtask-avatar-authority` change added imports/fields) but any delta
> spec MUST use the current lines.

## Current deletion flow (client)

1. `FragmentPerfil.configurarEliminacionCuenta()` opens a confirmation dialog requiring the literal
   text `ELIMINAR` plus the account password — `vista/FragmentPerfil.kt:208-275`.
2. `VistaModeloAuth.eliminarCuentaActual(password)` delegates to the repository —
   `viewmodel/VistaModeloAuth.kt:102-120`.
3. `AuthRepositorioFirebase.eliminarCuentaActual(password)` —
   `data/firebase/AuthRepositorioFirebase.kt:225-263`:
   - re-authenticate with email/password credential (`:231-243`);
   - call `limpiarDatosAsociados(uid)` (`:246`);
   - call `usuarioActual.delete()` on Firebase Auth (`:249`);
   - clear cache, `signOut()` (`:252-253`).
4. On success the Fragment clears the local `tfg_prefs` `grupoId` and navigates to login clearing the
   back stack — `vista/FragmentPerfil.kt:283-314`.
5. The informational page `EliminacionCuentaActivity` only renders `android_asset/eliminacion-cuenta.html`
   — `vista/EliminacionCuentaActivity.kt:10-31` (policy/info only; not part of the mutation flow).

## What `limpiarDatosAsociados` deletes today (best effort)

`data/firebase/AuthRepositorioFirebase.kt:265-363`, helper `borrarDocumentosPorCampo` at `:365-374`.
Every block is wrapped in `try/catch` that **only logs** (`Log.w`), so the caller always sees success.

| Target | Evidence | Current behavior |
|---|---|---|
| `usuarios/{uid}` | `:267-271` | deleted |
| `grupos` membership | `:274-295` | remove uid; delete group doc **only if empty** (`:290-294`) |
| `tareas` created by user | `:301-308` | deleted (cascade: partner loses those tasks) |
| `tareas` assigned to user | `:310-327` | unassigned (`asignadoA=null`, reset state fields) |
| `invitaciones` `creadoPor=uid` | `:330` | deleted |
| `notificaciones` `destinatario=uid` | `:333` | deleted |
| `notificaciones` `contenido.desde=uid` | `:336` | deleted |
| `recompensas` `creadoPor=uid` | `:339` | deleted |
| `canjes` `usuarioUid=uid` | `:342` | deleted |
| `disputas` `iniciador=uid` + Storage evidence | `:344-362` | evidence deleted then dispute doc deleted |
| `avatares/{uid}` | — | **NOT deleted → always orphaned** |

## Gaps / TD-13 ("Account cleanup is best effort")

1. **Silent best effort.** All per-step failures are swallowed (`Log.w`) and never surfaced; the
   user always sees "cuenta eliminada" even when steps failed (`:269-362`).
2. **`avatares/{uid}` orphan.** The avatar Firestore doc added by `teamtask-avatar-authority` is
   never removed by the cleanup; `firestore.rules:37-40` already allows `isSelf` write, so the owner
   can delete it while authenticated.
3. **Order risk.** Cleanup runs before `usuarioActual.delete()` (`:246` then `:249`). If Auth deletion
   fails (e.g. `FirebaseAuthRecentLoginRequiredException`, `:256-258`), the account survives while its
   data was already deleted.
4. **New semantics not implemented.** TD-13 / owner decisions require group dissolution and a
   remaining-member reset; today the code only removes the uid and keeps the group with the remaining
   member and their balances untouched.
5. **No retry / no completion signal.** There is no retry and no structured result from cleanup.

## Rules evidence (local, committed)

| Rule | Evidence | Relevance |
|---|---|---|
| `avatares/{userId}` write = `isSelf` | `firestore.rules:37-40` | self-delete of own avatar is allowed |
| `usuarios/{userId}` delete = `isSelf` | `firestore.rules:27-32` | self profile delete allowed |
| `grupos` delete = any signed-in | `firestore.rules:43-48` | deleting client can dissolve the group |
| `usuarios` update = any signed-in | `firestore.rules:27-32` | deleting client can clear the remaining member's `grupoId` |
| `tareas`, `recompensas`, `canjes`, `disputas`, `notificaciones`, `invitaciones` delete = signed-in | `firestore.rules:50-96` | cleanup deletes allowed client-side |

Production/Console parity of these rules remains `[UNVERIFIED]`; no Cloud Function exists in the repo
for server-side cleanup (`[UNVERIFIED]`, per `firestore-contracts` spec).

## Group / local-state mechanics (for decisions 2-4)

- `ParejaViewModel` persists the group id in `tfg_prefs` key `grupoId`
  (`viewmodel/ParejaViewModel.kt:39-40,70-81`).
- It observes the group doc; when the doc disappears the observer emits `null` and the ViewModel
  **clears the local `tfg_prefs` `grupoId`** — `viewmodel/ParejaViewModel.kt:83-105` (clear at `:91-98`).
  This is the reactive path by which a remaining member's device loses its local group reference.
- `RepositorioPareja.quitarMiembroGrupo` already nulls the leaving user's `usuarios.grupoId` inside a
  transaction and deletes the group when the last member leaves —
  `repositorio/RepositorioPareja.kt:231-263`; `limpiarGrupoIdUsuario` at `:291-302`.
- Balance fields (`Usuario.puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias`) —
  `modelo/Usuario.kt:13-16`.
- Group membership map (`miembros: Map<uid,rol>`) — `modelo/Grupo.kt:5-12`.

## Owner decisions (FIXED — captured from the exploration)

1. **Tasks created by the deleted user: DELETED** (current behavior kept). Cascade must be documented:
   the partner loses those tasks.
2. **When one member remains: the GROUP IS DISSOLVED.** The remaining member ends up without a group:
   delete the `grupos` document and clear the remaining member's `grupoId` both in Firestore
   (`usuarios/{remainingUid}.grupoId = null`) and in the local `tfg_prefs`.
3. **The remaining member's balances are RESET to zero**: `puntos`, `puntosReservados`,
   `puntosRecompensa`. `rachaDias` — **recommendation**: reset it too for a coherent clean slate
   (flagged as a decision to confirm, not silently applied).
4. **Derived decision to confirm (flagged)**: the dissolved group's remaining tasks are deleted too,
   for a coherent clean slate.

## Out of scope (from the change brief)

Google Sign-In deletion specifics, Storage redesign (avatars are Firestore since
`teamtask-avatar-authority`), account-deletion scheduling / server-side functions, TD-7, avatars and
navigation changes, and any redesign of the re-auth dialog UX or `EliminacionCuentaActivity` info page.

## Executor self-check

- Evidence is anchored to the current source tree (`file:line`).
- No application code, Gradle, canonical spec, or git state was modified by this exploration artifact.
- Console-only claims are marked `[UNVERIFIED]`.
