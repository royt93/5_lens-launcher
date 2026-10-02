# FISH-016 iOS-Style Pull-Down Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users open app search with a familiar one-finger pull-down from the top of the lens, with menu fallbacks for Search and Clean mode.

**Architecture:** `LensView` owns touch classification through pure predicates plus one callback; it never reads Clean mode, so gesture behavior cannot diverge by setting. `ActHome` owns SearchView transitions and menu actions through one shared `openSearchFromHome()` path, while existing `UtilSettings.KEY_CLEAN_LENS_MODE` remains the only source of truth for chrome and lens drawing.

**Tech Stack:** Kotlin, Java, Android Views, Material3 `SearchBar`/`SearchView`, SharedPreferences, JUnit4, AndroidX Test.

## Global Constraints

- minSdk 25, target/compileSdk 37.
- Zero new dependencies, resources, preference keys, timers, subscriptions, or database changes.
- Reuse translated `R.string.search_apps_hint` and `R.string.setting_clean_lens_mode`; no locale file changes.
- Top activation zone is exactly `20%` of `LensView` height; full trigger distance is exactly `4 × scaledTouchSlop`; vertical travel must be at least `2 × |horizontal travel|`.
- Gesture behavior is identical in Clean and non-Clean modes; `LensView` must not read `KEY_CLEAN_LENS_MODE` for gesture classification.
- Preserve pinch curvature, horizontal lens paging, icon/empty-space long-press, and ordinary fisheye pan outside the top activation zone.
- Every production change follows RED → GREEN: add one failing test, run it, then write minimum code.
- Locked device for this session: TECNO KJ7 serial `115333744A005844`. Never address attached Pixel/S24U. Never run a Gradle `connected*` task because it cannot be scoped to one serial.
- Story starts at `doc/task/todo/p2-fish-fish-016-ios-style-pull-down-search.md`; move the same file through `inprogress/` to `done/` rather than copying it.
- Full completion requires unit, widget, integration, lint, full direct-device instrumentation, physical smoke, self-audit strictly greater than 9.0/10, and resource/lifecycle review.

## File Map

- `app/src/main/java/com/mckimquyen/views/LensView.kt` — pure pull-down policy, transient touch state, one-shot callback.
- `app/src/test/java/com/mckimquyen/views/LensViewGestureStateTest.kt` — Context-free activation-zone and direction/threshold policy.
- `app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt` — real `MotionEvent` sequences and gesture-conflict regression.
- `app/src/main/java/com/mckimquyen/ui/ActHome.java` — shared SearchView opening, visibility restoration, menu actions, LensView callback binding.
- `app/src/androidTest/java/com/mckimquyen/ui/ActHomeCleanLensModeIntegrationTest.kt` — real SearchView open/close behavior for both Clean states.
- `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt` — real menu contents and Clean toggle persistence/UI refresh.
- `doc/task/inprogress/p2-fish-fish-016-ios-style-pull-down-search.md` then `doc/task/done/...` — acceptance/evidence lifecycle.
- `doc/feature.md` — source-of-truth status and final evidence.

---

### Task 1: Pure Pull-Down Gesture Policy

**Files:**
- Move: `doc/task/todo/p2-fish-fish-016-ios-style-pull-down-search.md` → `doc/task/inprogress/p2-fish-fish-016-ios-style-pull-down-search.md`
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt:84-235`
- Test: `app/src/test/java/com/mckimquyen/views/LensViewGestureStateTest.kt:236-271`

**Interfaces:**
- Produces: `LensView.isInSearchSwipeActivationZone(downY: Float, viewHeight: Int): Boolean`
- Produces: `LensView.shouldOpenSearchSwipe(startedInActivationZone: Boolean, dx: Float, dy: Float, touchSlop: Float, alreadyTriggered: Boolean): Boolean`
- Produces: `LensView.shouldConsumeSearchSwipeRelease(startedInActivationZone: Boolean, dx: Float, dy: Float, touchSlop: Float): Boolean`
- Produces constants: `SEARCH_SWIPE_ACTIVATION_ZONE_RATIO`, `SEARCH_SWIPE_DISTANCE_MULTIPLIER`, `SEARCH_SWIPE_VERTICAL_DOMINANCE_RATIO` (private; tests assert behavior, not implementation constants)

- [ ] **Step 1: Mark story in progress**

Run:
```bash
git mv doc/task/todo/p2-fish-fish-016-ios-style-pull-down-search.md \
  doc/task/inprogress/p2-fish-fish-016-ios-style-pull-down-search.md
