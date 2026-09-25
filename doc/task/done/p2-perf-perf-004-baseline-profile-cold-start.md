# PERF-004 — Add a Baseline Profile for cold-start

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Startup performance |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

No `baselineProfile` module and no shipped profile existed. `PERF-001`/`002`/`003` removed hot-path
allocation waste; a Baseline Profile is the complementary lever — AOT hints for the launcher's
cold-start path so ART doesn't have to JIT-warm it first.

Two gaps found during investigation that the original ticket didn't name:
- `reportFullyDrawn()` was never called, so `StartupTimingMetric.timeToFullDisplayMs` (the
  "icons visible" moment the ticket asks for) could not be measured at all. On a true cold start
  `ActHome.onCreate` sees an empty `RAppsSingleton` snapshot; icons arrive later via the
  `appsLoaded` observer.
- The release-signing guard's regex (`/(assemble|bundle|package).*release.*/`) also matched the
  baseline-profile plugin's synthetic `benchmarkRelease`/`nonMinifiedRelease` tasks.

## User story

As a user, I want the launcher to render its first frame as fast as possible every time it
starts, not just after ART has JIT-warmed it from repeated use.

## Changes

- `ActHome.assignApps()`: calls `reportFullyDrawn()` exactly once per Activity instance, the first
  time a non-empty visible app list is handed to `LensView`, gated by the new pure
  `UtilCalculator.shouldReportFullyDrawn(alreadyReported, hasVisibleApps)`.
- New `:baselineprofile` module (`com.android.test`, `missingDimensionStrategy 'type', 'production'`):
  `BaselineProfileGenerator` (`BaselineProfileRule`, startup profile) and `ColdStartupBenchmark`
  (`StartupTimingMetric`, `CompilationMode.None` vs `Partial(Require)`, 10 cold iterations). Both
  launch `ActHome` by explicit component — the zero-arg `startActivityAndWait()` would resolve
  `CATEGORY_LAUNCHER`, which is `ActSettings` here.
- `app/build.gradle`: applies `androidx.baselineprofile` (1.5.0), `mergeIntoMain`/`saveInSrc`,
  explicit `androidx.profileinstaller:profileinstaller:1.4.1` (was transitive 1.4.0),
  `benchmark*`/`nonMinified*` build types get `IS_ENABLE_ADMOB=false` via `androidComponents`
  (reuses the existing kill switch in `RApplication.setupAdmob()`), and the signing guard excludes
  `benchmark`/`nonminified` task names only.
- Committed profile: `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt`
  (8,916 rules, 495 in `com.mckimquyen`, incl. `RApplication`, `ActHome`, `LensView`, `BitmapCache`,
  `TaskUpdateApps`).
- Ticket's path correction: the plugin writes to `src/main/generated/baselineProfiles/`, not
  `src/main/baselineProfiles/`. Also the ticket's `am start` component omitted `.lenslauncher`.

## Required test matrix

- [x] Unit: `UtilCalculatorTest` — 4 new cases covering every `shouldReportFullyDrawn` branch.
  `scripts/test_release_signing_contract.py` — new case proving real release tasks
  (`assembleProductionRelease`, `bundleProductionRelease`, `assembleDevRelease`) still trip the
  signing guard while only the two benchmark carve-outs don't.
- [x] Widget/UI: `ActHomeFullyDrawnWidgetTest` — empty cold start does not report; warm start
  with preloaded apps reports exactly once.
- [x] Integration: `ActHomeFullyDrawnIntegrationTest` — real pipeline end
  (`RAppsSingleton.replaceSnapshot` + `AppEventManager.notifyAppsLoaded()`) flips the report on
  once; a second `appsLoaded` event does not report again. `ColdStartupBenchmark` is the
  real-process-start integration proof.
- [x] Smoke: before/after timing on the approved device (below).

## Test evidence (2026-09-25, Pixel 7 Pro `2B051FDH3006MU`, Android 17 / API 37, `user` build)

- Unit: 477/477 pass (`./gradlew testDevDebugUnitTest`); signing contract 5/5.
- Lint: 0 errors, 9 warnings (unchanged).
- Instrumentation (`adb -s … am instrument`, never Gradle `connected*`):
  - `ActHomeFullyDrawnWidgetTest` 2/2, `ActHomeFullyDrawnIntegrationTest` 1/1.
  - Full `com.mckimquyen.ui` package: 139/139. (First run was 137/139: `FrmLensWidgetTest` and
    `MaterialYouSettingsIntegrationTest` hardcoded `"21dp"` while A11Y-001 had moved the label to
    the localized `unit_dp_format` = `%1$d dp`. Fixed test-side by asserting against the same
    resource; production unchanged.)
