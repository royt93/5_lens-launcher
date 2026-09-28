# LINT-009 — Close the last code-real lint warning (NotifyDataSetChanged)

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Tech debt |
| Estimate | 1 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Backlog `todo/` is entirely blocked or declined (ADS-001 chain, `store-assets` out of scope, `TEST-001` declined) per the 2026-09-22/27 owner decisions — owner picked "dọn nợ kỹ thuật" (tech-debt cleanup) as the next round, same pattern as `LINT-001`..`008`/`BUILD-002` when the real backlog ran dry. `./gradlew lintDevDebug` showed 9 warnings, 0 errors: 1 `NotifyDataSetChanged` (code) + 8 icon-asset design warnings (`IconLauncherShape`×5, `IconLocation`, `IconDensities`, `IconMissingDensityFolder` — graphic-asset work, not code, same category `BUILD-002` already characterized as out of scope for a code loop).

## Investigation and changes

`ActHome.onConfigurationChanged` calls `homeAppAdapter.notifyDataSetChanged()` (line 368, pre-existing). Unlike `PERF-003`'s `AppAdapter`/`SearchResultAdapter` cases (data actually changes, `DiffUtil` via `updateApps()` is correct there), this call site fires on rotation/fold — `ActHome` declares `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"` so the activity is never recreated, and the RecyclerView must be force-rebound to re-measure rows for the new column count/width. The underlying `App` list is unchanged, so `AppDiffCallback` (identity+content by `packageName`/`name`) would compute zero diffs and `updateApps()` would skip every rebind — silently breaking the post-rotation re-layout. This is the legitimate, lint-acknowledged "last resort" use of `notifyDataSetChanged`, not a real inefficiency — same pattern as `LINT-002`'s documented `MergeRootFrame`/`AppBundleLocaleChanges` false-positive suppressions. Fixed by suppressing with a comment explaining why, rather than forcing a wrong "fix" (`DiffUtil` against identical data) that would regress rotation/fold behavior.

The comment+suppression alone changes no behavior, but the behavior it documents (force-rebind on rotation) had **zero prior test coverage** — a real gap, since a future "cleanup" could silently swap it for `DiffUtil` and only break on a real device. Added regression coverage rather than leaving it as prose:

- **Unit** — none added; `AppDiffCallbackTest`'s pre-existing `` `identical lists produce no changes` `` (`app/src/test/java/com/mckimquyen/adt/AppDiffCallbackTest.kt:28`) already proves the exact premise (diffing identical `App` lists yields zero `ListUpdateCallback` events) — adding a duplicate would be pure churn.
- **Widget** — new `ActHomeMarginStabilityWidgetTest.onConfigurationChangedForceRebindsHomeAppAdapter` (`app/src/androidTest/java/com/mckimquyen/ui/ActHomeMarginStabilityWidgetTest.kt`): registers an `AdapterDataObserver` on `rvHomeAppList`'s live adapter, calls `activity.onConfigurationChanged(...)` directly, asserts `onChanged()` fired.
- **Integration** — new `AccessibleListModeIntegrationTest.onConfigurationChanged_preservesAdapterContentFromRAppsSingleton` (`app/src/androidTest/java/com/mckimquyen/launcher/AccessibleListModeIntegrationTest.kt`): same trigger, but asserts the adapter's `itemCount` after rebind still matches `RAppsSingleton`'s real visible-app count — proves the force-rebind doesn't lose/corrupt the real data snapshot it's bound to.
- [x] Smoke test on the designated device, including the exact behavior this touches (rotation).

## Test evidence

- **Unit** — `./gradlew testDevDebugUnitTest`: 580/580 pass, 0 failures (unchanged; no new unit test needed, see above).
- **Lint** — `./gradlew lintDevDebug`: `NotifyDataSetChanged` fully gone (1 → 0). 9 → 8 warnings, 0 errors; remaining 8 are all icon-asset design work, non-code.
- **Mutation check** — temporarily deleted the `homeAppAdapter.notifyDataSetChanged();` call, reran the new widget test alone on TECNO KJ7: **failed** (`AssertionError: notifyDataSetChanged() must fire AdapterDataObserver.onChanged() on config change`), confirming the test actually detects the regression it's meant to catch, not just passing vacuously. Restored the call, reran: green again.
- **Widget + Integration** (TECNO KJ7, serial `115333744A005844`, 2026-09-28) — `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest`, then `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomeMarginStabilityWidgetTest,com.mckimquyen.launcher.AccessibleListModeIntegrationTest ...`: **OK (6 tests)** — both new tests plus the 4 pre-existing tests in those classes, all green.
- **Smoke** (TECNO KJ7, 2026-09-28) — launched `ActHome`: app grid rendered (search bar + full icon grid, no crash, no ads visible). Rotated portrait → landscape via `adb shell settings put system user_rotation 1`: grid re-flowed from 7 to 10 columns with all icons intact, `logcat` showed zero `FATAL`/`AndroidRuntime` errors.

## Device policy note

- S24 Ultra not connected (only TECNO KJ7 `115333744A005844` and Pixel 7 Pro `2B051FDH3006MU` attached). Found a same-day (2026-09-27) contradiction between a prior session's memory note ("S24U only, no fallback") and this repo's own `doc/task/README.md` device-policy entry ("only TECNO, S24U/Pixel banned") — both claimed to supersede the other. Asked the owner explicitly via `AskUserQuestion` rather than guessing; owner picked **TECNO KJ7**, matching the README policy. Session-local memory updated to resolve the conflict going forward.

## Audit score (2026-09-28, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | Traced the exact reason the lint-flagged call is correct (force-rebind across a `configChanges`-suppressed rotation, not a data change), verified `DiffUtil` would silently no-op there, and closed a real pre-existing coverage gap for that behavior rather than only documenting it. |
| Unit-test quality and coverage | 1.5 | 1.5 | Correctly identified and cited existing coverage (`AppDiffCallbackTest`) proving the exact premise instead of adding a duplicate. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | New test proves the observer fires on the real code path, verified via mutation check (fails when the fix is reverted, passes when restored) — not a vacuous pass. |
| Integration-test quality and coverage | 1.5 | 1.5 | New test crosses the real boundary this rubric asks for (adapter ↔ `RAppsSingleton`), proving the force-rebind doesn't corrupt the live data snapshot. |
| Tecno + general smoke | 1.0 | 1.0 | Real device: both new instrumented tests green (6/6 including pre-existing siblings), plus a manual rotation smoke on the actual app. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Zero behavior change confirmed live; new tests are permanent regression protection against a future incorrect "fix" of this same warning. |
| Maintainability and documentation truth | 1.0 | 1.0 | Documents exactly why this lint warning is legitimate, with the failure mode named and now backed by tests that would fail if the reasoning were ever wrong. |
| **Total** | **10.0** | **10.0** | **Exceeds the > 9.0 push gate.** |
