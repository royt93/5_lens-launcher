package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Static wiring guards for FISH-044; behaviour is proven by FocusSnapTest and the device tests. */
class LensViewFocusSnapSourceTest {
    private val dir = File(requireNotNull(System.getProperty("user.dir")))
    private fun read(rel: String) = sequenceOf(File(dir, "src/main/java/$rel"), File(dir, "app/src/main/java/$rel"))
        .first { it.isFile }.readText()

    private val source by lazy { read("com/mckimquyen/views/LensView.kt") }
    private val snapSource by lazy { read("com/mckimquyen/views/FocusSnap.kt") }

    private fun touchBranch(action: String): String {
        val start = source.indexOf("MotionEvent.$action ->")
        assertTrue("$action branch exists", start >= 0)
        val next = listOf(source.indexOf("\n            MotionEvent.ACTION_", start + 1), source.indexOf("\n            else ->", start + 1))
            .filter { it > start }.minOrNull()
        return source.substring(start, requireNotNull(next))
    }

    @Test fun downUpAndCancelResetTheSpeedTracker() {
        listOf("ACTION_DOWN", "ACTION_UP", "ACTION_CANCEL").forEach {
            assertTrue("$it resets", touchBranch(it).contains("mSnapTracker.reset()"))
        }
        assertTrue(source.substring(source.indexOf("private fun resetTouchState()")).take(900).contains("resetFocusSnap()"))
    }

    @Test fun speedComesFromTheRawFingerBeforeTheOverscrollMapping() {
        val move = touchBranch("ACTION_MOVE")
        val feed = move.indexOf("mSnapTracker.onMove(event.x, event.y, event.eventTime")
        assertTrue("raw event feeds the tracker", feed >= 0)
        assertTrue("before overscroll mapping", feed < move.indexOf("LensOverscrollPhysics.calculateEffectiveCoordinate"))
    }

    @Test fun selectionAndTheTouchCentreNeverReadTheSnapOffset() {
        assertTrue(source.contains("UtilCalculator.isInsideRect(mTouchX, mTouchY, selectRect)"))
        val frame = source.substring(source.indexOf("private fun drawGrid("))
        assertFalse("mTouchX is never assigned from snap", Regex("mTouch[XY]\\s*=[^=\\n]*snap", RegexOption.IGNORE_CASE).containsMatchIn(frame))
        assertTrue("select rect is built from the finger centre", frame.contains("lensProfile, lensCenterX, lensCenterY"))
    }

    @Test fun snapIsAppliedBeforeInertiaSoInertiaFollowsTheSnappedTarget() {
        val frame = source.substring(source.indexOf("private fun drawGrid("))
        assertTrue(frame.indexOf("FocusSnap.pull(") in 0 until frame.indexOf("updateLensInertia(inertiaOn, targetCenterX, targetCenterY)"))
    }

    @Test fun theGateUsesTheSettingRealPanAndReducedMotion() {
        val frame = source.substring(source.indexOf("val snapOn = FocusSnap.shouldApply("))
        val gate = frame.take(420)
        assertTrue(gate.contains("focusSnapEnabled"))
        assertTrue(gate.contains("mMoving && gestureState == LensGestureState.PANNING"))
        assertTrue(gate.contains("mReduceMotion"))
    }

    @Test fun theSettingIsSampledWhereTheOtherLensEffectsAre() {
        assertTrue(source.contains("focusSnapEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_FOCUS_SNAP)"))
    }

    @Test fun theFrameStepAndTheMathAllocateNothing() {
        val banned = listOf("RectF(", "Paint(", "Array(", "FloatArray(", "IntArray(", "listOf(", "mutableListOf(", "Pair(", "String.format")
        val math = snapSource.substringBefore("class Tracker")
        banned.forEach { assertFalse("FocusSnap math contains $it", math.replace("Array<RectF>", "").contains(it)) }
        val frame = source.substring(source.indexOf("val snapOn = FocusSnap.shouldApply("), source.indexOf("updateLensInertia(inertiaOn, targetCenterX"))
        banned.forEach { assertFalse("snap frame block contains $it", frame.contains(it)) }
    }
}
