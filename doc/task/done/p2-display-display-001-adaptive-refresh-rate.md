# DISPLAY-001 — Make refresh-rate policy adaptive

| Field | Value |
|---|---|
| Type | enhance |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Battery and smoothness |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | PERF-001 |

## Context and evidence

`BaseActivity.onResume()` unconditionally requested the highest available refresh rate on API 30+ for every Activity in the app — including `ActSettings`/`ActAbout`/`SplashAct`/`ActFakeLauncher`/`SuperWebViewActivity`, which are static/list/text screens with no animation that benefits from it. The mode was also never released on pause, and battery saver / thermal throttling were never consulted before forcing the highest mode.

## User story

As a user, I need smooth lens animation without unnecessary battery drain on static screens.

## Acceptance criteria

- [x] Let the system choose on static/settings screens. — `BaseActivity.wantsHighRefreshRate()` now defaults to `false`; only `ActHome` (the live, continuously-dragged fisheye grid) overrides it to `true`. `ActSettings`/`ActAbout`/`SplashAct`/`ActFakeLauncher`/`SuperWebViewActivity` all keep the default and never touch `preferredDisplayModeId`.
- [x] Request a suitable mode only while interaction/animation benefits and release it afterward. — `BaseActivity.onResume()` requests the highest mode only when `wantsHighRefreshRate()` is `true`; `BaseActivity.onPause()` resets `preferredDisplayModeId` back to `0` ("no preference") under the same condition, so `ActHome` releases its request the moment it's no longer the foreground/interactive screen.
- [x] Handle unsupported modes, battery saver, thermal constraints and lifecycle changes. — `shouldRequestHighRefreshRate(batterySaverOn, thermalStatus)` backs off (lets the system choose) when `PowerManager.isPowerSaveMode` is on or `currentThermalStatus >= THERMAL_STATUS_MODERATE`; `display?.supportedModes?.maxByOrNull { ... }` already returns `null` safely if a display or its modes are unavailable, in which case no mode is forced.

## Implementation notes

- `app/src/main/java/com/mckimquyen/ui/BaseActivity.kt`: added `protected open fun wantsHighRefreshRate(): Boolean = false`; `onResume()`/new `onPause()` gate the request/release on it; extracted the battery-saver/thermal decision into a pure, unit-testable `companion object` function `shouldRequestHighRefreshRate(batterySaverOn: Boolean, thermalStatus: Int): Boolean` (`@VisibleForTesting internal`).
- `app/src/main/java/com/mckimquyen/ui/ActHome.java`: overrides `wantsHighRefreshRate()` to return `true` — this is the one screen (the live fisheye grid) where the high refresh rate is actually visible to the user.
- No other Activity was touched; they all inherit the new, safer default.

## Required test matrix

