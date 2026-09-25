# Delta for firestore-contracts

## Change shape

Mixed delta. Two existing requirements are MODIFIED: "Avatar bytes SHALL live in a dedicated
`avatares/{uid}` collection" gains the account-cleanup deletion behavior, and "Query patterns
MUST be listed per collection" gains the account-cleanup query set (including the new
`whereEqualTo("grupoId", gid)` used by group dissolution). The remaining updates are
non-requirement table/row changes recorded as exact replacements for archive, following the
established recorded-table pattern: the collections/access table, the `usuarios` field table,
the Storage table, the assumed server-side behaviour table, the Future Convergence Work table,
and the Manual Verification table.

**Choice recorded here:** because the remaining updates are not requirement-shaped, this delta
records the exact table replacements to apply at archive instead of MODIFIED blocks. The archive
step MUST apply the tables below to `openspec/specs/firestore-contracts/spec.md`. All other
requirements and their scenarios are unchanged and MUST be preserved byte-for-byte: "Each
collection SHALL have a documented writer and reader", "Documented fields MUST be the union of
all observed writes", and "Unverified server-side claims MUST be marked".

Stale citations touched by this delta are refreshed to the current source: the `disputas`
cleanup citations move from `AuthRepositorioFirebase.kt:340-356`/`:347-352` to `:344-362`; the
best-effort cleanup citation moves from `:259-357` to `:265-363`; and the `tareas` query
citations move from `TareaRepositorioFirebase.kt:187-205` to `:222,227,234`.

Grounding: `proposal.md` (Modified Capabilities; Resolved Decisions 2-6; Approach sections 1-4),
`exploration.md`, and current source `data/firebase/AuthRepositorioFirebase.kt:225-263,265-374`,
`data/firebase/TareaRepositorioFirebase.kt:222,227,234,250`, `repositorio/RepositorioPareja.kt:231-263,291-302`,
`repositorio/RepositorioDisputas.kt:40-42`, `modelo/Usuario.kt:13-17`, and `firestore.rules`.

## MODIFIED Requirements

### Requirement: Avatar bytes SHALL live in a dedicated `avatares/{uid}` collection

