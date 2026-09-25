# Verification Report: teamtask-account-deletion-security

**Change**: `teamtask-account-deletion-security`
**Date**: 2026-09-25
**Verifier**: `sdd-verify` executor (read-only phase; no code, Gradle, specs, deltas, design, or git changed)
**Strict TDD**: `false` (`openspec/config.yaml:6`)
**Status**: Verification complete for all executable checks; the manual Firebase matrix is **NOT EXECUTED** (environment-gated).

## Scope

Inspected artifacts:

- `proposal.md`, `exploration.md`, `design.md`, `tasks.md`, `apply-progress.md`
- Deltas: `specs/account-deletion/spec.md` (12 requirements / 29 scenarios),
  `specs/firestore-contracts/spec.md`, `specs/implementation-recipes/spec.md`

Inspected implementation:

- `app/src/main/java/com/example/tfg/repositorio/AuthRepositorio.kt`
- `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt`
- `app/src/main/java/com/example/tfg/vista/FragmentPerfil.kt`
- `app/src/main/java/com/example/tfg/viewmodel/VistaModeloAuth.kt` (claimed reused)
- `app/src/main/java/com/example/tfg/viewmodel/ParejaViewModel.kt` (reactive clear)
- `app/src/test/java/com/example/tfg/repositorio/ResultadoLimpiezaCuentaTest.kt`
- `firestore.rules` (evidence)

## Observed progress

14/15 tasks complete per `tasks.md` / `apply-progress.md`. The single open item is **6.2 / gate G.7
(Manual Verification Matrix M1–M4)**, explicitly `NOT EXECUTED` and unchanged by this report. No task
checkbox was modified by verification.

## Checks (executed)

All commands run from `C:\Users\Victor\AndroidStudioProjects\TFG2`.

| ID | Check | Command | Result |
|---|---|---|---|
| G.1 | Compile gate (both build types) | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` | **PASS** — `BUILD SUCCESSFUL in 11s` (42 tasks up-to-date), exit `0` |
| G.2 | JVM suite | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` | **PASS** — `BUILD SUCCESSFUL`, exit `0`; task was `UP-TO-DATE`, so forced with `--rerun` (executed, `BUILD SUCCESSFUL in 9s`) |
| G.3 | No silent cleanup catches | `Select-String -CaseSensitive -Pattern 'No se pudieron\|No se pudo borrar\|No se pudo limpiar'` on `AuthRepositorioFirebase.kt` | **PASS** — **0 hits** |
| G.4 | Avatar coverage | `Select-String -CaseSensitive -Pattern 'collection\("avatares"\)'` | **PASS** — exactly **1 hit** (`:357`) |
| G.5 | Dissolution writes present | `'"grupoId" to null'`, `'whereEqualTo\("grupoId"'`, `'rachaDias'` | **PASS** — `"grupoId" to null` = 1 (`:476`), `whereEqualTo("grupoId"` = 1 (`:455`), `rachaDias` = 10 |
| G.6 | Auth-deletion gate present | `Select-String -CaseSensitive -Pattern 'if \(!reporte\.completado\)'` | **PASS** — **1 hit** (`:263`) |
| G.7 | Manual matrix M1–M4 | Requires device + two accounts + Firebase project | **NOT EXECUTED** — no results claimed |

### G.2 raw JUnit XML evidence (after `--rerun`)

| Test suite | tests | failures | errors | skipped |
|---|---|---|---|---|
| `com.example.tfg.ExampleUnitTest` | 1 | 0 | 0 | 0 |
| `com.example.tfg.repositorio.ResultadoLimpiezaCuentaTest` | 4 | 0 | 0 | 0 |
| `com.example.tfg.util.AvatarImagenTest` | 3 | 0 | 0 | 0 |
| **Total** | **8** | **0** | **0** | **0** |

Matches the expected `8/0` with the new report test `4/0`. The `ResultadoLimpiezaCuentaTest` cases
(`completado_allStepsSuccessful_isTrue`, `completado_oneFailedStep_isFalseAndListsTheStep`,
`mensajeFallos_duplicateDescriptions_areDeduplicated`, `completado_emptyReport_isTrue`) match the four
cases required by `design.md`.

## Contract review — `account-deletion` (12 requirements / 29 scenarios)

Static verification against source; runtime behavior is covered only by the unexecuted manual matrix.