```

Change its table row from `Status | todo` to `Status | inprogress`.

- [ ] **Step 2: Write failing pure-policy tests**

Append this section before notification-badge tests in `LensViewGestureStateTest.kt`:

```kotlin
    // =============================================== FISH-016: pull-down search

    @Test
    fun `top twenty percent is the search activation zone`() {
        assertTrue(LensView.isInSearchSwipeActivationZone(downY = 0f, viewHeight = 1_000))
        assertTrue(LensView.isInSearchSwipeActivationZone(downY = 200f, viewHeight = 1_000))
        assertFalse(LensView.isInSearchSwipeActivationZone(downY = 200.1f, viewHeight = 1_000))
        assertFalse(LensView.isInSearchSwipeActivationZone(downY = -1f, viewHeight = 1_000))
        assertFalse(LensView.isInSearchSwipeActivationZone(downY = 0f, viewHeight = 0))
    }

    @Test
    fun `top-zone downward vertical swipe at exact threshold opens search`() {
        assertTrue(
            LensView.shouldOpenSearchSwipe(
                startedInActivationZone = true,
                dx = 20f,
                dy = 40f,
                touchSlop = 10f,
                alreadyTriggered = false
            )
        )
    }

    @Test
    fun `pull-down search rejects wrong start direction distance dominance and repeats`() {
        assertFalse(LensView.shouldOpenSearchSwipe(false, 0f, 50f, 10f, false))
        assertFalse(LensView.shouldOpenSearchSwipe(true, 0f, -50f, 10f, false))
        assertFalse(LensView.shouldOpenSearchSwipe(true, 0f, 39.9f, 10f, false))
        assertFalse(LensView.shouldOpenSearchSwipe(true, 21f, 40f, 10f, false))
        assertFalse(LensView.shouldOpenSearchSwipe(true, 0f, 50f, 10f, true))
    }

    @Test
    fun `partial vertical pull consumes release but horizontal upward and tiny moves do not`() {
        assertTrue(LensView.shouldConsumeSearchSwipeRelease(true, 2f, 11f, 10f))
        assertFalse(LensView.shouldConsumeSearchSwipeRelease(false, 2f, 11f, 10f))
        assertFalse(LensView.shouldConsumeSearchSwipeRelease(true, 12f, 11f, 10f))
        assertFalse(LensView.shouldConsumeSearchSwipeRelease(true, 0f, -11f, 10f))
        assertFalse(LensView.shouldConsumeSearchSwipeRelease(true, 0f, 10f, 10f))
    }
```

- [ ] **Step 3: Run RED and verify missing API is the only failure**

Run:
```bash
./gradlew testDevDebugUnitTest --tests com.mckimquyen.views.LensViewGestureStateTest
```

Expected: compilation FAIL on unresolved `isInSearchSwipeActivationZone`, `shouldOpenSearchSwipe`, and `shouldConsumeSearchSwipeRelease`. Do not proceed if failure is unrelated.

- [ ] **Step 4: Add minimum pure policy**

In `LensView` companion object, next to existing gesture predicates, add:

```kotlin
        private const val SEARCH_SWIPE_ACTIVATION_ZONE_RATIO = 0.20f
        private const val SEARCH_SWIPE_DISTANCE_MULTIPLIER = 4f
        private const val SEARCH_SWIPE_VERTICAL_DOMINANCE_RATIO = 2f

        @androidx.annotation.VisibleForTesting
        internal fun isInSearchSwipeActivationZone(downY: Float, viewHeight: Int): Boolean =
            viewHeight > 0 && downY >= 0f && downY <= viewHeight * SEARCH_SWIPE_ACTIVATION_ZONE_RATIO

        @androidx.annotation.VisibleForTesting
        internal fun shouldOpenSearchSwipe(
            startedInActivationZone: Boolean,
            dx: Float,
            dy: Float,
            touchSlop: Float,
            alreadyTriggered: Boolean,
        ): Boolean =
            startedInActivationZone &&
                !alreadyTriggered &&
                dy >= touchSlop * SEARCH_SWIPE_DISTANCE_MULTIPLIER &&
                dy >= abs(dx) * SEARCH_SWIPE_VERTICAL_DOMINANCE_RATIO

        @androidx.annotation.VisibleForTesting
        internal fun shouldConsumeSearchSwipeRelease(
            startedInActivationZone: Boolean,
            dx: Float,
            dy: Float,
            touchSlop: Float,
        ): Boolean =
            startedInActivationZone && dy > touchSlop && dy > abs(dx)