- Build safety: with no signing configured, `assembleProductionRelease` still fails with the
  signing `GradleException`; `assembleProductionBenchmarkRelease`/`NonMinifiedRelease` build.
  Generated `BuildConfig.IS_ENABLE_ADMOB`: `productionRelease=true`, `devDebug=true`,
  `productionBenchmarkRelease=false`, `productionNonMinifiedRelease=false`.
- Packaging: `productionBenchmarkRelease` APK contains `assets/dexopt/baseline.prof` (7,915 B) +
  `baseline.profm`; the manifest carries `<profileable>` for that variant only.
- Installed-profile proof: after `INSTALL_PROFILE` broadcast to `ProfileInstallReceiver` +
  `cmd package compile -m speed-profile`, `dumpsys package dexopt` shows
  `[status=speed-profile]`.
- Cold start, Macrobenchmark (`ColdStartupBenchmark`, 10 iterations each, median):

  | Mode | timeToInitialDisplay | timeToFullDisplay |
  |---|---:|---:|
  | `CompilationMode.None` | 303.0 ms | 2,337.5 ms |
  | Baseline profile required | 285.9 ms (−5.6%) | 2,255.3 ms (−3.5%) |

- Cold start, `am start -W` `TotalTime` (7 runs each, same benchmarkRelease APK):
  speed-profile median 187 ms vs reset/verify median 205 ms (−8.8%).
- Initial `devDebug` baseline before any change: `TotalTime` median 758 ms (debug, unminified —
  not comparable to the release numbers above; recorded only for context).
- `timeToFullDisplay` is dominated by the async `PackageManager` scan (~2 s for this device's app
  inventory), which a profile can't shorten; the honest win is on the code-execution portion.

## Device policy note

S24 Ultra not connected. Owner explicitly authorized Pixel 7 Pro (`2B051FDH3006MU`) for this
session via `AskUserQuestion` (2026-09-25). TECNO KJ7 was attached and never touched. The
benchmark test APK (`com.mckimquyen.baselineprofile`) was uninstalled afterward.

## Regression check (audit round, not a CI gate)

Re-run `ColdStartupBenchmark` on the approved device; flag a regression if the
baseline-profile `timeToInitialDisplayMs` median exceeds the `None` median, or rises >15% above
285.9 ms. Regenerate the profile after significant startup-path changes:
build `:app:assembleProductionNonMinifiedRelease` + `:baselineprofile:assembleNonMinifiedRelease`,
install both with `adb -s <serial>`, run `am instrument -e class
com.mckimquyen.baselineprofile.BaselineProfileGenerator -e androidx.benchmark.enabledRules
BaselineProfile`, then pull `BaselineProfileGenerator_generate-startup-prof.txt` into both files
under `app/src/main/generated/baselineProfiles/`. (Gradle's `generateBaselineProfile` task was not
used: it runs through `connected*`, which fans out to every attached device.)

## Audit score (2026-09-25, self-audit against the README rubric)

| Dimension | Weight | Score | Note |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 1.8 | All ACs met; path corrected vs ticket. Improvement is real but modest. |
| Unit-test quality and coverage | 1.5 | 1.45 | Every gate branch + signing carve-out both directions. |
| Widget/UI-test quality and coverage | 1.0 | 0.95 | Cold/warm launch behavior. |
| Integration-test quality and coverage | 1.5 | 1.4 | Real event path + idempotency; real process-start Macrobenchmark. |
| Tecno + general smoke results | 1.0 | 0.85 | Real-device numbers on Pixel 7 Pro (authorized exception), not S24. |
| Security/privacy/Play readiness | 1.0 | 0.95 | Real release signing gate proven intact; ads off only for benchmark types. |
| Performance, lifecycle and regression risk | 1.0 | 0.9 | No UI behavior change; full `ui` package green. |
| Maintainability and documentation truth | 1.0 | 0.95 | Regeneration steps documented without the fan-out-prone Gradle task. |
| **Total** | **10.0** | **9.25** | |
