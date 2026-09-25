# firestore-contracts

## Purpose

Specification of TeamTask's Firestore data contracts: collections, fields, access
patterns, and the rules/indexes/App Check that the implementation **assumes** are
enforced server-side. All server-side claims are marked `[UNVERIFIED]` because no
Firebase console access was available during exploration.

Source-of-truth priority for this spec: code → `app/build.gradle.kts` →
`google-services.json` → README → historical Engram.

## Current State (observable)

### Project configuration

| Field | Value | Evidence |
|---|---|---|
| Project id | `teamtask-3a855` | `google-services.json:5` |
| Firebase BOM | 32.7.0 | `app/build.gradle.kts:46` |
| Firestore SDK | `firebase-firestore-ktx` | `app/build.gradle.kts:51` |
| Storage SDK | `firebase-storage-ktx` | `app/build.gradle.kts:52` |
| Auth SDK | `firebase-auth-ktx` | `app/build.gradle.kts:50` |
| Realtime Database SDK | `firebase-database-ktx` (declared; no consumer in `app/src/main/java`) | `app/build.gradle.kts:48` |
| Google Sign-In | `play-services-auth:20.7.0` | `app/build.gradle.kts:55` |
| App Check | not declared | [UNVERIFIED] not present in dependencies |
| Crashlytics | not declared | [UNVERIFIED] not present in dependencies |
| Cloud Functions | no artefact under `app/src/main/`, `functions/`, or repo root | [UNVERIFIED] |

### Collections and access patterns

