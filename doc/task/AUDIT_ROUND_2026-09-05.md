# Backlog audit — 2026-09-05

## Scope

This round changes documentation and backlog validation only. It adds the Scrum source of truth, 40 independently deliverable stories, the reusable story template, and a deterministic schema/dependency validator. Android and `store-assets` runtime source code are unchanged.

## Round history

### Round 1 — 8.8/10

The first review found inconsistent metadata values, non-story text mixed into `Dependencies`, and these cycles:

- `CORE-001` → `CORE-002` → `CORE-001`
- `CORE-001` → `DB-001` → `CORE-001`
- `A11Y-001` → `FEAT-003` → `A11Y-001`

The round did not qualify for push. Metadata was normalized, external prerequisites were separated from story dependencies, dependency direction was corrected, and every story received the full required test matrix.

### Round 2 — 9.6/10

| Dimension | Score | Evidence |
|---|---:|---|
| Correctness and acceptance criteria | 1.9/2.0 | 40 unique stories cover fixes, enhancements, new work, ideas and exclusive Fisheye Smart features. |
| Unit-test quality and coverage | 1.5/1.5 | `validate_backlog.py` checks count, schema, filename/metadata agreement and required test markers. |
| Widget/UI-test quality and coverage | 1.0/1.0 | Reviewed as not applicable: this round has no UI or runtime behavior; every implementation story explicitly requires widget/UI coverage or a reviewed rationale. |
| Integration-test quality and coverage | 1.5/1.5 | Validator resolves all dependency IDs and rejects cycles; path and Git checks verify the complete artifact set. |
| Tecno and general smoke results | 1.0/1.0 | Reviewed as not applicable to this Markdown-only change. Each implementation story requires an exact-candidate Tecno run with device, OS, SHA, network, timestamp and logs. |
| Security, privacy and Play readiness | 1.0/1.0 | P0/P1 stories cover exposed signing material, dependency vulnerability, consent, WebView boundaries, entitlement and Play/privacy gates. Proposed diff contains no credential value. |
| Performance, lifecycle and regression risk | 0.9/1.0 | Dedicated stories cover render cost, cache pressure, refresh races, database blocking and fragment cleanup; measurements remain implementation work. |
| Maintainability and documentation truth | 0.8/1.0 | One-file-per-story workflow, common schema, status folders and validator are present. Deduction: the external reviewer reached its usage limit before issuing a final score. |
| **Total** | **9.6/10** | **Qualifies for push when the recorded checks below remain green.** |

## Verification record

- `python3 doc/task/validate_backlog.py`: pass — 40 stories, unique IDs, valid metadata, resolved dependencies, zero cycles.
- `python3 -m py_compile doc/task/validate_backlog.py`: pass.
- Repository path check for explicit source/config references: pass.
- `git diff --check` and staged diff check: pass.
- Proposed-diff credential scan: pass; references name risks but expose no secret values.
- Existing baseline retained from the source audit: `./gradlew testDevDebugUnitTest` passed 136/136; `./gradlew lintDevDebug` failed with 5 errors and 167 warnings, captured by REL-001; `store-assets` `bun run build` passed.

## Independent-agent record

- Codex independently reviewed the codebase and corroborated the main security, lifecycle, persistence and store tooling findings.
- A second Codex documentation audit identified the dependency cycles fixed in Round 2, then hit its usage limit before returning a final numeric score.
- AGY's first response analyzed the wrong repository and was discarded; a clean retry timed out.
- Claude could not run because its session limit was reached. No unavailable or invalid response was counted as evidence.

## Residual constraints

- The five Android lint errors remain release blockers and are tracked by REL-001; they are outside this documentation round.
- Signing material already present in repository history remains an incident under SEC-001. This round neither copies nor changes the credential value.
- Tecno execution becomes mandatory for any candidate that changes the Android application. It is deliberately not represented as an executed device test for this documentation-only round.
