# ARCH-002 cluster 3 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, as owner requested for all five clusters.

**Goal:** Move Home search rendering/listeners and T9 wiring from `ActHome` (HEAD `258cce74`, 1,942 lines) without changing search behavior.

**Architecture:** Java `HomeSearchController` receives the Activity, existing SearchBar/SearchView and a small Host; owns result adapter, history, listener registrations, headers and query state. `T9KeypadBinder` owns keypad click listeners. Existing `searchView` and `searchBackCallback` fields remain on ActHome for reflection; back callback delegates reset/hide to the controller. `searchBar` also remains for the insets/chrome cluster. No new dependencies or UI.

**Tech Stack:** Java/Kotlin, Material SearchView, JUnit4, Robolectric, AndroidJUnitRunner.

## Constraints

- Preserve Unicode-digit/whitespace T9 detection exactly, blank-query alphabetical fallback, history, header predicates, web fallback, launch-before-clear order, default search/T9 limits, haptic feedback.
- No resource, Room, preference key or visible behavior changes.
- Pixel 7 Pro `2B051FDH3006MU` only, per owner replacement request. Never use KJ7 or connected* tasks. Preserve `/tmp/b-arch002-pixel/before.tar`, locale `[]`, font `1.0`, Contacts denied. KJ7 restore remains pending; do not touch it.
- Keep `ActHome.searchView`, `searchBackCallback` for reflection, `openSearchFromHome` package visibility and three public test accessors unchanged.
- Build/test and lint sequentially because combined gate previously hit a missing kapt stub.

## Files

- Create `ui/home/HomeSearchController.java`, `ui/home/T9KeypadBinder.java`.
- Modify `ui/ActHome.java`.
- Create `src/test/java/com/mckimquyen/ui/home/HomeSearchLogicTest.java` and Robolectric binder test if new input decisions are introduced (otherwise existing T9 widget covers original wiring).
- All main paths above are under `app/src/main/java/com/mckimquyen/`.

## Task 1 — Characterize pure decisions

- [ ] Write JVM tests against HomeSearchController static methods, expect missing symbol before implementation.

```java
assertFalse(HomeSearchController.isT9NumericQuery(null));
assertFalse(HomeSearchController.isT9NumericQuery("   "));
assertTrue(HomeSearchController.isT9NumericQuery(" 24 7 "));
assertTrue(HomeSearchController.isT9NumericQuery("٢٤٧"));
assertFalse(HomeSearchController.isT9NumericQuery("24x"));
assertTrue(HomeSearchController.showAllAppsFallback(true, 0, 3));
assertFalse(HomeSearchController.showAllAppsFallback(false, 0, 3));
assertFalse(HomeSearchController.showAllAppsFallback(true, 1, 3));
assertFalse(HomeSearchController.showAllAppsFallback(true, 0, 0));
assertTrue(HomeSearchController.showWebFallback(false, false, false, false));
assertFalse(HomeSearchController.showWebFallback(false, false, true, false));
assertFalse(HomeSearchController.showWebFallback(false, false, false, true));
```

Also cover complete truth tables for fallback predicates, header predicates `showRecentHeader(empty, hasResults, fallback)` / `showResultsHeader(empty, hasResults)`, and `sortedByLabel` with accented names, stable ties and unchanged input list. Keep these methods static on the controller; copy numeric query loop and sort comparator from ActHome, do not replace Character.isDigit with ASCII-only checks.

- [ ] Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.ui.home.HomeSearchLogicTest'`; record red. Add minimal pure methods, rerun green.
- [ ] Mutation-check each predicate input, numeric non-digit check and reversed sort; compile success plus fresh XML required. Restore source in finally; mutation compile failure is not a caught behavioral defect.

## Task 2 — Extract T9 binding

Move `setupT9Keypad` body (ActHome lines 1299–1349) to `T9KeypadBinder.bind(Activity, EditText)`; binder stores registered views so `close()` clears click and long-click listeners. Digit arrays, append, clear, short backspace, long clear and haptics remain exactly unchanged. Controller holds returned binder, nulls it on destroy. No new edit logic.

## Task 3 — Move search block

