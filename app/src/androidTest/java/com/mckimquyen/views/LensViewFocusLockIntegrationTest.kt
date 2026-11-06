package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewFocusLockIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun withView(block: (LensView) -> Unit) {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(arrayListOf(App(label = "Camera", packageName = "pkg.camera", name = "Main")))
                    setLayoutParams(FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX))
                    activity.setContentView(this)
                    measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, android.view.View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            block(requireNotNull(view))
        }
    }

    private fun tap(view: LensView, x: Float, y: Float, downTime: Long = SystemClock.uptimeMillis()) {
        instrumentation.runOnMainSync {
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            view.dispatchTouchEvent(down)
            down.recycle()
            view.draw(Canvas())
            val upTime = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(up)
            up.recycle()
            view.draw(Canvas())
        }
    }

    @Test fun twoEmptyTapsPinExactSecondTapPointAndAnotherPairUnpins() = withView { view ->
        var baselineRects = emptyList<RectF>()
        instrumentation.runOnMainSync {
            view.draw(Canvas())
            baselineRects = view.drawnRectsForTest().map { RectF(it) }
        }
        val slop = ViewConfiguration.get(instrumentation.targetContext).scaledDoubleTapSlop.toFloat()
        val firstX = 60f
        val firstY = 90f
        val secondX = firstX + slop / 4f
        val secondY = firstY + slop / 4f
        tap(view, firstX, firstY)
        assertFalse(view.isFocusPinnedForTest())
        SystemClock.sleep(80)
        tap(view, secondX, secondY)
        assertTrue(view.isFocusPinnedForTest())
        assertEquals(secondX to secondY, view.pinnedFocusForTest())
        assertEquals("completed double tap must release transient touch centre", -Float.MAX_VALUE,
            view.touchCoordinatesForTest().first)
        val pinnedRects = view.drawnRectsForTest()
        assertTrue("pinned visual centre must change the warp", pinnedRects.indices.any { i ->
            val before = baselineRects[i]
            val after = pinnedRects[i]
            before.left != after.left || before.top != after.top || before.right != after.right || before.bottom != after.bottom
        })
        assertEquals("touch/hit-test coordinates must stay idle while pinned", -Float.MAX_VALUE,
            view.touchCoordinatesForTest().first)

        SystemClock.sleep(80)
        tap(view, firstX, firstY)
        SystemClock.sleep(80)
        tap(view, secondX, secondY)
        assertFalse(view.isFocusPinnedForTest())
        instrumentation.runOnMainSync {
            val releasedRects = view.drawnRectsForTest()
            assertEquals(baselineRects.size, releasedRects.size)
            assertTrue("unpin must restore idle base geometry", baselineRects.indices.all { i ->
                baselineRects[i] == releasedRects[i]
            })
        }
    }

    @Test fun singleEmptyTapTimesOutWithoutPinAndIconTapDoesNotPin() = withView { view ->
        tap(view, 60f, 90f)
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout().toLong() + 100L)
        assertFalse(view.isFocusPinnedForTest())

        val bounds = Rect()
        var center = 0f to 0f
        instrumentation.runOnMainSync {
            assertTrue(view.getAppBounds(0, bounds))
            center = bounds.exactCenterX() to bounds.exactCenterY()
        }
        tap(view, center.first, center.second)
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout().toLong() + 100L)
        assertFalse("single icon tap must not pin focus", view.isFocusPinnedForTest())
    }

    private fun send(view: LensView, downTime: Long, action: Int, x: Float, y: Float) = instrumentation.runOnMainSync {
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    @Test fun panBetweenTapsCancelsPendingFirstTap() = withView { view ->
        val slop = ViewConfiguration.get(instrumentation.targetContext).scaledTouchSlop.toFloat()
        tap(view, 60f, 90f)
        SystemClock.sleep(80)
        val down = SystemClock.uptimeMillis()
        send(view, down, MotionEvent.ACTION_DOWN, 60f, 90f)
        send(view, down, MotionEvent.ACTION_MOVE, 60f + slop * 4f, 90f + slop * 4f)
        send(view, down, MotionEvent.ACTION_UP, 60f + slop * 4f, 90f + slop * 4f)
        assertFalse("a pan must never pin focus", view.isFocusPinnedForTest())
        SystemClock.sleep(80)
        tap(view, 60f, 90f)
        assertFalse("pan cancelled the first tap, so this lone tap must not pair", view.isFocusPinnedForTest())
    }

    @Test fun cancelBetweenTapsCancelsPendingFirstTap() = withView { view ->
        tap(view, 60f, 90f)
        val down = SystemClock.uptimeMillis()
        send(view, down, MotionEvent.ACTION_DOWN, 60f, 90f)
        send(view, down, MotionEvent.ACTION_CANCEL, 60f, 90f)
        SystemClock.sleep(80)
        tap(view, 60f, 90f)
        assertFalse(view.isFocusPinnedForTest())
    }

    @Test fun detachClearsPinnedFocus() = withView { view ->
        tap(view, 60f, 90f)
        SystemClock.sleep(80)
        tap(view, 60f, 90f)
        assertTrue(view.isFocusPinnedForTest())
        instrumentation.runOnMainSync { (view.parent as android.view.ViewGroup).removeView(view) }
        assertFalse("detach must clear pinned focus", view.isFocusPinnedForTest())
    }

    @Test fun hostAccessibilityActionPinsCentreThenReleasesAndNeverLaunches() = withView { view ->
        var launched = 0
        view.setPackageManager(null)
        val actionId = com.mckimquyen.R.id.action_toggle_focus_lock
        val node = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.obtain()
        instrumentation.runOnMainSync {
            androidx.core.view.ViewCompat.onInitializeAccessibilityNodeInfo(view, node)
        }
        assertEquals("host exposes exactly one focus-lock action", 1, node.actionList.count { it.id == actionId })
        assertTrue(node.actionList.first { it.id == actionId }.label?.isNotBlank() == true)

        var first = false
        instrumentation.runOnMainSync { first = view.performAccessibilityAction(actionId, null) }
        assertTrue(first)
        assertTrue(view.isFocusPinnedForTest())
        assertEquals(view.width / 2f to view.height / 2f, view.pinnedFocusForTest())

        var second = false
        instrumentation.runOnMainSync { second = view.performAccessibilityAction(actionId, null) }
        assertTrue(second)
        assertFalse(view.isFocusPinnedForTest())
        assertEquals("accessibility toggle must never launch an app", 0, launched)
    }

    private companion object {
        const val VIEW_SIZE_PX = 1_000
    }
}