```

No NaN-specific branch is needed: Kotlin floating comparisons already return false for NaN. Negative `touchSlop` is impossible because source is `ViewConfiguration.scaledTouchSlop`; do not add unneeded validation for an internal platform value.

- [ ] **Step 5: Run GREEN**

Run:
```bash
./gradlew testDevDebugUnitTest --tests com.mckimquyen.views.LensViewGestureStateTest
```

Expected: PASS, including all pre-existing pan/pinch/page predicates.

- [ ] **Step 6: Commit policy and story transition**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt \
  app/src/test/java/com/mckimquyen/views/LensViewGestureStateTest.kt \
  doc/task/inprogress/p2-fish-fish-016-ios-style-pull-down-search.md
git commit -m "feat(gesture): define pull-down search policy

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Drive Pull-Down Through Real `MotionEvent`s

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt:55-80,235-340,786-932,1346-1366`
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt:38-93,187-285`

**Interfaces:**
- Consumes: pure predicates from Task 1
- Produces: top-level `fun interface OnSearchSwipeDownListener { fun onSearchSwipeDown() }`
- Produces: `LensView.onSearchSwipeDownListener: OnSearchSwipeDownListener?`
- Internal touch state: fixed `ACTION_DOWN` coordinates plus candidate/triggered/consume-release flags

- [ ] **Step 1: Add failing widget helpers and tests**

Add this helper to `LensViewWidgetTest`:

```kotlin
    private fun layoutEmptyLens() {
        lensView.setApps(arrayListOf())
        lensView.measure(
            View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY)
        )
        lensView.layout(0, 0, 1_000, 1_000)
    }

    private fun dispatchTouch(action: Int, x: Float, y: Float, downTime: Long): Boolean {
        val event = MotionEvent.obtain(downTime, System.currentTimeMillis(), action, x, y, 0)
        return try {
            lensView.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
```

Add these tests:

```kotlin
    @Test
    fun pullDownFromTop_invokesSearchExactlyOnce() {
        var calls = 0
        val downTime = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            lensView.onSearchSwipeDownListener = OnSearchSwipeDownListener { calls++ }
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 510f, 180f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 510f, 240f, downTime)
            dispatchTouch(MotionEvent.ACTION_UP, 510f, 240f, downTime)
        }
        assertEquals(1, calls)
    }

    @Test
    fun pullDownFromMiddle_remainsAPanAndNeverOpensSearch() {
        var calls = 0
        val downTime = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            lensView.onSearchSwipeDownListener = OnSearchSwipeDownListener { calls++ }
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 500f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 600f, downTime)
            assertTrue(privateField<Boolean>("mMoving"))
            dispatchTouch(MotionEvent.ACTION_UP, 500f, 600f, downTime)
        }
        assertEquals(0, calls)
    }

    @Test
    fun partialTopPull_consumesReleaseWithoutOpeningSearchOrStartingPan() {
        var calls = 0
        val downTime = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            lensView.onSearchSwipeDownListener = OnSearchSwipeDownListener { calls++ }
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 120f, downTime)
            assertTrue(privateField<Boolean>("mSearchSwipeConsumesRelease"))
            assertFalse(privateField<Boolean>("mMoving"))
            dispatchTouch(MotionEvent.ACTION_UP, 500f, 120f, downTime)
            assertFalse(privateField<Boolean>("mSearchSwipeConsumesRelease"))
        }
        assertEquals(0, calls)
    }

    @Test
    fun cancel_resetsPullDownStateForTheNextGesture() {
        var calls = 0
        val firstDown = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            lensView.onSearchSwipeDownListener = OnSearchSwipeDownListener { calls++ }
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, firstDown)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 120f, firstDown)
            dispatchTouch(MotionEvent.ACTION_CANCEL, 500f, 120f, firstDown)

            val secondDown = System.currentTimeMillis()
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, secondDown)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 180f, secondDown)
            dispatchTouch(MotionEvent.ACTION_UP, 500f, 180f, secondDown)
        }
        assertEquals(1, calls)
    }
