package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reachability: real DOWN/MOVE events and real waiting. No speed, index, clock or pan hook. */
@RunWith(AndroidJUnit4::class)
class LensViewHoverMagnifyReachabilityTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW = 1_000
        const val APPS = 40
        const val PAN_DP = 40f
        const val DRIFT_PX = 1f
        const val FRAME_MS = 16L
        const val SETTLE_MS = HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS + 120L
    }

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val density = ctx.resources.displayMetrics.density

    @After fun tearDown() { UtilSettings(ctx).save(UtilSettings.KEY_FOCUS_MAGNIFY, false) }

    private class Obs(val index: Int, val rectBefore: RectF, val rectAfter: RectF, val scale: Float, val hitIndexBefore: Int, val hitIndexAfter: Int, val magnified: Int)

    private fun run(enabled: Boolean, moveWhileWaiting: Boolean): Obs {
        UtilSettings(ctx).save(UtilSettings.KEY_FOCUS_MAGNIFY, enabled)
        var result: Obs? = null
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { sc ->
            var v: LensView? = null
            sc.onActivity { a ->
                v = LensView(a).apply {
                    setApps(ArrayList((0 until APPS).map { App(id = it, packageName = "com.f046.r$it", name = "R$it", label = "R $it") }))
                    layoutParams = FrameLayout.LayoutParams(VIEW, VIEW); a.setContentView(this)
                    measure(View.MeasureSpec.makeMeasureSpec(VIEW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(VIEW, View.MeasureSpec.EXACTLY))
                    layout(0, 0, VIEW, VIEW); draw(Canvas())
                }
            }
            val t = requireNotNull(v)
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) = inst.runOnMainSync { val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); t.dispatchTouchEvent(e); e.recycle() }
            var x = 200f; var y = 800f
            send(MotionEvent.ACTION_DOWN, x, y)
            x += PAN_DP * density; y -= PAN_DP * density
            send(MotionEvent.ACTION_MOVE, x, y)            // real pan starts
            var sel = -1; var before = RectF(); var hitBefore = -1
            val startWait = SystemClock.uptimeMillis()
            // First few frames, to pick the icon under the finger, then keep the finger exactly there for SETTLE_MS.
            repeat(3) { inst.runOnMainSync { t.draw(Canvas()); sel = t.selectedIndexForTest; before = RectF(t.drawnRectsForTest().getOrNull(maxOf(sel, 0)) ?: RectF()); hitBefore = sel }; SystemClock.sleep(FRAME_MS) }
            var wiggle = 0f
            while (SystemClock.uptimeMillis() - startWait < SETTLE_MS) {
                if (moveWhileWaiting) { wiggle = if (wiggle == 0f) 12f * density else 0f; send(ACTION_MOVE, x + wiggle, y) } else send(ACTION_MOVE, x + DRIFT_PX * (if ((SystemClock.uptimeMillis() / 50) % 2 == 0L) 1 else -1), y)
                inst.runOnMainSync { t.draw(Canvas()) }
                SystemClock.sleep(FRAME_MS)
            }
            inst.runOnMainSync {
                t.draw(Canvas())
                val idx = t.selectedIndexForTest
                val after = RectF(t.drawnRectsForTest().getOrNull(maxOf(idx, 0)) ?: RectF())
                result = Obs(sel, before, after, t.hoverMagnifyScaleForTest(maxOf(idx, 0)), hitBefore, idx, t.magnifiedIconDrawnForTest)
            }
            send(MotionEvent.ACTION_UP, x, y)
        }
        return requireNotNull(result)
    }
    private val ACTION_MOVE = MotionEvent.ACTION_MOVE

    @Test fun aFingerThatRestsOnAnIconAfterARealPanMagnifiesIt() {
        val o = run(enabled = true, moveWhileWaiting = false)
        assertTrue("an icon is selected", o.index >= 0)
        assertEquals("the same icon stays selected", o.hitIndexBefore, o.hitIndexAfter)
        assertTrue("one icon is drawn magnified, was ${o.magnified}", o.magnified == 1)
        assertEquals("scale reaches the target", HoverMagnify.SCALE, o.scale, 0.01f)
        assertTrue("drawn width ${o.rectAfter.width()} vs ${o.rectBefore.width()}", o.rectAfter.width() >= o.rectBefore.width() * 1.15f)
    }

    @Test fun aFingerThatKeepsMovingNeverMagnifiesAnything() {
        val o = run(enabled = true, moveWhileWaiting = true)
        assertEquals(0, o.magnified)
    }

    @Test fun withTheSwitchOffNothingIsMagnified() {
        assertEquals(0, run(enabled = false, moveWhileWaiting = false).magnified)
    }
}
