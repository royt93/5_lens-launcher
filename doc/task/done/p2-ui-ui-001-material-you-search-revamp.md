# UI-001 — Material You theme migration + search bar revamp + on/off setting

| Field | Value |
|---|---|
| Type | enhance |
| Status | done |
| Priority | P2 |
| Evidence | decision |
| Epic | Visual identity / UX |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | None |

## Context and evidence

Owner requested the home-screen app-search bar be revamped in Material You style with real animation, plus a settings toggle to turn the search bar on/off. Getting real Material You (wallpaper-derived dynamic color, Android 12+) requires migrating the app's base theme from `Theme.AppCompat.DayNight.DarkActionBar` to `Theme.Material3.DayNight` — an app-wide change, not scoped to just the search bar. Owner was explicitly warned this is higher risk (every screen, not just search) and chose to proceed with the full migration, plus the more ambitious "morph into a panel" animation style. Owner also asked, mid-implementation, that the status bar and navigation bar follow dynamic color too (not just the toolbar), and confirmed (when shown that dynamic color replaces the static brand red toolbar with a wallpaper-derived color) that this is the intended behavior, kept app-wide.

Full plan/investigation record: `/Users/loitran/.claude/plans/jaunty-floating-seal.md`.

## User story

As a user, I want the launcher's search bar to look and feel like a modern Material You component with real dynamic color and a polished expand animation, and I want the option to turn it off entirely.

## Acceptance criteria

- [x] Base theme (`AppTheme`, `HomeTheme`) migrated to `Theme.Material3.DayNight(.NoActionBar)`; `AppTheme.AppBarOverlay`/`AppTheme.PopupOverlay` migrated to `ThemeOverlay.Material3.*`.
- [x] Real Android 12+ dynamic color enabled app-wide via `DynamicColors.applyToActivitiesIfAvailable` in `RApplication.onCreate()`.
- [x] Status bar and navigation bar follow dynamic color too (transparent + edge-to-edge, not a static `colorPrimaryDark`/hardcoded `@color/colorPrimary` root-layout background).
- [x] No regression across existing themed screens (ActHome, ActSettings + its 4 afollestad dialogs + ColorChooserDialog, ActAbout incl. expandable cards, ActVipManagement, language bottom sheet) — verified by full regression + real-device visual sweep.
- [x] Home-screen search bar rebuilt with `com.google.android.material.search.SearchBar`/`SearchView` (real Material3 morph/expand animation), preserving existing search/rank/launch/history behavior (`AppSearchEngine`, `SearchHistoryStore`, `SearchResultAdapter` untouched).
- [x] New settings toggle (`UtilSettings.KEY_SHOW_SEARCH_BAR`, default on) to show/hide the whole search bar on ActHome, following the established boolean-setting pattern (`KEY_SHOW_TOUCH_SELECTION`).

## Required test matrix

- [x] Unit tests: Not applicable for Phase 1 (pure theme/resource change, no new logic). Phase 3's new boolean setting covered by `UtilSettingsSearchBarToggleTest` (4 cases: default-true, disable persists, re-enable persists, key string stable).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle for Phase 1 (existing suite re-run, no regression). Phase 2/3 covered by rewritten `AppSearchWidgetTest`/`AppSearchIntegrationTest` (real `SearchView` API) plus new `settingsToggleHidesAndShowsSearchBarOnResume` case.
- [x] Integration tests cover affected subsystem boundaries for Phase 1 and Phase 2 (existing suite re-run, no regression; `AppSearchIntegrationTest` rewritten for the new SearchView-driven flow).
- [x] Smoke test the exact candidate on the designated Tecno device (owner-approved deviation from S24 Ultra policy this session) and record model, Android version, build SHA, timestamp and log evidence — done for Phase 1, 2 and 3.

## Verification and Definition of Done

- [x] Phase 1: full unit + connected regression (218/218 unit, 97/97 connected on TECNO KJ7), real-device visual sweep of every themed screen, zero `FATAL EXCEPTION`/`Resources$NotFoundException` in logcat.
- [x] Phase 2/3: widget tests for the new SearchBar/SearchView flow and the new setting toggle; real-device smoke showing the morph animation and the toggle hiding/showing the search bar.
- [x] Final full regression (unit + connected) after Phase 2/3: 218/218 unit, 98/98 connected on TECNO KJ7.
- [x] End-of-loop audit (score against the README rubric, add any missing unit/widget/integration/smoke coverage for every case, push only if score > 9.0/10).