```

- [ ] **Step 2: Build/install test APKs only on locked KJ7 and verify RED**

Run:
```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 115333744A005844 install -r "$(find app/build/outputs/apk/dev/debug -name '*.apk' -print -quit)"
adb -s 115333744A005844 install -r "$(find app/build/outputs/apk/androidTest/dev/debug -name '*.apk' -print -quit)"
adb -s 115333744A005844 shell am instrument -w \
  -e class com.mckimquyen.views.LensViewWidgetTest \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: compilation FAIL on missing listener/state. Pixel remains untouched.

- [ ] **Step 3: Add listener and transient state**

Near `OnEmptySpaceLongPressListener`, add:

```kotlin
fun interface OnSearchSwipeDownListener {
    fun onSearchSwipeDown()
}
```

Beside long-press fields, add:

```kotlin
    var onSearchSwipeDownListener: OnSearchSwipeDownListener? = null
    private var mTouchDownX = 0f
    private var mTouchDownY = 0f
    private var mSearchSwipeStartedInActivationZone = false
    private var mSearchSwipeTriggered = false
    private var mSearchSwipeConsumesRelease = false

    private fun resetSearchSwipeState() {
        mSearchSwipeStartedInActivationZone = false
        mSearchSwipeTriggered = false
        mSearchSwipeConsumesRelease = false
    }
```

- [ ] **Step 4: Integrate classifier before existing page/pan checks**

In `ACTION_DOWN`, after assigning `mTouchX/mTouchY`, add:

```kotlin
                mTouchDownX = event.x
                mTouchDownY = event.y
                mSearchSwipeStartedInActivationZone =
                    isInSearchSwipeActivationZone(event.y, height)
                mSearchSwipeTriggered = false
                mSearchSwipeConsumesRelease = false
```

At start of `ACTION_POINTER_DOWN`, cancel single-finger search candidacy before existing pinch logic:

```kotlin
                mSearchSwipeStartedInActivationZone = false
                mSearchSwipeConsumesRelease = false
```

In `ACTION_MOVE`, immediately after pinch/long-press guards and before current `dx/dy` calculation, use fixed down coordinates:

```kotlin
                val searchDx = event.x - mTouchDownX
                val searchDy = event.y - mTouchDownY
                if (shouldOpenSearchSwipe(
                        mSearchSwipeStartedInActivationZone,
                        searchDx,
                        searchDy,
                        mTouchSlop,
                        mSearchSwipeTriggered
                    )
                ) {
                    mSearchSwipeTriggered = true
                    mSearchSwipeConsumesRelease = true
                    mLongPressArmed = false
                    mLongPressHandler.removeCallbacks(mLongPressRunnable)
                    mSelectIndex = -1
                    mRectToSelect = null
                    onSearchSwipeDownListener?.onSearchSwipeDown()
                    return true
                }
                if (shouldConsumeSearchSwipeRelease(
                        mSearchSwipeStartedInActivationZone,
                        searchDx,
                        searchDy,
                        mTouchSlop
                    )
                ) {
                    mSearchSwipeConsumesRelease = true
                    mLongPressArmed = false
                    mLongPressHandler.removeCallbacks(mLongPressRunnable)
                    mSelectIndex = -1
                    mRectToSelect = null
                    return true
                }
                if (searchDy <= 0f || abs(searchDx) >= searchDy) {
                    mSearchSwipeStartedInActivationZone = false
                }
```

