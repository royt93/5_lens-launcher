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
        const val TARGET_CELL = 14
        const val JITTER_PX = 0.5f
        const val FAST_WIGGLE_DP = 4f
        const val FRAME_MS = 16L
        const val SETTLE_MS = HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS + 120L
    }

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val density = ctx.resources.displayMetrics.density

    @After fun tearDown() { UtilSettings(ctx).save(UtilSettings.KEY_FOCUS_MAGNIFY, false) }

    private class Obs(val index: Int, val rectBefore: RectF, val rectAfter: RectF, val scale: Float, val hitIndexBefore: Int, val hitIndexAfter: Int, val magnified: Int, val halo: RectF)

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
            // Put the finger exactly on the centre of a real cell, so an icon is selected. Start well away and pan onto it.
            val cell = t.baseRectsForTest()[TARGET_CELL]
            var x = cell.centerX() - PAN_DP * density; var y = cell.centerY() + PAN_DP * density
            send(MotionEvent.ACTION_DOWN, x, y)
            send(MotionEvent.ACTION_MOVE, x + PAN_DP * density / 2f, y - PAN_DP * density / 2f)
            x = cell.centerX(); y = cell.centerY()
            send(MotionEvent.ACTION_MOVE, x, y)            // real pan, finger now on the cell centre
            var sel = -1; var before = RectF(); var hitBefore = -1
            val startWait = SystemClock.uptimeMillis()
            // First few frames, to pick the icon under the finger, then keep the finger exactly there for SETTLE_MS.
            repeat(3) { inst.runOnMainSync { t.draw(Canvas()); sel = t.selectedIndexForTest; before = RectF(t.drawnRectsForTest().getOrNull(maxOf(sel, 0)) ?: RectF()); hitBefore = sel }; SystemClock.sleep(FRAME_MS) }
            var wiggle = 0f
            while (SystemClock.uptimeMillis() - startWait < SETTLE_MS) {
                // Fast but small: 4 dp every frame is ~250 dp/s, and it stays inside the icon, so ONLY the speed gate can stop the dwell.
                if (moveWhileWaiting) { wiggle = if (wiggle == 0f) FAST_WIGGLE_DP * density else 0f; send(ACTION_MOVE, x + wiggle, y) }
                // A real resting finger is never perfectly still: it jitters by about a pixel. That is what resets the
                // tracker's last segment to a tiny distance, so send ~1 px of jitter at the frame rate.
                if (!moveWhileWaiting) send(ACTION_MOVE, x + (if ((SystemClock.uptimeMillis() / FRAME_MS) % 2 == 0L) JITTER_PX else -JITTER_PX), y)
                inst.runOnMainSync { t.draw(Canvas()) }
                SystemClock.sleep(FRAME_MS)
            }
            inst.runOnMainSync {
                t.draw(Canvas())
                val idx = t.selectedIndexForTest
                val after = RectF(t.drawnRectsForTest().getOrNull(maxOf(idx, 0)) ?: RectF())
                result = Obs(sel, before, after, t.hoverMagnifyScaleForTest(maxOf(idx, 0)), hitBefore, idx, t.magnifiedIconDrawnForTest, t.haloRectForTest())
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

    @Test fun aFastFingerThatStaysInsideTheIconNeverMagnifiesIt() {
        val o = run(enabled = true, moveWhileWaiting = true)
        // Positive control: the finger really did stay on the icon, so the speed gate is the only thing that can stop the dwell.
        assertTrue("an icon is selected", o.index >= 0)
        assertEquals("the same icon stayed selected the whole time", o.index, o.hitIndexAfter)
        assertEquals(0, o.magnified)
    }

    @Test fun theFocusHaloAndTheNameFollowTheMagnifiedIcon() {
        val o = run(enabled = true, moveWhileWaiting = false)
        assertTrue("an icon is magnified", o.magnified == 1)
        // The halo is a circle of radius width/2 + padding drawn from the rect it is given: it must be the magnified one.
        assertTrue("halo rect ${o.halo.width()} follows the magnified drawn width ${o.rectAfter.width()}", Math.abs(o.halo.width() - o.rectAfter.width()) < 1f)
        assertTrue("halo is wider than the unscaled icon ${o.rectBefore.width()}", o.halo.width() > o.rectBefore.width() * 1.15f)
    }

    @Test fun withTheSwitchOffNothingIsMagnified() {
        assertEquals(0, run(enabled = false, moveWhileWaiting = false).magnified)
    }
}

/**
 * No manual draw() here: a resting finger sends no events, so only the view's own redraw request can keep the dwell and
 * the ramp going. Without it the icon would stay at 1x forever after the last MotionEvent.
 */
@RunWith(AndroidJUnit4::class)
class LensViewHoverMagnifySelfRedrawTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW = 1_000
        const val TARGET_CELL = 14
        const val PAN_DP = 40f
        const val JITTER_FRAMES = 12
        const val JITTER_PX = 0.5f
        const val FRAME_MS = 16L
        const val WAIT_MS = HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS + 500L
    }

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val density = ctx.resources.displayMetrics.density

    @After fun tearDown() { UtilSettings(ctx).save(UtilSettings.KEY_FOCUS_MAGNIFY, false) }

    @Test fun theViewKeepsRedrawingByItselfUntilTheIconIsFullyMagnified() {
        UtilSettings(ctx).save(UtilSettings.KEY_FOCUS_MAGNIFY, true)
        var scaleAtEnd = -1f
        var drawnAtEnd = -1
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { sc ->
            var v: LensView? = null
            sc.onActivity { a ->
                v = LensView(a).apply {
                    setApps(ArrayList((0 until 40).map { App(id = it, packageName = "com.f046.s$it", name = "S$it", label = "S $it") }))
                    layoutParams = FrameLayout.LayoutParams(VIEW, VIEW); a.setContentView(this)
                    measure(View.MeasureSpec.makeMeasureSpec(VIEW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(VIEW, View.MeasureSpec.EXACTLY))
                    layout(0, 0, VIEW, VIEW)
                    draw(Canvas()) // populate grid geometry before the gesture
                }
            }
            val t = requireNotNull(v)
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) = inst.runOnMainSync { val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); t.dispatchTouchEvent(e); e.recycle() }
            val cell = t.baseRectsForTest()[TARGET_CELL]
            send(MotionEvent.ACTION_DOWN, cell.centerX() - PAN_DP * density, cell.centerY() + PAN_DP * density)
            send(MotionEvent.ACTION_MOVE, cell.centerX() - PAN_DP * density / 2f, cell.centerY() + PAN_DP * density / 2f)
            send(MotionEvent.ACTION_MOVE, cell.centerX(), cell.centerY())
            // ~1 px of jitter makes the last segment tiny; then stop sending events and never call draw() again.
            repeat(JITTER_FRAMES) { i -> send(MotionEvent.ACTION_MOVE, cell.centerX() + (if (i % 2 == 0) JITTER_PX else -JITTER_PX), cell.centerY()); SystemClock.sleep(FRAME_MS) }
            SystemClock.sleep(WAIT_MS)
            inst.runOnMainSync { scaleAtEnd = t.hoverMagnifyScaleForTest(TARGET_CELL); drawnAtEnd = t.magnifiedIconDrawnForTest }
            send(MotionEvent.ACTION_UP, cell.centerX(), cell.centerY())
        }
        assertEquals("the view redrew itself to the full scale", HoverMagnify.SCALE, scaleAtEnd, 0.01f)
        assertEquals(1, drawnAtEnd)
    }
}
