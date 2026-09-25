# Delta for account-deletion

## Purpose and current observable state

This is a NEW capability. It defines the end-to-end account-deletion contract for the existing
XML, Fragment, ViewBinding, mixed MVVM/service-locator architecture: password re-authentication,
a complete and verifiable per-step cleanup across every associated Firestore collection plus the
`avatares/{uid}` document and dispute Storage evidence, group dissolution with a
remaining-member reset, the ordering guarantee that gates the Firebase Auth account deletion on
a complete cleanup, and explicit failure reporting.

Today deletion is silent best-effort. `FragmentPerfil` opens a confirmation dialog requiring the
literal text `ELIMINAR` plus the account password (`vista/FragmentPerfil.kt:214-275`) and calls
`VistaModeloAuth.eliminarCuentaActual(password)` (`viewmodel/VistaModeloAuth.kt:102-120`).
`AuthRepositorioFirebase.eliminarCuentaActual` re-authenticates
(`data/firebase/AuthRepositorioFirebase.kt:231-243`), calls `limpiarDatosAsociados(uid)` (`:246`),
then deletes the Auth account (`:249`). Every cleanup step is wrapped in `try/catch` that only
logs (`:265-363`), so the caller always sees success; `avatares/{uid}` is never deleted; and
cleanup runs before `usuarioActual.delete()`, so a failed Auth deletion leaves a live account
whose data was already deleted. Group handling only removes the uid and keeps the group with the
remaining member (`:274-295`).

Fixed owner decisions (from `proposal.md`): tasks created by the deleted user are deleted (with a
documented cascade); when one member remains the group is dissolved and the remaining member's
`grupoId` is cleared in Firestore and, reactively, locally; the remaining member's `puntos`,
`puntosReservados`, `puntosRecompensa`, and `rachaDias` are reset to `0`; the dissolved group's
remaining tasks are deleted; `avatares/{uid}` is deleted; cleanup is verifiable and gates the
Auth deletion; the re-authentication dialog and the `EliminacionCuentaActivity` info page are
preserved. Production/Console parity of the required cleanup writes remains `[UNVERIFIED]`.

Grounding: `proposal.md` (New Capabilities; Resolved Decisions 1-6; Approach sections 1-4),
`exploration.md`, and source `data/firebase/AuthRepositorioFirebase.kt:225-263,265-374`,
`vista/FragmentPerfil.kt:208-315`, `viewmodel/VistaModeloAuth.kt:102-124`,
`viewmodel/ParejaViewModel.kt:39-40,83-105`, `vista/EliminacionCuentaActivity.kt:10-31`,
`repositorio/RepositorioPareja.kt:231-263,291-302`, `modelo/Usuario.kt:13-17`,
`modelo/Grupo.kt:8`, and `firestore.rules`.

## ADDED Requirements

### Requirement: Account deletion SHALL re-authenticate before any data mutation

The deletion flow initiated from the account UI MUST re-authenticate the signed-in user with
their password before any Firestore or Storage write. An incorrect password MUST fail the flow
without mutating any associated data. When Firebase reports that a recent login is required, the
system MUST surface the re-authentication message and MUST NOT report success.

#### Scenario: Valid password proceeds to cleanup

- GIVEN a signed-in user with an email/password account
- WHEN they confirm deletion with the correct password
- THEN the system MUST re-authenticate before any cleanup write
- AND only after successful re-authentication MUST it run the cleanup

#### Scenario: Incorrect password mutates nothing

- GIVEN a signed-in user
- WHEN they confirm deletion with an incorrect password
- THEN the deletion MUST fail
- AND no `usuarios/{uid}` document, no `avatares/{uid}` document, no group document, no task, and no Storage evidence MUST be deleted or updated

#### Scenario: Recent-login requirement is surfaced

- GIVEN a valid session whose credential no longer satisfies Firebase's recent-login requirement
- WHEN deletion runs
- THEN the flow MUST return a failure that prompts for the password again
- AND the account MUST NOT be reported as deleted

### Requirement: The account-deletion UI SHALL require the literal confirmation and a password

The account-deletion confirmation dialog MUST require the literal text `ELIMINAR` and a
non-blank password before dispatching the deletion. The dialog MUST be preserved as-is
(`vista/FragmentPerfil.kt:214-275`). The informational `EliminacionCuentaActivity` page
(`vista/EliminacionCuentaActivity.kt:10-31`) MUST be preserved and MUST NOT become part of the
mutation flow.

