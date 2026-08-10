# Skill Registry

**Delegator use only.** This registry contains user skills resolved for TeamTask.

## User Skills

| Trigger | Skill | Path |
|---------|-------|------|
| Android code, MVVM, Firebase, coroutines, testing | android-mvvm | C:\Users\Victor\.config\opencode\skills\android-mvvm\SKILL.md |
| creating or preparing pull requests | branch-pr | C:\Users\Victor\.config\opencode\skills\branch-pr\SKILL.md |
| oversized or stacked pull requests | chained-pr | C:\Users\Victor\.config\opencode\skills\chained-pr\SKILL.md |
| guides, READMEs, RFCs, architecture, review docs | cognitive-doc-design | C:\Users\Victor\.config\opencode\skills\cognitive-doc-design\SKILL.md |
| PR feedback, issue replies, reviews, collaboration comments | comment-writer | C:\Users\Victor\.config\opencode\skills\comment-writer\SKILL.md |
| Go tests, coverage, Bubbletea, golden files | go-testing | C:\Users\Victor\.config\opencode\skills\go-testing\SKILL.md |
| GitHub issues, bug reports, feature requests | issue-creation | C:\Users\Victor\.config\opencode\skills\issue-creation\SKILL.md |
| judgment day, dual or adversarial review | judgment-day | C:\Users\Victor\.config\opencode\skills\judgment-day\SKILL.md |
| new skills or agent instructions | skill-creator | C:\Users\Victor\.config\opencode\skills\skill-creator\SKILL.md |
| implementation, commit splitting, reviewable work units | work-unit-commits | C:\Users\Victor\.config\opencode\skills\work-unit-commits\SKILL.md |

## Compact Rules

### android-mvvm
- Preserve XML layouts, Fragments, and ViewBinding; do not introduce Compose.
- Prefer lifecycle-aware coroutine collection with `repeatOnLifecycle`.
- Prefer StateFlow and sealed UI state for new state handling.
- Follow repository boundaries and test ViewModels/repositories with fakes where possible.
- This project currently has no Hilt, Room, or Retrofit; do not assume them.
- Avoid service locators, global scopes, unhandled coroutine exceptions, and god Fragments in new code.

### branch-pr
- Every PR must link one approved issue and have exactly one `type:*` label.
- Use conventional commits and branch names matching the documented regex.
- Run required automated checks before merge; never create a blank PR.
- Include summary, changes table, test plan, and contributor checklist.
- Do not add `Co-Authored-By` trailers.

### chained-pr
- Split changes over 400 changed lines unless a maintainer accepts `size:exception`.
- Keep each PR to one reviewable deliverable work unit.
- Keep tests and docs with the work unit they verify or explain.
- State dependencies, boundaries, follow-up work, and out-of-scope items.
- Do not mix stacked and feature-branch chain strategies.

### cognitive-doc-design
- Lead with the decision or action, then disclose context progressively.
- Use short sections, headings, tables, checklists, and explicit next steps.
- Optimize review docs for verification without reconstructing the whole story.
- Keep lists flat and focused; preserve stronger repository templates when present.

### comment-writer
- Start with the actionable observation or request.
- Be warm, direct, concise, and explain the technical reason when needed.
- Match the thread language and avoid pile-ons or low-value preferences.
- In Spanish use natural Rioplatense voseo; do not use em dashes.

### go-testing
- Prefer table-driven tests with named subtests for multiple cases.
- Test behavior and state transitions, not implementation trivia.
- Use temporary directories for filesystem tests and skip slow external integrations in short mode.
- Keep golden files deterministic and rerun tests without update mode after changes.

### issue-creation
- Search for duplicates and use the bug or feature template; blank issues are disabled.
- Include every required field and pre-flight approval checks.
- New issues receive `status:needs-review`; PRs require maintainer `status:approved`.
- Questions belong in Discussions, not issues.

### judgment-day
- Resolve project skills before launching reviewers.
- Launch two blind judges in parallel with identical targets and criteria.
- Wait for both verdicts; classify confirmed, suspect, contradictory, and informational findings.
- Ask before first-round fixes and re-judge both reviewers after any fix.
- End only with APPROVED or ESCALATED.

### skill-creator
- Treat skills as runtime LLM contracts, not human documentation.
- Keep descriptions one-line, quoted, trigger-first, and under 250 characters.
- Use the required frontmatter and section order.
- Put long examples, schemas, and edge cases in local assets or references.
- Register project skills in `AGENTS.md` when applicable.

### work-unit-commits
- Commit by deliverable behavior, fix, migration, or documentation unit, not file type.
- Keep tests with the code they verify and docs with the user-visible change.
- Each unit needs one clear purpose, verification, and reasonable rollback.
- Monitor the 400-line PR budget and follow any SDD delivery strategy.
- Use conventional commit messages that describe the outcome.

## Project Conventions

| File | Path | Notes |
|------|------|-------|
| README | C:\Users\Victor\AndroidStudioProjects\TFG2\README.md | Project documentation; source code remains authoritative. |
| Gradle app config | C:\Users\Victor\AndroidStudioProjects\TFG2\app\build.gradle.kts | Android, Kotlin, SDK, dependencies, ViewBinding, ProGuard state. |
| Android manifest | C:\Users\Victor\AndroidStudioProjects\TFG2\app\src\main\AndroidManifest.xml | Application and component declarations. |