The system MUST persist avatar bytes in the Firestore collection `avatares/{uid}`, with the
document writer being the signed-in owner only and the document readers being any signed-in
user (group members read each other's avatars). The document MUST contain exactly the fields
`base64` (String, the compressed image bytes), `contentType` (String, the image MIME type),
and `updatedAt` (Timestamp, the version marker). Avatar bytes MUST NOT be added to `usuarios`
documents because `AuthRepositorioFirebase.observarUsuarios()` re-transmits the full
`usuarios` document on every snapshot (`data/firebase/AuthRepositorioFirebase.kt:386-422`).
Avatar bytes MUST NOT be stored in Firebase Storage. The local `firestore.rules` file MUST
grant `read` to any signed-in user and `write` only to self; deployment remains out of scope
and `[UNVERIFIED]`. Account cleanup MUST delete the owner's `avatares/{uid}` document as part
of the deletion flow defined by `account-deletion`; an absent document MUST NOT be treated as a
cleanup failure.

(Previously: the requirement only defined where avatar bytes live, who may write them, and the
field contract; it did not cover removal on account cleanup.)

#### Scenario: Writer is self only

- GIVEN a signed-in user with uid `uA`
- WHEN the client writes `avatares/uA`
- THEN the write MUST be allowed by `firestore.rules`
- AND a write to `avatares/uB` by `uA` MUST be denied

#### Scenario: Reader is any signed-in user

- GIVEN a signed-in user with uid `uA`
- WHEN the client reads `avatares/uB` for a group member `uB`
- THEN the read MUST be allowed by `firestore.rules`

#### Scenario: Fields match the contract

- GIVEN a successful avatar upload
- WHEN the resulting `avatares/{uid}` document is inspected
- THEN it MUST contain `base64`, `contentType`, and `updatedAt`
- AND no avatar bytes MUST appear in `usuarios/{uid}`

#### Scenario: Account cleanup deletes the owner's document

- GIVEN a signed-in user with uid `uA` who owns `avatares/uA` and is deleting their account
- WHEN the account cleanup runs
- THEN `avatares/uA` MUST be deleted
- AND a successful deletion MUST NOT leave an orphaned `avatares/uA` document

### Requirement: Query patterns MUST be listed per collection

Every `whereEqualTo`, `whereIn`, `orderBy`, or `limit` call on a collection MUST be documented,
including the indexed fields it depends on. The account-cleanup queries on `tareas` MUST also be
documented: `whereEqualTo("creadoPor", uid)` and `whereEqualTo("asignadoA", uid)`
(`data/firebase/AuthRepositorioFirebase.kt:302,312`), plus `whereEqualTo("grupoId", gid)` for
group dissolution (`data/firebase/AuthRepositorioFirebase.kt` — added by this change).

(Previously: only the `observarTareas` queries were enumerated; the account-cleanup queries,
including the new `grupoId` dissolution query, are now listed.)

#### Scenario: `tareas` query

- GIVEN a developer reads `TareaRepositorioFirebase.observarTareas`
- WHEN they list query patterns in this spec
- THEN they MUST enumerate: `whereEqualTo("creadoPor", uid)`, `whereEqualTo("asignadoA", uid)`,
  `whereEqualTo("grupoId", grupoId)` (`TareaRepositorioFirebase.kt:222,227,234`)
- AND each pattern MUST mark whether it requires a composite index

#### Scenario: `tareas` account-cleanup queries

- GIVEN a developer reads `AuthRepositorioFirebase.limpiarDatosAsociados`
- WHEN they list query patterns in this spec
- THEN they MUST enumerate the cleanup queries `whereEqualTo("creadoPor", uid)` and
  `whereEqualTo("asignadoA", uid)` (`AuthRepositorioFirebase.kt:302,312`)
- AND they MUST enumerate the dissolution query `whereEqualTo("grupoId", gid)` added by this
  change
- AND each pattern MUST mark whether it requires a composite index

## Non-requirement updates (apply at archive)

### Collections and access patterns (updated table)

The `usuarios`, `avatares`, `grupos`, `tareas`, and `disputas` rows change (account cleanup
becomes a documented producer/consumer; the `disputas` cleanup citation is refreshed). All other
rows are unchanged.

| Collection | Producer(s) | Consumer(s) | Primary fields observed |
|---|---|---|---|
| `usuarios` | `AuthRepositorioFirebase.kt:30-82,160-212`; `TareaRepositorioFirebase.kt:281-334,440-494`; `RepositorioPareja.kt:118-141,247-254`; `RepositorioRecompensas.kt:91-118,131-141`; canonical avatar repository (writes `avatarUpdatedAt` only); UI direct (`FragmentPareja.kt:549-570`); account cleanup (this change: delete self `AuthRepositorioFirebase.kt:267-271`; clear surviving member's `grupoId` and reset balances on dissolution) | `AuthRepositorioFirebase.kt:97-138,386-422`; `TareaRepositorioFirebase.kt:127-184`; `RepositorioPareja.kt:218-228`; `MainActivity.kt:441-448` (drawer) | `nombre`, `email`, `puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias`, `grupoId`, `avatarUrl` (legacy, no producer), `avatarUpdatedAt`, `fechaCreacion` |
| `avatares` | canonical avatar repository, self-only upload (this change); account cleanup deletes the owner's document (this change) | canonical avatar repository read by uid; `MainActivity.kt:451-509`; `FragmentPgPrincipal.kt:357-391`; `FragmentPerfil.kt:49-62,190-205` | `base64`, `contentType`, `updatedAt` |
| `grupos` | `RepositorioPareja.kt:35-62,230-288`; account cleanup deletes a dissolved group (this change, `AuthRepositorioFirebase.kt:274-295`) | `RepositorioPareja.kt:160-203,269-288`; `TareaRepositorioFirebase.kt:130-181,200-206`; UI direct (`FragmentPareja.kt:364-371`) | `nombre`, `miembros` (Map<uid,rol>), `puntos`, `fechaCreacion`, `emoji` |
| `invitaciones` | `RepositorioPareja.kt:64-76`; account cleanup deletes by `creadoPor` (this change) | `RepositorioPareja.kt:78-87,89-156` | `codigo`, `creadoPor`, `grupoId`, `correoDestino`, `estado`, `fechaCreacion`, `expiracion` |
| `tareas` | `TareaRepositorioFirebase.kt:76-120,230-360,390-548`; `RepositorioTareas.kt:44-200`; UI direct (`FragmentTareas.kt:492`); account cleanup (this change: delete by `creadoPor`/`grupoId`, unassign by `asignadoA`, `AuthRepositorioFirebase.kt:301-327`) | `TareaRepositorioFirebase.kt:122-227`; `RepositorioTareas.kt:65-200`; UI direct (`FragmentPareja.kt:549-570`, `TareasHomeAdapter.kt:74`) | full schema in `Tarea.kt:5-32`; see also `task-domain` spec |
| `recompensas` | `RepositorioRecompensas.kt:49-72`; account cleanup deletes by `creadoPor` (this change) | `RepositorioRecompensas.kt:29-46,211-225` | `titulo`, `descripcion`, `coste`, `creadoPor`, `grupoId`, `fechaCreacion`, `esPredefinida`, `esPersonalizada` |
| `canjes` | `RepositorioRecompensas.kt:84-118`; account cleanup deletes by `usuarioUid` (this change) | `RepositorioRecompensas.kt:121-208` | `recompensaId`, `tituloRecompensa`, `coste`, `usuarioUid`, `nombreUsuario`, `grupoId`, `fecha`, `estado` |
| `disputas` | `RepositorioDisputas.kt:17-23`; UI (`FragmentTareas.kt:96-98`) | `RepositorioDisputas.kt:26-34`; `AuthRepositorioFirebase.kt:344-362` (cleanup) | `tareaId`, `iniciador`, `estado`, `pruebas` (List<String>), `fechaCreacion` |
| `notificaciones` | `RepositorioNotificaciones.kt:15-22`; `TareaRepositorioFirebase.kt:343-354`; UI direct (`FragmentTareas.kt:381-388,456-462`) | `RepositorioNotificaciones.kt:24-52`; `MainActivity.kt:286-329` | `tipo`, `contenido` (Map<String,Any>), `destinatario`, `visto`, `fecha` |

### `usuarios/{uid}` field table (updated rows)

The `puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias`, and `grupoId` rows gain the
account-cleanup dissolution write as a source of truth. All other rows are unchanged.

| Field | Type | Source of truth | Default |
|---|---|---|---|
| `id` | String (doc id) | `AuthRepositorioFirebase.kt:48-53` | n/a |
| `nombre` | String | `AuthRepositorioFirebase.kt:42,103,172-178` | `""` |
| `email` | String | `AuthRepositorioFirebase.kt:46,107,179` | `""` |
| `fechaNacimiento`, `sexo`, `pais`, `ciudad` | String? | `AuthRepositorioFirebase.kt:42-45,103-106,172-178` | null |
| `puntos` | Long (int range) | `AuthRepositorioFirebase.kt:48,109`; updated by `sumarPuntos`, `reservarPuntos`, `liberarPuntos`, `sumarPuntosConBonificacion`, `comprarPuntos`; reset to `0` on group dissolution by account cleanup (this change) | `INITIAL_POINTS=1000` (`Constants.kt:5`) |
| `puntosReservados` | Long | `AuthRepositorioFirebase.kt:49,110`; `TareaRepositorioFirebase.kt:295-335,491-494`; reset to `0` on group dissolution by account cleanup (this change) | `0` |
| `puntosRecompensa` | Long | `AuthRepositorioFirebase.kt:50,111`; `TareaRepositorioFirebase.kt:417-487`; `RepositorioRecompensas.kt:96-141`; reset to `0` on group dissolution by account cleanup (this change) | `0` |
| `rachaDias` | Long | `TareaRepositorioFirebase.kt:467,478-487`; `AuthRepositorioFirebase.kt:499-505`; reset to `0` on group dissolution by account cleanup (this change) | `0` |
| `grupoId` | String? | `RepositorioPareja.kt:51,129-138,247-254,290-300`; cleared to `null` on group dissolution by account cleanup (this change) | null |
| `avatarUrl` | String? | Legacy. Historically written by `AvatarRepositorioFirebase.kt:69-71,163-165` (Storage download URL); that implementation is removed/repurposed, so after this change it has no producer and MUST NOT be used as the avatar reference | null |
| `avatarUpdatedAt` | Timestamp? | Avatar hint written by the canonical avatar repository on a successful upload; surfaced by all four `AuthRepositorioFirebase` mappers (`:125-136,194-205,375-383,404-412`) | null |
| `fechaCreacion` | Timestamp | `Usuario.kt:19` (model) | null |

### Storage (updated rows)

The `disputas` rows gain refreshed cleanup citations. The avatar Storage row is unchanged.

| Path | Writer | Reader | Notes |
|---|---|---|---|
| `avatares/{uid}/{uuid}.{ext}` | none after this change (previously `AvatarRepositorioFirebase.kt:49,61-65`) | none | Unused: avatars moved to the Firestore `avatares/{uid}` collection. `storage.rules` avatar path becomes dead and is deferred to the rules/indexes change for pruning |
| `disputas/{tareaId}/{uuid}.jpg` | `RepositorioDisputas.kt:40-42` | `AuthRepositorioFirebase.kt:344-362` (cleanup) | URL stored in `disputas.pruebas[]`; account cleanup deletes each referenced object |

### Assumed server-side behaviour (UNVERIFIED) (updated rows)

The Cloud Function row citation is refreshed, and three rows are added for the account-cleanup
and dissolution writes. All other rows are unchanged.

| Assumption | Required by | Evidence |
|---|---|---|
| Authenticated user can read/write own `usuarios/{uid}` doc | `AuthRepositorioFirebase.kt:97-138,386-422` | [UNVERIFIED] local `firestore.rules:27-32`; Console parity [UNVERIFIED] |
| Group members can read each other's `usuarios` doc | `AuthRepositorioFirebase.observarUsuarios` reads ALL docs (`AuthRepositorioFirebase.kt:386-422`) | [UNVERIFIED] |
| Any authenticated user can read `grupos` collection | `RepositorioPareja.kt:160-167,193-203` | [UNVERIFIED] |
| `invitaciones` lookup by `codigo` requires auth | `RepositorioPareja.kt:89-156` | [UNVERIFIED] |
| `tareas` queries with `whereEqualTo("creadoPor"|"asignadoA"|"grupoId", uid)` succeed without composite index | `TareaRepositorioFirebase.kt:145-153,187-205`; account cleanup (`AuthRepositorioFirebase.kt:302,312`) and dissolution (this change) | [UNVERIFIED] |
| Composite index `tareas grupoId+estado` is configured | `TareaRepositorioFirebase.kt:201-205` | [UNVERIFIED] |
| Storage path `disputas/{tareaId}/*` allows the disputer to read/write | `RepositorioDisputas.kt:40-42` | [UNVERIFIED] |
| Signed-in users can read `avatares/{uid}`; only the owner can write it | Fixes the [UNVERIFIED] Storage avatar path with a Firestore rule (`firestore.rules`) | [UNVERIFIED] local `firestore.rules`; deployment/console parity [UNVERIFIED] |
| Storage path `avatares/{uid}/*` allows the owner | Previously `AvatarRepositorioFirebase.kt:49-71` | Superseded: this Storage path is no longer used by the client after avatars move to Firestore; `storage.rules` avatar path unused and pruning deferred |
| Signed-in users can delete their own `avatares/{uid}` during account cleanup | `account-deletion` cleanup | [UNVERIFIED] local `firestore.rules:37-40`; Console parity [UNVERIFIED] |
| A signed-in user can update another member's `usuarios/{uid}` (clear `grupoId`, reset balances) during group dissolution | `account-deletion` dissolution | [UNVERIFIED] local `firestore.rules:27-32`; Console parity [UNVERIFIED] |
| A signed-in user can delete `grupos/{gid}` during group dissolution | `account-deletion` dissolution | [UNVERIFIED] local `firestore.rules:43-48`; Console parity [UNVERIFIED] |
| Cloud Function exists to clean up on user deletion | client cleanup remains the only path (`AuthRepositorioFirebase.kt:265-363`); this change makes it verifiable and gated but still non-atomic | [UNVERIFIED] no functions source in repo |
| App Check enforcement | — | not declared in dependencies |
| Crashlytics reporting | — | not declared in dependencies |

### Future Convergence Work (updated row)

The Cloud Function row citation is refreshed and its note updated. All other rows are unchanged.

| Work item | Severity | Notes |
|---|---|---|
| Add `firestore.rules` and `storage.rules` in repo with explicit read/write grants | High | Today only client-side filtering; no proof of backend enforcement |
| Add `firestore.indexes.json` for composite queries | High | `tareas whereEqualTo("grupoId", gid) orderBy("estado")` is implicit |
| Add `firebase-appcheck` dependency and provider configuration | Med | [UNVERIFIED] whether intended for production |
| Add Cloud Function for transactional cleanup on user delete | Med | `AuthRepositorioFirebase.limpiarDatosAsociados` was best-effort (`AuthRepositorioFirebase.kt:265-363`); this change makes the client cleanup verifiable and gated, but it remains non-atomic, so a transactional server-side cleanup stays a future item |
| Add Crashlytics dependency and init in `TFGApplication` | Low | Observability gap |

### Manual Verification (rows added)

Existing rows are unchanged; the following rows are added for the account-deletion cleanup.

| Check | How | Expected |
|---|---|---|
| Cleanup coverage | Delete an account with data in every collection; inspect `usuarios`, `avatares`, `grupos`, `tareas`, `invitaciones`, `notificaciones`, `recompensas`, `canjes`, `disputas`, and the dispute Storage folder | No document remains for the deleted uid; `avatares/{uid}` is gone; dispute evidence is gone |
| Dissolution writes | Two-member group; delete one account; inspect `grupos/{gid}` and the remaining member's `usuarios` doc | Group document gone; `grupoId` is `null`; `puntos`/`puntosReservados`/`puntosRecompensa`/`rachaDias` are `0` |
| Dissolution task deletion | Two-member group with tasks; delete one account | All tasks with that `grupoId` are deleted |
| Reactive local clear | On the remaining member's device, trigger the dissolution | Group observer emits `null`; local `tfg_prefs` `grupoId` is cleared without manual action |
| Auth-deletion gate | Force one cleanup step to fail; run deletion | Auth account is NOT deleted; failure names the failed step; no success message |
| Residual Auth-delete failure | Complete cleanup, then force `FirebaseAuth.delete()` to fail | Account survives with cleaned data; the re-auth message is shown; retry re-runs the idempotent cleanup |
| `avatares/{uid}` cleanup rule | Emulator: delete `avatares/{self}` and attempt `avatares/{other}` | Self delete allowed; cross-user delete denied |