#### Scenario: Wrong confirmation text is rejected

- GIVEN the confirmation dialog is open
- WHEN the typed confirmation text is not exactly `ELIMINAR`
- THEN the deletion MUST NOT be dispatched

#### Scenario: Blank password is rejected

- GIVEN the confirmation dialog is open with the correct confirmation text
- WHEN the password field is blank
- THEN the deletion MUST NOT be dispatched

#### Scenario: Info page is display-only

- GIVEN the elimination info page is opened
- WHEN it renders
- THEN it MUST only display policy/information content
- AND it MUST NOT delete or update any account data

### Requirement: Cleanup SHALL be complete, verifiable, and gate the Auth deletion

The system MUST attempt every cleanup step and MUST collect a per-step success/failure result
instead of swallowing failures. Transient failures SHOULD be retried a bounded number of times.
`FirebaseAuth.delete()` MUST only be invoked when every cleanup step has completed successfully.
When any step still fails after retries, the system MUST NOT delete the Auth account and MUST
return a failure that identifies the failed step(s). Cleanup operations MUST be idempotent so
that a retry re-runs the whole cleanup safely. Cleanup is not atomic across documents and
Storage; a partial application before an aborted run is accepted and MUST be surfaced rather than
hidden.

#### Scenario: Complete cleanup deletes the Auth account

- GIVEN the user confirmed deletion and re-authenticated
- WHEN every cleanup step succeeds
- THEN the Auth account MUST be deleted
- AND the local session/cache MUST be cleared and the user signed out

#### Scenario: Partial failure aborts the Auth deletion

- GIVEN a cleanup step fails and remains failed after the bounded retries
- WHEN the flow finishes
- THEN the Auth account MUST NOT be deleted
- AND the returned failure MUST name the failed step(s)
- AND success MUST NOT be reported

#### Scenario: Retry converges

- GIVEN a previous run aborted after some cleanup steps had already been applied
- WHEN the user retries deletion
- THEN the cleanup MUST re-run every step
- AND deleting or updating an already-cleaned target MUST succeed without error

#### Scenario: Auth deletion failure after a complete cleanup

- GIVEN every cleanup step completed successfully
- WHEN `FirebaseAuth.delete()` itself fails
- THEN the failure MUST be surfaced through the preserved re-authentication error path
- AND the account MAY survive with its data already cleaned
- AND a retry MUST re-run the idempotent cleanup

### Requirement: Cleanup SHALL cover every associated Firestore collection and Storage evidence

Cleanup MUST delete the user's `usuarios/{uid}` document; MUST delete `avatares/{uid}`; MUST
apply the group path (see the group-dissolution requirement); MUST delete `tareas` where
`creadoPor == uid`; MUST unassign `tareas` where `asignadoA == uid` and that were not created by
the user, resetting `asignadoA` to `null` and `estado`, `fechaReclamada`, `reclamadoPor`, and
`motivoReclamo` to their pending/null values; MUST delete `invitaciones` where `creadoPor == uid`;
MUST delete `notificaciones` where `destinatario == uid` and where `contenido.desde == uid`; MUST
delete `recompensas` where `creadoPor == uid`; MUST delete `canjes` where `usuarioUid == uid`; and
MUST delete `disputas` where `iniciador == uid` together with the Storage evidence referenced by
each dispute's `pruebas[]` (`disputas/{tareaId}/{uuid}.jpg`, `repositorio/RepositorioDisputas.kt:36-46`).

#### Scenario: Every cleanup target is removed or updated

- GIVEN a user with documents in `usuarios`, `avatares`, `tareas`, `invitaciones`, `notificaciones`, `recompensas`, `canjes`, and `disputas`
- WHEN cleanup runs
- THEN each target listed in this requirement MUST be deleted or updated
- AND the cleanup result MUST record a success outcome for each attempted step

#### Scenario: Assigned tasks are unassigned, not deleted

- GIVEN a task assigned to the deleted uid that was created by another user
- WHEN cleanup runs
- THEN the task document MUST remain
- AND its `asignadoA`, `estado`, `fechaReclamada`, `reclamadoPor`, and `motivoReclamo` fields MUST be reset to the pending state

#### Scenario: Dispute evidence is deleted with its dispute

