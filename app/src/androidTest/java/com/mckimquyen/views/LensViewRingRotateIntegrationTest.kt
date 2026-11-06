package com.mckimquyen.views

import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.util.RingRotation
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class LensViewRingRotateIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Already in the order UtilAppSorter would produce: F pinned first, D/E favorites, then A/B/C. */
    private fun fixture() = listOf(
        app("F", PinnedZone.START, false), app("D", PinnedZone.NONE, true), app("E", PinnedZone.NONE, true),
        app("A", PinnedZone.NONE, false), app("B", PinnedZone.NONE, false), app("C", PinnedZone.NONE, false)
    )

    private fun app(name: String, zone: PinnedZone, favorite: Boolean) =
        App(label = name, packageName = "pkg.ring.${name.lowercase()}", name = "Main", pinnedZone = zone, isFavorite = favorite)

    private fun expected(order: List<App>, steps: Int) =
        RingRotation.rotateByGroup(order, steps) { RingRotation.groupKeyOf(it.pinnedZone.rank, it.isFavorite) }

    private fun labels(apps: List<App>) = apps.map { it.label.toString() }

    private fun withView(apps: List<App> = fixture(), block: (LensView) -> Unit) {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(ArrayList(apps))
                    layoutParams = FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            block(requireNotNull(view) { "view is assigned inside onActivity above" })
        }
    }

    private fun displayed(view: LensView) = labels(view.displayedApps)

    private fun pinAt500(view: LensView) = instrumentation.runOnMainSync { assertTrue(view.toggleFocusLockAtForTest(CX, CY)) }

    private fun listenerInto(view: LensView, sink: MutableList<RingRotationRequest>) =
        instrumentation.runOnMainSync { view.onRingRotatedListener = OnRingRotatedListener { sink += it } }

    private fun sendAt(view: LensView, downTime: Long, t: Long, action: Int, x: Float, y: Float) =
        instrumentation.runOnMainSync {
            val e = MotionEvent.obtain(downTime, downTime + t, action, x, y, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }

    private data class Pt(val x: Float, val y: Float)

    /** Points on a circle: [angles] are absolute radians, the first one is where the finger lands. */
    private fun onCircle(radius: Float, angles: List<Float>, centerX: Float = CX, centerY: Float = CY) =
        angles.map { Pt(centerX + radius * cos(it), centerY + radius * sin(it)) }

    /** Angles from 0 to [sweepRad] at [radPerSec], one per frame (the last one lands exactly on the sweep). */
    private fun ramp(sweepRad: Float, radPerSec: Float = ARC_SPEED_RAD_PER_SEC, from: Float = 0f): List<Float> {
        val perFrame = radPerSec * FRAME_MS / 1000f
        val frames = (abs(sweepRad) / perFrame).toInt().coerceAtLeast(1)
        val dir = if (sweepRad < 0f) -1f else 1f
        return List(frames + 1) { i -> if (i == frames) from + sweepRad else from + dir * perFrame * i }
    }

    /** One continuous finger: DOWN on the first point, a MOVE every frame, then UP (unless [release] is false). */
    private fun drag(view: LensView, points: List<Pt>, release: Boolean = true): Long {
        val downTime = SystemClock.uptimeMillis()
        sendAt(view, downTime, 0, MotionEvent.ACTION_DOWN, points[0].x, points[0].y)
        for (i in 1 until points.size) sendAt(view, downTime, i * FRAME_MS, MotionEvent.ACTION_MOVE, points[i].x, points[i].y)
        if (release) sendAt(view, downTime, points.size * FRAME_MS, MotionEvent.ACTION_UP, points.last().x, points.last().y)
        return downTime
    }

    private fun arc(view: LensView, sweepSteps: Float, release: Boolean = true, radius: Float = ARC_RADIUS_PX) =
        drag(view, onCircle(radius, ramp(sweepSteps * RingRotation.STEP_RADIANS)), release)

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, frames: Int) =
        List(frames + 1) { i -> Pt(x0 + (x1 - x0) * i / frames, y0 + (y1 - y0) * i / frames) }

    private fun send(view: LensView, downTime: Long, action: Int, x: Float, y: Float) =
        sendAt(view, downTime, SystemClock.uptimeMillis() - downTime, action, x, y)

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)


    @Test fun aClockwiseArcPreviewsTheGroupRotationAndReportsOneRequestOnRelease() = withView { view ->
        val base = view.displayedApps.toList()
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        assertEquals("exactly one commit request per gesture", 1, requests.size)
        // F is pinned, D/E favorites, A/B/C plain: each group rotates by one slot, never across a boundary
        assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(requests[0].apps))
        assertEquals(labels(expected(base, 1)), labels(requests[0].apps))
        assertEquals(labels(requests[0].apps), displayed(view))
        assertEquals(view.lensId, requests[0].lensId)
        assertEquals(requests[0].token, view.ringPendingTokenForTest())
    }

    @Test fun twoWholeStepsAreAppliedAsOneRequest() = withView { view ->
        val base = view.displayedApps.toList()
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 2.2f)
        assertEquals(1, requests.size)
        assertEquals(listOf("F", "D", "E", "C", "A", "B"), labels(requests[0].apps))   // 2 steps: D/E back to start
        assertEquals(labels(expected(base, 2)), labels(requests[0].apps))
    }

    @Test fun aCounterClockwiseArcRotatesBack() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = -1.2f)
        assertEquals(1, requests.size)
        assertEquals(listOf("F", "E", "D", "C", "A", "B"), labels(requests[0].apps))
    }

    @Test fun thePreviewFollowsTheFingerAndTheOrderIsStableWhileTheGestureIsStillDown() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val down = arc(view, sweepSteps = 1.2f, release = false)
        assertTrue(view.isRingRotatingForTest())
        assertEquals(listOf("F", "E", "D", "B", "C", "A"), displayed(view))
        assertTrue("nothing is reported before the finger lifts", requests.isEmpty())
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    @Test fun reversingTheArcBelowOneStepRestoresTheOrderAndReportsNothing() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val out = ramp(RingRotation.STEP_RADIANS * 2.2f)
        val back = ramp(-RingRotation.STEP_RADIANS * 1.9f, from = out.last())   // ends at +0.3 steps -> net 0
        drag(view, onCircle(ARC_RADIUS_PX, out + back.drop(1)))
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
        assertNull(view.ringPendingTokenForTest())
    }

    @Test fun aFullTurnIsANetNoOpAndWritesNothing() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 12.1f)           // 12 whole steps: every group returns to its start
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun whenEveryAppIsInItsOwnGroupARotationChangesNothingAndReportsNothing() = withView(
        apps = listOf(app("S", PinnedZone.START, false), app("N", PinnedZone.NONE, false), app("E", PinnedZone.END, false))
    ) { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        assertTrue(requests.isEmpty())
        assertEquals(listOf("S", "N", "E"), displayed(view))
    }

    @Test fun theSameArcWithoutAPinIsAnOrdinaryPanAndNeverRotates() = withView { view ->
        val base = labels(view.displayedApps)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val down = arc(view, sweepSteps = 3f, release = false)
        assertEquals(LensGestureState.PANNING, view.gestureState)
        assertFalse(view.isRingRotatingForTest())
        send(view, down, MotionEvent.ACTION_UP, 800f, 500f)
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun aPinnedStraightDragIsAPanThatStartsOnlyAfterTheClassificationWindow() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        // 600 px/s straight down the tangent: 4.8 px per 8 ms frame, 50 frames = 240 px
        val pts = line(800f, 400f, 800f, 640f, frames = 50)
        val down = SystemClock.uptimeMillis()
        sendAt(view, down, 0, MotionEvent.ACTION_DOWN, pts[0].x, pts[0].y)
        for (i in 1..10) sendAt(view, down, i * FRAME_MS, MotionEvent.ACTION_MOVE, pts[i].x, pts[i].y)
        assertEquals("pan is withheld while the tracker is still classifying", LensGestureState.IDLE, view.gestureState)
        for (i in 11..50) sendAt(view, down, i * FRAME_MS, MotionEvent.ACTION_MOVE, pts[i].x, pts[i].y)
        assertEquals("after the window the pan runs as before", LensGestureState.PANNING, view.gestureState)
        assertEquals("the pan follows the finger's absolute position, nothing was lost", 640f, view.touchCoordinatesForTest().second, 0.5f)
        sendAt(view, down, 51 * FRAME_MS, MotionEvent.ACTION_UP, pts[50].x, pts[50].y)
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun aPinnedDragStartingInsideTheMinimumRadiusPansImmediately() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        // a 20 px circle swept through 180 degrees puts the finger 40 px from where it landed, beyond any touch slop
        val down = drag(view, onCircle(20f, ramp(RingRotation.STEP_RADIANS * 6f)), release = false)
        assertEquals(LensGestureState.PANNING, view.gestureState)
        assertFalse(view.isRingRotatingForTest())
        send(view, down, MotionEvent.ACTION_UP, 480f, 500f)
        assertTrue(requests.isEmpty())
    }

    @Test fun aSecondFingerDuringTheWaitCancelsTheRingAndBecomesAPinch() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val down = SystemClock.uptimeMillis()
        sendAt(view, down, 0, MotionEvent.ACTION_DOWN, 800f, 500f)
        instrumentation.runOnMainSync {
            val e = MotionEvent.obtain(
                down, down + 16, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
                    MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { x = 800f; y = 500f },
                    MotionEvent.PointerCoords().apply { x = 200f; y = 500f }),
                0, 0, 1f, 1f, 0, 0, 0, 0
            )
            view.dispatchTouchEvent(e)
            e.recycle()
        }
        assertFalse(view.isRingRotatingForTest())
        assertTrue(requests.isEmpty())
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
        assertEquals(base, displayed(view))
    }

    @Test fun aSecondFingerDuringRotationRestoresTheOrderAndReportsNothing() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val down = arc(view, sweepSteps = 1.2f, release = false)
        assertTrue(view.isRingRotatingForTest())
        instrumentation.runOnMainSync {
            val e = MotionEvent.obtain(
                down, down + 400, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
                    MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { x = 700f; y = 600f },
                    MotionEvent.PointerCoords().apply { x = 200f; y = 200f }),
                0, 0, 1f, 1f, 0, 0, 0, 0
            )
            view.dispatchTouchEvent(e)
            e.recycle()
        }
        assertFalse(view.isRingRotatingForTest())
        assertEquals(base, displayed(view))
        send(view, down, MotionEvent.ACTION_UP, 700f, 600f)
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun cancelAndPauseAndDetachEachRestoreAnUncommittedPreview() {
        for (how in listOf("cancel", "pause", "detach")) withView { view ->
            val base = labels(view.displayedApps)
            pinAt500(view)
            val down = arc(view, sweepSteps = 1.2f, release = false)
            assertTrue("$how: rotating", view.isRingRotatingForTest())
            when (how) {
                "cancel" -> send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
                "pause" -> onMain { view.cancelRingRotationForPause() }
                else -> onMain { (view.parent as ViewGroup).removeView(view) }
            }
            assertFalse("$how: state cleared", view.isRingRotatingForTest())
            if (how != "detach") assertEquals("$how: order restored", base, displayed(view))
        }
    }

    @Test fun releaseAfterARotationLeavesNoSelectionSoNoAppCanLaunch() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        // launchApp() reads mSelectIndex; -1 means it has nothing to launch (and the UP handler returns before it anyway)
        assertEquals(-1, view.selectedIndexForTest)
        assertEquals(-Float.MAX_VALUE, view.touchCoordinatesForTest().first, 0f)
        assertEquals("the pin is untouched", CX to CY, view.pinnedFocusForTest())
    }

    @Test fun aLongPressNeverFiresWhileRotating() = withView { view ->
        pinAt500(view)
        val down = arc(view, sweepSteps = 1.2f, release = false)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L)
        instrumentation.waitForIdleSync()
        assertTrue("still rotating, no menu or ring replaced the gesture", view.isRingRotatingForTest())
        assertFalse(view.isFocusRingActiveForTest())
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    @Test fun anEdgeSwipeWinsOverTheRingAndNeverRotates() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        var edgeSwipes = 0
        onMain { view.onEdgeSwipeListener = OnEdgeSwipeListener { edgeSwipes++ } }
        // lands on the right edge strip (x = 990) and drags horizontally left, far beyond the edge threshold
        drag(view, line(990f, 500f, 300f, 500f, frames = 40))
        assertEquals("the existing FISH-025 edge swipe still fires exactly once", 1, edgeSwipes)
        assertFalse(view.isRingRotatingForTest())
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun theHostConfirmsAndTheViewAdoptsTheNewOrderAsItsSource() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        val committed = labels(requests.single().apps)
        onMain { view.resolveRingCommit(requests.single().token, committed = true) }
        assertNull(view.ringPendingTokenForTest())
        assertEquals(committed, displayed(view))
        onMain { view.refreshSmartFocus() }
        assertEquals("a refresh must keep the adopted order, not rebuild the old one", committed, displayed(view))
    }

    @Test fun aFailedCommitRestoresTheOrderTheViewHadBeforeTheGesture() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        onMain { view.resolveRingCommit(requests.single().token, committed = false) }
        assertNull(view.ringPendingTokenForTest())
        assertEquals(base, displayed(view))
        onMain { view.refreshSmartFocus() }
        assertEquals("the source order was never changed", base, displayed(view))
    }

    @Test fun aStaleOrUnknownTokenIsIgnored() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        val preview = labels(requests.single().apps)
        onMain { view.resolveRingCommit(requests.single().token + 1, committed = false) }
        assertEquals("a wrong token must not touch the preview", preview, displayed(view))
        assertEquals(requests.single().token, view.ringPendingTokenForTest())
    }

    @Test fun newDataWhileACommitIsPendingDropsThePendingPreview() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        val fresh = ArrayList(fixture().reversed())
        onMain { view.setApps(fresh) }
        assertNull(view.ringPendingTokenForTest())
        assertEquals(labels(fresh), displayed(view))
        onMain { view.resolveRingCommit(requests.single().token, committed = true) }   // late callback
        assertEquals("a late confirmation must not overwrite newer data", labels(fresh), displayed(view))
    }

    @Test fun aNewGestureIsRefusedWhileACommitIsStillPending() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        val afterFirst = labels(requests.single().apps)
        arc(view, sweepSteps = 1.2f)
        assertEquals(1, requests.size)
        assertEquals(afterFirst, displayed(view))
    }

    @Test fun smartFocusIsNotAppliedOverAPreviewWhenTheGridSizeChanges() = withView { view ->
        val settings = UtilSettings(instrumentation.targetContext)
        val before = settings.isSmartFocusBias(view.lensId)
        try {
            settings.saveSmartFocusBias(view.lensId, true)
            pinAt500(view)
            val down = arc(view, sweepSteps = 1.2f, release = false)
            val preview = displayed(view)
            onMain { view.layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX - 100); view.draw(Canvas()) }
            assertEquals("a resize mid-gesture must not rebuild the list from the old source", preview, displayed(view))
            send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
        } finally {
            settings.saveSmartFocusBias(view.lensId, before)
        }
    }

    @Test fun rotateAndCommitForTheHostActionsNeedsNoPinAndIsRefusedBelowTwoAppsOrForANetNoOp() {
        withView { view ->
            val requests = mutableListOf<RingRotationRequest>()
            listenerInto(view, requests)
            var ok = false
            onMain { ok = view.rotateAndCommit(1) }
            assertTrue(ok)
            assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(requests.single().apps))
        }
        withView(apps = listOf(app("only", PinnedZone.NONE, false))) { view ->
            var ok = true
            onMain { ok = view.rotateAndCommit(1) }
            assertFalse(ok)
        }
        withView { view ->
            var ok = true
            onMain { ok = view.rotateAndCommit(12) }
            assertFalse("a full turn changes nothing", ok)
        }
    }

    @Test fun detachClearsTheListenerAndAnyPendingCommit() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        onMain { (view.parent as ViewGroup).removeView(view) }
        assertNull(view.onRingRotatedListener)
        assertNull(view.ringPendingTokenForTest())
    }

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val CX = 500f
        const val CY = 500f
        const val FRAME_MS = 8L
        const val ARC_RADIUS_PX = 300f
        const val ARC_SPEED_RAD_PER_SEC = 3f
    }
}
