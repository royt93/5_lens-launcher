# FISH-016 — iOS-Style Pull-Down Search Design

- **Date:** 2026-10-02
- **Status:** Approved
- **Story:** `p2-fish-fish-016-ios-style-pull-down-search` (3 SP)
- **Target branch:** `dev`

## 1. Problem

Clean the Lens hides the Home search bar. `ActHome` has no second path into full search, so a user must leave Home, open Settings, disable Clean mode, then return before searching.

The obvious iOS-style interaction is a one-finger downward swipe. This app already uses one-finger movement across most of `LensView` for fisheye panning, two fingers for curvature adjustment, horizontal swipes for lens paging, and long-press for quick actions. A global downward swipe would therefore steal an established primary interaction.

## 2. Goals

- Open full app search by swiping downward from the top activation zone of `LensView`.
- Keep the gesture identical whether Clean mode is enabled or disabled.
- Preserve fisheye panning outside that activation zone.
- Add an accessible, discoverable fallback: "Search apps" in the existing empty-space long-press menu.
- Add a second menu item to toggle Clean mode without leaving Home.
- Closing search must restore the search bar to the visibility dictated by current settings, never force it visible.

## 3. Non-Goals

- No three-finger or two-finger search gesture.
- No global anywhere-on-screen downward swipe; it conflicts with fisheye panning.
- No onboarding overlay, animation tutorial, or persistent hint.
- No new preference key, database table, dependency, or string resource. Existing translated `search_apps_hint` and `setting_clean_lens_mode` strings are reused.
- No change to pinch curvature, horizontal lens paging, or icon quick-action behavior.

## 4. Interaction Design

### 4.1 Activation zone

The activation zone is the top 20% of the laid-out `LensView`, measured from its own top edge, not from the physical screen edge. This avoids the status bar and display cutout while keeping the gesture near the iOS Home-screen convention.

The 20% value is a named constant, not an unexplained literal. It is an initial measured choice and must be checked on TECNO KJ7 during smoke testing; changing it later requires changing only that constant and its tests.

### 4.2 Gesture recognition

A touch becomes a search swipe only when all conditions are true:

1. `ACTION_DOWN` started inside the activation zone.
2. Movement is downward.
3. Downward travel reaches four times Android's device-specific touch slop.
4. Vertical travel is at least twice absolute horizontal travel.
5. The callback has not already fired during the current gesture.

`LensView.shouldOpenSearchSwipe(...)` is a pure predicate covering these conditions. Named constants own the zone ratio, distance multiplier, and direction ratio.

Gesture flow:

- `ACTION_DOWN`: remember whether the touch is a search candidate.
- `ACTION_MOVE`: evaluate search before the existing horizontal-page and pan classifiers.
- Candidate movement that crosses ordinary touch slop suppresses icon launch on release, even if it stops before the full search threshold. This prevents a partial pull-down from launching the icon underneath the finger.
- A clearly upward or horizontally dominant movement stops being a search candidate and falls through to current pan/page behavior.
- On successful recognition: cancel long-press, suppress app launch, stop further handling for search, invoke `OnSearchSwipeDownListener` exactly once, and wait for `ACTION_UP`/`ACTION_CANCEL` to reset.
- `ACTION_UP`, `ACTION_CANCEL`, and detach reset all new transient flags. No timer, subscription, or retained object is added.

### 4.3 Accessibility and discoverability

Gesture is optional. Long-pressing empty lens space opens the existing lens-management popup. It gains:

- **Search apps** — opens full search.
- **Clean the lens (Minimalist mode)** — toggles current global Clean mode.

These labels reuse already translated strings. TalkBack users can reach both menu actions without performing the swipe.

## 5. Architecture

### 5.1 `LensView.kt`

Add one callback interface/property:

```kotlin
fun interface OnSearchSwipeDownListener {
    fun onSearchSwipeDown()
}

var onSearchSwipeDownListener: OnSearchSwipeDownListener? = null
```

Add pure gesture policy:

```kotlin
internal fun shouldOpenSearchSwipe(
    startedInActivationZone: Boolean,
    dx: Float,
    dy: Float,
    touchSlop: Float,
    alreadyTriggered: Boolean
): Boolean
```

New transient booleans live beside the existing gesture-state fields and are reset in every terminal touch path plus `onDetachedFromWindow`. No new enum state is needed: this is a one-shot classifier that exits before the existing pan/pinch state transitions.

