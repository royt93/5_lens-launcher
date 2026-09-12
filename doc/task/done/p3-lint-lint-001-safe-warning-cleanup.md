# LINT-001 — Clean up the safe, unblocked subset of pre-existing lint warnings

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Code health |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Not a pre-existing backlog item — picked directly by the owner via `AskUserQuestion` as loop filler once the real backlog ran out of unblocked, non-declined work (everything else chains to `A11Y-001`, declined 2026-09-12, or to `TEST-001`/`ADS-001`/`VIP-001`/`SEC-001`, already declined or externally blocked). `./gradlew :app:lintDevDebug` reported 0 errors / 171 pre-existing warnings across 22 categories. Fixing all 171 in one pass would be large and risky (`UnusedResources` alone is 58 items, some plausibly referenced dynamically/reflectively — see the `@string` resource-indirection failure discovered in `LAUNCH-001`). This story scopes to the safe, mechanical, zero-dependency subset: `ApplySharedPref` (1), `DefaultLocale` (1), `SwitchIntDef` (1), `ObsoleteSdkInt` (6), `UselessParent` (4) — 13 warnings, 5 categories. `ContentDescription` (7) was explicitly excluded — that's accessibility surface, which is `A11Y-001` territory and the owner declined that work; fixing it here would re-introduce scope the owner just closed.

## User story

As a maintainer, I want the codebase's cheap, safe lint debt cleared without touching declined or risky scope, so lint output stays a useful signal.

## Changes

- **`ApplySharedPref`** (`ext/Activity.kt`, `rateAppInApp`): the `commit()` call already had a comment explaining it's intentional (synchronous write needed right after the Play in-app review flow completes). This was a lint false-positive against a documented decision, not a bug — suppressed with `@SuppressLint("ApplySharedPref")` referencing the existing comment, rather than "fixing" it into `apply()` and silently reintroducing the race the original author avoided.
- **`DefaultLocale`** (`util/UtilAppSorter.java`, `sortByLabelAscending`): `toLowerCase()` (implicit default locale) → `toLowerCase(Locale.getDefault())` (explicit). This sorts user-visible app labels, so the user's own locale is the correct choice (vs. `Locale.ROOT`, which is for internal/non-displayed keys) — behavior is unchanged (`toLowerCase()` was already documented to equal `toLowerCase(Locale.getDefault())`), this only makes the locale-sensitivity explicit and intentional per lint's own guidance.
- **`SwitchIntDef`** (`util/UtilNightModeUtil.kt`): both `when` branches used the deprecated `AppCompatDelegate.MODE_NIGHT_AUTO` alias, which is no longer a member of the current `@NightMode` `@IntDef` (confirmed via `javap` on the resolved `androidx.appcompat:appcompat:1.7.1` classes: `MODE_NIGHT_AUTO_TIME = 0` and `MODE_NIGHT_AUTO = 0` are the same int constant). Renamed both to `MODE_NIGHT_AUTO_TIME` — zero behavior change, same value.
- **`ObsoleteSdkInt`** (6 sites): `ActAbout.java`, `SuperWebViewActivity.kt`, `ActVipManagement.kt`, and 3 sites in `LocaleHelper.kt`. All were `Build.VERSION.SDK_INT >= X` checks where `X` < minSdk (25), so the branch was unconditionally true (or the `else` unconditionally dead). Removed the checks and the now-unreachable branches; `LocaleHelper.updateResourcesLegacy()` (the entire pre-N fallback) was deleted outright since minSdk (25) already exceeds N (24) — its only caller is gone, and the now-unused `TargetApi`/`Build` imports were removed too.
- **`UselessParent`** (4 sites): `act_about.xml` (3 cards: `cardFeatures`/`cardAbout`/`cardCredits`) and `act_vip_management.xml` (1). Each was a `FrameLayout` (holding only `background`/`elevation`/etc.) wrapping a single `LinearLayout` child with no attributes of its own beyond `orientation`. Merged into one `LinearLayout` per site carrying both sets of attributes. Verified the affected ids (`cardFeatures`/`cardAbout`/`cardCredits`) are declared as generic `View` in `ActAbout.java` (never cast to `FrameLayout`), so the type change is safe; `act_vip_management.xml`'s merged `FrameLayout` had no `id` at all.

## Required test matrix

