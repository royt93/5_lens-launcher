package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
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
        const val ICON_PX = 48
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val launched = ArrayList<String>()

    private fun withView(block: (LensView) -> Unit) {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.cancel.t$i", name = "T$i", label = "T $i", iconCacheKey = "cancelt#$i")
        })
        // An icon without a cached bitmap is not drawn and so never selected, which would make every "launches nothing" assertion pass for the wrong reason.
        val icon = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, icon) }
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
            try {
                block(requireNotNull(view) { "assigned in onActivity above" })
            } finally {
                com.mckimquyen.util.BitmapCache.clear()
            }
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

    /**
     * Presses the icon nearest the view centre, drags out past the touch slop so the pan starts, then comes back to the icon centre and draws
     * there, so the finger ends ON an icon (a drag that ends in a gap selects nothing and would make the cancel test pass for the wrong reason).
     */
    private fun dragOutAndBackOntoAnIcon(view: LensView): Triple<Long, Float, Float> {
        val bounds = Rect()
        var best = Float.MAX_VALUE
        var found = false
        instrumentation.runOnMainSync {
            val candidate = Rect()
            for (i in 0 until APP_COUNT) {
                if (!view.getAppBounds(i, candidate)) continue
                val distance = Math.hypot((candidate.exactCenterX() - SIZE_PX / 2f).toDouble(), (candidate.exactCenterY() - SIZE_PX / 2f).toDouble()).toFloat()
                if (distance < best) { best = distance; bounds.set(candidate); found = true }
            }
        }
        assertTrue("positive control: a cell has bounds", found)
        val down = SystemClock.uptimeMillis()
        val startX = bounds.exactCenterX()
        val startY = bounds.exactCenterY()
        send(view, down, MotionEvent.ACTION_DOWN, startX, startY)
        repeat(DRAG_STEPS) { step ->
            val offset = DRAG_STEP_PX * (step + 1)
            send(view, down, MotionEvent.ACTION_MOVE, startX + offset, startY + offset)
            SystemClock.sleep(STEP_DELAY_MS)
        }
        repeat(DRAG_STEPS) { step ->
            val offset = DRAG_STEP_PX * (DRAG_STEPS - step - 1)
            send(view, down, MotionEvent.ACTION_MOVE, startX + offset, startY + offset)
            SystemClock.sleep(STEP_DELAY_MS)
        }
        return Triple(down, startX, startY)
    }

    @Test fun aCancelledPanLaunchesNothingEvenAfterTheWindDownAnimationEnds() = withView { view ->
        val (down, x, y) = dragOutAndBackOntoAnIcon(view)
        send(view, down, MotionEvent.ACTION_CANCEL, x, y)
        SystemClock.sleep(ANIMATION_SETTLE_MS)
        instrumentation.waitForIdleSync()
        assertTrue("a cancelled gesture must not open an app (got $launched)", launched.isEmpty())
    }

    @Test fun aNormalLiftAfterAPanStillOpensTheSelectedApp() = withView { view ->
        val (down, x, y) = dragOutAndBackOntoAnIcon(view)
        send(view, down, MotionEvent.ACTION_UP, x, y)
        SystemClock.sleep(ANIMATION_SETTLE_MS)
        instrumentation.waitForIdleSync()
        assertEquals("positive control: lifting after a pan opens exactly one app", 1, launched.size)
    }
}
