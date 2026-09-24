# Apply Progress: TeamTask Firebase Emulator Safety Strategy

## Current bounded slice

- Work unit: WU2 — Tooling / fixtures (tasks 2.1–2.7)
- Delivery: feature-branch-chain (PR #2 targets the feature/tracker branch)
- Branch: `feat/emulator-tooling`
- Commit boundary: exactly the WU2 files (repo-root Firebase config + `tools/firebase/`)
- Not in this slice: Phase 3 / WU3 verification seam (`app/src/androidTest/**`)

## Cumulative completed tasks

### WU1 — Foundation / composition safety (prior batch)

- [x] 1.1 Added the `emulator` build type and explicit Firebase BuildConfig identity/host fields.
- [x] 1.2 Added fail-closed `FirebaseComposition` with configured Auth, Firestore, and Storage accessors.
- [x] 1.3 Changed `TFGApplication` to initialize `FirebaseComposition` synchronously without swallowing failures.
- [x] 1.4 Routed `LocalizadorServicios` through the initialized composition and removed the mutable Firebase selector.
- [x] 1.5 Routed Firebase data repositories through composition clients; no default SDK construction remains.
- [x] 1.6 Routed direct repository classes through composition clients; no default SDK construction remains.
- [x] 1.7 Routed `MainActivity`, `FragmentPareja`, and `TareasHomeAdapter` through `FirebaseComposition`; UI direct Auth/Firestore imports and construction are gone.
- [x] 1.8 Completed the `app/src/main` direct-client construction audit: only `FirebaseComposition.kt` contains FirebaseApp/Auth/Firestore/Storage construction.

### WU2 — Tooling / fixtures (this batch)

- [x] 2.1 Created `firebase.json` declaring only Auth (9099), Firestore (8080), and Storage (9199) on `127.0.0.1`, with rules/index references, disabled emulator UI, and `singleProjectMode`.
- [x] 2.2 Created `.firebaserc` with `default`/`local` aliases pinned to `demo-teamtask-local`; the alias policy and production-credential rejection are documented.
- [x] 2.3 Created `firestore.rules` covering the observed collections and query patterns; unknown collections are denied.
- [x] 2.4 Created `storage.rules` covering `avatares/{uid}/**` and `disputas/{tareaId}/**`; other paths are denied.
- [x] 2.5 Created `firestore.indexes.json` covering the observed composite `canjes` queries and the contract `tareas` index.
- [x] 2.6 Created `tools/firebase/reset.sh`, `tools/firebase/seed.sh` (plus `_guard.sh`, `fixtures.mjs`) with deterministic namespaced fixtures; seed is idempotent and reset leaves a known empty state.
- [x] 2.7 Created `tools/firebase/README.md` documenting Node.js/Firebase CLI/Java prerequisites with actionable failures and marking production parity `[UNVERIFIED]`.

## Work Unit Evidence (WU2)

| Evidence | Value |
|---|---|
| Focused test command and exact result | `node --check tools\firebase\fixtures.mjs` and `node --check tools\firebase\emulator-verify.mjs` → OK; `bash -n` on `_guard.sh`/`reset.sh`/`seed.sh` → OK; JSON parse of `firebase.json`/`.firebaserc`/`firestore.indexes.json` → OK. Standalone guard: `bash tools/firebase/_guard.sh prerequisites` → exit 0; `NODE_BIN=node-does-not-exist bash tools/firebase/_guard.sh prerequisites` → exit 1 with actionable message. |
| Runtime harness command/scenario and exact result | `firebase emulators:exec --only auth,firestore,storage --project demo-teamtask-local "node tools/firebase/emulator-verify.mjs"` → exit 0; `emulator-verify: 7/7 checks passed` (ports, indexes, reset/seed determinism, firestore-rules, storage-rules, namespace-guard, prerequisite-guard). |
| Rollback boundary | Delete `firebase.json`, `.firebaserc`, `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `tools/firebase/`, and the `.gitignore` emulator-log lines. No app code, Gradle, OpenSpec specs, or production state is touched. |

## Verification

1. Declared services only: `emulators:exec --only auth,firestore,storage` started exactly those three; ports responded `Auth 9099=200, Firestore 8080=200, Storage 9199=501` (501 confirms the Storage port is listening).
2. Rules denial: Firestore `unauth=403, own=200, create-other=403, outside-contract=403`; Storage `unauth=403, own=200, other=403`.
3. Indexes: `4 indexes declared; canjes(grupoId+fecha DESC) query accepted (200)`.
4. Reset/seed determinism: `reset empty; seed;seed converged (usuarios=2, grupos=1, auth=2)`.
5. Namespace guard: production project id `teamtask-3a855` and `GOOGLE_APPLICATION_CREDENTIALS` both rejected with actionable errors.
6. Prerequisite guard: a missing Node.js binary reported as an actionable prerequisite error.
7. `firebase use` could not run: Firebase CLI 15.31.0 requires `firebase login` and returned `Failed to authenticate`. The alias policy was instead evidenced by `.firebaserc` (`default = demo-teamtask-local`), by `emulators:exec` auto-detecting the demo project id from that config, and by the guard rejecting the production project id. Marked `partial` for this single sub-check.

## Findings and boundary

- The Storage rules runtime prints a cosmetic `NullPointerException` warning at emulator shutdown (SIGINT); it does not affect the run, which exits 0. Reported honestly rather than hidden.
- `firestore-debug.log` is generated on each emulator run; added `*-debug.log` to `.gitignore` so it is never committed.
- Local rules/indexes are an executable local contract only. Production rules, indexes, App Check, providers, buckets, Functions, FCM, and Console state remain `[UNVERIFIED]`.
- `.idea/*` edits and Phase 3 files remain untouched and unstaged; no Firebase Console or production state was changed.
- Authored lines exceed the WU2 forecast (150–250): the config + tooling + verification helper total is materially larger. Content was not compressed; see the return summary for the size recommendation.

Session: sdd-apply-teamtask-wu2-20260924
Project: TFG-TeamTask
Scope: project
Topic: sdd/teamtask-testing-emulator-strategy/apply-progress
