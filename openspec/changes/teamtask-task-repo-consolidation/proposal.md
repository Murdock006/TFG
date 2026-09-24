# Proposal: TeamTask Task Repository Consolidation

## Intent

TeamTask runs two task repositories with divergent financial rules. Create/assign/read/update
already use the canonical `TareaRepositorioFirebase` (behind the `TareaRepositorio` interface),
but **complete/confirm** runs through the simplified `RepositorioTareas`, so the real completion
flow silently skips streak (`rachaDias`), `multiplicadorPuntos`, recurrence spawn, reminder
scheduling, `personalizada` normalization, and auto-assignment guards
(`TareaRepositorioFirebase.kt:466-543` vs `RepositorioTareas.kt:66-201`). This is convergence
roadmap step 2 (`docs/architecture/TEAMTASK_GUIDE.md:168`) and resolves debt items **TD-1** and
**TD-11** (`docs/architecture/TEAMTASK_GUIDE.md:142,152`).

Consolidating exposes one latent defect: the canonical no-confirmation completion path does not
consume the creator's `puntosReservados` (`TareaRepositorioFirebase.kt:405-435`), which today is
masked because that flow runs through the simplified repo. Landing the switch without the fix
would turn a dormant inconsistency into an active points leak.

## Resolved Decisions

These are settled; the proposal records them and does not re-open them.

1. **Single canonical path.** `TareaRepositorioFirebase` behind the `TareaRepositorio` interface
   becomes the only task repository. `TareasViewModel` switches from the concrete
   `RepositorioTareas` to the canonical path. Method signatures already match
   (`TareaRepositorio.kt:13-14`), so UI callers do not change.
2. **Points-leak fix.** The no-confirmation completion path in `TareaRepositorioFirebase` MUST
   consume the creator's `puntosReservados`, with the same semantics as the confirm path
   (`TareaRepositorioFirebase.kt:491-495`) and the simplified repo (`RepositorioTareas.kt:117-126`).
3. **Dead code removal.** Delete `repositorio/RepositorioTareas.kt` (whole file),
   `data/inmemory/TareaRepositorioInMemory.kt` (never wired), the unused import in
   `LocalizadorServicios.kt:7`, and the unused `TareasViewModel` members `crearTarea`,
   `_tareaCreada`/`tareaCreada`, `resetTareaCreada`. Keep the `TareaRepositorio` interface.
4. **Docs/spec updates.** TD-1 and TD-11 become resolved; guide §6/§7/§9 are updated; canonical
   specs are updated through this change's delta specs (the spec phase owns the deltas).

After consolidation, completing/confirming runs through the canonical path and therefore applies:
streak (+1 and bonus), `multiplicadorPuntos`, the reward floor (minimum 1), recurrence spawn,
reminder scheduling, and auto-assign guards. Scope of the behavior change is bounded by decision
2: `marcarCompletada` (no-confirmation) gains only the reservation-consumption fix; streak,
multiplier, recurrence, and reminder remain the confirm-flow behaviors already implemented in
`TareaRepositorioFirebase.kt:466-543`.

## Scope

### In Scope

- Repoint `TareasViewModel` from concrete `RepositorioTareas` to the `TareaRepositorio` interface
  (canonical `TareaRepositorioFirebase`).
- Fix the no-confirmation completion transaction to read the creator document and decrement
  `puntosReservados` by the task's `puntos`, coerced to ≥ 0, inside the same transaction.
- Remove dead code: `RepositorioTareas.kt`, `TareaRepositorioInMemory.kt`,
  `LocalizadorServicios.kt:7` unused import, and the unused `TareasViewModel` members.
- Update `docs/architecture/TEAMTASK_GUIDE.md` §6 (TD-1/TD-11 resolved), §7 (roadmap step 2
  status), and §9 (change status).
- Provide delta specs for the affected canonical capabilities (spec phase).
- Provide a manual verification matrix for the changed points paths.

### Out of Scope

- TD-7 (`resolverReclamo` split state/points write, `TareaRepositorioFirebase.kt:363-388`).
- Avatar authority (TD-2/TD-3), navigation/lifecycle convergence (TD-4/TD-5/TD-6/TD-15),
  account deletion (TD-13), Firestore rules/indexes (TD-9), and `Tarea.estado` typing (TD-8).
- Introducing Hilt, Room, Retrofit, Compose, or any new dependency.
- Adding automated tests (roadmap step 5 is a separate change; no test safety net exists here).
- Any change to Firestore collection/field contracts or to UI callers' signatures.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `task-domain`: the completion/confirmation points invariants change. The no-confirmation path
  now consumes the creator's `puntosReservados` (closing the leak), and the single canonical
  completion path becomes the specified behavior. TD-1 and TD-11 move from debt to resolved.
- `architecture-map`: `repositorio/RepositorioTareas` and `data/inmemory/TareaRepositorioInMemory`
  are removed from the package inventory; the task domain has one canonical repository
  (`TareaRepositorio` interface + `TareaRepositorioFirebase`). The dual-repo convergence item is
  closed.