Keep existing `dx = event.x - mTouchX` / `dy = event.y - mTouchY` logic after this block unchanged.

- [ ] **Step 5: Consume terminal release and reset every terminal path**

At start of `ACTION_UP`, after cancelling long-press but before pinch/app-launch handling:

```kotlin
                if (mSearchSwipeConsumesRelease) {
                    resetSearchSwipeState()
                    gestureState = LensGestureState.IDLE
                    mSelectIndex = -1
                    mTouchX = -Float.MAX_VALUE
                    mTouchY = -Float.MAX_VALUE
                    invalidate()
                    return true
                }
                resetSearchSwipeState()
```

In `ACTION_CANCEL`, call `resetSearchSwipeState()` before returning. In `onDetachedFromWindow()`, call it immediately after removing the long-press callback. Do not null the callback: it is ordinary child-to-owner wiring, not a registered external listener, and ViewPager2 can detach/reattach pages.

- [ ] **Step 6: Run GREEN plus pinch/page/long-press regression classes**

Run:
```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 115333744A005844 install -r "$(find app/build/outputs/apk/dev/debug -name '*.apk' -print -quit)"
adb -s 115333744A005844 install -r "$(find app/build/outputs/apk/androidTest/dev/debug -name '*.apk' -print -quit)"
adb -s 115333744A005844 shell am instrument -w \
  -e class com.mckimquyen.views.LensViewWidgetTest,com.mckimquyen.ui.ActHomePinchWidgetTest,com.mckimquyen.ui.ActHomeMultiLensWidgetTest,com.mckimquyen.views.LensViewQuickActionsIntegrationTest \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: all selected tests PASS. If existing middle-screen pan test fails, fix production classifier; do not weaken the test.

- [ ] **Step 7: Commit MotionEvent behavior**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt \
  app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt
git commit -m "feat(gesture): open search from top pull-down

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Shared Home Search Opening and Correct Visibility Restoration

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java:606-650,1019-1052,1589-1605`
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeCleanLensModeIntegrationTest.kt`

**Interfaces:**
- Consumes: `LensView.setOnSearchSwipeDownListener(OnSearchSwipeDownListener)` from Task 2
- Produces: package-visible `@VisibleForTesting void openSearchFromHome()`
- Produces: hidden-state restoration through existing `updateSearchBarVisibility()`

- [ ] **Step 1: Add failing real-Activity tests**

Add imports for Material `SearchBar`/`SearchView` and these helpers to `ActHomeCleanLensModeIntegrationTest`:

```kotlin
    private fun awaitSearchState(
        scenario: ActivityScenario<ActHome>,
        expectedShowing: Boolean
    ) {
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        var showing = !expectedShowing
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                showing = it.findViewById<com.google.android.material.search.SearchView>(R.id.searchView)
                    .isShowing
            }
            if (showing == expectedShowing) return
            android.os.SystemClock.sleep(POLL_MS)
        }
        assertEquals("SearchView did not reach requested state", expectedShowing, showing)
    }
```

Add tests:

```kotlin
    @Test
    fun pullDownCallback_opensSearchWhenCleanModeHidesSearchBar() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val lens = activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                org.junit.Assert.assertNotNull(lens.onSearchSwipeDownListener)
                lens.onSearchSwipeDownListener!!.onSearchSwipeDown()
            }
            awaitSearchState(scenario, true)
        }
    }

    @Test
    fun closingSearch_restoresVisibleBarWhenCleanOffAndSearchBarEnabled() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchState(scenario, true)
            scenario.onActivity {
                it.findViewById<com.google.android.material.search.SearchView>(R.id.searchView).hide()
            }
            awaitSearchState(scenario, false)
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }

    @Test
    fun closingSearch_restoresGoneBarWhenCleanOn() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchState(scenario, true)
            scenario.onActivity {
                it.findViewById<com.google.android.material.search.SearchView>(R.id.searchView).hide()
            }
            awaitSearchState(scenario, false)
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }
```

- [ ] **Step 2: Run RED on KJ7**

Build/install with Task 2's direct commands, then run:

```bash
adb -s 115333744A005844 shell am instrument -w \
  -e class com.mckimquyen.ui.ActHomeCleanLensModeIntegrationTest \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: compilation FAIL because `openSearchFromHome()` and listener binding do not exist. Existing Clean tests must still compile conceptually unchanged.