`LensView` never reads Clean mode for this gesture. Therefore Clean and non-Clean behavior cannot drift.

### 5.2 `ActHome.java`

`bindLensView(...)` connects `OnSearchSwipeDownListener` to one shared method:

```java
openSearchFromHome();
```

The same method serves the long-press menu's Search item.

`openSearchFromHome()`:

1. Returns if `searchView` is already showing.
2. If `searchBar` is `GONE`, sets it to `INVISIBLE` so it has anchor geometry without becoming visible.
3. Posts `searchView.show()` after layout.

The existing search transition listener currently sets `searchBar` to `VISIBLE` whenever SearchView becomes hidden. Replace that unconditional restoration with `updateSearchBarVisibility()`, so current `KEY_CLEAN_LENS_MODE` and `KEY_SHOW_SEARCH_BAR` remain authoritative.

### 5.3 Empty-space menu

Add two named menu IDs after existing IDs 1–6:

- `MENU_ID_SEARCH_APPS`
- `MENU_ID_TOGGLE_CLEAN_LENS`

`onLensMenuItemSelected(...)` handles them:

- Search calls `openSearchFromHome()`.
- Clean toggle flips `UtilSettings.KEY_CLEAN_LENS_MODE`, then immediately refreshes search-bar visibility, lens navigation chrome, and the bound `LensView` draw state. No Activity restart is required.

Existing lens actions and ordering stay unchanged.

## 6. Error and Edge Handling

- Zero-height view: never qualifies for the activation zone.
- NaN or negative movement inputs: predicate returns false through ordinary comparisons; no crash.
- Repeated `ACTION_MOVE` after trigger: callback remains exactly once.
- Search already open: no duplicate transition.
- Clean mode toggled while search is open: setting persists immediately; final bar state is resolved when search closes.
- Touch cancelled by parent/system: flags reset and no app launches.
- Reduced-motion setting: recognition stays functional; no new animation or haptic is introduced.

## 7. Test Matrix

### Unit

Extend `LensViewGestureStateTest`:

- Starts in top zone + sufficiently vertical downward travel: true.
- Starts below top zone: false.
- Upward travel: false.
- Horizontal-dominant travel: false.
- Downward travel below four-touch-slop threshold: false.
- Already triggered: false.
- Exact boundary values are deterministic.

Every pure-policy test is written and observed failing before production code.

### Widget

Extend `LensViewWidgetTest` using real multi-step `MotionEvent`s:

- Top-zone pull-down invokes callback exactly once.
- Continued movement after trigger does not invoke again.
- Partial top pull-down suppresses icon launch.
- Middle-screen pull-down remains normal fisheye pan and never invokes search.
- Upward/horizontal movement from top remains existing behavior.
- Detach/cancel resets candidate state.

### Integration

Extend real-Activity Home tests:

- Clean mode off: menu Search opens `SearchView`; closing restores visible bar when its own setting is enabled.
- Clean mode on: menu Search opens `SearchView`; closing restores `GONE` bar.
- Gesture callback and menu item reach the same `openSearchFromHome()` behavior.
- Clean toggle menu item persists setting and immediately hides/shows search bar, navigation chrome, app labels, NEW tags, and badges through existing Clean-mode gates.
- Menu items expose existing translated labels.

### Regression

- Existing pinch, paging, long-press, icon launch, Clean mode, and predictive-back tests remain green.
- `./gradlew testDevDebugUnitTest`
- `./gradlew lintDevDebug`
- Direct single-device instrumentation on locked TECNO KJ7; never use `connected*` while another device is attached.

### Physical smoke

On TECNO KJ7 (`115333744A005844`):

1. Clean off: pull down from top activation zone, type query, close search, confirm bar returns.
2. Clean on: pull down from same zone, type query, close search, confirm bar stays hidden.
3. Pull down from screen middle and confirm fisheye still pans.
4. Long-press empty space, open Search through menu.
5. Toggle Clean mode through menu and confirm chrome changes immediately.
6. Confirm pinch curvature, horizontal lens paging, and icon long-press still work.

## 8. Definition of Done

- Test-first evidence recorded for every new behavior.
- Full JVM suite passes.
- Lint has zero errors and no new warnings.
- Full instrumented suite passes on locked TECNO KJ7, or any unrelated environmental failure is independently reproduced and documented.
- Physical smoke passes.
- Audit score is strictly greater than 9.0/10.
- Story and `doc/feature.md` contain final evidence.
