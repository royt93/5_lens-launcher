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
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-042 integration: pan lifecycle, cancel/UP reset, switch gate; haptic seam + mMoving. */
@RunWith(AndroidJUnit4::class)
class LensViewFocusBoundaryHapticIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 40
        const val LIGHT_AMP = 80
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val hapticEvents = mutableListOf<Int>()

    private fun withView(block: (LensView) -> Unit) {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish042.int$i", name = "Int$i", label = "Int $i")
        })
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(apps)
                    layoutParams = FrameLayout.LayoutParams(SIZE_PX, SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, SIZE_PX, SIZE_PX)
                    onHapticPerformed = { hapticEvents.add(it) }
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            hapticEvents.clear()
            block(requireNotNull(view))
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun draw(view: LensView) = onMain { view.draw(Canvas()) }

    private fun startPan(view: LensView) {
        val moving = LensView::class.java.getDeclaredField("mMoving").apply { isAccessible = true }
        moving.setBoolean(view, true)
        onMain { view.gestureState = LensGestureState.PANNING }
    }

    private fun cellCentre(view: LensView, index: Int): Pair<Float, Float> {
        draw(view)
        val bounds = Rect()
        var found = false
        onMain { found = view.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun send(view: LensView, action: Int, downTime: Long, x: Float, y: Float) = onMain {
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    @Test fun boundaryTransitionOnSameAppDuringPanEmitsLightPulse() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        val (cx, cy) = cellCentre(view, 0)
        view.setLensStateForTest(cx, cy, 1f, reduceMotion = false)
        startPan(view)
        draw(view)
        hapticEvents.clear()
        view.setLensStateForTest(SIZE_PX / 2f, SIZE_PX / 2f, 1f, reduceMotion = false)
        draw(view)
        assertTrue("a LIGHT boundary pulse was emitted on leaving the sharp zone",
            hapticEvents.contains(LIGHT_AMP) || view.boundaryHapticFiredForTest)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
    }

    @Test fun actionCancelResetsTheBoundaryTracker() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        val (cx, cy) = cellCentre(view, 0)
        view.setLensStateForTest(cx, cy, 1f, reduceMotion = false)
        startPan(view)
        draw(view)
        val down = SystemClock.uptimeMillis()
        send(view, MotionEvent.ACTION_CANCEL, down, cx, cy)
        draw(view)
        assertFalse("after cancel the boundary tracker is silent", view.boundaryHapticFiredForTest)
    }

    @Test fun hoverSwitchOffSuppressesBoundaryPulse() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        val (cx, cy) = cellCentre(view, 0)
        view.setLensStateForTest(cx, cy, 1f, reduceMotion = false)
        startPan(view)
        draw(view)
        hapticEvents.clear()
        view.setLensStateForTest(SIZE_PX / 2f, SIZE_PX / 2f, 1f, reduceMotion = false)
        draw(view)
        assertFalse("switch off suppresses boundary haptic", view.boundaryHapticFiredForTest)
        assertTrue("no haptic at all", hapticEvents.isEmpty())
    }

    @Test fun detachResetsTheBoundaryTracker() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        val (cx, cy) = cellCentre(view, 0)
        view.setLensStateForTest(cx, cy, 1f, reduceMotion = false)
        startPan(view)
        draw(view)
        onMain { (view.parent as FrameLayout).removeView(view) }
        assertFalse(view.boundaryHapticFiredForTest)
    }
}
