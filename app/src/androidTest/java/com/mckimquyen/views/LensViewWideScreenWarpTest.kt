package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.util.LensPhysicsPolicy
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The warp under a real finger on the REAL window of whatever display the device is in (phone, landscape, or a `wm size`
 * override standing in for a tablet). Real dispatchTouchEvent pans, no state hooks. The test logs the geometry (tag F155WIDE) so
 * the same run can be compared across display configurations, and asserts only things that must hold on every screen:
 * the idle grid is untouched (control), a pan really magnifies something (positive control), no icon is magnified past the
 * lens's own cap, and when an icon is selected the finger really is inside its drawn rect. Skipped under reduced motion,
 * where the lens does not warp.
 */
@RunWith(AndroidJUnit4::class)
class LensViewWideScreenWarpTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F155WIDE"
        const val APP_COUNT = 102
        const val FRAME_MS = 16L
        const val SETTLE_FRAMES = 45
        const val PAN_DP = 40f
        const val MIN_MAGNIFICATION = 1.05f
        const val IDLE_TOLERANCE = 0.01f
        const val MAX_MAGNIFICATION_CAP = 6f
        const val FRACTIONS_COUNT = 5
        const val OVERLAP_FRACTION = 0.25f
    }

    private class Spot(val name: String, val fx: Float, val fy: Float)

    private val spots = listOf(
        Spot("centre", 0.5f, 0.5f), Spot("upper-left", 0.25f, 0.25f), Spot("lower-right", 0.8f, 0.8f),
        Spot("right-edge", 0.92f, 0.5f), Spot("bottom-edge", 0.5f, 0.92f),
    )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class Frame(val rects: List<RectF>, val selected: Int, val finger: Pair<Float, Float>)

    @Test fun theWarpUnderARealFingerIsSaneOnThisDisplay() {
        assumeFalse("reduced motion: the lens does not warp", LensPhysicsPolicy.shouldReduceLensMotion(instrumentation.targetContext))
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val apps = ArrayList((0 until APP_COUNT).map { App(id = it, packageName = "com.wide.e$it", name = "E$it", label = "E $it") })
        var peakMagnification = 0f
        var selectionChecks = 0
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(apps)
                    activity.setContentView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
            instrumentation.waitForIdleSync()
            val target = requireNotNull(view)
            var width = 0
            var height = 0
            var base: List<RectF> = emptyList()
            var idle: List<RectF> = emptyList()
            instrumentation.runOnMainSync {
                width = target.width
                height = target.height
                target.draw(Canvas())
                base = target.baseRectsForTest().toList()
                idle = target.drawnRectsForTest()
            }
            assertTrue("the lens must have a real size, was ${width}x$height", width > 0 && height > 0)
            assertTrue("a grid must exist", base.isNotEmpty())
            // The probe is filled per cell inside the icon loop, which skips every cell past the last app, so the grid can have more
            // cells than apps and the surplus drawn rects stay empty (an earlier version of this test compared those and read a 2900 px
            // "movement"). Only the first [appCount] cells are real.
            val appCount = apps.size.coerceAtMost(base.size)
            assertTrue("probe sanity: the first $appCount drawn rects must be real, not unfilled zero rects", idle.size == base.size && (0 until appCount).all { idle[it].width() > 0f && idle[it].height() > 0f })
            val cols = base.map { Math.round(it.left) }.distinct().size
            val rows = base.map { Math.round(it.top) }.distinct().size
            Log.i(TAG, "display ${metrics.widthPixels}x${metrics.heightPixels} @${metrics.densityDpi}dpi view ${width}x$height grid ${cols}x$rows cells=${base.size} apps=$appCount baseCell=${"%.0f".format(base[0].width())}x${"%.0f".format(base[0].height())}px")
            val idleMovement = (0 until appCount).maxOf { maxOf(Math.abs(idle[it].left - base[it].left), Math.abs(idle[it].top - base[it].top)) }
            Log.i(TAG, "idle max movement ${"%.2f".format(idleMovement)}px")
            assertEquals("control: an idle lens draws the base grid", 0f, idleMovement, IDLE_TOLERANCE * base[0].width())

            for (spot in spots) {
                // End the pan exactly on the centre of the grid cell nearest to the spot, so an icon really is under the finger.
                val wantX = width * spot.fx
                val wantY = height * spot.fy
                val cellIndex = (0 until appCount).minByOrNull { hypot(base[it].centerX() - wantX, base[it].centerY() - wantY) }!!
                val endX = base[cellIndex].centerX()
                val endY = base[cellIndex].centerY()
                val x = endX - PAN_DP * metrics.density
                val y = endY - PAN_DP * metrics.density
                val downTime = SystemClock.uptimeMillis()
                fun send(action: Int, px: Float, py: Float) = instrumentation.runOnMainSync {
                    val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, px, py, 0)
                    target.dispatchTouchEvent(e); e.recycle()
                }
                send(MotionEvent.ACTION_DOWN, x, y)
                // Diagonal on purpose: a near-horizontal drag reads as a page swipe and never pans the lens.
                send(MotionEvent.ACTION_MOVE, (x + endX) / 2, (y + endY) / 2)
                send(MotionEvent.ACTION_MOVE, endX, endY)
                var best: Frame? = null
                var spotPeak = 0f
                repeat(SETTLE_FRAMES) {
                    var frame: Frame? = null
                    instrumentation.runOnMainSync {
                        target.draw(Canvas())
                        frame = Frame(target.drawnRectsForTest(), target.selectedIndexForTest, target.touchCoordinatesForTest())
                    }
                    val f = requireNotNull(frame)
                    val mag = (0 until appCount).maxOf { f.rects[it].width() / base[it].width() }
                    if (mag >= spotPeak) { spotPeak = mag; best = f }
                    SystemClock.sleep(FRAME_MS)
                }
                send(MotionEvent.ACTION_CANCEL, endX, endY)
                val f = requireNotNull(best)
                val outside = f.rects.take(appCount).count { it.centerX() < 0 || it.centerY() < 0 || it.centerX() > width || it.centerY() > height }
                val overlapping = overlappingPairs(f.rects.take(appCount))
                val selectedHit = f.selected >= 0 && f.rects[f.selected].contains(f.finger.first, f.finger.second)
                Log.i(TAG, "${spot.name} finger=(${"%.0f".format(f.finger.first)},${"%.0f".format(f.finger.second)}) peakMagnification=${"%.2f".format(spotPeak)} " +
                    "selected=${f.selected} selectedContainsFinger=$selectedHit centresOutsideView=$outside overlappingPairs=$overlapping")
                peakMagnification = maxOf(peakMagnification, spotPeak)
                assertTrue("${spot.name}: magnification $spotPeak beyond the sanity cap $MAX_MAGNIFICATION_CAP", spotPeak <= MAX_MAGNIFICATION_CAP)
                // Positive control for selection: the finger rests on the centre of cell [cellIndex], so that cell (and nothing else)
                // must be the selected one and its drawn rect must contain the finger. (The first version of this test put the finger
                // on arbitrary points, read selected=-1 at 20 of 20, and so asserted nothing.)
                selectionChecks++
                assertEquals("${spot.name}: the icon under the finger must be selected", cellIndex, f.selected)
                assertTrue("${spot.name}: the selected icon's drawn rect must contain the finger", selectedHit)
            }
        }
        assertEquals("every spot must have been checked for selection", spots.size, selectionChecks)
        assertTrue("positive control: a real pan must magnify at least one icon somewhere, peak $peakMagnification", peakMagnification > MIN_MAGNIFICATION)
    }

    private fun overlappingPairs(rects: List<RectF>): Int {
        var count = 0
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            val a = rects[i]
            val b = rects[j]
            val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (w <= 0 || h <= 0) continue
            val smaller = minOf(a.width() * a.height(), b.width() * b.height())
            if (w * h > OVERLAP_FRACTION * smaller) count++
        }
        return count
    }
}