- [ ] **Step 3: Bind every lens page to shared Home action**

In `bindLensView(...)`, directly after curvature listener wiring:

```java
        view.setOnSearchSwipeDownListener(this::openSearchFromHome);
```

- [ ] **Step 4: Add minimum shared search opener**

Near `showRecentAppsPanel()`, add:

```java
    @androidx.annotation.VisibleForTesting
    void openSearchFromHome() {
        if (searchView == null || searchBar == null || searchView.isShowing()) return;
        if (searchBar.getVisibility() == View.GONE) {
            searchBar.setVisibility(View.INVISIBLE);
        }
        searchBar.post(searchView::show);
    }
```

`INVISIBLE` gives the Material morph anchor layout geometry without showing it; `post` waits for that visibility/layout update. Do not set Clean mode here and do not duplicate SearchView setup.

- [ ] **Step 5: Restore bar through settings, never unconditionally**

Change only this existing transition branch:

```java
            } else if (newState == SearchView.TransitionState.HIDDEN) {
                updateSearchBarVisibility();
            }
```

Keep `updateModeVisibility()` in the later HIDDEN branch. `updateSearchBarVisibility()` is safe here because `searchView.isShowing()` is false at `HIDDEN`, so its existing defensive `hide()` branch cannot recurse.

- [ ] **Step 6: Run GREEN and predictive-back regression**

Build/install directly on KJ7, then run:

```bash
adb -s 115333744A005844 shell am instrument -w \
  -e class com.mckimquyen.ui.ActHomeCleanLensModeIntegrationTest,com.mckimquyen.ui.ActHomePredictiveBackWidgetTest,com.mckimquyen.ui.AppSearchWidgetTest \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: all selected tests PASS; Clean-on close leaves bar `GONE`, Clean-off close restores `VISIBLE`.

- [ ] **Step 7: Commit shared search path**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomeCleanLensModeIntegrationTest.kt
git commit -m "feat(home): open search from lens pull-down

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Search and Clean Actions in Empty-Space Menu

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java:704-801`
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt:44-110,273-355,376-403`
- Test regression: `app/src/test/java/com/mckimquyen/a11y/AllStringsTranslationTest.kt` (run only; no source change expected)

**Interfaces:**
- Consumes: `openSearchFromHome()` from Task 3
- Produces: package-visible constants `MENU_ID_SEARCH_APPS = 7`, `MENU_ID_TOGGLE_CLEAN_LENS = 8`
- Produces: `onLensMenuItemSelected(7, validPosition)` opens search
- Produces: `onLensMenuItemSelected(8, validPosition)` flips global Clean mode and refreshes Home immediately

- [ ] **Step 1: Add failing menu-content and behavior tests**

In `ActHomeLensManagementWidgetTest`, add:

```kotlin
    private val itemSearch = 7
    private val itemToggleCleanLens = 8
```

Change the existing menu-size assertion from six to eight and require both reused labels:

```kotlin
                assertEquals(
                    "The menu must offer all eight actions",
                    8,
                    activity.lensManagementMenu!!.menu.size()
                )
                val titles = (0 until activity.lensManagementMenu!!.menu.size())
                    .map { activity.lensManagementMenu!!.menu.getItem(it).title.toString() }
                assertTrue(titles.contains(activity.getString(R.string.search_apps_hint)))
                assertTrue(titles.contains(activity.getString(R.string.setting_clean_lens_mode)))