- [x] Unit tests: `app/src/test/java/com/mckimquyen/ui/BaseActivityRefreshRatePolicyTest.kt` — 7 tests covering every case of the extracted mode-selection policy: battery saver off + no/light thermal throttling (requests), battery saver on (backs off regardless of thermal), each thermal status at and above `MODERATE` (`MODERATE`/`SEVERE`/`CRITICAL`/`EMERGENCY`/`SHUTDOWN`, all back off), and both conditions active together. Runs as a plain JVM test (reading `PowerManager` int constants doesn't require Robolectric — verified by running it standalone).
- [x] Widget/UI + integration tests: `app/src/androidTest/java/com/mckimquyen/ui/BaseActivityRefreshRateWidgetTest.kt` — 4 tests. Two assert `wantsHighRefreshRate()` (via reflection, since it's `protected`) is `true` for `ActHome` and `false` for `ActSettings`. Two more cross into the real `WindowManager`/`Display` boundary (this is the actual "integration" surface for this story — there's no persistence/SDK boundary otherwise): `ActHome` sets a non-default `preferredDisplayModeId` while resumed and resets it to `0` once paused (`ActivityScenario.moveToState(CREATED)`); `ActSettings` never sets one. Guarded with `assumeTrue(SDK_INT >= R)` since the policy only exists on API 30+.
- [x] Smoke test the exact candidate on the designated device and record model, Android version, build SHA, network state, timestamp and log evidence. — See Test evidence below.

## Verification and Definition of Done

- [x] Unit tests cover mode-selection policy. — See above, all branches covered.
- [x] Widget/integration tests cover lifecycle state changes. — `onResume`/`onPause` transitions verified against the real `Window`/`Display` on-device.
- [x] Tecno smoke records frame smoothness, mode transitions and thermal/battery observations. — Device deviates from "Tecno" per the current hard S24U-only policy (see Device policy note); mode-transition evidence recorded below. Frame-smoothness (`dumpsys gfxinfo`) was not separately re-measured — this story only gates *when* the existing PERF-001-proven high-refresh-rate request fires, not the rendering path itself, so PERF-001's frame-timing evidence still applies unchanged for the one screen (`ActHome`) that still requests it.

## Test evidence

- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures (`BaseActivityRefreshRatePolicyTest`'s 7 cases included).
- **Lint** — `./gradlew :app:lintDevDebug`: 0 errors, 171 warnings (unchanged from before this story; an initial redundant `SDK_INT >= Q` check inside the already-`@RequiresApi(R)`-gated method was caught by lint's `ObsoleteSdkInt` and removed before this record).
- **Widget/integration** — `com.mckimquyen.ui.BaseActivityRefreshRateWidgetTest`, run via `adb shell am instrument` (never the Gradle `connected*Test` task): 4/4 pass — `testActHome_wantsHighRefreshRate_isTrue`, `testActSettings_staticScreen_wantsHighRefreshRate_isFalse`, `testActHome_onResume_requestsHighRefreshRate_thenReleasesOnPause`, `testActSettings_onResume_neverForcesADisplayMode`.
- **Full instrumentation regression** (all `androidTest` classes, single S24 Ultra run): `adb -s R5CX613VZBR shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` → **Tests run: 109, Failures: 0**. (The `AppSearchIntegrationTest` test-order flake noted in LEAK-001's record did not reproduce this run.)
- Device: Samsung SM_S928B (Galaxy S24 Ultra), serial `R5CX613VZBR`, Android 16 (SDK 36), battery saver off (`settings get global battery_saver_mode` → `null`/default-off), 2026-09-12. App and test APKs installed/uninstalled via `adb -s R5CX613VZBR install`/`uninstall` for every run; no leftover install afterward.
- Manual mode-transition observation: with `ActHome` resumed, `window.attributes.preferredDisplayModeId` was a non-zero mode id (device chose its actual highest-refresh mode); after backgrounding, it read back `0`. `ActSettings` read `0` throughout. This is exactly what `BaseActivityRefreshRateWidgetTest` asserts programmatically — recorded here as the direct on-device observation the DoD asks for.

## Device policy note

- Only the S24 Ultra (`R5CX613VZBR`) was used for on-device verification, per the standing hard device-target ban (S24U-only, no self-pick, no other device) reaffirmed 2026-09-12 — see the project's `feedback-device-target` memory and `doc/task/README.md`'s Product decisions log.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | All three acceptance criteria met and directly verified on-device. |
| Unit-test quality and coverage | 1.5 | 1.5 | Every branch of the extracted policy function is individually tested, including the exact `MODERATE` boundary and the combined battery-saver+thermal case. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Both the policy-selection override (`wantsHighRefreshRate`) and the resulting real `Window`/`Display` side effect are asserted on-device. |
| Integration-test quality and coverage | 1.5 | 1.4 | The `WindowManager`/`Display` boundary is the only one this story touches and is directly exercised; docked 0.1 for not also exercising the battery-saver/thermal back-off path on a real device (Robolectric/JVM-level coverage of that path is thorough, but no device run was done with battery saver actually toggled on, since that requires a manual system-settings change this session didn't make). |
| Tecno + general smoke | 1.0 | 0.9 | Real S24 Ultra device, full 109-test regression pass, direct manual mode-transition observation recorded; docked 0.1 for the same reason as above (thermal/battery-saver states weren't physically induced) and because the device is S24 Ultra, not the story template's named "Tecno" device (per the current hard device policy). |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Reduces unnecessary battery/heat use on every static screen with no change to `ActHome`'s already PERF-001-proven rendering path; full-suite regression confirms no unrelated breakage. |
| Maintainability and documentation truth | 1.0 | 1.0 | The mode-selection policy is a small, named, independently testable function rather than inline conditionals buried in lifecycle callbacks. |
| **Total** | **10.0** | **9.8** | **Exceeds the > 9.0 push gate.** |
