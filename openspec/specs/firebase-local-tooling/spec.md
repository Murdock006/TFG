# firebase-local-tooling

## Purpose and current observable state

This capability defines reproducible local Auth/Firestore/Storage tooling. The repository
currently has no `firebase.json`, `.firebaserc`, rules, indexes, seed harness, or emulator
configuration (`openspec/changes/teamtask-testing-emulator-strategy/exploration.md:15-18,44`).
The app declares Firebase Auth, Firestore, Storage, Realtime Database, and Analytics
(`app/build.gradle.kts:46-52`), while observed Firestore collections and Storage paths are
listed with producer/consumer evidence in `openspec/specs/firestore-contracts/spec.md:32-41,79-84`.

## Requirements

### Requirement: Local configuration SHALL be explicit and limited

`firebase.json` MUST declare Auth, Firestore, and Storage emulators, fixed documented
ports, rules/index files, and deterministic reset/import-export behavior. A project alias
policy MAY use `.firebaserc`, but local tooling MUST NOT reuse production credentials.

#### Scenario: Tooling starts the declared services
- GIVEN a supported local environment and the repository configuration
- WHEN the local emulator command starts
- THEN only Auth, Firestore, and Storage MUST be required for this capability
- AND each service MUST bind to its documented port

#### Scenario: Missing prerequisite fails clearly
- GIVEN Node.js, Firebase CLI, or Java is absent or incompatible
- WHEN the local command is invoked
- THEN it MUST fail with an actionable prerequisite error and MUST NOT target production
- Note: exact versions and CI availability are `[UNVERIFIED]` until confirmed

### Requirement: Rules and indexes SHALL be local executable contracts

`firestore.rules`, `storage.rules`, and `firestore.indexes.json` MUST cover observed
collections, Storage paths, and query patterns. They MUST be tested against emulators.
Passing local rules/indexes MUST NOT imply deployed production parity: production rules,
indexes, App Check, buckets, providers, Functions, and Console state remain `[UNVERIFIED]`.

#### Scenario: Local rule denial is testable
- GIVEN a fixture user attempts an operation outside the local rule contract
- WHEN the emulator evaluates the request
- THEN the request MUST be denied and the test MUST report the rule decision

### Requirement: Seed and reset SHALL be deterministic and namespaced

The harness MUST provide idempotent `seed` and `reset` operations. Fixture Auth users,
emails, UIDs, and documents MUST use a deterministic test namespace; reset MUST run before
and after an isolated verification and leave a known empty state. Production credentials
and unnamespaced data MUST be rejected.

#### Scenario: Repeatable fixture run
- GIVEN an empty emulator or a prior seeded namespace
- WHEN reset, seed, and seed are executed in sequence
- THEN the second seed MUST converge to the same fixture state without duplicates

### Verification, risks, and boundary

Automated checks MUST validate service ports, rules/indexes loading, reset idempotence,
namespace isolation, and the documented prerequisites. Manual checks MUST inspect the
emulator UI/logs and confirm no production project is selected. Main risk is false confidence
from local artifacts that differ from `[UNVERIFIED]` deployed configuration; deployment is
not part of this capability. Rollback deletes only local tooling artifacts and scripts, with
no production rollback. Realtime Database, Google OAuth, Analytics assertions, Functions,
FCM, WorkManager, CI enforcement, and production deployment are explicitly out of scope.
