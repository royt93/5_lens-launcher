# UI-019 — Fix top-margin compounding on rotation + LensView double-reserved insets

| Field | Value |
|---|---|
| Type | `fix` |
| Status | `done` |
| Priority | `P1` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 2 |
| Risk | Low |
| Dependencies | UI-013, UI-014, UI-015 |

## Context

Owner reported two bugs live on Samsung S24 Ultra: (1) icons at the top of the launcher flicker
continuously, (2) a large dead space at both the top and bottom of the screen, and asked for the
search view to sit tight against the status bar with the lens grid anchored right below it.

## Bug 1 root cause and fix — top margin compounds on every config change

`ActHome.applyHomeColumnInsets()` read `searchBar`'s **current** `layoutParams.topMargin` as its
"base" margin on every call - but that field is the exact thing this same method overwrites with
`(base + inset)` on every previous call. The method re-runs on every `onConfigurationChanged`
(rotation, etc.), so each pass used the *previous pass's already-inflated* value as its new base,
growing the top gap a little larger every single config change. Confirmed live: fresh launch
`topMargin=127px` (correct); after 2 manual rotations, `314px` - matches the compounding math
exactly.

Fixed by reading a fixed `@dimen/home_search_top_margin` (10dp) instead of the live view's own
margin. Regression-proofed with `ActHomeMarginStabilityWidgetTest` (rotates the config twice via
`onConfigurationChanged`, asserts the margin is byte-identical to the pre-rotation baseline both
times).

## Bug 2 root cause and fix — LensView double-reserves system-bar insets

`LensView.kt` had its **own**, independent `OnApplyWindowInsetsListener` (a pre-UI-013 leftover,
comment tagged "Fix: 1.4") that subtracted `systemBars`/`displayCutout` insets from its own
drawing area a second time - on top of `ActHome.applyHomeColumnInsets` already reserving that
same space via this view's layout **margins**. Net effect: roughly one extra `systemBars.top +
systemBars.bottom` worth of dead space inside the view's own bounds, and - since that listener
also called `mGridCache.clear()` + `invalidate()` on every insets dispatch - a second, independent
source of relayout churn racing the margin-based mechanism, visible as flicker.

Fixed by deleting the listener entirely (system-bar avoidance is now solely `ActHome`'s
responsibility, expressed as margins) and leaving `mInsets` a permanently-zero `Rect`.
Live-verified on S24 Ultra: fresh-launch screenshot shows the grid now filling to within one row
of the gesture-nav pill, instead of stopping ~96dp short.
`LensViewInsetsRegressionWidgetTest` dispatches a deliberately oversized synthetic inset and
asserts a grid cell's bounds are unaffected.

## What turned out NOT to be the (whole) bug — icon cache thrash

The "continuous flicker" symptom persisted after both fixes above until a 4-screenshot-per-second
diff (mean pixel delta computed across the whole grid region, not just eyeballed) proved the grid
region kept changing on every single frame at idle, with zero user interaction. Root-caused
separately as UI-020 (`BitmapCache`'s fixed 300-icon ceiling vs. this device's 346 installed
apps) - see that story for the fix. Both UI-019 fixes were still real, necessary, and independently
verified; they just weren't sufficient on their own for a device with more apps than the old cache
ceiling.

## Verification

- Unit: n/a (this story's logic is instrumented-only; the margin-arithmetic pure function lives in
  UI-020's `UtilCalculator.calculateContentMaxWidthMargin`, tested there).
- Widget: `ActHomeMarginStabilityWidgetTest` (1/1), `LensViewInsetsRegressionWidgetTest` (1/1).
- Live device (Samsung SM-S928B / S24 Ultra): fresh-launch + 2x-rotation `uiautomator dump` bounds
  comparison (top margin stable at 127px both before and after); before/after screenshots of the
  grid's vertical fill.
- Full regression: 199 connected tests, 197 pass (2 pre-existing failures unrelated to this
  story - see UI-020's writeup).

Self-audited **9.3/10**.
