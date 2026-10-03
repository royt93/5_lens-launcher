package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewWidgetTest {

    private lateinit var context: Context
    private lateinit var lensView: LensView

    @Before
    fun setup() {
        // UI-022: real production LensView always gets an Activity context (inflated from
        // act_home.xml by a themed ActHome), never a bare Application context - wrapping with
        // the app's own AppTheme here matches that, so tests that exercise showQuickActionsMenu
        // (which needs Material3 theme attrs to resolve) prove real success/failure instead of
        // failing on a test-harness artifact unrelated to production behavior.
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        context = ContextThemeWrapper(targetContext, R.style.AppTheme)
        // Create the view on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(context)
        }
    }

    private fun <T> privateField(name: String): T {
        val field = LensView::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(lensView) as T
    }

    /**
     * Lays out a real single-icon grid, dispatches ACTION_DOWN at that icon's real on-screen
     * center, and forces one draw pass so `mSelectIndex` is populated for that down-point -
     * `invalidate()` alone is a no-op on a view not attached to a window (no `ViewRootImpl` to
     * schedule a traversal), matching the technique
     * `LensViewAccessibilityWidgetTest.testLensView_getAppBounds_returnsValidCoordinates`
     * already established. Returns the down-point actually used, so callers can dispatch a
     * real pan relative to it.
     */
    private fun layoutSingleAppGridAndDispatchDown(): Pair<Float, Float> {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1")
        )
        lensView.setApps(testApps)
        lensView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        lensView.layout(0, 0, 1080, 1920)
        // baseRects are only computed inside drawGrid() (onDraw) - draw once first so
        // getAppBounds() below has real geometry to report, matching
        // testLensView_getAppBounds_returnsValidCoordinates's own ordering.
        lensView.draw(Canvas())
        val bounds = android.graphics.Rect()
        assertTrue("icon 0 must have real bounds to touch", lensView.getAppBounds(0, bounds))
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val now = System.currentTimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        lensView.dispatchTouchEvent(down)
        down.recycle()
        // Force drawGrid() to run once against this down-point, populating mSelectIndex.
        lensView.draw(Canvas())
        return x to y
    }

    private fun dispatchUp(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0)
        lensView.dispatchTouchEvent(up)
        up.recycle()
    }

    private fun dispatchMove(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        val move = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, x, y, 0)
        lensView.dispatchTouchEvent(move)
        move.recycle()
    }

    private fun layoutEmptyLens() {
        lensView.setApps(arrayListOf())
        lensView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1_000, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1_000, android.view.View.MeasureSpec.EXACTLY)
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

    // ==================================================================== FISH-016

    @Test
    fun pullDownFromTop_invokesSearchExactlyOnce() {
        var calls = 0
        val downTime = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            lensView.onSearchSwipeDownListener = OnSearchSwipeDownListener { calls++ }
            val slop = privateField<Float>("mTouchSlop")
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f + slop / 2f, 100f + slop * 2f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f + slop / 2f, 100f + slop * 5f, downTime)
            dispatchTouch(MotionEvent.ACTION_UP, 500f + slop / 2f, 100f + slop * 5f, downTime)
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
    fun cancel_resetsPullDownStateForTheNextGesture() {
        var calls = 0
        val firstDown = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            val slop = privateField<Float>("mTouchSlop")
            lensView.onSearchSwipeDownListener = OnSearchSwipeDownListener { calls++ }
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, firstDown)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 100f + slop + 1f, firstDown)
            dispatchTouch(MotionEvent.ACTION_CANCEL, 500f, 100f + slop + 1f, firstDown)
            assertFalse(privateField<Boolean>("mSearchSwipeTriggered"))

            val secondDown = System.currentTimeMillis()
            val fullPullY = 100f + slop * 5f
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, secondDown)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, fullPullY, secondDown)
            dispatchTouch(MotionEvent.ACTION_UP, 500f, fullPullY, secondDown)
        }
        assertEquals(1, calls)
    }

    // ---- FISH-016 review findings ----

    /** Review #1: a downward drag from the top zone must still pan the lens until it becomes search. */
    @Test
    fun topZoneDownwardDrag_belowSearchThreshold_stillStartsAPan() {
        val downTime = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            val slop = privateField<Float>("mTouchSlop")
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 100f + slop * 2f, downTime)
            assertTrue("a sub-threshold top drag must pan, not be swallowed", privateField<Boolean>("mMoving"))
            dispatchTouch(MotionEvent.ACTION_UP, 500f, 100f + slop * 2f, downTime)
        }
    }

    /** Review #2: if search fires mid-pan, the pan must still be ended on release. */
    @Test
    fun searchTriggeredMidPan_endsThePanOnRelease() {
        val downTime = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            val slop = privateField<Float>("mTouchSlop")
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 100f + slop * 2f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 100f + slop * 5f, downTime)
            dispatchTouch(MotionEvent.ACTION_UP, 500f, 100f + slop * 5f, downTime)
            assertFalse("pan must not stay stuck after release", privateField<Boolean>("mMoving"))
        }
    }

    /** Review #3: a second finger after the trigger must not make the release launch an app. */
    @Test
    fun secondFingerAfterSearchTriggered_stillConsumesRelease() {
        var calls = 0
        val downTime = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutEmptyLens()
            val slop = privateField<Float>("mTouchSlop")
            lensView.onSearchSwipeDownListener = OnSearchSwipeDownListener { calls++ }
            dispatchTouch(MotionEvent.ACTION_DOWN, 500f, 100f, downTime)
            dispatchTouch(MotionEvent.ACTION_MOVE, 500f, 100f + slop * 5f, downTime)
            assertEquals(1, calls)
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
                MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER })
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = 500f; y = 100f + slop * 5f },
                MotionEvent.PointerCoords().apply { x = 600f; y = 300f })
            val pointerDown = MotionEvent.obtain(downTime, System.currentTimeMillis(),
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            lensView.dispatchTouchEvent(pointerDown)
            pointerDown.recycle()
            assertTrue(
                "the already-triggered search swipe must still own the release",
                privateField<Boolean>("mSearchSwipeTriggered")
            )
        }
    }

    @Test
    fun quickActionsMenu_onAnUnattachedView_failsCleanlyAndLeavesNoAnchor() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutSingleAppGridAndDispatchDown()
            val shown = lensView.showAppOptionsAtIndex(0)
            assertFalse("no window token, so the menu cannot show", shown)
            assertNull(
                "no parent FrameLayout means no anchor may be created",
                lensView.quickActionsAnchorForTest
            )
        }
    }

    @Test
    fun testInitialization() {
        assertNotNull(lensView)
    }

    @Test
    fun testSetApps() {
        // Independent of Smart Focus global default/leftover state from other test classes
        // (LensViewSmartFocusIntegrationTest's own @Before/@After already follow this same
        // defensive pattern for the same reason).
        com.mckimquyen.util.UtilSettings(context).save(com.mckimquyen.util.UtilSettings.KEY_SMART_FOCUS_BIAS, false)
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1"),
            App(id = 2, label = "Test App 2", packageName = "com.test2", name = "Activity2")
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
        }

        val displayed: ArrayList<App> = privateField("mApps")
        assertEquals("setApps must populate the displayed list with exactly the apps given", 2, displayed.size)
        assertEquals(listOf(1, 2), displayed.map { it.id })
    }

    // ==================================================================== B2 (test-audit)

    @Test
    fun launchAppAtIndex_withoutPackageManagerSet_isANoOp() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1")
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            // mPackageManager was never set (setPackageManager() not called) - the guard at
            // LensView.kt:417 must block the launch attempt entirely, not just fail inside it.
            lensView.launchAppAtIndex(0)
        }
        // No crash is the only observable contract here: the whole point of the guard is that
        // nothing downstream (UtilApp.launchComponent) ever runs without a PackageManager.
    }

    @Test
    fun launchAppAtIndex_invalidIndex_isANoOpAndDoesNotThrow() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1")
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            lensView.setPackageManager(context.packageManager)
            lensView.launchAppAtIndex(99)
        }
    }

    @Test
    fun launchAppAtIndex_validIndexWithPackageManagerSet_doesNotThrow() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1")
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            lensView.setPackageManager(context.packageManager)
            // com.test1/Activity1 does not resolve to a real component - UtilApp.launchComponent
            // catches ActivityNotFoundException internally (shows a toast), so this must reach
            // that real call path and return normally, never throw or crash the test.
            lensView.launchAppAtIndex(0)
        }
    }

    @Test
    fun testTouchEventsOnMainThread() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1")
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            // Perform simulated actions
            val downEvent = android.view.MotionEvent.obtain(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                android.view.MotionEvent.ACTION_DOWN,
                100f,
                100f,
                0
            )
            val handled = lensView.dispatchTouchEvent(downEvent)
            assertTrue("Touch event should be handled by LensView", handled)
            downEvent.recycle()
        }
    }

    // ==================================================================== UI-022

    @Test
    fun testShowAppOptionsAtIndex_validIndexButUnattachedWindow_failsCleanlyWithoutCrashing() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1")
        )
        var result = true
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            // This view is never attached to a real window in this test harness (no
            // ViewRootImpl), so PopupMenu.show() has no window token and fails internally -
            // caught and reported as `false`, never a crash. Real success on a genuine
            // attached+themed window is proven separately by
            // LensViewQuickActionsIntegrationTest, which launches the real ActHome Activity.
            result = lensView.showAppOptionsAtIndex(0)
        }
        assertFalse("a popup that can't attach to a window must fail cleanly, not crash", result)
    }

    @Test
    fun testShowAppOptionsAtIndex_invalidIndex_returnsFalse() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1")
        )
        var result = true
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            result = lensView.showAppOptionsAtIndex(5)
        }
        assertFalse("an out-of-range index must report failure, not throw", result)
    }

    @Test
    fun testLongPress_stationaryTouch_firesAndIsConsumedByUp_neverLaunchesOnRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var point = 0f to 0f
        instrumentation.runOnMainSync {
            point = layoutSingleAppGridAndDispatchDown()
        }
        Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 200)
        instrumentation.waitForIdleSync()

        instrumentation.runOnMainSync {
            assertTrue(
                "the long-press must have fired while the touch stayed stationary over an icon",
                privateField<Boolean>("mLongPressTriggered")
            )
            assertFalse("a stationary long-press must never become a pan", privateField<Boolean>("mMoving"))
            dispatchUp(point.first, point.second)
            assertFalse(
                "ACTION_UP must consume the triggered flag instead of also launching the app",
                privateField<Boolean>("mLongPressTriggered")
            )
        }
    }

    @Test
    fun testLongPress_realPanBeforeTimeout_neverFires() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var point = 0f to 0f
        instrumentation.runOnMainSync {
            point = layoutSingleAppGridAndDispatchDown()
            // A real pan, well past touch slop, before the long-press timer can elapse.
            dispatchMove(point.first + 400f, point.second + 400f)
            assertTrue("a real pan must set mMoving", privateField<Boolean>("mMoving"))
            assertFalse("a real pan must immediately disarm the pending long-press", privateField<Boolean>("mLongPressArmed"))
        }
        Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 200)
        instrumentation.waitForIdleSync()

        instrumentation.runOnMainSync {
            assertFalse(
                "a gesture that started as a pan must never fire the long-press, even after the timeout elapses",
                privateField<Boolean>("mLongPressTriggered")
            )
            dispatchUp(point.first + 400f, point.second + 400f)
        }
    }

    @Test
    fun testQuickTap_wellBeforeTimeout_cancelsPendingLongPressCleanly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var point = 0f to 0f
        instrumentation.runOnMainSync {
            point = layoutSingleAppGridAndDispatchDown()
            dispatchUp(point.first, point.second)
            assertFalse("releasing before the timeout must disarm the long-press", privateField<Boolean>("mLongPressArmed"))
        }
        Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 200)
        instrumentation.waitForIdleSync()

        instrumentation.runOnMainSync {
            assertFalse(
                "the cancelled Runnable must never fire after the touch sequence already ended",
                privateField<Boolean>("mLongPressTriggered")
            )
        }
    }
}
