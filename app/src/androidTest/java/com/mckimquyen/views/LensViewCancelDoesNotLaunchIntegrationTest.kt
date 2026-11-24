package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release smoke 2026-11-23 (KJ7): a drag that ended with ACTION_CANCEL (the system took the gesture back) opened the app under the finger, because the
 * wind-down animation of the pan launched the selected app when it ended. A cancelled gesture must open nothing; a normal lift after a pan still opens
 * the selected app (positive control, so the "nothing launched" assertion cannot pass because launching is broken). The launch is observed through
 * [LensView.appLauncherForTest], with the real hide animation running in a real window.
 */
@RunWith(AndroidJUnit4::class)
class LensViewCancelDoesNotLaunchIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 40
        const val DRAG_STEPS = 12
        const val DRAG_STEP_PX = 6f
        const val STEP_DELAY_MS = 16L
        const val ANIMATION_SETTLE_MS = 1_500L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val launched = ArrayList<String>()

    private fun withView(block: (LensView) -> Unit) {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.cancel.t$i", name = "T$i", label = "T $i", iconCacheKey = "cancelt#$i")
        })
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setPackageManager(activity.packageManager)
                    appLauncherForTest = { pkg, _ -> launched.add(pkg) }
                    setApps(apps)
                    layoutParams = FrameLayout.LayoutParams(SIZE_PX, SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, SIZE_PX, SIZE_PX)
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            block(requireNotNull(view) { "assigned in onActivity above" })
        }
    }

    private fun send(view: LensView, down: Long, action: Int, x: Float, y: Float) {
        instrumentation.runOnMainSync {
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
            view.draw(Canvas())
        }
    }

    /** Presses an icon, drags past the touch slop so the pan starts, and returns the last finger position. */
    private fun dragFromAnIcon(view: LensView): Triple<Long, Float, Float> {
        val bounds = Rect()
        var found = false
        instrumentation.runOnMainSync { found = view.getAppBounds(APP_COUNT / 2, bounds) }
        assertTrue("positive control: the icon has bounds", found)
        val down = SystemClock.uptimeMillis()
        var x = bounds.exactCenterX()
        var y = bounds.exactCenterY()
        send(view, down, MotionEvent.ACTION_DOWN, x, y)
        repeat(DRAG_STEPS) {
            x += DRAG_STEP_PX
            y += DRAG_STEP_PX
            send(view, down, MotionEvent.ACTION_MOVE, x, y)
            SystemClock.sleep(STEP_DELAY_MS)
        }
        return Triple(down, x, y)
    }

    @Test fun aCancelledPanLaunchesNothingEvenAfterTheWindDownAnimationEnds() = withView { view ->
        val (down, x, y) = dragFromAnIcon(view)
        send(view, down, MotionEvent.ACTION_CANCEL, x, y)
        SystemClock.sleep(ANIMATION_SETTLE_MS)
        instrumentation.waitForIdleSync()
        assertTrue("a cancelled gesture must not open an app (got $launched)", launched.isEmpty())
    }

    @Test fun aNormalLiftAfterAPanStillOpensTheSelectedApp() = withView { view ->
        val (down, x, y) = dragFromAnIcon(view)
        send(view, down, MotionEvent.ACTION_UP, x, y)
        SystemClock.sleep(ANIMATION_SETTLE_MS)
        instrumentation.waitForIdleSync()
        assertEquals("positive control: lifting after a pan opens exactly one app", 1, launched.size)
    }
}
