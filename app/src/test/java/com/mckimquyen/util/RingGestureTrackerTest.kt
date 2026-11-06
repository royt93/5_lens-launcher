package com.mckimquyen.util

import com.mckimquyen.util.RingRotation.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RingGestureTrackerTest {
    private val density = 3f
    private val cx = 500f
    private val cy = 500f
    private val dtMs = 8L

    private data class P(val x: Float, val y: Float, val t: Long)

    private fun circle(r: Float, radPerSec: Float, n: Int = 80, jitter: Float = 0f, seed: Long = 1, start: Float = 0f): List<P> {
        val rnd = Random(seed)
        return List(n) { i ->
            val a = start + radPerSec * i * dtMs / 1000f
            P(cx + r * cos(a) + (rnd.nextFloat() - .5f) * 2 * jitter, cy + r * sin(a) + (rnd.nextFloat() - .5f) * 2 * jitter, i * dtMs)
        }
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, pxPerSec: Float, n: Int = 80, jitter: Float = 0f, seed: Long = 1): List<P> {
        val rnd = Random(seed)
        val len = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
        val ux = (x1 - x0) / len
        val uy = (y1 - y0) / len
        return List(n) { i ->
            val d = pxPerSec * i * dtMs / 1000f
            P(x0 + ux * d + (rnd.nextFloat() - .5f) * 2 * jitter, y0 + uy * d + (rnd.nextFloat() - .5f) * 2 * jitter, i * dtMs)
        }
    }

    /** Feeds the points; returns the decision after the last one and the tracker. */
    private fun feed(points: List<P>, tracker: RingGestureTracker = RingGestureTracker(density)): Pair<Decision, RingGestureTracker> {
        tracker.begin(cx, cy, points[0].x, points[0].y, points[0].t)
        var d = tracker.decision
        for (p in points.drop(1)) d = tracker.onMove(p.x, p.y, p.t)
        return d to tracker
    }

    @Test fun aClockwiseArcAtNormalSpeedRotatesPositively() {
        val (d, t) = feed(circle(r = 300f, radPerSec = 3f))
        assertEquals(Decision.ROTATE, d)
        assertTrue(t.sweepRadians > 0f)
    }

    @Test fun aCounterClockwiseArcRotatesNegatively() {
        val (d, t) = feed(circle(r = 300f, radPerSec = -3f))
        assertEquals(Decision.ROTATE, d)
        assertTrue(t.sweepRadians < 0f)
    }

    @Test fun arcsAcrossRadiiAndJitterStillRotate() {
        for (r in listOf(100f, 200f, 450f, 600f)) for (seed in 1L..20L) {
            val (d, _) = feed(circle(r, 3f, jitter = 2f, seed = seed, start = seed * 0.3f))
            assertEquals("r=$r seed=$seed", Decision.ROTATE, d)
        }
    }

    @Test fun aStraightTangentDragNeverRotates() {
        for (seed in 1L..40L) for (speed in listOf(300f, 600f, 1000f, 1500f)) {
            val (d, _) = feed(line(800f, 500f, 800f, 1500f, speed, jitter = 3f, seed = seed))
            assertTrue("tangent speed=$speed seed=$seed was $d", d != Decision.ROTATE)
        }
    }

    @Test fun aStraightRadialOrThroughCentreDragNeverRotates() {
        assertTrue(feed(line(800f, 500f, 100f, 500f, 400f)).first != Decision.ROTATE)
        assertTrue(feed(line(800f, 500f, 1500f, 500f, 400f)).first != Decision.ROTATE)
        assertTrue(feed(line(800f, 500f, 200f, 500f, 1500f)).first != Decision.ROTATE)
    }

    @Test fun aStartInsideTheMinimumRadiusIsPanImmediately() {
        val (d, _) = feed(circle(r = RingRotation.MIN_RADIUS_DP * density * 0.9f, radPerSec = 8f))
        assertEquals(Decision.PAN, d)
    }

    @Test fun aSlowCircleExpiresTheWindowAsPan() {
        val (d, _) = feed(circle(r = 300f, radPerSec = 1f, n = 100))
        assertEquals(Decision.PAN, d)
    }

    @Test fun aSpiralThatDriftsOutsideTheRadiusBandIsPan() {
        val rnd = Random(3)
        val pts = List(80) { i ->
            val a = 3f * i * dtMs / 1000f
            val r = 300f + i * 6f       // grows 480 px over the window, far beyond 25 percent
            P(cx + r * cos(a) + rnd.nextFloat(), cy + r * sin(a), i * dtMs)
        }
        assertEquals(Decision.PAN, feed(pts).first)
    }

    @Test fun theDecisionIsStickyAndLaterSamplesCannotChangeIt() {
        val rotating = circle(300f, 3f, n = 120)
        val (d, tracker) = feed(rotating.take(40))
        assertEquals(Decision.ROTATE, d)
        assertEquals(Decision.ROTATE, tracker.onMove(cx + 300f, cy, rotating[40].t + 5000))
    }

    @Test fun sweepKeepsAccumulatingAfterTheDecisionAcrossThePiBoundary() {
        val pts = circle(300f, 3f, n = 200, start = (PI - 0.2).toFloat())
        val (_, tracker) = feed(pts)
        assertTrue("sweep must exceed pi without wrapping, was ${tracker.sweepRadians}", tracker.sweepRadians > PI.toFloat())
    }

    @Test fun nonFiniteSamplesAreIgnoredAndNeverRotate() {
        val tracker = RingGestureTracker(density)
        tracker.begin(cx, cy, 800f, 500f, 0)
        assertTrue(tracker.onMove(Float.NaN, 500f, 8) != Decision.ROTATE)
        assertTrue(tracker.onMove(800f, Float.POSITIVE_INFINITY, 16) != Decision.ROTATE)
        assertEquals(0f, tracker.sweepRadians, 0f)
    }

    @Test fun invalidDensityOrCentreMakesEverythingPan() {
        val bad = RingGestureTracker(0f)
        bad.begin(cx, cy, 800f, 500f, 0)
        assertEquals(Decision.PAN, bad.decision)
        val nan = RingGestureTracker(density)
        nan.begin(Float.NaN, cy, 800f, 500f, 0)
        assertEquals(Decision.PAN, nan.decision)
    }

    @Test fun resetAllowsAFreshGesture() {
        val (_, tracker) = feed(circle(300f, 3f))
        tracker.reset()
        assertEquals(Decision.UNDECIDED, tracker.decision)
        assertEquals(0f, tracker.sweepRadians, 0f)
        assertEquals(Decision.ROTATE, feed(circle(300f, 3f), tracker).first)
    }

    @Test fun moveBeforeBeginIsHarmlessAndUndecided() {
        val tracker = RingGestureTracker(density)
        assertEquals(Decision.UNDECIDED, tracker.onMove(10f, 10f, 0))
    }
}