| Collection | Producer(s) | Consumer(s) | Primary fields observed |
|---|---|---|---|
| `usuarios` | `AuthRepositorioFirebase.kt:30-82,160-212`; `TareaRepositorioFirebase.kt:281-334,440-494`; `RepositorioPareja.kt:118-141,247-254`; `RepositorioRecompensas.kt:91-118,131-141`; canonical avatar repository (writes `avatarUpdatedAt` only); UI direct (`FragmentPareja.kt:549-570`); account cleanup (this change: delete self `AuthRepositorioFirebase.kt:361-362`; clear surviving member's `grupoId` and reset balances on dissolution) | `AuthRepositorioFirebase.kt:97-138,386-422`; `TareaRepositorioFirebase.kt:127-184`; `RepositorioPareja.kt:218-228`; `MainActivity.kt:441-448` (drawer) | `nombre`, `email`, `puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias`, `grupoId`, `avatarUrl` (legacy, no producer), `avatarUpdatedAt`, `fechaCreacion` |
| `avatares` | canonical avatar repository, self-only upload (this change); account cleanup deletes the owner's document (this change) | canonical avatar repository read by uid; `MainActivity.kt:451-509`; `FragmentPgPrincipal.kt:357-391`; `FragmentPerfil.kt:49-62,190-205` | `base64`, `contentType`, `updatedAt` |
| `grupos` | `RepositorioPareja.kt:35-62,230-288`; account cleanup deletes a dissolved group (this change, `AuthRepositorioFirebase.kt:418-421`) | `RepositorioPareja.kt:160-203,269-288`; `TareaRepositorioFirebase.kt:130-181,200-206`; UI direct (`FragmentPareja.kt:364-371`) | `nombre`, `miembros` (Map<uid,rol>), `puntos`, `fechaCreacion`, `emoji` |
| `invitaciones` | `RepositorioPareja.kt:64-76`; account cleanup deletes by `creadoPor` (this change) | `RepositorioPareja.kt:78-87,89-156` | `codigo`, `creadoPor`, `grupoId`, `correoDestino`, `estado`, `fechaCreacion`, `expiracion` |
| `tareas` | `TareaRepositorioFirebase.kt:76-120,230-360,390-548`; `RepositorioTareas.kt:44-200`; UI direct (`FragmentTareas.kt:492`); account cleanup (this change: delete by `creadoPor`/`grupoId`, unassign by `asignadoA`, `AuthRepositorioFirebase.kt:298,307`) | `TareaRepositorioFirebase.kt:122-227`; `RepositorioTareas.kt:65-200`; UI direct (`FragmentPareja.kt:549-570`, `TareasHomeAdapter.kt:74`) | full schema in `Tarea.kt:5-32`; see also `task-domain` spec |
| `recompensas` | `RepositorioRecompensas.kt:49-72`; account cleanup deletes by `creadoPor` (this change) | `RepositorioRecompensas.kt:29-46,211-225` | `titulo`, `descripcion`, `coste`, `creadoPor`, `grupoId`, `fechaCreacion`, `esPredefinida`, `esPersonalizada` |
| `canjes` | `RepositorioRecompensas.kt:84-118`; account cleanup deletes by `usuarioUid` (this change) | `RepositorioRecompensas.kt:121-208` | `recompensaId`, `tituloRecompensa`, `coste`, `usuarioUid`, `nombreUsuario`, `grupoId`, `fecha`, `estado` |
| `disputas` | `RepositorioDisputas.kt:17-23`; UI (`FragmentTareas.kt:96-98`) | `RepositorioDisputas.kt:26-34`; `AuthRepositorioFirebase.kt:340-353` (cleanup) | `tareaId`, `iniciador`, `estado`, `pruebas` (List<String>), `fechaCreacion` |
| `notificaciones` | `RepositorioNotificaciones.kt:15-22`; `TareaRepositorioFirebase.kt:343-354`; UI direct (`FragmentTareas.kt:381-388,456-462`) | `RepositorioNotificaciones.kt:24-52`; `MainActivity.kt:286-329` | `tipo`, `contenido` (Map<String,Any>), `destinatario`, `visto`, `fecha` |

### Field-by-field contracts (from code)

#### `usuarios/{uid}`

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

#### `avatares/{uid}`

| Field | Type | Source of truth | Default |
|---|---|---|---|
| `base64` | String | canonical avatar repository — compressed image bytes, base64-encoded (this change) | n/a |
| `contentType` | String | canonical avatar repository (this change) | `image/jpeg` |
| `updatedAt` | Timestamp | canonical avatar repository; also surfaced as `usuarios/{uid}.avatarUpdatedAt` (this change) | n/a |

#### `grupos/{gid}`

| Field | Type | Source of truth | Default |
|---|---|---|---|
| `nombre` | String | `RepositorioPareja.kt:38,265-275` | `""` |
| `miembros` | Map<String,String> (uid→rol) | `RepositorioPareja.kt:39,122-126,230-254` | empty map |
| `puntos` | Long | `RepositorioPareja.kt:40` | `0` |
| `fechaCreacion` | Timestamp | `RepositorioPareja.kt:41` | `Timestamp.now()` |
| `emoji` | String | `RepositorioPareja.kt:42,278-288` | `"❤️"` |

#### `tareas/{tid}` — see `task-domain` for full schema

#### `notificaciones/{nid}`

`contenido` is a heterogeneous `Map<String,Any>`; observed keys: `tareaId`, `titulo`,
`puntos`, `desde`, `texto` (`TareaRepositorioFirebase.kt:347-349`; `FragmentTareas.kt:385,460`;
`MainActivity.kt:312-315`).

### Storage

| Path | Writer | Reader | Notes |
|---|---|---|---|
| `avatares/{uid}/{uuid}.{ext}` | none after this change (previously `AvatarRepositorioFirebase.kt:49,61-65`) | none | Unused: avatars moved to the Firestore `avatares/{uid}` collection. `storage.rules` avatar path becomes dead and is deferred to the rules/indexes change for pruning |
| `disputas/{tareaId}/{uuid}.jpg` | `RepositorioDisputas.kt:40-42` | `AuthRepositorioFirebase.kt:340-353` (cleanup) | URL stored in `disputas.pruebas[]`; account cleanup deletes each referenced object |

### Assumed server-side behaviour (UNVERIFIED)

| Assumption | Required by | Evidence |
|---|---|---|
| Authenticated user can read/write own `usuarios/{uid}` doc | `AuthRepositorioFirebase.kt:97-138,386-422` | [UNVERIFIED] local `firestore.rules:27-32`; Console parity [UNVERIFIED] |
| Group members can read each other's `usuarios` doc | `AuthRepositorioFirebase.observarUsuarios` reads ALL docs (`AuthRepositorioFirebase.kt:386-422`) | [UNVERIFIED] |
| Any authenticated user can read `grupos` collection | `RepositorioPareja.kt:160-167,193-203` | [UNVERIFIED] |
| `invitaciones` lookup by `codigo` requires auth | `RepositorioPareja.kt:89-156` | [UNVERIFIED] |
| `tareas` queries with `whereEqualTo("creadoPor"|"asignadoA"|"grupoId", uid)` succeed without composite index | `TareaRepositorioFirebase.kt:145-153,187-205`; account cleanup (`AuthRepositorioFirebase.kt:298,307`) and dissolution (this change) | [UNVERIFIED] |
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

## Requirements

### Requirement: Each collection SHALL have a documented writer and reader

Every Firestore collection referenced by the client MUST appear in this spec with at
least one writer and one reader, each with a `file:line` evidence.

#### Scenario: New collection added in a future change

- GIVEN a developer adds a new collection access in `data/firebase/` or `repositorio/`
- WHEN they open a PR
- THEN this spec MUST be updated in the same change (delta spec, ADDED section)
- AND the new collection row MUST list at least one writer and one reader with evidence

### Requirement: Documented fields MUST be the union of all observed writes

Each collection's field table MUST list every field written by any of its producers.
A field with no observed producer MUST be marked `[UNVERIFIED]` or removed.

#### Scenario: Spotting a stray field

- GIVEN a reviewer notices `usuarios.foo` in a Firestore document via the console
- WHEN they cross-check the field table in this spec
- THEN the field MUST have at least one producer with `file:line`
- IF not, the field MUST be marked as `[UNVERIFIED]` until confirmed via console

### Requirement: Unverified server-side claims MUST be marked

Every claim about Firestore rules, composite indexes, App Check, Cloud Functions, or
other backend enforcement MUST be marked `[UNVERIFIED]` in this spec.

#### Scenario: Future change author

- GIVEN a future SDD change proposes server-side enforcement
- WHEN the change references this spec
- THEN the change MUST NOT rely on any rule/index claim without first confirming
  the Firebase console state and updating the spec to remove the `[UNVERIFIED]`
  marker

### Requirement: Query patterns MUST be listed per collection

Every `whereEqualTo`, `whereIn`, `orderBy`, or `limit` call on a collection MUST be documented,
including the indexed fields it depends on. The account-cleanup queries on `tareas` MUST also be
documented: `whereEqualTo("creadoPor", uid)` and `whereEqualTo("asignadoA", uid)`
(`data/firebase/AuthRepositorioFirebase.kt:298,307`), plus `whereEqualTo("grupoId", gid)` for
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
  `whereEqualTo("asignadoA", uid)` (`AuthRepositorioFirebase.kt:298,307`)
- AND they MUST enumerate the dissolution query `whereEqualTo("grupoId", gid)` added by this
  change
- AND each pattern MUST mark whether it requires a composite index

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

## Future Convergence Work

| Work item | Severity | Notes |
|---|---|---|
| Add `firestore.rules` and `storage.rules` in repo with explicit read/write grants | High | Today only client-side filtering; no proof of backend enforcement |
| Add `firestore.indexes.json` for composite queries | High | `tareas whereEqualTo("grupoId", gid) orderBy("estado")` is implicit |
| Add `firebase-appcheck` dependency and provider configuration | Med | [UNVERIFIED] whether intended for production |
| Add Cloud Function for transactional cleanup on user delete | Med | `AuthRepositorioFirebase.limpiarDatosAsociados` was best-effort (`AuthRepositorioFirebase.kt:265-363`); this change makes the client cleanup verifiable and gated, but it remains non-atomic, so a transactional server-side cleanup stays a future item |
| Add Crashlytics dependency and init in `TFGApplication` | Low | Observability gap |

## Known Risks

- **No server-side enforcement is verifiable from the repo.** Any read/write protection
  depends on the Firebase console configuration, which was not inspected.
- **Wide `usuarios` and `grupos` reads** (`AuthRepositorioFirebase.kt:386-422`;
  `RepositorioPareja.kt:160-167`) require that rules permit them; if rules are strict,
  the app may break silently. [UNVERIFIED]
- **Composite-index risk** for `tareas` queries: any future `orderBy` on
  `creadoPor`/`asignadoA`/`grupoId` will require a new index. [UNVERIFIED] whether current
  rules allow without.
- **Avatar storage path is unused (resolved).** `AvatarRepositorioFirebase` is no longer dead
  code that ships; avatars are stored in the Firestore `avatares/{uid}` collection, so the
  `storage.rules` avatar path becomes dead and its pruning is deferred to the rules/indexes
  change.

[UNVERIFIED] All claims about Firestore rules, indexes, App Check, Cloud Functions, and
Crashlytics.

## Manual Verification

| Check | How | Expected |
|---|---|---|
| Field writes match spec | Add a `Log.d` in each writer, run a smoke flow, dump writes | All written fields are in the spec table |
| Unused writer detection | `grep -r "AvatarRepositorioFirebase(" app/src/main/java` | Zero non-test callers (confirms dead-code) |
| Composite index claims | Open Firebase console → Firestore → Indexes | Each `[UNVERIFIED]` index entry is either present or annotated |
| Rules claim | Open Firebase console → Firestore → Rules | Each `[UNVERIFIED]` rules claim is either true or annotated |
| `avatares/{uid}` writer/reader rule | Run the emulator; write `avatares/{self}` and attempt `avatares/{other}`; read a member's document | Self write allowed, cross-user write denied, member read allowed |
| Avatar not in `usuarios` | Upload an avatar, inspect `usuarios/{uid}` | No `base64`/blob field present; `avatarUpdatedAt` set |
| Storage avatar path unused | `grep -r "FirebaseStorage" app/src/main/java` on the avatar path | No avatar code reads/writes the Storage avatar path |
| Cleanup coverage | Delete an account with data in every collection; inspect `usuarios`, `avatares`, `grupos`, `tareas`, `invitaciones`, `notificaciones`, `recompensas`, `canjes`, `disputas`, and the dispute Storage folder | No document remains for the deleted uid; `avatares/{uid}` is gone; dispute evidence is gone |
| Dissolution writes | Two-member group; delete one account; inspect `grupos/{gid}` and the remaining member's `usuarios` doc | Group document gone; `grupoId` is `null`; `puntos`/`puntosReservados`/`puntosRecompensa`/`rachaDias` are `0` |
| Dissolution task deletion | Two-member group with tasks; delete one account | All tasks with that `grupoId` are deleted |
| Reactive local clear | On the remaining member's device, trigger the dissolution | Group observer emits `null`; local `tfg_prefs` `grupoId` is cleared without manual action |
| Auth-deletion gate | Force one cleanup step to fail; run deletion | Auth account is NOT deleted; failure names the failed step; no success message |
| Residual Auth-delete failure | Complete cleanup, then force `FirebaseAuth.delete()` to fail | Account survives with cleaned data; the re-auth message is shown; retry re-runs the idempotent cleanup |
| `avatares/{uid}` cleanup rule | Emulator: delete `avatares/{self}` and attempt `avatares/{other}` | Self delete allowed; cross-user delete denied |
