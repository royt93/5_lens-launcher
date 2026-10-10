# Repository Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Audit correctness/lifecycle, security/privacy, test gaps, and performance in risk order; fix only verified defects and add focused regression tests.

**Architecture:** Run four sequential audit passes over the existing Android app and its tests. Trace candidate behavior through callers, persistence, lifecycle, and existing tests; do not edit until a candidate has a deterministic reproduction, focused failing test, or directly demonstrable unsafe code path. Keep any fix and its regression test local to the affected behavior, then run the narrow regression and available broader verification.

**Tech Stack:** Kotlin, Java, Android SDK, Gradle, JUnit4/Robolectric/Mockito, Android instrumentation tests, Android Lint; repository scripts and backlog documentation.

## Global Constraints

- Preserve unrelated working-tree state; the initial working tree was clean.
- Audit order: correctness/lifecycle → security/privacy → test gaps → performance.
- Do not reopen owner-declined or externally blocked backlog categories without a distinct, actionable finding.
- Do not build, install, or interact with Android devices until required by a finding; if required, first follow `CLAUDE.md` device-target policy and lock one device for the session.
- Do not push or commit as part of this audit unless separately requested.
- Report only verified defects as findings; state explicitly when an area has no confirmed finding.
- Every applied fix must include a regression test proving the behavior.

---

## File Map

- `app/src/main/java/com/mckimquyen/**`: application implementation under audit; modify only the exact file implicated by a verified defect.
- `app/src/test/java/com/mckimquyen/**`: JVM regression tests; add or update the narrowest relevant test only when a verified finding needs one.
- `app/src/androidTest/java/com/mckimquyen/**`: instrumentation/widget/integration tests; use only when behavior cannot be proven at JVM level. Device execution requires the device-target policy.
- `doc/task/README.md`, `doc/task/todo/`, `doc/task/inprogress/`, `doc/task/done/`: check owner decisions, status and existing coverage context; edit only if a verified fix is completed and repository practice requires a corresponding story/status record.
- `docs/superpowers/specs/2026-10-10-repo-audit-design.md`: approved audit scope and completion criteria; read-only during execution unless user requests a design change.
- `docs/superpowers/plans/2026-10-10-repo-audit.md`: this execution checklist; check off completed audit tasks and record concise results.

No production or test source is preselected for modification: audit findings determine the minimal files, preventing speculative edits.

## Task 1: Correctness and lifecycle pass

**Files:**
- Inspect: `app/src/main/java/com/mckimquyen/**`
- Inspect: `app/src/test/java/com/mckimquyen/**`
- Inspect: `app/src/androidTest/java/com/mckimquyen/**`
- Inspect decisions: `doc/task/README.md` and relevant task files
- Modify/test: only exact implementation and regression-test files implicated by a verified finding

- [x] **Step 1: Map mutable state, async work, and lifecycle-owned resources**

Search the source for state mutation and lifecycle-sensitive constructs; follow each hit through its callers and cleanup paths. Prioritize coroutines/scopes, callbacks/listeners, observers, timers/animators, adapters, Activity/Fragment/View lifecycle, SharedPreferences/Room writes, and generation/cancellation guards. Use existing tests and task docs to understand intended behavior; do not infer a defect from a suspicious pattern alone.

Run: `rg -n 'CoroutineScope|launch\s*\{|async\s*\{|Handler|postDelayed|addListener|addObserver|registerReceiver|unregisterReceiver|onDestroy|onCleared|cancel\(|removeCallbacks|SharedPreferences|Room|synchronized|volatile' app/src/main/java app/src/test/java app/src/androidTest/java`

Expected: candidate locations for tracing; search hits alone are not findings.

- [x] **Step 2: Trace and verify each candidate before changing code**

For each candidate, trace trigger → state change → async completion/cancellation → persistence/UI observation → teardown. Inspect tests exercising the same path. A candidate qualifies only if a test can demonstrate incorrect output/state, a deterministic reproduction demonstrates it, or code-path proof establishes a concrete lifecycle/data-loss failure. If none qualify, record “No confirmed findings” for this pass.