Move these methods verbatim from HEAD `258cce74`:
- `setupSearch` 1154–1297 (but recovery-button callback remains a host callback).
- `prefillOnTap`, `updateSearchFilterChipChecked`, `updateSearchCustomization` 1351–1402.
- `isT9NumericQuery`, `updateSearchResults`, both `updateWebSearchFallback`, `sortedByLabel`, `setSearchSystemBarsHarmonized` 1404–1534.
- `launchSearchResult`, `hideSearchOverlay`, `resetPredictiveBackPreview` 1553–1589.
- `openSearchFromHome` 695–701.

Move header views, `appSearch`, `searchResults`, chips, `searchHistoryStore`, `searchResultAdapter`, `searchEnterDownHandled`, `searchLaunchCount` into controller. Controller finds owned row views in attach; SearchView.setupWithSearchBar and search bar recent-panel menu remain in ActHome setupViews, preserving order. Attach runs where old setupSearch ran, after quickActions.attach.

Host contract (nested interface; Activity not duplicated by getter):
```java
interface Host {
    UtilSettings settings();
    ArrayList<App> apps();
    List<App> appsForLens(String lensId);
    LensViewHolder lenses();
    HomeQuickActionDelegate quickActions();
    void restoreHiddenApps();
    void refreshSearchBarVisibility();
    void refreshHiddenRecovery();
    void hideHomeAppList();
    void refreshModeVisibility();
}
```

ActHome passes an anonymous Host calling existing private methods rather than implementing public methods that collide. Initialize controller after SearchBar/SearchView setup. Controller provides `text`, `adapter`, `enterDownHandled`, `launchCount`, `updateResults`, `updateCustomization`, `updateWebSearchFallback`, `open`, `hide`, `resetBackPreview`, `harmonizeSystemBars`, `trimShortcutCache`, `onDestroy`.

Keep wrappers on ActHome where referenced outside cluster: setupSearch, openSearchFromHome, updateSearchResults, updateSearchCustomization, updateWebSearchFallback(), hideSearchOverlay, setSearchSystemBarsHarmonized, resetPredictiveBackPreview. Test accessor bodies read controller state. Outside reads of appSearch use controller.text(). onTrimMemory delegates cache trimming using existing 0/.5/.75 factors. Do not move searchBackCallback or its constants: reflection test must keep finding original fields.

Listener teardown: store TextWatcher and SearchView.TransitionListener instead of anonymous registrations that cannot be removed; onDestroy removes them, clears chips/editor/tile/clear-history/web listeners, unsets searchResults adapter and closes T9 binder. Activity callbacks are released along with view references. Add lifecycle test asserting teardown removes adapter and no result update occurs when editing detached search text; run against real controller with Robolectric or instrumentation.

- [ ] Compile then run unit/build: `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`.
- [ ] Run lint separately: `./gradlew lintDevDebug`.

## Task 4 — Device verification and close cluster

- [ ] Confirm Pixel awake/unlocked, backup before install (do not overwrite original backup). Use original backup for restoration if still matches previous restored state; verify hash of tar members first.
- [ ] Install app/test APKs only on serial `2B051FDH3006MU`.
- [ ] Run runner `/tmp/run_acthome_pixel.py`, extending existing 43 classes with `com.mckimquyen.ui.SearchResultCountHeaderWidgetTest`, `PinyinSearchWidgetTest`, `RecentInstallSearchWidgetTest` and `com.mckimquyen.search.AppSearchIntegrationTest`, `SearchHistoryStoreTest`, plus lifecycle test for this cluster. Each class gets timeout and full log; any red stops work before next cluster.
- [ ] Restore `databases/shared_prefs`, compare every member byte hash, locale/font/Contacts; no screenshot steps through ads.
- [ ] `git diff --check`, `bash scripts/check-secrets.sh`; record unit count, lint, actual lines and device outcomes in ARCH-002 story and cluster plan.
- [ ] Commit only cluster files/docs. Do not push until whole ARCH-002 audit >9.0. Leave `.idea` untouched.

## Self-review

All source members read from current ActHome, not old line references. Reflection storage stays, launch order and T9 detection unchanged, pure tests plus real UI regressions and teardown covered. New Host is nested and uses existing collaborators; no factory/state framework or extra persistence introduced.
