# Delta for firestore-contracts

## Change shape

Mixed delta. One requirement is ADDED — the `avatares/{uid}` collection contract (writer,
reader, fields, and the no-blob-in-`usuarios` rule). The remaining updates are
non-requirement table/row changes (collections inventory, `usuarios` field table, the new
`avatares` field table, Storage table, assumed server-side behaviour, Known Risks, Future
Convergence Work, Manual Verification).

**Choice recorded here:** because those remaining updates are not requirement-shaped, and to
follow the pattern used by `teamtask-task-repo-consolidation`, this delta records the exact
table replacements to apply at archive instead of MODIFIED requirement blocks. The archive
step MUST apply the tables below to `openspec/specs/firestore-contracts/spec.md`. The four
existing requirements and all their scenarios are unchanged and MUST be preserved
byte-for-byte: "Each collection SHALL have a documented writer and reader", "Documented
fields MUST be the union of all observed writes", "Unverified server-side claims MUST be
marked", and "Query patterns MUST be listed per collection". No existing requirement body
changes.

Grounding: `proposal.md` (Modified Capabilities; Resolved Decisions 2-4; Pointer/hint
strategy), `exploration.md`, the `avatar-management` spec, and source `firestore.rules`,
`storage.rules`, `modelo/Usuario.kt:18`,
`data/firebase/AvatarRepositorioFirebase.kt:49,61-71,163-165`, and
`data/firebase/AuthRepositorioFirebase.kt:386-422`.

## ADDED Requirements

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
and `[UNVERIFIED]`.

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

## Non-requirement updates (apply at archive)

### Collections and access patterns (updated table)

The `usuarios` row changes (primary fields gain the hint; the legacy `avatarUrl` producer is
gone). One row is added for `avatares`. All other rows are unchanged.

| Collection | Producer(s) | Consumer(s) | Primary fields observed |
|---|---|---|---|
| `usuarios` | `AuthRepositorioFirebase.kt:30-82,160-212`; `TareaRepositorioFirebase.kt:281-334,440-494`; `RepositorioPareja.kt:118-141,247-254`; `RepositorioRecompensas.kt:91-118,131-141`; canonical avatar repository (writes `avatarUpdatedAt` only, this change); UI direct (`FragmentPareja.kt:549-570`) | `AuthRepositorioFirebase.kt:97-138,386-422`; `TareaRepositorioFirebase.kt:127-184`; `RepositorioPareja.kt:218-228`; `MainActivity.kt:441-448` (drawer) | `nombre`, `email`, `puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias`, `grupoId`, `avatarUrl` (legacy, no producer after this change), `avatarUpdatedAt`, `fechaCreacion` |
| `avatares` | canonical avatar repository, self-only upload (this change) | canonical avatar repository read by uid; `MainActivity.kt:451-509`; `FragmentPgPrincipal.kt:357-391`; `FragmentPerfil.kt:49-62,190-205` (this change) | `base64`, `contentType`, `updatedAt` |
| `grupos` | `RepositorioPareja.kt:35-62,230-288` | `RepositorioPareja.kt:160-203,269-288`; `TareaRepositorioFirebase.kt:130-181,200-206`; UI direct (`FragmentPareja.kt:364-371`) | `nombre`, `miembros` (Map<uid,rol>), `puntos`, `fechaCreacion`, `emoji` |
| `invitaciones` | `RepositorioPareja.kt:64-76` | `RepositorioPareja.kt:78-87,89-156` | `codigo`, `creadoPor`, `grupoId`, `correoDestino`, `estado`, `fechaCreacion`, `expiracion` |
| `tareas` | `TareaRepositorioFirebase.kt:76-120,230-360,390-548`; `RepositorioTareas.kt:44-200`; UI direct (`FragmentTareas.kt:492`) | `TareaRepositorioFirebase.kt:122-227`; `RepositorioTareas.kt:65-200`; UI direct (`FragmentPareja.kt:549-570`, `TareasHomeAdapter.kt:74`) | full schema in `Tarea.kt:5-32`; see also `task-domain` spec |
| `recompensas` | `RepositorioRecompensas.kt:49-72` | `RepositorioRecompensas.kt:29-46,211-225` | `titulo`, `descripcion`, `coste`, `creadoPor`, `grupoId`, `fechaCreacion`, `esPredefinida`, `esPersonalizada` |
| `canjes` | `RepositorioRecompensas.kt:84-118` | `RepositorioRecompensas.kt:121-208` | `recompensaId`, `tituloRecompensa`, `coste`, `usuarioUid`, `nombreUsuario`, `grupoId`, `fecha`, `estado` |
| `disputas` | `RepositorioDisputas.kt:17-23`; UI (`FragmentTareas.kt:96-98`) | `RepositorioDisputas.kt:26-34`; `AuthRepositorioFirebase.kt:340-356` (cleanup) | `tareaId`, `iniciador`, `estado`, `pruebas` (List<String>), `fechaCreacion` |
| `notificaciones` | `RepositorioNotificaciones.kt:15-22`; `TareaRepositorioFirebase.kt:343-354`; UI direct (`FragmentTareas.kt:381-388,456-462`) | `RepositorioNotificaciones.kt:24-52`; `MainActivity.kt:286-329` | `tipo`, `contenido` (Map<String,Any>), `destinatario`, `visto`, `fecha` |