- GIVEN a dispute whose `iniciador == uid` with one or more URLs in `pruebas[]`
- WHEN cleanup runs
- THEN each referenced Storage object MUST be deleted
- AND the dispute document MUST be deleted

### Requirement: `avatares/{uid}` SHALL be deleted during cleanup

The cleanup MUST delete the `avatares/{uid}` document for the deleted user. The local
`firestore.rules` grants `write` to self (`firestore.rules:37-40`); production/Console parity
remains `[UNVERIFIED]`. A successful deletion MUST NOT leave an orphaned `avatares/{uid}`
document. An absent `avatares/{uid}` document MUST NOT be treated as a cleanup failure.

#### Scenario: Avatar document is removed

- GIVEN a user who owns an `avatares/{uid}` document
- WHEN cleanup runs
- THEN `avatares/{uid}` MUST be deleted
- AND no new orphaned avatar document MUST result from a successful deletion

#### Scenario: Missing avatar document is not a failure

- GIVEN a user with no `avatares/{uid}` document
- WHEN cleanup runs
- THEN the avatar step MUST be reported as successful
- AND it MUST NOT block the Auth deletion

### Requirement: Tasks created by the deleted user SHALL be deleted, with a documented cascade

Cleanup MUST delete every `tareas` document where `creadoPor == uid`. This cascade is
intentional and destructive: the other group member loses those tasks because they no longer
exist. The cascade MUST be documented in the change artifacts and MUST NOT be reported as
data loss on the app side.

#### Scenario: User-created tasks are deleted

- GIVEN a task created by the deleted uid
- WHEN cleanup runs
- THEN the task document MUST be deleted
- AND the remaining member MUST NOT be able to read it afterwards

### Requirement: A group SHALL be dissolved when exactly one member remains

For each group containing the deleted uid, cleanup MUST apply exactly one of these outcomes:

- If no members remain, the group document MUST be deleted (current behavior preserved).
- If exactly one member remains, the group MUST be dissolved: delete `grupos/{gid}`, set the
  remaining member's `usuarios/{remainingUid}.grupoId` to `null` in Firestore, reset the
  remaining member's balances (next requirement), and delete the dissolved group's remaining
  tasks (next requirement).
- If two or more members remain, the group MUST keep the current membership-map update that
  removes only the deleted uid and MUST NOT be dissolved.

The local `firestore.rules` allows any signed-in update on `usuarios` (`firestore.rules:27-32`)
and delete on `grupos` (`firestore.rules:43-48`); production/Console parity remains
`[UNVERIFIED]`.

#### Scenario: Exactly one member remains and the group is dissolved

- GIVEN a two-member group containing the deleted uid and one remaining member
- WHEN cleanup runs
- THEN `grupos/{gid}` MUST be deleted
- AND `usuarios/{remainingUid}.grupoId` MUST be set to `null` in Firestore
- AND the group MUST NOT remain readable to the remaining member

#### Scenario: Two or more members remain and the group is kept

- GIVEN a group containing the deleted uid and at least two other members
- WHEN cleanup runs
- THEN the group document MUST remain
- AND its `miembros` map MUST be updated to remove only the deleted uid
- AND no remaining member's `grupoId` MUST be cleared

#### Scenario: No members remain and the group is deleted

- GIVEN a group whose only member is the deleted uid
- WHEN cleanup runs
- THEN the group document MUST be deleted

### Requirement: A dissolved group's remaining member balances SHALL be reset to zero

When a group is dissolved because the deleted user left exactly one remaining member, the system
MUST set that member's `puntos`, `puntosReservados`, `puntosRecompensa`, and `rachaDias` to `0`
in `usuarios/{remainingUid}`. All other profile fields MUST be preserved. The reset MUST be part
of the verifiable cleanup result. The reset reaches the remaining member's device through the
existing `observeUsuarios()` stream (`data/firebase/AuthRepositorioFirebase.kt:393-432`) and MUST
NOT require a second mechanism.

#### Scenario: Balances and streak are reset

- GIVEN a two-member group where the deleted uid leaves one remaining member with non-zero `puntos`, `puntosReservados`, `puntosRecompensa`, or `rachaDias`
- WHEN dissolution runs
- THEN each of `puntos`, `puntosReservados`, `puntosRecompensa`, and `rachaDias` for the remaining member MUST be `0`
- AND the reset MUST be reported as a successful cleanup step

#### Scenario: Other profile fields are preserved

