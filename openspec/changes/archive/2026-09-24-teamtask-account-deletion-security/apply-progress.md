# Apply Progress: Harden Account Deletion and Security Cleanup

**Change**: `teamtask-account-deletion-security`
**Mode**: Standard (`strict_tdd: false`, `openspec/config.yaml:6`)
**Date**: 2026-09-25
**Status**: 14/15 tasks complete — `6.2` (and gate `G.7`, the same manual matrix) NOT EXECUTED;
environment-gated (no device/emulator and only one account available).

## Completed Tasks

- [x] 1.1 Add `PasoLimpieza` + `ResultadoLimpiezaCuenta` to `AuthRepositorio.kt`
- [x] 1.2 Create `ResultadoLimpiezaCuentaTest.kt` (pure JVM JUnit 4)
- [x] 2.1 Add cleanup constants + imports (incl. `FirebaseNetworkException`, `delay`)
- [x] 2.2 Add `ejecutarPaso` / `ejecutarPasoConValor` / `esErrorTransitorio` retry wrapper
- [x] 2.3 Make `borrarDocumentosPorCampo` stop swallowing (remove internal `try/catch`)
- [x] 3.1 Add `limpiarGrupos(uid)` (enumerate, empty-delete, dissolve, member update)
- [x] 3.2 Add dissolution group-task deletion with distinct `:leer` / `:borrar` steps
- [x] 3.3 Add atomic dissolution core (delete group + `set merge` remaining member reset)
- [x] 4.1 Rebuild `limpiarDatosAsociados(uid)` as exception-safe collector
- [x] 4.2 Add field sweeps (`invitaciones`, `notificaciones` x2, `recompensas`, `canjes`)
- [x] 4.3 Add `disputas` (evidence first), `avatar`, `usuario` (last) sweeps
- [x] 4.4 Gate `FirebaseAuth.delete()` on `reporte.completado`
- [x] 5.1 Replace partial-failure `Toast` with an `AlertDialog` in `FragmentPerfil`
- [x] 6.1 Run Global Gates G.1–G.6 and record results
- [ ] 6.2 Manual Verification Matrix M1–M4 — **NOT EXECUTED**

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/src/main/java/com/example/tfg/repositorio/AuthRepositorio.kt` | Modified | Added `PasoLimpieza` and `ResultadoLimpiezaCuenta` (`completado`, `fallidos`, `mensajeFallos()`); interface signature unchanged |
| `app/src/main/java/com/example/tfg/data/firebase/AuthRepositorioFirebase.kt` | Modified | Constants + imports; retry wrapper (`MAX_INTENTOS_LIMPIEZA=3`, fixed 300 ms delay, transient-only); non-swallowing `borrarDocumentosPorCampo`; `limpiarGrupos`; `disolverGrupo` (group tasks first, atomic core, 3 sub-steps); rebuilt `limpiarDatosAsociados` collector (11 ordered steps); gated Auth deletion |
| `app/src/main/java/com/example/tfg/vista/FragmentPerfil.kt` | Modified | Partial-failure branch now an `AlertDialog` titled `"Eliminación incompleta"`; success path, button re-enable, re-auth dialog untouched |
| `app/src/test/java/com/example/tfg/repositorio/ResultadoLimpiezaCuentaTest.kt` | Created | Pure JUnit 4 test of the report aggregation invariant (4 cases) |

`app/src/main/java/com/example/tfg/viewmodel/VistaModeloAuth.kt` was **not** touched (already forwards
`Result<Unit>`). No Gradle, rules, schema, navigation, or Compose changes.

## Deviations from Design

1. **`disolverGrupo` step ids** — `design.md` reused `grupo:$gid:tareas` for both the query and the
   delete; `tasks.md` 3.2 explicitly overrides this with distinct `grupo:$gid:tareas:leer` and
   `grupo:$gid:tareas:borrar`. Implemented per `tasks.md` (the exact contract).
2. **Test location** — the apply brief's restriction list named
   `app/src/test/java/com/example/tfg/util/ResultadoLimpiezaCuentaTest.kt`, but `tasks.md` 1.2 and
   `design.md` both specify package `com.example.tfg.repositorio` at
   `app/src/test/java/com/example/tfg/repositorio/ResultadoLimpiezaCuentaTest.kt`, and the gate command
   filters `com.example.tfg.repositorio.ResultadoLimpiezaCuentaTest`. Implemented at the
   `repositorio/` path/package so the specified test filter resolves. The brief's `util/` appears to
   be a typo.

No other deviations — implementation matches design.

## Issues Found

- None blocking. The only unresolved item is the manual matrix (6.2 / G.7), which cannot run without a
  device/emulator and two accounts. Production Firestore Console rule parity stays `[UNVERIFIED]` as
  the design already records.

## Remaining Tasks

- [ ] 6.2 / G.7 — Execute Manual Verification Matrix batches M1–M4 (device + two accounts, Firebase
      project/console) and record per-row evidence in `verify-report.md`.

## Work Unit Evidence

| Evidence | Required value |
|---|---|
| Focused test command and exact result | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tfg.repositorio.ResultadoLimpiezaCuentaTest" --no-daemon --console=plain` → **BUILD SUCCESSFUL**, `ResultadoLimpiezaCuentaTest: tests=4 failures=0 errors=0` |
| Runtime harness command/scenario and exact result | `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` → **BUILD SUCCESSFUL**; `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` → **BUILD SUCCESSFUL**, 8 tests / 0 failures / 0 errors (`ExampleUnitTest` 1, `ResultadoLimpiezaCuentaTest` 4, `AvatarImagenTest` 3). Real Firebase runtime path is not executable here (no device/emulator; `strict_tdd: false`) — covered by the deferred manual matrix. |
| Rollback boundary | Revert `AuthRepositorio.kt`, `AuthRepositorioFirebase.kt`, `FragmentPerfil.kt`, and delete the new test. `VistaModeloAuth.kt`, `ParejaViewModel.kt`, `RepositorioPareja.kt`, `firestore.rules`, and Gradle are untouched, so the rollback removes no unrelated work. |