### `usuarios/{uid}` field table (updated rows)

The `avatarUrl` row changes and a new `avatarUpdatedAt` row is added. All other rows are
unchanged.

| Field | Type | Source of truth | Default |
|---|---|---|---|
| `id` | String (doc id) | `AuthRepositorioFirebase.kt:48-53` | n/a |
| `nombre` | String | `AuthRepositorioFirebase.kt:42,103,172-178` | `""` |
| `email` | String | `AuthRepositorioFirebase.kt:46,107,179` | `""` |
| `fechaNacimiento`, `sexo`, `pais`, `ciudad` | String? | `AuthRepositorioFirebase.kt:42-45,103-106,172-178` | null |
| `puntos` | Long (int range) | `AuthRepositorioFirebase.kt:48,109`; updated by `sumarPuntos`, `reservarPuntos`, `liberarPuntos`, `sumarPuntosConBonificacion`, `comprarPuntos` | `INITIAL_POINTS=1000` (`Constants.kt:5`) |
| `puntosReservados` | Long | `AuthRepositorioFirebase.kt:49,110`; `TareaRepositorioFirebase.kt:295-335,491-494` | `0` |
| `puntosRecompensa` | Long | `AuthRepositorioFirebase.kt:50,111`; `TareaRepositorioFirebase.kt:417-487`; `RepositorioRecompensas.kt:96-141` | `0` |
| `rachaDias` | Long | `TareaRepositorioFirebase.kt:467,478-487`; `AuthRepositorioFirebase.kt:499-505` | `0` |
| `grupoId` | String? | `RepositorioPareja.kt:51,129-138,247-254,290-300` | null |
| `avatarUrl` | String? | Legacy. Historically written by `AvatarRepositorioFirebase.kt:69-71,163-165` (Storage download URL); that implementation is removed/repurposed by this change, so after this change it has no producer and MUST NOT be used as the avatar reference | null |
| `avatarUpdatedAt` | Timestamp? | Avatar hint written by the canonical avatar repository on a successful upload (this change); surfaced by all four `AuthRepositorioFirebase` mappers (`:125-136,194-205,375-383,404-412`) | null |
| `fechaCreacion` | Timestamp | `Usuario.kt:19` (model) | null |

### `avatares/{uid}` field table (new)

| Field | Type | Source of truth | Default |
|---|---|---|---|
| `base64` | String | canonical avatar repository — compressed image bytes, base64-encoded (this change) | n/a |
| `contentType` | String | canonical avatar repository (this change) | `image/jpeg` |
| `updatedAt` | Timestamp | canonical avatar repository; also surfaced as `usuarios/{uid}.avatarUpdatedAt` (this change) | n/a |

