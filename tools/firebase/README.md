# Local Firebase tooling (Auth / Firestore / Storage emulators)

Reproducible, local-only Firebase tooling for TeamTask. It starts only the Auth,
Firestore, and Storage emulators on fixed ports, applies local rules and indexes,
and provides deterministic `reset`/`seed` fixtures. It never touches the
production project.

## Quick path

1. Install the prerequisites below.
2. From the repository root, run the full automated check (starts emulators,
   runs the checks, shuts them down):

   ```bash
   firebase emulators:exec --only auth,firestore,storage --project demo-teamtask-local "node tools/firebase/emulator-verify.mjs"
   ```

3. Expect `emulator-verify: 7/7 checks passed`.

To drive the emulators by hand instead, start them and use the scripts:

```bash
firebase emulators:start --only auth,firestore,storage --project demo-teamtask-local
# in another shell:
bash tools/firebase/reset.sh
bash tools/firebase/seed.sh
bash tools/firebase/seed.sh   # second run converges, no duplicates
```

On Windows, invoke the shell scripts with Git Bash:
`& 'C:\Program Files\Git\bin\bash.exe' tools\firebase\seed.sh`.

## Prerequisites

| Tool | Version verified | Install | Actionable failure guidance |
|------|------------------|---------|-----------------------------|
| Node.js | v24.11.0 (18+ required) | https://nodejs.org or a version manager | `Missing prerequisite 'node'` → install Node.js 18+ and ensure `node` is on `PATH`. The helper scripts use the built-in `fetch` (Node 18+). |
| Firebase CLI | 15.31.0 | `npm install -g firebase-tools` | `Missing prerequisite 'firebase'` → install the CLI and run `firebase login --no-localhost` only if a hosted feature is needed; local emulators need no login. |
| Java (JDK) | 25 | Adoptium Temurin or any JDK 11+ | `Missing prerequisite 'java'` → install a JDK and ensure `java` is on `PATH`. The emulators are Java processes and fail to start without it. |

If a prerequisite is missing, `reset.sh`/`seed.sh` exit non-zero with the message
above and never fall back to production.

## Configuration

| File | Purpose |
|------|---------|
| `firebase.json` | Declares only Auth (9099), Firestore (8080), and Storage (9199) on `127.0.0.1`; disables the emulator UI; points at the rules/index files. |
| `.firebaserc` | Project alias policy. `default` and `local` both resolve to `demo-teamtask-local`. |
| `firestore.rules` | Local contract for `usuarios`, `grupos`, `invitaciones`, `tareas`, `recompensas`, `canjes`, `disputas`, `notificaciones`; everything else denied. |
| `storage.rules` | Local contract for `avatares/{uid}/**` and `disputas/{tareaId}/**`; everything else denied. |
| `firestore.indexes.json` | Composite indexes for observed `canjes` queries and the contract `tareas` `grupoId + estado` index. |

### Alias policy (production can never be selected)

- The emulator project id is always `demo-teamtask-local`, and every script
  refuses any project id that does not start with `demo-`.
- The scripts also refuse to run when `GOOGLE_APPLICATION_CREDENTIALS` or
  `FIREBASE_TOKEN` is set, so production credentials cannot be reused.
- The production project `teamtask-3a855` must never be the `.firebaserc`
  default. Deploying to it is out of scope for this tooling.

### Ports

| Service | Host | Port |
|---------|------|------|
| Auth | 127.0.0.1 | 9099 |
| Firestore | 127.0.0.1 | 8080 |
| Storage | 127.0.0.1 | 9199 |

The emulator UI is disabled so only these three services bind ports.

## Fixtures

`reset` leaves a known empty state (no Auth accounts, no Firestore documents, no
Storage objects). `seed` creates deterministic, namespaced fixtures:

| Fixture | Value |
|---------|-------|
| Creator UID / email | `fixture-creator-0001` / `creator@fixture.teamtask.local` |
| Executor UID / email | `fixture-executor-0001` / `executor@fixture.teamtask.local` |
| Password (both) | `FixturePass123!` |
| Group | `fixture-grupo-0001` (`Fixture Group`) |
| Initial points | 1000 (`puntosReservados`/`puntosRecompensa` = 0) |

`seed` is idempotent: it replaces the fixture documents and re-creates the two
Auth users, so `reset -> seed -> seed` converges to the same state without
duplicates. The namespace prefix is configurable with `FIXTURE_NAMESPACE`
(lowercase letters, digits, and dashes only).

### Reset / import-export determinism

- Emulator state is in-memory and is discarded on shutdown; a fresh
  `emulators:exec` run always starts empty.
- `reset.sh` clears Auth, Firestore, and Storage through the emulator REST
  endpoints, so it produces the same known empty state whether or not a previous
  run wrote data.
- `reset` MUST run before and after an isolated verification; `emulator-verify.mjs`
  does this automatically.
- No `--import`/`--export-on-exit` snapshot is used, by design: a persisted
  snapshot would make state depend on a previous run instead of converging.

## Verification

`tools/firebase/emulator-verify.mjs` asserts, against the running emulators:

1. Auth (9099), Firestore (8080), and Storage (9199) respond.
2. `firestore.indexes.json` parses and a composite `canjes` query is accepted.
3. Firestore rules deny unauthenticated writes, cross-user creates, and
   outside-contract writes; allow an own-profile write.
4. Storage rules deny unauthenticated and cross-user uploads; allow an own
   avatar upload.
5. `reset -> seed -> seed` converges to identical state with no duplicates.
6. The namespace guard rejects a production project id and production
   credentials.
7. The prerequisite guard reports a missing tool with an actionable error.

## Status and boundaries

- `[UNVERIFIED]` Production rules, indexes, App Check, enabled Auth providers,
  OAuth/SHA, Storage buckets, Cloud Functions, FCM, and Console state. Passing
  local rules/indexes does **not** imply deployed production parity.
- Out of scope: Realtime Database, Google Sign-In automation, Analytics
  assertions, Functions, WorkManager, FCM, CI enforcement, and production
  deployment.
- Rollback: delete `firebase.json`, `.firebaserc`, `firestore.rules`,
  `storage.rules`, `firestore.indexes.json`, and `tools/firebase/`. No
  production state is involved.