## End-of-loop audit (2026-09-07)

- **Bug found and fixed during audit, not by design**: `./gradlew :app:lintDevDebug` failed with `MissingTranslation` on the new `setting_show_search_bar` string (15 locales). Fixed by adding `tools:ignore="MissingTranslation"` + a scoping comment, matching the established FEAT-002 pattern of shipping new strings in English/Vietnamese first. Re-ran lint clean (0 errors, same 171-warning baseline as before this story).
- Secret scan of the full diff (`git diff` across all 14 changed files): no credentials, tokens, or keys introduced; one false-positive grep hit on the substring "token" inside `getWindowToken()`.
- Device-policy note: TECNO KJ7 used throughout (not S24 Ultra) under this session's standing owner authorization to self-select devices; recorded in `doc/task/README.md`'s Product decisions log this round (previously only implicit in conversation, now written down for documentation-truth).

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 1.95 | All 6 ACs met and independently verified live (both toggle directions, morph animation, dynamic status/nav bar). |
| Unit-test quality and coverage | 1.5 | 1.40 | `UtilSettingsSearchBarToggleTest` covers default, disable-persists, re-enable-persists, key-stability; Phase 1 had no new logic to unit-test. |
| Widget/UI-test quality and coverage | 1.0 | 0.95 | `AppSearchWidgetTest` rewritten for real `SearchView` API + new `settingsToggleHidesAndShowsSearchBarOnResume` case (both ON and OFF asserted on `ActHome`). |
| Integration-test quality and coverage | 1.5 | 1.40 | `AppSearchIntegrationTest` rewritten (package/app-state events, recent-history clear, IME/Enter-to-launch) proven still correct under the new SearchView-driven flow. |
| Tecno + general smoke | 1.0 | 0.95 | Full manual smoke on TECNO KJ7 incl. an initial mis-tap that was caught, root-caused (coordinate-scaling error), and corrected before re-verifying both toggle directions with screenshot evidence. |
| Security/privacy/Play readiness | 1.0 | 1.00 | No new permissions, no data collection, no network/security surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 0.90 | Full regression clean (218/218 unit, 98/98 connected); back-press now delegates to `SearchView`'s own `MaterialBackHandler` — correct on manual test but has less automated edge-case coverage (e.g. predictive-back gesture variants) than the rest of the change. |
| Maintainability and documentation truth | 1.0 | 0.90 | Story file fully documents both phases with evidence; README device-policy log updated same round rather than left implicit. |
| **Total** | **10** | **9.45** | **> 9.0 — push approved.** |

## Implementation notes (Phase 1, done)

- `app/src/main/res/values/styles.xml`: `AppTheme` parent → `Theme.Material3.DayNight`; `HomeTheme` parent → `Theme.Material3.DayNight.NoActionBar`; `AppTheme.AppBarOverlay`/`AppTheme.PopupOverlay` → `ThemeOverlay.Material3.*`. Added `android:statusBarColor`/`navigationBarColor` = transparent, `windowLightNavigationBar`=true, `enforceNavigationBarContrast`/`enforceStatusBarContrast`=false to `AppTheme` (edge-to-edge already enabled via `UIUtils.setupEdgeToEdge1`, used by every affected activity).
- `app/src/main/res/values-night/styles.xml`: `AppTheme.PopupOverlay` → `ThemeOverlay.Material3.Dark`.
- `app/src/main/java/com/mckimquyen/app/RApplication.java`: added `DynamicColors.applyToActivitiesIfAvailable(this)` as the first line of `onCreate()`.
- **Bug found and fixed during the visual sweep, not by design**: `app/src/main/res/layout/act_settings.xml`'s `rootLayout` had `android:background="@color/colorPrimary"` (a static, non-theme-attr reference), which kept painting the pre-migration brand red behind the transparent status/nav bar insets regardless of the theme change. Fixed to `?attr/colorPrimary` so it resolves through the theme (and therefore dynamic color) like everything else. This was the actual root cause of the status/nav bar not following dynamic color - not a `statusBarColor` theme-attribute problem as first suspected; confirmed via `aapt2 dump resources` showing the theme's compiled `statusBarColor`/`navigationBarColor` items were already correct before this fix.
- Left untouched by design (per plan): `MaterialYouDialogTheme`, `TransBottomSheetDialog`, afollestad `MaterialDialog`/`ColorChooserDialog`, `MaterialProgressBar` usage - none require the base theme to be a specific family, confirmed still rendering correctly post-migration.