- [x] **Step 3: Add a focused failing regression test for each confirmed issue**

Use the existing test class and fixture nearest to the behavior. Assert the externally meaningful result or persisted state, not a private implementation detail. Run only the new test first and verify it fails for the defect. For a test-only verified gap without production defect, add a test only when it protects a concrete identified risk; do not add coverage for coverage’s sake.

Run: `./gradlew testDevDebugUnitTest --tests '<fully.qualified.TestClass>'`

Expected: new regression test fails before the fix for the reproduced behavior. If only instrumentation can express the behavior, stop before invoking connected-device tasks and apply the device policy first.

- [x] **Step 4: Apply the smallest fix and verify the regression**

Change only the implicated behavior. Preserve existing API and architecture unless the evidence requires otherwise. Re-run the focused test and then the relevant existing regression class.

Run: `./gradlew testDevDebugUnitTest --tests '<fully.qualified.TestClass>'`

Expected: targeted tests pass. Record exact command result and changed paths in this plan.

- [x] **Step 5: Close the correctness/lifecycle pass**

Record confirmed findings and fixes, or explicitly record no confirmed finding. Do not continue to later passes with a failing regression or unverified code change.

## Task 2: Security and privacy pass

**Files:**
- Inspect: `app/src/main/java/com/mckimquyen/**`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/**`, build configuration and security-relevant tests
- Inspect: `doc/task/README.md` and `doc/task/RELEASE_OWNER_CHECKLIST.md` for accepted/declined scope
- Modify/test: only files implicated by a distinct verified, actionable finding

- [x] **Step 1: Inventory trust boundaries and sensitive-data paths**

Trace externally supplied intents/URIs, WebView navigation and settings, exported components, permissions, filesystem/content-URI access, logs, backup/extraction rules, network calls, and entitlement/ad state. Compare behavior against `CLAUDE.md`'s WebView and persistence constraints and relevant task decisions. Known release blockers are context, not new findings by themselves.

Run: `rg -n 'WebView|WebViewClient|shouldOverrideUrlLoading|addJavascriptInterface|EXPORTED|android:exported|uses-permission|ACTION_VIEW|Intent\.get|Uri\.parse|Log\.|Logger|http://|https://|openInputStream|openOutputStream|backup|dataExtraction|entitlement|consent' app/src/main app/build.gradle`

Expected: locations to inspect; matches alone are not vulnerabilities.

- [x] **Step 2: Prove or reject each candidate**

For each candidate, identify attacker/user-controlled input, the boundary check, and the concrete consequence if the check fails. Confirm existing tests and caller reachability. Reject theoretical issues without a reachable input and demonstrable unsafe outcome. Record “No confirmed findings” if no candidate meets this bar.

- [x] **Step 3: Test and fix only verified security/privacy defects**

Add a regression test that exercises the untrusted input or unsafe configuration and asserts rejection/protection. Verify the test fails before implementation change, apply the narrow fix, and re-run the focused test. Avoid weakening existing host allowlists, backup exclusions, or permission controls.

Run: `./gradlew testDevDebugUnitTest --tests '<fully.qualified.TestClass>'`

Expected: focused security regression passes. If instrumentation/build/device is necessary, pause and obtain/lock target according to device policy before any such command.

- [x] **Step 4: Close the security/privacy pass**

Record findings, test evidence and changes, or explicitly record no confirmed finding. Keep pre-existing owner decisions unchanged absent a distinct finding.

## Task 3: Test-gap pass

**Files:**
- Inspect: `app/src/test/java/com/mckimquyen/**`, `app/src/androidTest/java/com/mckimquyen/**`, and corresponding source
- Modify/test: only the narrowest existing test file for a concrete behavior gap; modify production source only if a test exposes a verified defect

- [x] **Step 1: Compare critical behaviors with existing tests**

Use outcomes from Tasks 1–2 and inspect tests for state transitions, error/empty paths, cancellation/reattachment, persistence/recreation, validation boundaries, and multi-lens isolation. Consult existing audit and task records to avoid duplicating tests or resurrecting declined test-process work.

Run: `find app/src/test/java app/src/androidTest/java -type f | sort`

Expected: existing test inventory to compare against concrete behavior paths; absence of a test file alone is not a finding.

- [x] **Step 2: Select only behavior gaps tied to a concrete failure mode**

For each proposed test, state input/state → expected behavior and the regression it prevents. Prefer JVM tests for pure/domain behavior, Robolectric/widget tests for UI wiring, and instrumentation only for platform behavior that cannot be tested locally. If no concrete gap is found, record “No confirmed findings.”

- [x] **Step 3: Add and validate focused tests where warranted**

Write the test to fail when the specific required behavior is removed or broken; where practical, perform a temporary local mutation and confirm failure, then restore it. Do not make production changes solely to improve test structure.

Run: `./gradlew testDevDebugUnitTest --tests '<fully.qualified.TestClass>'`

Expected: new focused test passes on final code. For instrumentation-only candidates, apply device-target policy before execution.

- [x] **Step 4: Close the test-gap pass**

Record tests added and the behavior each protects, or explicitly record no concrete uncovered risk.

## Task 4: Performance pass

**Files:**
- Inspect: `app/src/main/java/com/mckimquyen/**`, existing performance tests/benchmarks, `app/src/main/generated/baselineProfiles/**` where relevant
- Modify/test: only code and tests tied to a measured or directly demonstrable performance defect

- [x] **Step 1: Identify plausible hot paths from actual execution structure**

Prioritize `LensView` drawing/gesture paths, app search and sorting, package scanning, image/icon loading and repeated database access. Check existing caches, allocation guarantees, and performance task records first. Do not infer slowness from code appearance alone.

- [x] **Step 2: Measure or directly establish impact**

Use existing deterministic unit/performance tests or inspect a provable per-frame/repeated-work path with a focused count/allocation assertion. If credible measurement requires a build or device, stop and apply device policy before execution. If no impact can be measured or directly demonstrated, record “No confirmed findings” and do not optimize.

- [x] **Step 3: Test and fix only a confirmed performance regression**

Add a focused regression assertion for the measured work/behavior, verify it fails before the fix, apply the smallest change, then verify both the assertion and behavior correctness. Avoid broad refactors or new dependencies.

Run: `./gradlew testDevDebugUnitTest --tests '<fully.qualified.TestClass>'`

Expected: focused performance and correctness checks pass. Record measurements with device/runtime conditions if device measurement was authorized.

- [x] **Step 4: Close the performance pass**

Record measured findings and evidence, or explicitly record no confirmed performance defect.

## Task 5: Final verification and report

**Files:**
- Inspect: all files changed by verified findings/tests
- Update: this plan checklist and, only where repository practice requires after a fix, the corresponding task/feature documentation

- [x] **Step 1: Run full JVM unit suite**

Run: `./gradlew testDevDebugUnitTest`

Expected: task succeeds with zero test failures. If it fails, diagnose the failure before reporting completion; distinguish unrelated pre-existing failure only with evidence.

- [x] **Step 2: Run lint and secret scan for changed content**

Run: `./gradlew lintDevDebug`

Run: `scripts/check-secrets.sh`

Expected: report actual lint counts and secret-scan result; compare warnings/findings against baseline when available. Do not hide failures or pre-existing warnings.

- [x] **Step 3: Review diff and resource cleanup**

Run: `git diff --check && git status --short && git diff --stat`

Expected: no whitespace errors, only audit-related changes, and no untracked/generated artifacts unrelated to verified work. Inspect each changed lifecycle resource for matching disposal/cancellation and each new test for meaningful behavior assertions.

- [x] **Step 4: Publish audit outcome in terminal response and this plan**

For each of four areas, record either confirmed findings with file/line, evidence, fix/test status, or “No confirmed findings.” Include exact verification outcomes and anything not run (especially instrumentation/device checks). State remaining risks and external/device-gated work. Do not push or commit.

## Execution Handoff

Run tasks sequentially. Within each pass, do not proceed from candidate to code edit until verification establishes the defect. The plan is complete only after all four areas have an explicit result and final verification outcomes are recorded.