- `implementation-recipes`: the debt register marks TD-1 and TD-11 resolved and the convergence
  roadmap records step 2 as done. (Documentation-status change; no behavioral requirement.)

## Approach

1. Repoint the dependency: change `TareasViewModel.kt:11` to accept `TareaRepositorio` and default
   it to the canonical instance (via `LocalizadorServicios.repositorioTarea`), keeping the
   existing `marcarCompletada`/`confirmarTarea` signatures so `TareasHomeAdapter` and
   `FragmentTareas` need no edits.
2. Fix the leak in `TareaRepositorioFirebase.marcarCompletada` no-confirmation transaction:
   resolve the creator reference, read it before writing, and update `puntosReservados` to
   `max(0, reservados - tareaTx.puntos)` alongside the existing executor credit and task state
   write — matching the confirm path's transaction shape.
3. Delete the dead files/import/members listed in decision 3.
4. Update the guide and emit delta specs; the spec phase writes
   `openspec/changes/teamtask-task-repo-consolidation/specs/{task-domain,architecture-map,implementation-recipes}/spec.md`.
5. Verify by compiling, auditing references, and running the manual matrix (no test suite).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `app/src/main/java/com/example/tfg/viewmodel/TareasViewModel.kt` | Modified | Depend on `TareaRepositorio`; remove `crearTarea`/`_tareaCreada`/`tareaCreada`/`resetTareaCreada` |
| `app/src/main/java/com/example/tfg/data/firebase/TareaRepositorioFirebase.kt` | Modified | Consume creator `puntosReservados` in the no-confirmation transaction (`~405-435`) |
| `app/src/main/java/com/example/tfg/repositorio/RepositorioTareas.kt` | Removed | Entire simplified repo deleted |
| `app/src/main/java/com/example/tfg/data/inmemory/TareaRepositorioInMemory.kt` | Removed | Never-wired in-memory task repo deleted |
| `app/src/main/java/com/example/tfg/service/LocalizadorServicios.kt` | Modified | Remove unused `TareaRepositorioInMemory` import |
| `app/src/main/java/com/example/tfg/repositorio/TareaRepositorio.kt` | Unchanged | Canonical interface retained |
| `docs/architecture/TEAMTASK_GUIDE.md` | Modified | §6/§7/§9 reflect TD-1/TD-11 resolved and step 2 done |
| `openspec/changes/teamtask-task-repo-consolidation/specs/**` | New | Delta specs for the three capabilities |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Silent points behavior change with no test net | High | Compile + grep/reference audit + manual matrix; explicitly note the absence of automated coverage |
| Legacy auto-assigned tasks become uncompletable under the new guard | Med | Document manual reassignment (clear `asignadoA` or reassign) and provide a data check to find tasks where `creadoPor == asignadoA` before release |
| Points leak persists if the fix is partial | Med | Implement the reservation consumption with the exact confirm-path semantics and verify with the manual matrix |
| `RepositorioTareas` still referenced after deletion | Low | Grep for `RepositorioTareas` and `TareaRepositorioInMemory`; expect zero matches in `app/src/main` |
| Behavior drift because `marcarCompletada` still lacks streak/multiplier | Low | Documented as confirm-flow-only behavior; scope is fixed by decision 2, not widened |

## Rollback Plan

The change is confined to `master` commits in a solo-developer repository (no pull requests).
Rollback is a `git revert` of this change's commit(s):

1. Revert the commit(s); this restores `RepositorioTareas.kt`, `TareaRepositorioInMemory.kt`, the
   `LocalizadorServicios.kt` import, and the original `TareasViewModel` dependency/members, and
   reverts the `TareaRepositorioFirebase` transaction edit.
2. No Firestore data rollback is implied: the fix only corrects future writes; already-persisted
   `puntosReservados` values are not rewritten.
3. Revert the guide §6/§7/§9 edits and the delta specs (or leave the archive trail intact and
   mark the change superseded).

Because the change is mostly deletions, reverting restores the prior dual-repo state without a
data migration.

## Dependencies

- Roadmap step 1 (the guide and five canonical specs) is already published.
- No new library, Gradle, or Firebase configuration is required.
- Delivery context: solo developer, commits go directly to `master`; no PR slicing or
  size-exception ceremony applies. Expected authored change size is low and dominated by
  deletions, so it is well under the 400-line review budget.

## Success Criteria

- [ ] `TareasViewModel` depends on the `TareaRepositorio` interface, not `RepositorioTareas`.
- [ ] `TareaRepositorioFirebase.marcarCompletada` (no-confirmation) decrements the creator's
      `puntosReservados` inside the transaction, coerced to ≥ 0.
- [ ] `RepositorioTareas.kt`, `TareaRepositorioInMemory.kt`, the unused import, and the unused
      `TareasViewModel` members are removed; `grep` finds no remaining references.
- [ ] The project compiles with no unresolved references.
- [ ] TD-1 and TD-11 are marked resolved in the guide and specs; roadmap step 2 is marked done.
- [ ] A manual verification matrix covers create-with-points, no-confirmation complete, and
      confirmation points/reservation invariants.
