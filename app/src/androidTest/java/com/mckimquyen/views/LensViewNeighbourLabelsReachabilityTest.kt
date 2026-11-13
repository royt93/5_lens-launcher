package com.mckimquyen.views

import android.graphics.Canvas
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-047 reachability: real DOWN/MOVE events through dispatchTouchEvent and a real draw; no label, index or pan hook. */
@RunWith(AndroidJUnit4::class)
class LensViewNeighbourLabelsReachabilityTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW = 1_000
        const val PAN_DP = 40f
        const val FRAMES = 4
        const val FRAME_MS = 16L
        // Short labels sit far inside the room a 100-app lens leaves between neighbours (measured 220-240 px apart at 3.0 density
        // while a 7-character label plus the gap needs about 225 px, so that length is borderline and flips with the lens animation).
        const val SHORT_LABEL = "A"
        const val LONG_LABEL = "A very long application name that cannot sit beside another"
    }

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val density = ctx.resources.displayMetrics.density

    @Before fun setUp() {
        UtilSettings(ctx).apply {
            save(UtilSettings.KEY_SHOW_NAME_APP_HOVER, true)
            save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        }
    }

    @After fun tearDown() {
        UtilSettings(ctx).apply {
            save(UtilSettings.KEY_NEIGHBOUR_LABELS, false)
            save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        }
    }

    private class Obs(val selected: Int, val cols: Int, val drawn: Int)

    /** Pans a real finger onto the centre of [targetCell] of an [apps]-app lens and reports the labels drawn there. */
    private fun run(apps: Int, targetCell: Int, switchOn: Boolean, clean: Boolean = false, label: String = SHORT_LABEL): Obs {
        UtilSettings(ctx).apply {
            save(UtilSettings.KEY_NEIGHBOUR_LABELS, switchOn)
            save(UtilSettings.KEY_CLEAN_LENS_MODE, clean)
        }
        var result: Obs? = null
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { sc ->
            var v: LensView? = null
            sc.onActivity { a ->
                v = LensView(a).apply {
                    setApps(ArrayList((0 until apps).map { App(id = it, packageName = "com.f047.r$it", name = "R$it", label = "$label$it") }))
                    layoutParams = FrameLayout.LayoutParams(VIEW, VIEW); a.setContentView(this)
                    measure(View.MeasureSpec.makeMeasureSpec(VIEW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(VIEW, View.MeasureSpec.EXACTLY))
                    layout(0, 0, VIEW, VIEW); draw(Canvas())
                }
            }
            val t = requireNotNull(v)
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) = inst.runOnMainSync {
                val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); t.dispatchTouchEvent(e); e.recycle()
            }
            val cell = t.baseRectsForTest()[targetCell]
            val x = cell.centerX(); val y = cell.centerY()
            send(MotionEvent.ACTION_DOWN, x - PAN_DP * density, y + PAN_DP * density)
            send(MotionEvent.ACTION_MOVE, x - PAN_DP * density / 2f, y + PAN_DP * density / 2f)
            send(MotionEvent.ACTION_MOVE, x, y)
            var selected = -1; var drawn = 0
            repeat(FRAMES) {
                inst.runOnMainSync { t.draw(Canvas()); selected = t.selectedIndexForTest; drawn = t.neighbourLabelsDrawnForTest }
                SystemClock.sleep(FRAME_MS)
            }
            result = Obs(selected, t.baseRectsForTest().size.let { _ -> columnsOf(t) }, drawn)
            send(MotionEvent.ACTION_UP, x, y)
        }
        return requireNotNull(result)
    }

    private fun columnsOf(t: LensView): Int {
        val rects = t.baseRectsForTest()
        val firstRowTop = rects.first().top
        return rects.count { it.top == firstRowTop }
    }

    @Test fun aRealPanOverADenseLensDrawsBothNeighbourLabels() {
        val o = run(apps = 100, targetCell = 52, switchOn = true)
        assertTrue("an icon is selected, was ${o.selected}", o.selected >= 0)
        assertEquals("both same-row neighbours fit on a 100-app lens, cols=${o.cols}", 2, o.drawn)
    }

    @Test fun neighbourLabelsThatWouldTouchTheMainLabelAreSkippedNotCrowded() {
        val o = run(apps = 100, targetCell = 52, switchOn = true, label = LONG_LABEL)
        assertTrue("positive control: an icon is selected, was ${o.selected}", o.selected >= 0)
        assertEquals("labels this long do not fit beside each other on a 100-app lens", 0, o.drawn)
    }

    @Test fun theSwitchOffDrawsNoNeighbourLabels() {
        val o = run(apps = 100, targetCell = 52, switchOn = false)
        assertTrue("positive control: an icon is selected, was ${o.selected}", o.selected >= 0)
        assertEquals(0, o.drawn)
    }

    @Test fun cleanLensModeDrawsNoNeighbourLabels() {
        val o = run(apps = 100, targetCell = 52, switchOn = true, clean = true)
        assertTrue("positive control: an icon is selected, was ${o.selected}", o.selected >= 0)
        assertEquals(0, o.drawn)
    }

    @Test fun neverDrawsMoreThanTheTwoSameRowNeighbours() {
        for (apps in listOf(30, 60, 100, 150)) {
            val o = run(apps = apps, targetCell = apps / 2, switchOn = true)
            assertTrue("apps=$apps drawn=${o.drawn}", o.drawn in 0..NeighbourLabels.MAX_NEIGHBOURS)
        }
    }
}