| # | Requirement | Static result | Evidence |
|---|---|---|---|
| 1 | Re-authenticate before any mutation | MATCH | Re-auth `:247-259` precedes cleanup `:262`; wrong password returns failure `:257` without writes; recent-login surfaced `:276-278` |
| 2 | Literal `ELIMINAR` + non-blank password; info page display-only | MATCH | `FragmentPerfil.kt:258-267`; re-auth dialog `:214-275` untouched; `EliminacionCuentaActivity` not in mutation flow |
| 3 | Complete, verifiable cleanup gating Auth deletion | MATCH | Per-step report `:287-366`; retry `:370-397`; gate `:263-266` before `delete()` `:269` |
| 4 | Every collection + Storage evidence covered | MATCH | `usuarios :361`, `avatares :357`, groups `:415-445`, `tareas:creadas :298`, `tareas:asignadas :307-318` (5 reset fields), `invitaciones :323`, `notificaciones :326,:329`, `recompensas :332`, `canjes :335`, `disputas :340-353` |
| 5 | `avatares/{uid}` deleted; absent = success | MATCH | `:356-358`; Firestore `delete()` is a no-op on absent doc |
| 6 | User-created tasks deleted (documented cascade) | MATCH | `:298`; cascade documented in delta + proposal |
| 7 | Group dissolution when exactly one remains | MATCH | `limpiarGrupos :429-441`: empty → delete; one → `disolverGrupo`; 2+ → members update only |
| 8 | Remaining member balances reset incl. `rachaDias` | MATCH | `set merge` batch `:473-483` resets `grupoId=null`, `puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias` to 0; profile fields preserved by `merge` |
| 9 | Dissolved group's remaining tasks deleted | MATCH | `:454-467` (read `:leer`, chunked delete `:borrar`); group doc kept if read/delete fails for retry convergence `:459,:468` |
| 10 | Remaining member's local `grupoId` clears reactively | MATCH (static) | `ParejaViewModel.kt:88-99`: observer emits `null` → `prefs.edit().remove(keyGrupoId)`; no second mechanism added |
| 11 | Failures never reported as success | MATCH | `FragmentPerfil.kt:283-295`: success clears + navigates; failure shows `AlertDialog "Eliminación incompleta"`, re-enables button `:281`, no navigation |
| 12 | Observable outcomes for both users | MATCH (static) | Composed of requirements 3–10; runtime confirmation deferred to manual matrix |

**Result: 12/12 requirements statically satisfied; 29/29 scenarios have matching implementation paths.
None are runtime-verified in this run.**

## Findings

1. **[Low — documentation]** Stale line citations in the `firestore-contracts` delta archive tables.
   The recorded table rows cite pre-change cleanup lines (e.g. `AuthRepositorioFirebase.kt:267-271`,
   `:274-295`, `:302,312`, `:344-362`) that no longer match the post-change file (self delete now
   `:361-362`, avatar `:356-358`, group enumeration `:418-421`, `tareas` sweeps `:298,:307`, disputes
   `:340-353`). No code impact, but applying the tables verbatim at archive will record inaccurate
   citations. **Archive should refresh these line references.**
2. **[Low — documentation]** The `account-deletion` delta cites `observeUsuarios()` at
   `AuthRepositorioFirebase.kt:393-432`; the actual span is `:516-555`. Same class of drift.
3. **[Informational — scalability]** `limpiarGrupos` reads the entire `grupos` collection
   (`firestore.collection("grupos").get()`, `:419`) and filters `miembros.containsKey(uid)`
   client-side. Behavior is correct and permitted (`firestore.rules:43-47`), but it is an O(all groups)
   read rather than a membership query. Not a spec violation; noted for future optimization.
4. **[Informational — interpretation limit]** The no-remaining-members branch deletes only the group
   document and does not cascade to its tasks (`tasks.md` 3.1 documents this limit). Tasks created by
   a different author in a group that becomes empty (only the deleted uid was a member) would be
   orphaned. The `account-deletion` requirements do not mandate that cascade, so this is not a
   contract breach.
5. **[Informational — runtime gap]** Manual matrix G.7 / task 6.2 is **NOT EXECUTED**. The gate,
   retry convergence, dissolution writes, dispute-evidence deletion, and the reactive local clear are
   static observations only. No runtime PASS is claimed for them.
6. **[Informational — unchanged]** Production/Console parity for the cross-user `usuarios` update,
   the self `avatares/{uid}` delete, and the `grupos/{gid}` delete remains `[UNVERIFIED]`
   (local rules `firestore.rules:27-32,37-40,43-48` allow them).

## Historical context

`apply-progress.md` (dated 2026-09-25) claimed G.1 `BUILD SUCCESSFUL`, G.2 `8 tests / 0 failures`,
G.3 zero hits, G.4 one hit, G.5 all patterns, G.6 one hit, G.7 `NOT EXECUTED`. This verification
**independently reproduced all six automatable results** (including a forced `--rerun` of the JVM
suite and direct parsing of the JUnit XML). No prior conclusion is contradicted or withdrawn. G.7
remains `NOT EXECUTED` in both records.

## Summary

Everything executable in this environment passes: both build types compile, the JVM suite is 8/0 with
the new report test at 4/0, and all four grep audits match the design contract. Static contract review
found all 12 `account-deletion` requirements (29 scenarios) satisfied by the implementation, with the
declared `disolverGrupo` step-id and test-location deviations matching `tasks.md`. The only unresolved
verification dimension is the manual Firebase matrix (G.7 / 6.2), which cannot run without a device
and two accounts; two low-severity stale-citation issues should be corrected during archive.

## Recommended next work

- **Archive** the change after refreshing the stale line citations in the `firestore-contracts` and
  `account-deletion` deltas (findings 1–2). Archive records the actual state; no synthetic PASS.
- The developer must still execute manual matrix M1–M4 (G.7 / 6.2) against the Firebase project and
  record per-row evidence before the change is considered runtime-verified.