## Global Gates (recorded)

- **G.1 Compile gate** — `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` → **BUILD SUCCESSFUL** (exit 0).
- **G.2 JVM suite** — `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` → **BUILD SUCCESSFUL** (8 tests, 0 failures, 0 errors; includes the new `ResultadoLimpiezaCuentaTest`).
- **G.3 Silent cleanup catches** — `Select-String -CaseSensitive -Pattern 'No se pudieron|No se pudo borrar|No se pudo limpiar'` on `AuthRepositorioFirebase.kt` → **0 hits**.
- **G.4 Avatar coverage** — `Select-String -CaseSensitive -Pattern 'collection\("avatares"\)'` → **1 hit** (`AuthRepositorioFirebase.kt:357`).
- **G.5 Dissolution writes** — `'"grupoId" to null'` → 1; `'whereEqualTo\("grupoId"'` → 1; `'rachaDias'` → 10 (includes pre-existing usages). All patterns present.
- **G.6 Auth-deletion gate** — `Select-String -CaseSensitive -Pattern 'if \(!reporte\.completado\)'` → **1 hit**.
- **G.7 Manual matrix** — **NOT EXECUTED** (see Remaining Tasks).

### Re-verification run (apply executor, 2026-09-25)

This run re-audited the four files against `design.md`/`tasks.md` and re-ran every automatable
gate. No code change was required; the implementation already matched the design contract.

- **G.1 Compile gate** — `.\gradlew.bat :app:compileEmulatorKotlin :app:compileReleaseKotlin --no-daemon --console=plain` → **BUILD SUCCESSFUL** in 11s (42 tasks, up-to-date).
- **G.2 JVM suite** — `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` was UP-TO-DATE, so it was forced with `--rerun` → **BUILD SUCCESSFUL** (1 executed). Result XMLs: `ResultadoLimpiezaCuentaTest` **tests=4 failures=0 errors=0**, `AvatarImagenTest` 3/0/0, `ExampleUnitTest` 1/0/0 → **8 tests / 0 failures / 0 errors**.
- **G.3 Silent cleanup catches** — `Select-String -CaseSensitive -Pattern 'No se pudieron|No se pudo borrar|No se pudo limpiar'` on `AuthRepositorioFirebase.kt` → **0 hits**.
- **G.4 Avatar coverage** — `Select-String -CaseSensitive -Pattern 'collection\("avatares"\)'` → **1 hit** (`AuthRepositorioFirebase.kt:357`).
- **G.5 Dissolution writes** — `"grupoId" to null` → 1 hit; `whereEqualTo("grupoId"` → 1 hit; `rachaDias` → 10 hits. All patterns present.
- **G.6 Auth-deletion gate** — `Select-String -CaseSensitive -Pattern 'if \(!reporte\.completado\)'` → **1 hit** (`AuthRepositorioFirebase.kt:263`).
- **G.7 Manual matrix** — **NOT EXECUTED** (unchanged; environment-gated).

## Workload / PR Boundary

- Mode: single direct-to-`master` delivery (solo dev, no PRs; `auto-chain` preflight). The 400-line
  forecast is informational only and produced no slicing decision.
- Current work unit: full change (`teamtask-account-deletion-security`), all phases 1–6.
- Boundary: starts at the report types + retry engine and ends at the gated Auth deletion + UI failure
  dialog; the manual matrix (6.2) is the only out-of-run item.
- Estimated review budget impact: High (~420–560 changed lines) but explicitly not gated by the owner
  directive.

## Skill Resolution

`android-mvvm` loaded; the project stack contract wins (XML/Fragments/ViewBinding, manual service
locator, no Hilt/Room/Retrofit/Compose). Code matches the existing mixed MVVM/repository patterns.