- GIVEN a remaining member with `nombre`, `email`, and profile fields set
- WHEN dissolution runs and resets the balances
- THEN `nombre`, `email`, and the other profile fields MUST be unchanged

### Requirement: A dissolved group's remaining tasks SHALL be deleted

When a group is dissolved, cleanup MUST delete every `tareas` document whose `grupoId` equals the
dissolved group, including tasks created by the remaining member. Tasks already removed by the
user-created step or the unassign step MUST NOT cause a failure if they were already deleted.
This is the confirmed decision 4 (full clean slate for the dissolved group).

#### Scenario: Group tasks are deleted on dissolution

- GIVEN a two-member group with tasks whose `grupoId` is the group
- WHEN the group is dissolved because one member deleted their account
- THEN every task whose `grupoId` equals the dissolved group MUST be deleted
- AND the deletion MUST be reported as a successful cleanup step

#### Scenario: Already-deleted tasks are tolerated

- GIVEN a task of the dissolved group that was already deleted by an earlier cleanup step
- WHEN the group-task deletion runs
- THEN it MUST succeed without error

### Requirement: The remaining member's device SHALL clear its local group reference reactively

The remaining member's device MUST clear its local `tfg_prefs` `grupoId` through the existing
observer path: `ParejaViewModel.startObservingGrupo` observes the group document and, when the
document no longer exists, emits `null` and clears the preference
(`viewmodel/ParejaViewModel.kt:83-105`, clear at `:91-98`). The deleting client MUST NOT add a
second mechanism to clear the remaining member's local preference. The deleting user's own
client MUST continue to clear its own local `tfg_prefs` `grupoId` on successful deletion
(`vista/FragmentPerfil.kt:297-307`).

#### Scenario: Remaining member's local group reference clears

- GIVEN the remaining member's device is observing the group document
- WHEN the deleted account's cleanup removes `grupos/{gid}`
- THEN the observer MUST emit `null`
- AND the remaining member's local `tfg_prefs` `grupoId` MUST be cleared
- AND the remaining member MUST end up with no active group

#### Scenario: Deleting client clears only its own local state

- GIVEN the deleting user's flow succeeds
- WHEN the deletion completes
- THEN the deleting client MUST clear its own local `tfg_prefs` `grupoId`
- AND it MUST NOT write the remaining member's local preference

### Requirement: Cleanup failures SHALL never be reported as success

The user-observable outcome MUST distinguish full success from partial failure. On partial
failure the UI MUST show a message that identifies the incomplete cleanup, MUST re-enable the
deletion action so the user can retry, and MUST NOT navigate to the login screen or claim the
account was deleted. The success message and the login navigation MUST appear only when the Auth
deletion completed (`vista/FragmentPerfil.kt:277-295`).

#### Scenario: Success is shown only on a completed deletion

- GIVEN every cleanup step succeeded and the Auth account was deleted
- WHEN the flow reports back to the UI
- THEN the UI MUST show the deletion success message
- AND it MUST navigate to login clearing the back stack

#### Scenario: Partial failure shows a clear message and allows retry

- GIVEN at least one cleanup step failed and the Auth account was not deleted
- WHEN the flow reports back to the UI
- THEN the UI MUST show a partial-failure message that identifies the incomplete cleanup
- AND the deletion action MUST be re-enabled
- AND the UI MUST NOT navigate to login and MUST NOT claim the account was deleted

### Requirement: Account deletion SHALL have defined observable outcomes for both users

The deletion flow MUST produce a consistent, observable end state for both the deleting user and
the remaining member of a dissolved group.

#### Scenario: Deleting user's observable outcome

- GIVEN a user deletes their account successfully
- WHEN the flow completes
- THEN their `usuarios/{uid}` and `avatares/{uid}` documents MUST be gone
- AND their group memberships and associated data MUST be cleaned per the requirements above
- AND their local session and local `grupoId` MUST be cleared
- AND they MUST be returned to the login screen

#### Scenario: Remaining member's observable outcome

- GIVEN the deleting user was in a two-member group
- WHEN dissolution completes
- THEN the remaining member MUST have no active group in Firestore
- AND the remaining member's `puntos`, `puntosReservados`, `puntosRecompensa`, and `rachaDias` MUST be `0`
- AND the dissolved group's tasks MUST no longer be readable
- AND the remaining member's device MUST observe the group removal and clear its local `tfg_prefs` `grupoId` without a manual action