### Storage (updated table)

The avatar Storage row becomes unused. Pruning of `storage.rules` is deferred to the
rules/indexes change per the proposal; this delta marks the claim as unused rather than
deleting the file.

| Path | Writer | Reader | Notes |
|---|---|---|---|
| `avatares/{uid}/{uuid}.{ext}` | none after this change (previously `AvatarRepositorioFirebase.kt:49,61-65`) | none | Unused: avatars moved to the Firestore `avatares/{uid}` collection. `storage.rules` avatar path becomes dead and is deferred to the rules/indexes change for pruning |
| `disputas/{tareaId}/{uuid}.jpg` | `RepositorioDisputas.kt:36-46` | `AuthRepositorioFirebase.kt:347-352` (cleanup) | URL stored in `disputas.pruebas[]` |

### Assumed server-side behaviour (UNVERIFIED) (updated rows)

The Storage `avatares/{uid}/*` row changes, and one row is added for the Firestore
`avatares/{uid}` rule. All other rows are unchanged.

| Assumption | Required by | Evidence |
|---|---|---|
| Authenticated user can read/write own `usuarios/{uid}` doc | `AuthRepositorioFirebase.kt:97-138,386-422` | [UNVERIFIED] local `firestore.rules:27-32`; Console parity [UNVERIFIED] |
| Group members can read each other's `usuarios` doc | `AuthRepositorioFirebase.observarUsuarios` reads ALL docs (`AuthRepositorioFirebase.kt:386-422`) | [UNVERIFIED] |
| Signed-in users can read `avatares/{uid}`; only the owner can write it | Fixes the [UNVERIFIED] Storage avatar path with a Firestore rule (`firestore.rules`) | [UNVERIFIED] local `firestore.rules` (added by this change); deployment/console parity [UNVERIFIED] |
| Storage path `avatares/{uid}/*` allows the owner | Previously `AvatarRepositorioFirebase.kt:49-71` | Superseded: this Storage path is no longer used by the client after avatars move to Firestore; `storage.rules` avatar path unused and pruning deferred |

### Known Risks (updated bullet)

The avatar Storage risk bullet is replaced. All other bullets are unchanged.

- **Avatar storage path is unused (resolved).** `AvatarRepositorioFirebase` is no longer dead
  code that ships; avatars are stored in the Firestore `avatares/{uid}` collection, so the
  `storage.rules` avatar path becomes dead and its pruning is deferred to the rules/indexes
  change.

### Future Convergence Work (closed row)

The row "Decide avatar authority (local vs Storage) and remove the unused one | High" is
closed by this change and removed from the table. All other rows are unchanged.

| Work item | Severity | Notes |
|---|---|---|
| Add `firestore.indexes.json` for composite queries | High | `tareas whereEqualTo("grupoId", gid) orderBy("estado")` is implicit |
| Add `firebase-appcheck` dependency and provider configuration | Med | [UNVERIFIED] whether intended for production |
| Add Cloud Function for transactional cleanup on user delete | Med | `AuthRepositorioFirebase.limpiarDatosAsociados` is best-effort (`AuthRepositorioFirebase.kt:259-357`) |
| Add Crashlytics dependency and init in `TFGApplication` | Low | Observability gap |

The already-closed `firestore.rules`/`storage.rules`/indexes row is not re-added here; the
local artifacts landed with `teamtask-testing-emulator-strategy` WU2.

### Manual Verification (rows added)

Existing rows are unchanged; the following rows are added.

| Check | How | Expected |
|---|---|---|
| `avatares/{uid}` writer/reader rule | Run the emulator; write `avatares/{self}` and attempt `avatares/{other}`; read a member's document | Self write allowed, cross-user write denied, member read allowed |
| Avatar not in `usuarios` | Upload an avatar, inspect `usuarios/{uid}` | No `base64`/blob field present; `avatarUpdatedAt` set |
| Storage avatar path unused | `grep -r "FirebaseStorage" app/src/main/java` on the avatar path | No avatar code reads/writes the Storage avatar path |
