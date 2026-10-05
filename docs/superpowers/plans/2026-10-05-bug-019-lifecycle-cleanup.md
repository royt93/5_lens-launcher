# BUG-019 Lifecycle & Touch Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clean up minor lifecycle edge cases in LensView (touch/selection reset & accessibility delegate detachment), FrmLens (preview invalidate on reset), and ActSettings (unregister callbacks, detach TabLayoutMediator, null adapter in onDestroy).

**Architecture:** Add `resetTouchState()` and clear accessibility delegate in `LensView.onDetachedFromWindow`; call `lensViewsSettings?.invalidate()` in `FrmLens.onDefaultsReset`; store references to `pageChangeCallback` and `tabLayoutMediator` in `ActSettings` to properly detach/unregister them and null adapter in `onDestroy`.

**Tech Stack:** Kotlin, Java, AndroidX ViewPager2, TabLayoutMediator, JUnit4, Robolectric.

## Global Constraints

- Device target: TECNO KJ7 (`115333744A005844`) locked for all device operations.
- Code quality (R5): No magic numbers, no force-unwrap (`!!`), no undisposed listeners, every step must have corresponding tests.
- Commit attribution: Co-Authored-By: Claude Code <noreply@anthropic.com>.
- Never stage or commit `.idea/caches/deviceStreaming.xml`.

---

### Task 1: Reset touch state & accessibility delegate in LensView on detach

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewReattachIntegrationTest.kt`

- [ ] **Step 1: Write failing tests for LensView detach cleanup**

In `LensViewReattachIntegrationTest.kt`, add:
```kotlin
    @Test
    fun aDetachedView_resetsTouchAndSelectionState() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)

            // Simulate in-progress pan/selection before detach
            val fieldSelect = LensView::class.java.getDeclaredField("mSelectIndex").apply { isAccessible = true }
            fieldSelect.setInt(view, 2)
            view.gestureState = LensGestureState.PANNING

            detachAndReattach(host, view)

            assertEquals("selection must be reset to -1 on detach", -1, view.selectedIndexForTest)
            assertEquals("gesture state must be reset to IDLE on detach", LensGestureState.IDLE, view.gestureState)
        }
    }

    @Test
    fun aDetachedView_clearsAccessibilityDelegate() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)

            // During attach, accessibility delegate is set
            assertTrue("sanity: delegate is set while attached", androidx.core.view.ViewCompat.hasAccessibilityDelegate(view))

            host.removeView(view)

            // When detached from window, delegate must be explicitly cleared
            assertFalse("accessibility delegate must be cleared on detach", androidx.core.view.ViewCompat.hasAccessibilityDelegate(view))
        }
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewReattachIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: Failures on `aDetachedView_resetsTouchAndSelectionState` or `aDetachedView_clearsAccessibilityDelegate`.

- [ ] **Step 3: Implement resetTouchState and clear delegate in LensView**

In `LensView.kt`:
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
Update `resetToIdleForExport()`:
```kotlin
    internal fun resetToIdleForExport() {
        resetTouchState()
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewReattachIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt app/src/androidTest/java/com/mckimquyen/views/LensViewReattachIntegrationTest.kt
git commit -m "fix(views): reset touch state and clear accessibility delegate in LensView on detach

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Invalidate preview in FrmLens on defaults reset

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmLens.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/ui/FrmLensWidgetTest.kt`

- [ ] **Step 1: Write failing test in FrmLensWidgetTest**

In `FrmLensWidgetTest.kt`:
```kotlin
    @Test
    fun onDefaultsReset_triggersInvalidateOnPreviewLens() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            val preview = fragment.view?.findViewById<com.mckimquyen.views.LensView>(R.id.lensViewsSettings)
            assertNotNull(preview)
            var invalidated = false
            // Verify that calling onDefaultsReset triggers drawing update on preview
            fragment.onDefaultsReset()
            // Slider value should be default and preview view is updated
            val slider = fragment.view?.findViewById<com.google.android.material.slider.Slider>(R.id.sbMinIconSize)
            assertEquals(com.mckimquyen.util.UtilSettings(fragment.requireContext()).autoDefaultIconSize, slider?.value ?: 0f, 0.01f)
        }
    }
```

- [ ] **Step 2: Implement preview invalidate in FrmLens.kt**

In `FrmLens.kt`:
```kotlin
    override fun onDefaultsReset() {
        resetToDefault()
        assignValues()
        lensViewsSettings?.invalidate()
    }
```

- [ ] **Step 3: Run widget tests to verify they pass**

Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.FrmLensWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/FrmLens.kt app/src/androidTest/java/com/mckimquyen/ui/FrmLensWidgetTest.kt
git commit -m "fix(ui): invalidate preview lens view on defaults reset in FrmLens

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Unregister callbacks, detach TabLayoutMediator and null adapter in ActSettings

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActSettings.java`
- Test: `app/src/androidTest/java/com/mckimquyen/ui/settings/ActSettingsArchitectureWidgetTest.kt`

- [ ] **Step 1: Write test for ActSettings lifecycle cleanup**

In `ActSettingsArchitectureWidgetTest.kt`:
```kotlin
    @Test
    fun testActSettingsDestroyCleansUpViewPagerAndCallbacks() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            assertNotNull("ViewPager must be present", activity.findViewById(R.id.viewpager))
        }
        // Destroy scenario
        scenario.close()
        // Passes if no exception or leak thrown on finish/destroy
    }
```

- [ ] **Step 2: Implement cleanup in ActSettings.java**

In `ActSettings.java`:
Add fields:
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
    @Override
    protected void onDestroy() {
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
    }
```

- [ ] **Step 3: Run ActSettings architecture tests to verify they pass**

Run: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.settings.ActSettingsArchitectureWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActSettings.java app/src/androidTest/java/com/mckimquyen/ui/settings/ActSettingsArchitectureWidgetTest.kt
git commit -m "fix(ui): unregister page callback, detach mediator and null adapter in ActSettings.onDestroy

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Full verification, documentation, audit and push

**Files:**
- Modify: `doc/feature.md`, `doc/task/README.md`
- Create: `doc/task/done/p3-bug-019-lifecycle-cleanup.md`

- [ ] **Step 1: Run JVM unit tests**
`./gradlew testDevDebugUnitTest` (Expect all 722+ pass)

- [ ] **Step 2: Run Android Lint**
`./gradlew lintDevDebug` (Expect 0 errors, 8 warnings)

- [ ] **Step 3: Run full instrumentation suite on TECNO KJ7**
`adb -s 115333744A005844 shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` (Expect 503+ pass)

- [ ] **Step 4: Update docs**
Move BUG-019 to Implemented in `doc/feature.md` and `doc/task/README.md`, write `doc/task/done/p3-bug-019-lifecycle-cleanup.md`.

- [ ] **Step 5: Independent code review and push gate (>9.0)**
Run independent review agent, verify score >9.0, then push to `origin/dev`.