```

Add behavior tests:

```kotlin
    @Test
    fun searchMenuItem_opensTheSameFullSearchSurface() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            openMenuAndSelect(scenario, itemSearch, position = 0)
            scenario.onActivity { activity ->
                assertTrue(
                    activity.findViewById<com.google.android.material.search.SearchView>(R.id.searchView)
                        .isShowing
                )
            }
        }
    }

    @Test
    fun cleanMenuItem_togglesPreferenceAndHomeChromeImmediately() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            openMenuAndSelect(scenario, itemToggleCleanLens, position = 0)
            assertTrue(settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.searchBar).visibility)
            }

            openMenuAndSelect(scenario, itemToggleCleanLens, position = 0)
            assertFalse(settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }
```

Extend stale-position coverage list to include both new IDs. Current behavior intentionally rejects all menu actions once captured lens position is stale, including global actions, because that menu instance no longer refers to a valid Home state.

Ensure `clearLensPrefs()` removes `UtilSettings.KEY_CLEAN_LENS_MODE` and `UtilSettings.KEY_SHOW_SEARCH_BAR` to prevent cross-test state leakage.

- [ ] **Step 2: Run RED**

Build/install directly on KJ7, then run:

```bash
adb -s 115333744A005844 shell am instrument -w \
  -e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: FAIL because menu has six items and IDs 7/8 return false.

- [ ] **Step 3: Add named IDs and menu items**

Near other `ActHome` constants add:

```java
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_SEARCH_APPS = 7;
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_TOGGLE_CLEAN_LENS = 8;
```

After Recent apps in `showLensManagementMenu(...)`, add:

```java
        menu.getMenu().add(0, MENU_ID_SEARCH_APPS, 0, R.string.search_apps_hint);
        menu.getMenu().add(0, MENU_ID_TOGGLE_CLEAN_LENS, 0, R.string.setting_clean_lens_mode);
```

- [ ] **Step 4: Add minimum action branches**

After item 6 in `onLensMenuItemSelected(...)`, add:

```java
        } else if (itemId == MENU_ID_SEARCH_APPS) {
            openSearchFromHome();
            return true;
        } else if (itemId == MENU_ID_TOGGLE_CLEAN_LENS) {
            boolean enabled = utilSettings != null
                    && utilSettings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE);
            if (utilSettings != null) {
                utilSettings.save(UtilSettings.KEY_CLEAN_LENS_MODE, !enabled);
            }
            updateSearchBarVisibility();
            if (lensViews != null) {
                lensViews.invalidate();
            }
            return true;
```

`updateSearchBarVisibility()` already refreshes recent-icon visibility and lens navigation chrome. `LensView.invalidate()` causes existing per-frame Clean gates for labels, badges, and NEW tags to reread the same preference. Do not duplicate those gates.

- [ ] **Step 5: Run GREEN and translation regression**

Run:
```bash
./gradlew testDevDebugUnitTest --tests com.mckimquyen.a11y.AllStringsTranslationTest
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 115333744A005844 install -r "$(find app/build/outputs/apk/dev/debug -name '*.apk' -print -quit)"
adb -s 115333744A005844 install -r "$(find app/build/outputs/apk/androidTest/dev/debug -name '*.apk' -print -quit)"
adb -s 115333744A005844 shell am instrument -w \
  -e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest,com.mckimquyen.ui.ActHomeCleanLensModeIntegrationTest,com.mckimquyen.views.LensViewWidgetTest \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: all PASS. Translation guard passes without locale edits because both labels already exist across all locales.

- [ ] **Step 6: Commit menu actions**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt
git commit -m "feat(home): add search and clean lens menu actions

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Full Verification, Device Smoke, Audit, and Documentation

**Files:**
- Move: `doc/task/inprogress/p2-fish-fish-016-ios-style-pull-down-search.md` → `doc/task/done/p2-fish-fish-016-ios-style-pull-down-search.md`
- Modify: `doc/task/done/p2-fish-fish-016-ios-style-pull-down-search.md`
- Modify: `doc/feature.md`

**Interfaces:**
- Consumes: complete FISH-016 behavior from Tasks 1–4
- Produces: fresh test/lint/device evidence and final status

- [ ] **Step 1: Run full JVM and lint gates**

Run:
```bash
./gradlew testDevDebugUnitTest
./gradlew lintDevDebug
```

Expected: all JVM tests PASS; lint reports 0 errors and no new warning versus baseline. Record exact counts.

- [ ] **Step 2: Build and install only on locked KJ7**

Run:
```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 115333744A005844 install -r "$(find app/build/outputs/apk/dev/debug -name '*.apk' -print -quit)"
adb -s 115333744A005844 install -r "$(find app/build/outputs/apk/androidTest/dev/debug -name '*.apk' -print -quit)"
```

Expected: both installs succeed. Do not run `connectedDevDebugAndroidTest` while Pixel remains attached.

- [ ] **Step 3: Run full instrumented suite directly on KJ7**

Run:
```bash
adb -s 115333744A005844 shell am instrument -w \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner \
  | tee /tmp/fish-016-instrumented-kj7.log
