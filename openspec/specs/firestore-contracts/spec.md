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
| `usuarios` | `AuthRepositorioFirebase.kt:30-82,160-212`; `TareaRepositorioFirebase.kt:281-334,440-494`; `RepositorioPareja.kt:118-141,247-254`; `RepositorioRecompensas.kt:91-118,131-141`; UI direct (`FragmentPareja.kt:549-570`) | `AuthRepositorioFirebase.kt:97-138,386-422`; `TareaRepositorioFirebase.kt:127-184`; `RepositorioPareja.kt:218-228`; `MainActivity.kt:484-496` (drawer) | `nombre`, `email`, `puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias`, `grupoId`, `avatarUrl`, `fechaCreacion` |
| `grupos` | `RepositorioPareja.kt:35-62,230-288` | `RepositorioPareja.kt:160-203,269-288`; `TareaRepositorioFirebase.kt:130-181,200-206`; UI direct (`FragmentPareja.kt:364-371`) | `nombre`, `miembros` (Map<uid,rol>), `puntos`, `fechaCreacion`, `emoji` |
| `invitaciones` | `RepositorioPareja.kt:64-76` | `RepositorioPareja.kt:78-87,89-156` | `codigo`, `creadoPor`, `grupoId`, `correoDestino`, `estado`, `fechaCreacion`, `expiracion` |
| `tareas` | `TareaRepositorioFirebase.kt:76-120,230-360,390-548`; `RepositorioTareas.kt:44-200`; UI direct (`FragmentTareas.kt:492`) | `TareaRepositorioFirebase.kt:122-227`; `RepositorioTareas.kt:65-200`; UI direct (`FragmentPareja.kt:549-570`, `TareasHomeAdapter.kt:74`) | full schema in `Tarea.kt:5-32`; see also `task-domain` spec |
| `recompensas` | `RepositorioRecompensas.kt:49-72` | `RepositorioRecompensas.kt:29-46,211-225` | `titulo`, `descripcion`, `coste`, `creadoPor`, `grupoId`, `fechaCreacion`, `esPredefinida`, `esPersonalizada` |
| `canjes` | `RepositorioRecompensas.kt:84-118` | `RepositorioRecompensas.kt:121-208` | `recompensaId`, `tituloRecompensa`, `coste`, `usuarioUid`, `nombreUsuario`, `grupoId`, `fecha`, `estado` |
| `disputas` | `RepositorioDisputas.kt:17-23`; UI (`FragmentTareas.kt:96-98`) | `RepositorioDisputas.kt:26-34`; `AuthRepositorioFirebase.kt:340-356` (cleanup) | `tareaId`, `iniciador`, `estado`, `pruebas` (List<String>), `fechaCreacion` |
| `notificaciones` | `RepositorioNotificaciones.kt:15-22`; `TareaRepositorioFirebase.kt:343-354`; UI direct (`FragmentTareas.kt:381-388,456-462`) | `RepositorioNotificaciones.kt:24-52`; `MainActivity.kt:286-329` | `tipo`, `contenido` (Map<String,Any>), `destinatario`, `visto`, `fecha` |

### Field-by-field contracts (from code)

#### `usuarios/{uid}`

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
| `avatarUrl` | String? | `AvatarRepositorioFirebase.kt:69-71,163-165` (writes only; not consumed by `AvatarViewModel`) | null |
| `fechaCreacion` | Timestamp | `Usuario.kt:19` (model) | null |

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
| `avatares/{uid}/{uuid}.{ext}` | `AvatarRepositorioFirebase.kt:49,61-65` | none observed in client (`Glide` would need a public URL) | unreachable from `AvatarViewModel.kt:14-16` |
| `disputas/{tareaId}/{uuid}.jpg` | `RepositorioDisputas.kt:36-46` | `AuthRepositorioFirebase.kt:347-352` (cleanup) | URL stored in `disputas.pruebas[]` |

