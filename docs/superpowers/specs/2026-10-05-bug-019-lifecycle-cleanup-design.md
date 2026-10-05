# BUG-019: Lifecycle & Touch Cleanup for LensView, FrmLens and ActSettings

**Date:** 2026-10-05  
**Domain:** core / ui / lifecycle  
**Status:** Approved  
**Priority:** P3  
**Target:** TECNO KJ7 (`115333744A005844`), JVM unit tests, Android Lint

---

## 1. Context & Motivation

During the whole-branch review of FISH-018 (per-lens icon size), two minor lifecycle gaps were identified:
1. `LensView.onDetachedFromWindow` leaves touch/gesture state (`gestureState`, `mSelectIndex`, `mRectToSelect`, `mTouchX`, `mTouchY`, `mMoving`, `mLongPressArmed`, `mHasVibratedThisHover`) uncleared, and does not explicitly remove the accessibility delegate set via `ViewCompat.setAccessibilityDelegate`.
2. `FrmLens.onDefaultsReset` restores settings and re-reads values via `assignValues()`, but omits `lensViewsSettings?.invalidate()`, so the preview lens view at the top of the Settings screen does not immediately redraw with the restored default icon size and distortion.

Additionally, an audit of `ActSettings.java` revealed that:
3. `ActSettings` registers an anonymous `PageChangeCallback` on `viewpager` and attaches a `TabLayoutMediator` without holding references to detach or unregister them in `onDestroy()`. Setting `viewpager.setAdapter(null)` is also omitted, preventing clean garbage collection of fragments and pages when `ActSettings` is destroyed.

---

## 2. Requirements & Acceptance Criteria

### LensView
- **AC-1:** On `onDetachedFromWindow()`, `LensView` resets all touch and gesture tracking fields to their idle/unselected state:
  - `mTouchX = -Float.MAX_VALUE`, `mTouchY = -Float.MAX_VALUE`
  - `gestureState = LensGestureState.IDLE`
  - `mSelectIndex = -1`, `mRectToSelect = null`
  - `mMoving = false`, `mLongPressArmed = false`, `mHasVibratedThisHover = false`
- **AC-2:** On `onDetachedFromWindow()`, `LensView` explicitly calls `ViewCompat.setAccessibilityDelegate(this, null)` so the View's internal delegate field does not retain the virtual accessibility helper.
- **AC-3:** Existing leak prevention and GPU resource cleanup in `onDetachedFromWindow()` remain unchanged and intact.

### FrmLens
- **AC-4:** `FrmLens.onDefaultsReset()` calls `lensViewsSettings?.invalidate()` immediately after `assignValues()`, ensuring the top preview lens redraws with default values.

### ActSettings
- **AC-5:** `ActSettings` retains fields for `pageChangeCallback` (instance of `PageChangeCallback`) and `tabLayoutMediator` (instance of `TabLayoutMediator`).
- **AC-6:** In `ActSettings.onDestroy()`:
  - `tabLayoutMediator.detach()` is called and `tabLayoutMediator = null`.
  - `viewpager.unregisterOnPageChangeCallback(pageChangeCallback)` is called and `pageChangeCallback = null`.
  - `viewpager.setAdapter(null)` is called to release fragment pager bindings.

---

## 3. Architecture & Detailed Design

### 3.1 `LensView.kt`
Extract a private helper method:
```kotlin
private fun resetTouchState() {
    mTouchX = -Float.MAX_VALUE
    mTouchY = -Float.MAX_VALUE
    gestureState = LensGestureState.IDLE
    mSelectIndex = -1
    mRectToSelect = null
    mMoving = false
    mLongPressArmed = false
    mHasVibratedThisHover = false
}
```
In `onDetachedFromWindow()`:
```kotlin
clearAnimation()
resetTouchState()
ViewCompat.setAccessibilityDelegate(this, null)
```
Note: `resetToIdleForExport()` continues to exist for polaroid export, or delegates to `resetTouchState()` to avoid duplication.

### 3.2 `FrmLens.kt`
In `onDefaultsReset()`:
```kotlin
override fun onDefaultsReset() {
    resetToDefault()
    assignValues()
    lensViewsSettings?.invalidate()
}
```

### 3.3 `ActSettings.java`
Declare instance fields:
```java
private ViewPager2.OnPageChangeCallback pageChangeCallback;
private TabLayoutMediator tabLayoutMediator;
```
In `onCreate()`:
```java
tabLayoutMediator = new TabLayoutMediator(tabs, viewpager, (tab, position) -> tab.setText(mPagerAdapter.getPageTitle(position)));
tabLayoutMediator.attach();

pageChangeCallback = new PageChangeCallback(fabSort);
viewpager.registerOnPageChangeCallback(pageChangeCallback);
```
In `onDestroy()`:
```java
try {
    dismissAllDialogs();
    if (tabLayoutMediator != null) {
        tabLayoutMediator.detach();
        tabLayoutMediator = null;
    }
    if (viewpager != null) {
        if (pageChangeCallback != null) {
            viewpager.unregisterOnPageChangeCallback(pageChangeCallback);
            pageChangeCallback = null;
        }
        viewpager.setAdapter(null);
    }
    lensInterface = null;
    appsInterface = null;
    settingsInterface = null;
} finally {
    adVipDelegate.onDestroy();
    adView = null;
    super.onDestroy();
}
```

---

## 4. Test Strategy

1. **Unit / Widget Test (`LensViewLifecycleTest` or `LensViewWidgetTest`):**
   - Verify `onDetachedFromWindow` resets `gestureState` to `IDLE` and `selectedIndexForTest` to `-1`.
   - Verify `ViewCompat.hasAccessibilityDelegate(view)` is false after detach.
2. **Widget Test (`FrmLensWidgetTest`):**
   - Verify calling `onDefaultsReset()` triggers `invalidate()` on `lensViewsSettings`.
3. **Integration / Lifecycle Test (`ActSettingsLifecycleTest`):**
   - Verify `ActSettings` can launch and destroy cleanly without leaking callbacks or crashing when fragments are torn down.
