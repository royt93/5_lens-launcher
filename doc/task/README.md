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
- Every delivery wave ends with a fresh code-change audit scored on a 10-point rubric. Push is allowed only when the score is **strictly greater than 9.0**, all in-scope tests pass, Tecno smoke testing passes, and no open P0/P1 remains in that wave.

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

## ✅ Implemented

- None in this audit wave. Existing historical fixes remain documented under `doc/` and must not be treated as verified if they conflict with current code or tests.

## 🟡 In progress

- None.

## 📋 Picked

| Order | Story | Priority | SP |
|---:|---|:---:|---:|
| 1 | SEC-001 Release signing incident response | P0 | 8 |
| 2 | SEC-002 Patch vulnerable Next.js dependency | P0 | 5 |
| 3 | REL-001 Clear Android lint release blockers | P0 | 3 |
| 4 | ADS-001 Consent-driven advertising state machine | P1 | 8 |
| 5 | SEC-003 Harden WebView and exported components | P1 | 5 |
| 6 | VIP-001 Replace reusable VIP secrets | P1 | 13 → split required |
| 7 | CORE-001 Serialize installed-app refresh pipeline | P1 | 8 |
| 8 | DB-001 Move Room off main thread and make counters atomic | P1 | 8 |
| 9 | CORE-002 Correct icon cache identity and invalidation | P1 | 5 |
| 10 | STORE-001 Harden store-assets write/upload APIs | P1 | 8 |
| 11 | STORE-002 Add revision-safe project persistence | P1 | 5 |
| 12 | LAUNCH-001 Repair and test static shortcuts | P1 | 2 |
| 13 | REL-002 Add Play/privacy release gate | P1 | 8 |
| 14 | TEST-001 Establish trustworthy CI test gates | P1 | 8 |
| 15 | TEST-002 Build complete test coverage and Tecno smoke matrix | P1 | 8 |
| 16 | AUDIT-001 Score every change round and gate push | P1 | 3 |

## ⏸️ Deferred

- P2/P3 engineering improvements remain in `todo`; schedule them after P0/P1 release gates are green.
- Fisheye Smart implementation starts after baseline frame-time and accessibility measurements exist.

## ❌ Skipped

- None.

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

Required push predicate: score `> 9.0/10`, zero failed required checks, zero unresolved P0/P1 in scope, a clean secret scan of the proposed diff, and a reviewed audit record. A score of exactly `9.0` does not qualify. Pre-existing repository-wide secrets remain release blockers under SEC-001 and may not be copied, modified or newly exposed by another wave.