### Assumed server-side behaviour (UNVERIFIED)

| Assumption | Required by | Evidence |
|---|---|---|
| Authenticated user can read/write own `usuarios/{uid}` doc | `AuthRepositorioFirebase.kt:97-138,386-422` | [UNVERIFIED] no `firestore.rules` file in repo |
| Group members can read each other's `usuarios` doc | `AuthRepositorioFirebase.observarUsuarios` reads ALL docs (`AuthRepositorioFirebase.kt:386-422`) | [UNVERIFIED] |
| Any authenticated user can read `grupos` collection | `RepositorioPareja.kt:160-167,193-203` | [UNVERIFIED] |
| `invitaciones` lookup by `codigo` requires auth | `RepositorioPareja.kt:89-156` | [UNVERIFIED] |
| `tareas` queries with `whereEqualTo("creadoPor"|"asignadoA"|"grupoId", uid)` succeed without composite index | `TareaRepositorioFirebase.kt:145-153,187-205` | [UNVERIFIED] |
| Composite index `tareas grupoId+estado` is configured | `TareaRepositorioFirebase.kt:201-205` | [UNVERIFIED] |
| Storage path `disputas/{tareaId}/*` allows the disputer to read/write | `RepositorioDisputas.kt:36-46` | [UNVERIFIED] |
| Storage path `avatares/{uid}/*` allows the owner | `AvatarRepositorioFirebase.kt:49-71` | [UNVERIFIED] |
| Cloud Function exists to clean up on user deletion | best-effort client cleanup is the only path (`AuthRepositorioFirebase.kt:259-357`) | [UNVERIFIED] no functions source in repo |
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

Every `whereEqualTo`, `whereIn`, `orderBy`, or `limit` call on a collection MUST be
documented, including the indexed fields it depends on.

#### Scenario: `tareas` query

- GIVEN a developer reads `TareaRepositorioFirebase.observarTareas`
- WHEN they list query patterns in this spec
- THEN they MUST enumerate: `whereEqualTo("creadoPor", uid)`, `whereEqualTo("asignadoA", uid)`,
  `whereEqualTo("grupoId", grupoId)` (`TareaRepositorioFirebase.kt:187-205`)
- AND each pattern MUST mark whether it requires a composite index

## Future Convergence Work

| Work item | Severity | Notes |
|---|---|---|
| Add `firestore.rules` and `storage.rules` in repo with explicit read/write grants | High | Today only client-side filtering; no proof of backend enforcement |
| Add `firestore.indexes.json` for composite queries | High | `tareas whereEqualTo("grupoId", gid) orderBy("estado")` is implicit |
| Decide avatar authority (local vs Storage) and remove the unused one | High | See `architecture-map` convergence |
| Add `firebase-appcheck` dependency and provider configuration | Med | [UNVERIFIED] whether intended for production |
| Add Cloud Function for transactional cleanup on user delete | Med | `AuthRepositorioFirebase.limpiarDatosAsociados` is best-effort (`AuthRepositorioFirebase.kt:259-357`) |
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
- **Avatar storage path is unused.** `AvatarRepositorioFirebase` is never called from
  `AvatarViewModel`; it is dead code that still ships. Risk: future maintainer may assume
  uploads work.

[UNVERIFIED] All claims about Firestore rules, indexes, App Check, Cloud Functions, and
Crashlytics.

## Manual Verification

| Check | How | Expected |
|---|---|---|
| Field writes match spec | Add a `Log.d` in each writer, run a smoke flow, dump writes | All written fields are in the spec table |
| Unused writer detection | `grep -r "AvatarRepositorioFirebase(" app/src/main/java` | Zero non-test callers (confirms dead-code) |
| Composite index claims | Open Firebase console → Firestore → Indexes | Each `[UNVERIFIED]` index entry is either present or annotated |
| Rules claim | Open Firebase console → Firestore → Rules | Each `[UNVERIFIED]` rules claim is either true or annotated |