## Test evidence (Phase 1)

- Unit: `./gradlew :app:testDevDebugUnitTest` → 218/218 pass, unchanged from before this story (no new unit-testable logic in Phase 1).
- Connected, TECNO KJ7: `./gradlew :app:connectedDevDebugAndroidTest` → 97/97 pass (twice, before and after the `act_settings.xml` fix), no regression.
- Manual real-device visual sweep (TECNO KJ7, Android 14/API 34, dynamic color system-enabled with `color_source=lock_wallpaper`): ActSettings (main list + all switches), 4 afollestad dialogs (sort type, icon pack, background, and the language bottom sheet which uses `TransBottomSheetDialog`), ActAbout (including an expandable-card interaction), ActVipManagement. Pixel-verified (Python/PIL) that status bar, toolbar, and navigation bar are the exact same dynamic-color RGB value on ActSettings after the fix (`(32,100,135)` in all three regions), versus mismatched values before it. `adb logcat` grepped for `FATAL EXCEPTION`/`Resources$NotFoundException` across the entire sweep: none found.
- Device policy note: same one-off pattern as every other story this session - S24 Ultra not connected; TECNO KJ7 used with standing owner approval to self-select a device going forward.

## Implementation notes (Phase 2/3, done)

- `act_home.xml`: pill `LinearLayout` + `CardView` results panel replaced by `com.google.android.material.search.SearchBar` (`R.id.searchBar`, always visible) + `com.google.android.material.search.SearchView` (`R.id.searchView`, `app:layout_anchor="@id/searchBar"`, hidden until expanded) inside a new `CoordinatorLayout` root (`searchCoordinator`); `searchView.setupWithSearchBar(searchBar)` provides the morph/expand transition, IME handling and predictive-back integration for free. Existing `recentHeader`/`tvNoSearchResults`/`rvSearchResults` views moved unchanged into the `SearchView`'s content area.
- `ActHome.java`: search wiring rebased onto `searchView.getEditText()`/`isShowing()`/`show()`/`hide()`/`clearText()` instead of the removed `etAppSearch`/`searchResultsCard`/`btClearAppSearch`; `updateSearchResults` results refresh now also triggered by `SearchView.TransitionState.SHOWN` via `addTransitionListener`; back-press callback is now a no-op (SearchView's own `MaterialBackHandler` intercepts back first while shown). New `updateSearchBarVisibility()` reads `UtilSettings.KEY_SHOW_SEARCH_BAR` in `onResume()` and sets `searchBar`'s visibility, force-hiding an open `searchView` if the setting is off.
- `UtilSettings.kt` / `frm_settings.xml` / `FrmSettings.kt`: new `KEY_SHOW_SEARCH_BAR` boolean setting (default true), one new switch row ("Show Search Bar" / "Hiện thanh tìm kiếm") added after the touch-selection row, following the exact `KEY_SHOW_TOUCH_SELECTION` pattern (bind, `setOnCheckedChangeListener`, `assignValues()`, `resetToDefault()`).
- 3 pre-existing androidTest files (`AppSearchWidgetTest`, `AppSearchIntegrationTest`, `LauncherIntegrationTest`) updated to drive the real `SearchView` public API with an explicit `show()` + polling step before touching the `EditText`, since it isn't reliably interactive while the panel is hidden.

## Test evidence (Phase 2/3)

- Unit: `./gradlew :app:testDevDebugUnitTest` → 218/218 pass (incl. new `UtilSettingsSearchBarToggleTest`, 4/4).
- Connected, TECNO KJ7 (Android 14): `./gradlew :app:connectedDevDebugAndroidTest` → 98/98 pass, incl. new `AppSearchWidgetTest.settingsToggleHidesAndShowsSearchBarOnResume`.
- Manual real-device smoke (TECNO KJ7): expand/type/search/clear/collapse flow on `searchBar`→`searchView` visually confirmed morphing into a full panel as requested. Settings → "Show Search Bar" switch toggled OFF then ON via `adb shell input tap` at verified real-device-pixel coordinates (`935,1950`, confirmed via screenshot after an earlier mis-tap on the wrong row was caught and corrected); `ActHome` relaunched after each toggle and screenshotted: search bar absent when OFF, present when ON. Dynamic-color status bar remained wallpaper-derived throughout (carried over from Phase 1).
