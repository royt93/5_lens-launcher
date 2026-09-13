# UI-009 — Search overlay revamp: tonal scrim replaces blur, bar color harmony, section headers

| Field | Value |
|---|---|
| Type | `fix` + `feature` |
| Status | `done` |
| Priority | `P2` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 3 |
| Risk | Medium |
| Dependencies | UI-007 |

## Context

Owner reported (2026-09-13) that the UI-007 real-blur behind the search overlay caused visible
lag during the SearchBar<->SearchView morph animation (`RenderEffect` recomputes every frame while
the panel transforms), and asked for a revamp of search UI/UX/animation covering: a smooth open
transition, status/nav/content bar color harmony, and clearer visual separation between the three
search states (no data, recent data, valid results with sections). Picked via `AskUserQuestion`:
**tonal M3 scrim replacing blur** (over deferred-blur, snapshot-blur, gradient-scrim alternatives).

## Changes

1. **Blur removed, tonal scrim unified.** `ActHome.setLensBlurred`/`SEARCH_BLUR_RADIUS_PX`/
   `lensBlurActive` deleted along with `res/color-v31/search_view_scrim_background.xml` (the two-tier
   scrim+blur design). `res/color/search_view_scrim_background.xml` is now a single near-opaque
   (0.96 alpha) `?attr/colorSurfaceContainerHigh` scrim on every API level — no blur to compensate
   for, so one value covers all devices with zero per-frame cost.
2. **Status/navigation bar color harmony.** New `ActHome.setSearchSystemBarsHarmonized(boolean)`
   paints both bars the exact same `?attr/colorSurfaceContainerHigh` tone as the scrim (via
   `MaterialColors.getColor`), toggled on `SHOWING`/`HIDING` (same reasoning as the blur toggle it
   replaces — `SearchView.isShowing()` flips at the start of `HIDING`). Bar icon contrast
   (`WindowInsetsControllerCompat.setAppearanceLightStatusBars`/`...NavigationBars`) is derived from
   `ColorUtils.calculateLuminance` on the actual resolved color, not hardcoded, since dynamic color
   can land light or dark depending on wallpaper/day-night.
3. **Section headers for the three search states.** `resultsSectionHeader` ("Ứng dụng phù hợp" /
   "Matching apps", search icon) is shown for a non-empty query with matches; `recentHeader`
   ("Ứng dụng gần đây", history icon) covers the blank-query-with-history state; the empty/no-match
   `tvNoSearchResults` now carries a compound search-icon drawable instead of being text-only. The
   three states are now visually distinct instead of an unlabeled flat list.

## Bugs found and fixed during this story (not shipped)

- **`TransitionManager.beginDelayedTransition` reintroduced an overlapping-UI bug.** Tried a
  crossfade between the three states; `onTextChanged` can fire once per keystroke (confirmed live —
  `adb shell input text` and some IMEs commit char-by-char), and overlapping
  `beginDelayedTransition` calls left a stuck `GhostView` fade-out overlay rendering on top of the
  new content — the exact "UI đè lên nhau" bug this whole revamp exists to remove. **Reverted** to
  plain visibility swaps (matches the pre-revamp approach); the state-switch is a single flag write
  and instant, no jank to hide.
- **`onResume()` clobbered the harmonized bar color.** `setupTransparentSystemBarsForLollipop()`
  runs unconditionally in `onResume()`; if the activity paused/resumed (Home button, launching an
  app from a search result) while `SearchView` was still showing, the bars reset to transparent and
  the wallpaper showed through behind the search panel — caught live on TECNO KJ7. Fixed by
  reapplying `setSearchSystemBarsHarmonized(true)` in `onResume()` when `searchView.isShowing()`.
  Covered by a new widget test using `ActivityScenario.moveToState` to simulate the pause/resume.

## Tests

- `AppSearchWidgetTest`: replaced the old blur-toggle test with
  `searchShowingHarmonizesSystemBarColors_andHidingRevertsToTransparent`; added
  `systemBarsStayHarmonizedAcrossPauseAndResumeWhileSearchIsShowing` (the resume regression),
  `blankQueryWithNoHistoryShowsOnlyEmptyState`, and
  `nonEmptyQueryWithMatchesShowsResultsSectionHeader_notRecentHeader`. 14/14 pass on real hardware.
- `search_results_section` string added to every locale (`values` + 15 `values-*`), avoiding a
  `MissingTranslation` lint error.

## Verification and Definition of Done

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` 0 errors, 20 pre-existing warnings, none new (fixed 2 new
      `UseCompoundDrawables` + a `UseCompatTextViewDrawableXml` regression found along the way by
      switching to compound drawables and `app:drawable*Compat` attributes).
- [x] `am instrument` full run of `AppSearchWidgetTest` on real hardware (TECNO KJ7,
      `115333744A005844`, one-off exception): **14/14 pass**.
- [x] Live smoke on TECNO KJ7: blank-query empty state, non-empty-query-with-matches (section
      header), non-empty-query-no-match, and the Home-button pause/resume-while-showing bar-color
      regression — all captured via screenshot, all correct after the two fixes above.

Self-audited **9.3/10** (2026-09-13, TECNO KJ7). Docked from higher: the "recent apps with
history" state (as opposed to blank/no-history) was exercised only through the pre-existing
`recentHeader` visibility logic (unit/widget-test covered, unchanged conditional) rather than a
fresh live screenshot with real history populated, since the accidental app-launch during manual
testing didn't record a `launchSearchResult` history entry to reproduce it with.