```

Expected: all tests PASS. If `UiAutomationService already registered!` recurs, preserve log, force-stop target/test packages, rerun once, and document both outcomes; never hide an app assertion failure as infrastructure flake.

- [ ] **Step 4: Physical smoke on KJ7**

Use only TECNO KJ7. Verify in order:

1. Clean off: drag downward from top 20% of lens; SearchView opens; type a query; close search; bar returns.
2. Clean on: use the same gesture; SearchView opens; close search; bar stays hidden.
3. Drag downward from middle of lens; fisheye pans and search does not open.
4. Long-press empty space; choose Search; SearchView opens.
5. Long-press empty space; choose Clean toggle twice; chrome hides then returns immediately.
6. Pinch curvature, swipe horizontally to another lens, long-press an icon, and tap an icon; each existing behavior still works.
7. If any ad overlays UI, stop immediately under R4, report ad type/location, and wait for owner confirmation before continuing.

Capture screenshots only when no ad is visible. Record device model/serial, build SHA, timestamp, and observed behavior in story.

- [ ] **Step 5: Resource and lifecycle audit**

Check diff and confirm:

```bash
git diff --check
git diff --stat
git status --short
```

Manual checklist:

- No new dependency, resource string, preference key, timer, observer, coroutine, or registered listener.
- `mLongPressHandler` still removes callback on release/cancel/detach.
- Search transient flags reset on `ACTION_UP`, `ACTION_CANCEL`, and detach.
- Lens callback is overwritten on each bind and does not accumulate.
- No `late`, force unwrap, magic business values, or per-frame allocation added.
- No changes to `LensGridCache` or draw geometry.
- User-owned `.idea/caches/deviceStreaming.xml` remains untouched.

- [ ] **Step 6: Run code review and score audit**

Invoke `superpowers:requesting-code-review`, inspect every finding, and fix only verified defects with RED tests first. Score against correctness, UX, regression safety, tests, lifecycle/resources, performance, accessibility, localization, scope discipline, and docs. Push allowed only if score is strictly greater than 9.0/10.

- [ ] **Step 7: Close story and tracker**

Move story:

```bash
git mv doc/task/inprogress/p2-fish-fish-016-ios-style-pull-down-search.md \
  doc/task/done/p2-fish-fish-016-ios-style-pull-down-search.md
```

Update story table to `Status | done`, check every acceptance box, and append exact JVM/lint/instrumented/smoke/audit evidence. Move FISH-016 from `📋 Picked` to `✅ Implemented` in `doc/feature.md`. Remove no historical evidence.

- [ ] **Step 8: Final verification after docs**

Run:
```bash
git diff --check
git status --short
```

Expected: only intended FISH-016 code/tests/docs plus pre-existing user-owned `.idea/caches/deviceStreaming.xml` if still modified.

- [ ] **Step 9: Commit completion**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt \
  app/src/main/java/com/mckimquyen/ui/ActHome.java \
  app/src/test/java/com/mckimquyen/views/LensViewGestureStateTest.kt \
  app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomeCleanLensModeIntegrationTest.kt \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt \
  doc/feature.md \
  doc/task/done/p2-fish-fish-016-ios-style-pull-down-search.md
git commit -m "docs(fish): complete pull-down search verification

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

Do not push unless owner separately requests push and all gates above pass.
