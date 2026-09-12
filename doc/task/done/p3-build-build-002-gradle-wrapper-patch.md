# BUILD-002 — Bump the Gradle wrapper to the latest patch release

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Build tooling |
| Estimate | 1 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Owner-picked as the last real lint category once every other remaining warning was confirmed out of code-scope. Lint's `AndroidGradlePluginVersion` warning was misread at first glance as "bump the Android Gradle Plugin" (a materially riskier change); reading the actual warning text showed it only flags the Gradle **wrapper** version (`gradle-wrapper.properties`, 8.14.3 → 8.14.5) — a patch-level bump within the same minor line, not an AGP or Gradle major/minor upgrade. `com.android.tools.build:gradle:8.13.0` (the real AGP version, in `build.gradle`) was left untouched.

## Changes

- `./gradlew wrapper --gradle-version 8.14.5` — regenerated `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, and `gradlew.bat` to the official Gradle 8.14.5 distribution. `gradlew`/`gradlew.bat` diffs are the expected upstream wrapper-script boilerplate/license-header refresh, not a project-specific edit.

## Side finding (disclosed, not expanded on)

Regenerating the wrapper properties file removed a pre-existing line, `android.enable16kPageAlignment=true`. Investigated before accepting the removal: `gradle-wrapper.properties` is read only by the wrapper bootstrap script to pick a Gradle distribution — Gradle never merges its other keys into the build, so this line was inert regardless of file location. Also decompiled the resolved `com.android.tools.build:gradle:8.13.0` jar and confirmed no `BooleanOption`/`StringOption` (or any class) references a property named `enable16kPageAlignment` — it isn't a recognized AGP 8.13.0 flag either. This was dead configuration before this change, not something this fix disabled; real 16 KB native-library page-size support (a genuine, separate Play Store requirement) is out of scope here and would need its own story if the owner wants it (related to the still-open `BUILD-001` backlog item).

## Required test matrix

- [x] Unit tests: Not applicable — a build-tooling version bump has no application logic to unit test; the full unit-test suite passing unchanged is the correct proof of no regression.
- [x] Widget/UI tests: Not applicable as new tests — the existing full widget/integration suite is the correct and sufficient regression check for a wrapper-only change.
- [x] Integration tests: Not applicable.
- [x] Smoke test the exact candidate on a real device and record model, Android version, build SHA, network state, timestamp and log evidence. — See Test evidence below.

## Test evidence

- **Wrapper sanity** — `./gradlew --version` → `Gradle 8.14.5` (confirms the new distribution downloads and runs correctly).
- **Compile** — `./gradlew :app:compileDevDebugKotlin :app:compileDevDebugJavaWithJavac`: clean.
- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures.
- **Lint** — `./gradlew :app:lintDevDebug`: 21 → 20 warnings, 0 errors. `AndroidGradlePluginVersion` fully gone. **This was the last code-fixable lint category** — all 20 remaining warnings are `ContentDescription` (7, `A11Y-001` territory, declined by the owner) or icon-asset completeness/shape issues (13, design/asset work, not code).
- **Full instrumentation regression** (all `androidTest` classes, TECNO KJ7): `adb -s 115333744A005844 shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` → **Tests run: 119, Failures: 0** — fully green, including the real-network `SuperWebViewActivity*` tests that had hung during `LINT-008`'s smoke (the device's Wi-Fi had dropped mid-session; reconnected — `adb shell svc wifi enable` plus the AP coming back — and verified with a real `ping` before this run).
- Device: TECNO KJ7 (TECNO-KJ7), serial `115333744A005844`, Android 14 (SDK 34), Wi-Fi connected and validated, 2026-09-12. Owner re-authorized this one-off (fourth time this session) with S24 Ultra still disconnected — see the `feedback-device-target` memory.

## Device policy note

- S24 Ultra (`R5CX613VZBR`) remained disconnected for this story. Asked the user again rather than assuming; owner re-authorized TECNO KJ7 specifically. Standing S24U-only policy unchanged once it reconnects.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | Verified the warning was a Gradle patch bump (not AGP) before acting, used the official `gradlew wrapper` task rather than hand-editing the URL, and investigated (rather than assumed away) the one line the regeneration dropped. |
| Unit-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable; full unit suite re-run is the right proof for a build-tooling change. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Correctly reasoned as Not applicable; full widget/integration suite re-run is the right proof. |
| Integration-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable. |
| Tecno + general smoke | 1.0 | 1.0 | Real device, fully green 119/119 full-suite regression — the first time in this session's several rounds that the previously-flaky `AppSearchIntegrationTest` and the WebView network tests both passed cleanly in the same run. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Patch-level tooling bump only; zero application code or resource changed. |
| Maintainability and documentation truth | 1.0 | 1.0 | Corrects the earlier mischaracterization of this warning as an AGP bump before acting, and documents the dead-configuration side finding with evidence rather than silently dropping or silently keeping it. |
| **Total** | **10.0** | **10.0** | **Exceeds the > 9.0 push gate.** |