- [x] Unit tests: `app/src/test/java/com/mckimquyen/util/UtilNightModeUtilTest.kt` (new — this class had zero prior coverage) — 5 tests covering every branch of both conversion directions (each named mode, the two "falls back to Follow System" cases, `null` input, and a full round-trip over every known mode). The `UtilAppSorter`/`LocaleHelper` changes are behavior-preserving (see Changes above), so their existing regression coverage (`AppOrganizationTest`, `LocaleHelperTest.testPersistLanguage`) is the applicable proof — both already pass unchanged; no new locale-flipping test was added since there is no actual before/after behavior difference to lock in (would only assert the status quo).
- [x] Widget/UI tests: `app/src/androidTest/java/com/mckimquyen/ui/ActAboutWidgetTest.kt` (new — `ActAbout` had zero prior test coverage) — 3 tests: launches without crash, all three cards resolve as `LinearLayout` post-merge (proving `findViewById` / the view-type change is safe), and clicking each of the three headers expands its content (proving the merged layouts still support the existing expand/collapse interaction). `SuperWebViewActivity`/`ActVipManagement`'s existing widget suites (`SuperWebViewActivityWidgetTest`, `FVipManagementWidgetTest`) already cover those two Activities and re-ran clean, so no new tests were needed there.
- [x] Integration tests: Not applicable — no persistence, cross-component, or SDK boundary is touched; every change here is either dead-code removal, a lint-satisfying annotation, or a layout merge with no new external contract. The widget tests above already cross into the real `View`/`Activity` inflation boundary, which is the only boundary this story touches.
- [x] Smoke test the exact candidate on the designated device and record model, Android version, build SHA, network state, timestamp and log evidence. — See Test evidence below.

## Test evidence

- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures (`UtilNightModeUtilTest`'s 5 new cases included).
- **Lint** — `./gradlew :app:lintDevDebug`: **171 → 157 warnings** (0 errors throughout). All 5 targeted categories (`ApplySharedPref`, `DefaultLocale`, `SwitchIntDef`, `ObsoleteSdkInt`, `UselessParent`) are fully gone from the report.
- **Widget** — `com.mckimquyen.ui.ActAboutWidgetTest`, via `adb shell am instrument` (never the Gradle `connected*Test` task): 3/3 pass.
- **Full instrumentation regression** (all `androidTest` classes, single S24 Ultra run): `adb -s R5CX613VZBR shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` → **Tests run: 112, Failures: 0**.
- Device: Samsung SM_S928B (Galaxy S24 Ultra), serial `R5CX613VZBR`, Android 16 (SDK 36), 2026-09-12. App and test APKs installed/uninstalled via `adb -s R5CX613VZBR install`/`uninstall` for every run; no leftover install afterward.

## Device policy note

- Only the S24 Ultra (`R5CX613VZBR`) was used for on-device verification, per the standing hard device-target ban reaffirmed 2026-09-12 — see the project's `feedback-device-target` memory and `doc/task/README.md`'s Product decisions log.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | All 13 targeted warnings resolved with behavior-preserving or explicitly-justified changes; none of the excluded categories (`ContentDescription`, `UnusedResources`, etc.) were touched. |
| Unit-test quality and coverage | 1.5 | 1.4 | `UtilNightModeUtilTest` closes a real pre-existing coverage gap with every branch tested; docked 0.1 for not adding a dedicated `UtilAppSorter` locale test, judged unnecessary since that change has no actual behavior difference to lock in. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | `ActAboutWidgetTest` closes a real pre-existing coverage gap (zero tests existed for this Activity) and proves the specific risk this story introduced (view-type change from the `UselessParent` merge) is safe. |
| Integration-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable — no boundary this story touches needs integration-level coverage beyond what the widget tests already exercise. |
| Tecno + general smoke | 1.0 | 0.9 | Real S24 Ultra device, full 112-test regression pass, 0 failures; docked 0.1 only because the device is S24 Ultra rather than the story template's named "Tecno" device, per the current hard device policy. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Every change is dead-code removal, an explicit-intent annotation, or a layout merge with identical rendered output; full-suite regression confirms no unrelated breakage. |
| Maintainability and documentation truth | 1.0 | 1.0 | This file records exactly which lint categories were fixed vs. deliberately excluded and why (especially the `A11Y-001`/`ContentDescription` boundary), so a future round doesn't have to rediscover the scoping decision. |
| **Total** | **10.0** | **9.8** | **Exceeds the > 9.0 push gate.** |
