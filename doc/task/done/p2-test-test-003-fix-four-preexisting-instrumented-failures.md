# TEST-003 — Fix 4 pre-existing instrumented test failures found by FISH-011's full-suite run

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Tech debt |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`FISH-011`'s final regression step ran the full instrumented suite with no class filter (`adb shell am instrument` with no `-e class`) for the first time in a while — most verification in this repo normally runs targeted `-e class` subsets. That surfaced 4 failures, independently confirmed via `git diff` as pre-existing and unrelated to `FISH-011`'s own diff (none of the failing files, nor `LensView`'s constructor/`init`, were touched by any `FISH-011` commit). Owner picked fixing them as the next round.

Followed `superpowers:systematic-debugging` throughout — no fix was applied until each root cause was confirmed with direct evidence (logcat instrumentation added to the failing tests, read, then removed), not guessed.

## Root causes and fixes (4 distinct issues, only 2 actually the same underlying bug)

1. **`AccessibilityActionsIntegrationTest.testLensAccessibilityAction_click_triggersLaunchCallback`** and **`MaterialYouWidgetTest.testMaterialYouCardTokens`** — both construct/inflate a `LensView` off the main thread. `LensView.init()` has needed a real `Looper` since `FISH-009` added a `ScaleGestureDetector` for pinch-to-curve; any thread without `Looper.prepare()` (the instrumentation runner's own thread, not the app's main/UI thread) crashes with `RuntimeException: Can't create handler inside thread ... that has not called Looper.prepare()`. Neither test predates `FISH-009`'s change reaching them; nobody had rerun the full suite since to catch it. **Fix:** wrap both in `instrumentation.runOnMainSync { }`, matching every other test in this codebase that constructs a `LensView`.
2. **`MaterialYouWidgetTest.testMaterialYouCardTokens`**, second bug found underneath the first — the test also does `container.getChildAt(1) as MaterialCardView`, a hardcoded child index into `frm_lens.xml` that `FISH-011`'s own Task 4 shifted by inserting a new "Share lens image" button between the lens-preview card (index 0) and the Min Icon Size card (previously index 1, now index 2). This was masked by bug #1's crash happening first during `inflate()`, so it was never actually exercised until #1 was fixed. **Fix:** updated the index to 2, with a comment naming why.
3. **`AppSearchIntegrationTest.packageAndAppStateEventsRefreshAnActiveSearch`** — asserts `findViewById(R.id.lensViews).visibility == INVISIBLE` after the app list goes empty. `R.id.lensViews` is the *per-page* `LensView` inside each `ViewPager2` page (`item_lens_page.xml`); its own `visibility` property is never explicitly toggled by production code — only the `ViewPager2` wrapping it (`lensPager`) gets `setVisibility(hasApps ? VISIBLE : INVISIBLE)` (`ActHome.java`). `git blame` dates this assertion to 2026-09-05, well before `FISH-008`'s multi-lens/`ViewPager2` migration (2026-09-26) moved visibility ownership from the single old `lensViews` instance to `lensPager` — confirmed by `item_lens_page.xml`'s own comment acknowledging exactly this move. Direct-logged evidence: at the failure point, `lensPager.visibility` was already correctly `INVISIBLE(4)` while `lensViews.visibility` stayed `VISIBLE(0)` — the production behavior was always correct; only the test checked the wrong view. **Fix:** check `lensPager`'s visibility instead.
4. **`AppSearchIntegrationTest.supportedImeActionsAndPhysicalEnterLaunchFirstResultOnly`** — intermittently failed with the search box text not clearing after an IME action, meaning `searchResultAdapter.firstOrNull()` returned `null` for a query that should have matched. Direct-logged evidence on TECNO KJ7: `adapterCount=0` at the failure point. Traced to `RApplication.onCreate()`'s unconditional `updateApps()` call, which kicks off a *real* background `PackageManager` scan once per test process with no test-environment awareness — `am instrument` reuses one process for the whole suite, so this one real scan can land and overwrite a test's fake single-app `RAppsSingleton.apps` list with the real device's apps at any point. This test set its fake data once, then called `waitUntilShowing()` (polls up to 5s) before using it — ample window for a slow-device scan to land. Reproduced reliably on TECNO KJ7 (2/2 runs), did not reproduce on Samsung S24 Ultra (faster device, apparently outrunning the race) — genuinely device/timing-dependent, not caused by test order. **Fix:** re-set the fake data (+ `notifyAppsUpdated()` + `waitForMainThread()`) immediately before the block that uses it, the same pattern this file's sibling test method already uses before each of its own checks — closes the window to zero, since everything from there to the end of that `scenario.onActivity` block runs synchronously on the main thread with nothing else able to interleave.

**Considered and rejected:** adding test-environment detection to `RApplication.updateApps()` to skip the real scan under instrumentation. That's a real, working pattern already used elsewhere in this file for `setupAdmob()` (`isTestEnv` via reflection on `InstrumentationRegistry`), but changing core app-initialization behavior under test is a bigger, riskier change than this bugfix round's scope — it could mask other real bugs a live scan currently happens to exercise. Left as-is; the test-side fix above is the minimal, root-cause-accurate change for the actual failures in hand.

## A 5th issue found, not fixed (disclosed, out of scope)

A full-suite rerun on Samsung S24 Ultra (see Device policy note) surfaced a *different*, previously-unseen failure: `ThemedIconAndStrictModeInstrumentedTest.debugProcess_hasStrictModeInstalledByApplication` — expects the real app process's `StrictMode.VmPolicy` to be non-`LAX` (installed by `RApplication.onCreate()` in debug builds) but found it `LAX`. Confirmed via `git diff` as untouched by any commit this session. Same general architecture class as a StrictMode-related test-isolation gap found and fixed earlier this session in `p2-fish-fish-011-polaroid-lens-export.md`'s Task 1 (`DebugStrictModeAndThemedIconTest`) — global, process-wide `StrictMode` state with no central test coordination, so any test that resets it to `LAX` can make an unrelated later test see the wrong value depending on run order. Not fixed here: this is a 5th, separately-scoped issue outside this round's original 4-test ask, only surfaced incidentally by an extra full-suite run on a temporarily-authorized device. Recorded in `doc/feature.md`'s Ideas section for a future round.

## Required test matrix

- [x] Unit tests: Not applicable — every change is inside `app/src/androidTest`, no production code touched this round.
- [x] Widget/Integration tests: the 4 fixed tests themselves are the test matrix; each fix was verified failing (before) and passing (after) with the real, unmodified failure/success evidence — no separate new test needed since these ARE the tests.
- [x] Smoke test: not applicable in the usual sense (no production code changed) — the real-device instrumented runs across two different physical devices are the equivalent evidence here.

## Test evidence

- **Root cause confirmation** — temporary `Log.e` instrumentation added to the two `AppSearchIntegrationTest` methods, read via `adb logcat -d -s DEBUGFISH:E`, then fully removed before the final diff. Evidence: `lensPager.vis=4` (correct) vs `lensViews.vis=0` (what the stale assertion checked) for issue #3; `adapterCount=0` for issue #4.
- **Per-fix verification** (TECNO KJ7 `115333744A005844`, until it disconnected mid-round; then Samsung S24 Ultra `R5CX613VZBR`, one-off owner-approved exception — see Device policy note): `AccessibilityActionsIntegrationTest` + `MaterialYouWidgetTest`: `OK (7 tests)` after the `runOnMainSync`/child-index fixes. `AppSearchIntegrationTest`: `OK (3 tests)`, reran 3 consecutive times with no failure (closing the timing-dependent issue #4 with confidence, not a single lucky pass).
- **Combined** — `AccessibilityActionsIntegrationTest` + `MaterialYouWidgetTest` + `AppSearchIntegrationTest` together: `OK (10 tests)`, reran twice.
- **Full unit suite** — `./gradlew testDevDebugUnitTest`: `591/591 pass, 0 failures` (unchanged from `FISH-011`'s own final count — this round touched no unit-tested code).
- **Lint** — `./gradlew lintDevDebug`: `0 errors`, `8` pre-existing warnings unchanged.
- **Full instrumented suite** — `adb shell am instrument` with no class filter, Samsung S24 Ultra: `347 tests, 1 failure` — the newly-found, separately-scoped, disclosed-not-fixed 5th issue above. The original 4 target failures did not reproduce.

## Device policy note

- Session-standing device (TECNO KJ7, `115333744A005844`) disconnected mid-investigation, along with Pixel 7 Pro. Only Samsung S24 Ultra (`R5CX613VZBR`) remained attached, which standing policy bans. Stopped and asked the owner explicitly via `AskUserQuestion` rather than guessing or silently falling back; owner approved a one-off exception for this debugging step only ("Cho phép dùng S24 Ultra tạm thời"), not a standing policy change. All work from that point on used `ANDROID_SERIAL=R5CX613VZBR` / `adb -s R5CX613VZBR`.

## Audit score (2026-09-28, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | All 4 target failures root-caused with direct evidence (not guessed) and confirmed fixed; the timing-dependent one reran 3x clean rather than trusting a single pass. |
| Unit-test quality and coverage | 1.5 | 1.5 | Correctly reasoned Not applicable — no production code changed. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Two genuinely distinct bugs in `MaterialYouWidgetTest` (Looper crash masking a stale child-index) were both found and fixed, not just the first one that happened to throw first. |
| Integration-test quality and coverage | 1.5 | 1.5 | The two `AppSearchIntegrationTest` fixes are root-cause-accurate (stale view reference; real production-code race), each backed by logcat evidence, not pattern-matched guesses. |
| Tecno + general smoke | 1.0 | 0.9 | Real-device verification on two physical devices; docked slightly for the device-policy interruption mid-round (disclosed, not hidden) rather than a clean single-device run. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched; test-only changes. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Zero production code changed - zero regression risk to the shipped app. Rejected a riskier production-code fix (test-env-aware `updateApps()` skip) in favor of the minimal test-side fix. |
| Maintainability and documentation truth | 1.0 | 1.0 | A 5th, newly-found issue was disclosed rather than left out because it wasn't part of the original ask; each fix's comment names the exact root cause for future readers. |
| **Total** | **10.0** | **9.9** | **Exceeds the > 9.0 push gate.** |
