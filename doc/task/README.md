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
- ✅ FEAT-002 Smart organization completed with its CORE-001 and DB-001 foundations.
- ✅ Device policy from FEAT-001 through CORE-002: qualification and smoke used the designated TECNO KJ7 only; Pixel excluded.
- ✅ Device policy update (2026-09-06, owner decision): qualification and smoke now use the designated **Samsung S24 Ultra (SM_S928B, serial R5CX613VZBR)** only; no other physical device or emulator. Applies from CORE-002 onward. Prior TECNO KJ7 evidence in `done/` stays valid as historical record.
- ⚠️ One-off exception (2026-09-06, owner decision): S24 Ultra was not connected during SEC-003; owner explicitly approved TECNO BG6 for that round's build/run/smoke only. The S24-Ultra-only policy is unchanged for all other/future stories.

## ✅ Implemented

- REL-001 — Android lint release blockers cleared; unit, widget/UI, integration and Pixel smoke evidence recorded.
- SEC-002 — Closed by the owner's decision to exclude `store-assets`; all Wave 0 code changes in that directory were reverted.
- FEAT-001 — Search-first app navigation; local deterministic ranking, recent history, accessibility, locale/offline behavior and TECNO KJ7 performance smoke completed.
- CORE-001 — Installed-app refresh is application-owned, debounced, cancel-latest and generation guarded.
- DB-001 — Room access is asynchronous and atomic with unique component identity, exported schemas and explicit migrations.
- FEAT-002 — Local favorites, folders, pinned zones, drag/menu ordering and reinstall recovery completed on TECNO KJ7.
- CORE-002 — Icon cache identity/invalidation fixed (composite key: component + package version + icon-pack identity), self-audited 9.15/10, S24 Ultra smoke and 172 unit + 5 instrumented tests all pass (2026-09-06).
- SEC-003 — WebView and exported-component hardening: `SuperWebViewActivity`/`ActAbout`/`ActVipManagement`/`SplashAct` set `exported=false` (no legitimate external callers); exact HTTPS host allowlist (`isAllowedWebViewUrl`) gates both the initial load and in-page navigation, replacing a bypassable substring check; dangerous schemes (`javascript:`, `file:`, `content:`, `data:`, `intent:`) rejected; WebView file/content access and mixed content disabled, Safe Browsing enabled; WebView removed from its parent before `destroy()`. Also fixed an unrelated same-day `app/build.gradle` typo that broke every debug build. 18 unit + 14 instrumented (5 hardening + 4 security-integration + 5 widget) tests pass, plus 3 pre-existing tests confirmed non-regressed (17/17 connected suite); real-device proof that an external `am start` is denied (`START_CLASS_NOT_FOUND`); lint 0 errors. Self-audited **9.65/10** (2026-09-06, TECNO BG6, owner-approved one-off exception to the S24 Ultra policy — see the story file for the full rubric).

## 🟡 In progress

- SEC-001 — Local signing remediation is complete. Play Console rotation/revocation, CI secret replacement, non-production upload validation and coordinated Git-history cleanup require publisher-owner access.
- ADS-001 — Consent-driven advertising state machine handed to the Ad SDK team (2026-09-06); excluded from this repo's code-loop until they deliver. VIP-001 stays blocked on this dependency.

## 📋 Picked

| Order | Story | Priority | SP |
|---:|---|:---:|---:|
| 1 | VIP-001 Replace reusable VIP secrets | P1 | 13 → split required, blocked on ADS-001 |
| 2 | STORE-001 Harden store-assets write/upload APIs | P1 | 8 |
| 3 | STORE-002 Add revision-safe project persistence | P1 | 5 |
| 4 | LAUNCH-001 Repair and test static shortcuts | P1 | 2 |
| 5 | REL-002 Add Play/privacy release gate | P1 | 8 |
| 6 | TEST-001 Establish trustworthy CI test gates | P1 | 8 |
| 7 | TEST-002 Build complete test coverage and Tecno smoke matrix | P1 | 8 |
| 8 | AUDIT-001 Score every change round and gate push | P1 | 3 |

## ⏸️ Deferred

- Remaining P2/P3 engineering improvements stay deferred until the next owner selection.
- Fisheye Smart implementation starts after baseline frame-time and accessibility measurements exist.

## ❌ Skipped

- `store-assets` implementation work is excluded from the current product loop by owner decision. Reopen its security/store stories before deploying that tool.

## 💭 Ideas

- Accessible list mode, large-screen support, store asset automation, local insights, privacy-first monetization, and five Fisheye Smart concepts remain captured as individual `idea` stories in `todo`.

## Recommended delivery waves

1. **Wave 0 — Incident response:** SEC-001, SEC-002, REL-001.
2. **Wave 1 — Trust and correctness:** ADS-001, VIP-001 split, CORE-001, DB-001, CORE-002. SEC-003 complete.
3. **Wave 2 — Release system:** STORE-001/002, LAUNCH-001, REL-002, TEST-001.
4. **Wave 3 — Quality:** accessibility, performance, lifecycle, preferences, architecture, build reproducibility.
5. **Wave 4 — Product:** FEAT-002, CORE-001 and DB-001 are complete; select the next idea using measured retention and performance.

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
