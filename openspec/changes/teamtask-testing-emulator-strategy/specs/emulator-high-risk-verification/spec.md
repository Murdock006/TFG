# Delta for emulator-high-risk-verification

## Purpose and current observable state

This capability defines one focused emulator-backed proof for the highest-risk task/points
path. No Firebase emulator tests or fixtures exist; only template JUnit/AndroidX tests are
present (`openspec/changes/teamtask-testing-emulator-strategy/exploration.md:16-18`). The
full repository reserves creator points during creation (`.../data/firebase/TareaRepositorioFirebase.kt:76-115`
via `.../data/firebase/AuthRepositorioFirebase.kt:440-454`) and confirms state, executor
credits/rewards, and creator reservation release in a transaction
(`.../data/firebase/TareaRepositorioFirebase.kt:441-497`).

## ADDED Requirements

### Requirement: The seam SHALL prove create/reserve/confirm invariants

An emulator-backed email/password fixture flow MUST create a two-user, confirmable task
through the configured repository path. It MUST prove: creator `puntos` decreases by task
points, `puntosReservados` increases; completion reaches `pendiente_confirmacion`; and
confirmation reaches `confirmada`, credits executor `puntos`/`puntosRecompensa` and releases
creator reservation according to the current transaction contract. No in-memory fake may
substitute for the Firebase emulator.

#### Scenario: Two-user happy path
- GIVEN deterministic creator/executor fixtures and creator balance 1000/0
- WHEN creator creates a 100-point confirmable task, executor completes it, and creator confirms
- THEN task state and both user balances MUST match the invariant contract

#### Scenario: Insufficient reservation
- GIVEN a creator with fewer points than the requested task points
- WHEN the create operation runs
- THEN it MUST fail and MUST leave no task document or balance mutation

### Requirement: Endpoint identity SHALL be asserted before writes

The seam MUST inspect the configured mode/project/endpoint and fail before fixture seed,
Auth sign-in, or any Firestore/Storage write when identity is ambiguous or mismatched.

#### Scenario: Negative mode guard
- GIVEN emulator build plus production endpoint, or release plus emulator endpoint
- WHEN the verification starts
- THEN it MUST fail before the first write and report the mismatch

### Requirement: Fixture reset and seed SHALL bound test state

Each run MUST reset the namespaced fixture state, seed deterministic email/password users
and required `usuarios` documents, and verify the expected baseline before the flow. Cleanup
MUST run after success or failure; incomplete cleanup MUST fail the verification rather than
silently pass.

#### Scenario: Repeat run isolation
- GIVEN a failed or completed prior run
- WHEN the next run resets and seeds
- THEN it MUST observe the same baseline users/documents and no prior task residue

### Verification matrix, risks, and boundary

Automated coverage is exactly this seam plus endpoint, reset, and invariant assertions.
Manual two-user matrix MUST cover: create with/without confirmation; insufficient points;
executor completion; creator confirmation; retry after reset; and both mode mismatches.
Deferred coverage MUST be listed, not implied: invitations, listeners, rewards/canjes,
dispute Storage, account cleanup, recurrence, Google OAuth, Analytics, WorkManager/local
notifications, FCM, Realtime Database, Functions, App Check, and production rules/indexes
parity are `[UNVERIFIED]` or deferred. Main risks are repository divergence, non-atomic
reclamo behavior, and a missed direct client. Rollback removes only the seam/fixtures; it
does not delete production data or alter Console state. XML/Fragments/ViewBinding and the
existing manual locator remain the boundary; Hilt/Room/Retrofit/Compose are not assumed.
