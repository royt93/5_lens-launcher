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

    @Test
    fun testInitialization() {
        assertNotNull(lensView)
    }

    @Test
    fun testSetApps() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1"),
            App(id = 2, label = "Test App 2", packageName = "com.test2", name = "Activity2")
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
        }

        assertTrue(true)
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
