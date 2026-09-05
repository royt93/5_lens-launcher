# Fisheye Lens Launcher — Product & Engineering Backlog

> Audit baseline: 2026-09-05 · Branch: `dev` · Scope: Android app + tests/resources/config/docs + `store-assets`

## Working agreement

- Workflow: move one story file from `todo` → `inprogress` → `done`; do not copy it.
- Filename: `{priority}-{domain}-{id}-{slug}.md`; `domain` is the owning subsystem such as `sec`, `core`, `store`, or `fish`.
- Priority: P0 blocks release, P1 is required for release candidate, P2 is the next improvement wave, P3 is maintenance.
- Estimate: Fibonacci story points `1, 2, 3, 5, 8, 13`; split anything above 8 before implementation.
- Evidence: `confirmed` is proven by current code/build, `conditional` depends on deployment/runtime, `decision` records an explicit owner choice, and `idea` is a product hypothesis.
- A story is done only after its acceptance criteria, tests, audit notes, and documentation are complete.
- Every implementation case must include the applicable unit, widget/UI, integration, and smoke coverage. Any omitted layer requires a written `Not applicable` rationale reviewed during audit.
- Every delivery wave ends with a fresh code-change audit scored on a 10-point rubric. Push is allowed only when the score is **strictly greater than 9.0**, all code-controlled in-scope checks pass, and approved-device smoke testing passes. External owner actions may remain `inprogress` only when the audit records them explicitly.

## Audit baseline

- 374 tracked files and approximately 63,791 lines were reviewed using the code knowledge graph, direct source inspection, builds, lint, and an independent Codex audit.
- `./gradlew testDevDebugUnitTest`: 136 tests passed, 0 failed.
- `./gradlew lintDevDebug`: failed with 5 errors and 167 warnings.
- `store-assets`: `bun run build` passed.
- Initial worktree was clean.
- AGY's first report referenced another repository and was rejected; its clean retry timed out. Claude could not run because its session limit was reached. Neither is treated as evidence.

## Product decisions

- ✅ Backlog strategy: balance release hardening with visible user value.
- ✅ Story structure: one Markdown file per independently deliverable story.
- ✅ Differentiation direction: prioritize **Fisheye Smart** features implemented locally on device.
- ✅ FEAT-001 search-first app navigation completed, with only its directly required CORE/A11Y integration seams included.
- ✅ Device policy from FEAT-001 onward: qualification and smoke use the designated TECNO KJ7 only; Pixel is explicitly excluded.

## ✅ Implemented

- REL-001 — Android lint release blockers cleared; unit, widget/UI, integration and Pixel smoke evidence recorded.
- SEC-002 — Closed by the owner's decision to exclude `store-assets`; all Wave 0 code changes in that directory were reverted.
- FEAT-001 — Search-first app navigation; local deterministic ranking, recent history, accessibility, locale/offline behavior and TECNO KJ7 performance smoke completed.

## 🟡 In progress

- SEC-001 — Local signing remediation is complete. Play Console rotation/revocation, CI secret replacement, non-production upload validation and coordinated Git-history cleanup require publisher-owner access.

## 📋 Picked

| Order | Story | Priority | SP |
|---:|---|:---:|---:|
| 1 | ADS-001 Consent-driven advertising state machine | P1 | 8 |
| 2 | SEC-003 Harden WebView and exported components | P1 | 5 |
| 3 | VIP-001 Replace reusable VIP secrets | P1 | 13 → split required |
| 4 | CORE-001 Serialize installed-app refresh pipeline | P1 | 8 |
| 5 | DB-001 Move Room off main thread and make counters atomic | P1 | 8 |
| 6 | CORE-002 Correct icon cache identity and invalidation | P1 | 5 |
| 7 | STORE-001 Harden store-assets write/upload APIs | P1 | 8 |
| 8 | STORE-002 Add revision-safe project persistence | P1 | 5 |
| 9 | LAUNCH-001 Repair and test static shortcuts | P1 | 2 |
| 10 | REL-002 Add Play/privacy release gate | P1 | 8 |
| 11 | TEST-001 Establish trustworthy CI test gates | P1 | 8 |
| 12 | TEST-002 Build complete test coverage and Tecno smoke matrix | P1 | 8 |
| 13 | AUDIT-001 Score every change round and gate push | P1 | 3 |

## ⏸️ Deferred

- P2/P3 engineering improvements remain in `todo`; schedule them after P0/P1 release gates are green.
- Fisheye Smart implementation starts after baseline frame-time and accessibility measurements exist.

## ❌ Skipped

- `store-assets` implementation work is excluded from the current product loop by owner decision. Reopen its security/store stories before deploying that tool.

## 💭 Ideas

- Search-first navigation, smart organization, accessible list mode, large-screen support, store asset automation, local insights, privacy-first monetization, and five Fisheye Smart concepts are captured as individual `idea` stories in `todo`.

## Recommended delivery waves

1. **Wave 0 — Incident response:** SEC-001, SEC-002, REL-001.
2. **Wave 1 — Trust and correctness:** ADS-001, SEC-003, VIP-001 split, CORE-001, DB-001, CORE-002.
3. **Wave 2 — Release system:** STORE-001/002, LAUNCH-001, REL-002, TEST-001.
4. **Wave 3 — Quality:** accessibility, performance, lifecycle, preferences, architecture, build reproducibility.
5. **Wave 4 — Product:** validate FISH-001 first, then select follow-up ideas using measured retention and performance.

## Test-layer rule inherited by every story

Every story file inherits TEST-002 even when its local verification section highlights only the most important checks:

- **Unit:** pure logic, parsing, state, validation and deterministic failure branches.
- **Widget/UI:** visible behavior, accessibility, lifecycle and user feedback.
- **Integration:** boundaries across persistence, Android components, SDKs, filesystem/API and process recreation.
- **Tecno smoke:** exact candidate build on the designated physical device, with device/build/network/log evidence.
- A layer may be marked `Not applicable` only with a concrete, reviewed reason in the story's audit record.

## Audit score and push gate

Score each round from evidence, never from task completion claims:

| Dimension | Weight |
|---|---:|
| Correctness and acceptance criteria | 2.0 |
| Unit-test quality and coverage | 1.5 |
| Widget/UI-test quality and coverage | 1.0 |
| Integration-test quality and coverage | 1.5 |
| Tecno + general smoke results | 1.0 |
| Security/privacy/Play readiness | 1.0 |
| Performance, lifecycle and regression risk | 1.0 |
| Maintainability and documentation truth | 1.0 |

Required push predicate: score `> 9.0/10`, zero failed code-controlled checks, a clean secret scan of the proposed diff, and a reviewed audit record. A score of exactly `9.0` does not qualify. External P0/P1 actions require named ownership and must remain visible under `inprogress`; pre-existing repository-wide secrets remain release blockers under SEC-001 and may not be copied, modified or newly exposed by another wave.
