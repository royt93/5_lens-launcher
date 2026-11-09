package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LensViewHoverMagnifySourceTest {
    private val dir = File(requireNotNull(System.getProperty("user.dir")))
    private val source by lazy {
        sequenceOf(File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"), File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt"))
            .first { it.isFile }.readText()
    }

    private fun touchBranch(action: String): String {
        val start = source.indexOf("MotionEvent.$action ->")
        assertTrue("$action branch exists", start >= 0)
        val next = listOf(source.indexOf("\n            MotionEvent.ACTION_", start + 1), source.indexOf("\n            else ->", start + 1)).filter { it > start }.minOrNull()
        return source.substring(start, requireNotNull(next))
    }

    @Test fun downUpCancelAndResetClearHoverState() {
        listOf("ACTION_DOWN", "ACTION_UP", "ACTION_CANCEL").forEach { assertTrue("$it", touchBranch(it).contains("mHoverMagnifyTracker.reset()")) }
        assertTrue(source.substring(source.indexOf("private fun resetTouchState()")).take(1000).contains("resetFocusSnap()"))
        assertTrue(source.substring(source.indexOf("private fun resetFocusSnap()")).take(200).contains("mHoverMagnifyTracker.reset()"))
    }

    @Test fun realFrameGateUsesSwitchPanAndReducedMotion() {
        val start = source.indexOf("val hoverMagnifyOn = HoverMagnify.shouldApply(")
        assertTrue(start >= 0)
        val gate = source.substring(start, start + 420)
        assertTrue(gate.contains("hoverMagnifyEnabled"))
        assertTrue(gate.contains("mMoving && gestureState == LensGestureState.PANNING"))
        assertTrue(gate.contains("mReduceMotion"))
    }

    @Test fun hitTestUsesUnscaledRectAndTheCopyIsOnlyForDrawing() {
        val frame = source.substring(source.indexOf("private fun drawGrid("))
        val hit = frame.indexOf("UtilCalculator.isInsideRect(mTouchX, mTouchY, selectRect)")
        val magnify = frame.indexOf("mHoverMagnifyTracker.scaleFor(currentIndex")
        val draw = frame.indexOf("drawAppIcon(iconCanvas, drawRect")
        assertTrue(hit >= 0 && magnify > hit && draw > magnify)
        assertTrue("source creates a scratch drawing rect", frame.contains("mMagnifyRect.set(rect)"))
        assertFalse("base rect never changed by magnification", frame.contains("baseRect.set("))
    }

    @Test fun redrawRequestedDuringDwellAndRamp() {
        assertTrue(source.contains("mHoverMagnifyTracker.needsRedraw(selectIndex, frameTimeMs)"))
        assertTrue(source.contains("postInvalidateOnAnimation()"))
    }

    @Test fun settingsSampledFromTheKey() {
        assertTrue(source.contains("hoverMagnifyEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_FOCUS_MAGNIFY)"))
    }

    @Test fun frameDrawBlockCreatesNoTemporaryObjects() {
        // Only the code this story added: the per-cell scale block (the older RectF(rect) in the hit-test is not ours).
        val start = source.indexOf("var drawRect = rect")
        val end = source.indexOf("drawAppIcon(iconCanvas, drawRect", start)
        assertTrue(start >= 0 && end > start)
        val block = source.substring(start, end)
        listOf("RectF(", "FloatArray(", "Array(", "Pair(", "listOf(", "mutableListOf(").forEach {
            assertFalse("frame block contains $it", block.contains(it))
        }
    }
}
