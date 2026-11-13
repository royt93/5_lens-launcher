package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LensViewRecentDotsSourceTest {
    private val dir = File(requireNotNull(System.getProperty("user.dir")))
    private val source by lazy {
        sequenceOf(File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"), File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt"))
            .first { it.isFile }.readText()
    }
    /** Only drawGrid itself: the helpers that follow it in the file (refreshRecentDotCells...) are not the draw path. */
    private val frame by lazy {
        val start = source.indexOf("private fun drawGrid(")
        assertTrue(start >= 0)
        source.substring(start, source.indexOf("\n    private fun ", start + 10))
    }

    private fun touchBranch(action: String): String {
        val start = source.indexOf("MotionEvent.$action ->")
        assertTrue("$action branch exists", start >= 0)
        val next = listOf(source.indexOf("\n            MotionEvent.ACTION_", start + 1), source.indexOf("\n            else ->", start + 1)).filter { it > start }.minOrNull()
        return source.substring(start, requireNotNull(next))
    }

    @Test fun settingSampledFromTheKey() {
        assertTrue(source.contains("recentDotsEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_RECENT_DOTS)"))
    }

    @Test fun theFrameGateUsesTheSwitchCleanModeAndARealDrag() {
        val start = frame.indexOf("val recentDotsOn = RecentDots.shouldApply(")
        assertTrue(start >= 0)
        val gate = frame.substring(start, start + 420)
        assertTrue(gate.contains("recentDotsEnabled"))
        assertTrue(gate.contains("KEY_CLEAN_LENS_MODE"))
        assertTrue(gate.contains("mMoving"))
        assertTrue(gate.contains("DrawType.APPS"))
    }

    @Test fun theCellsAreResolvedOnceOnTouchDownNotInTheDrawPath() {
        assertTrue(touchBranch("ACTION_DOWN").contains("refreshRecentDotCells()"))
        val refresh = source.substring(source.indexOf("private fun refreshRecentDotCells()"))
        assertTrue(refresh.take(1200).contains("recentKeysSince(RecentDots.WINDOW_MS, RecentDots.MAX_DOTS)"))
        assertFalse("the draw path never reads the history store", frame.contains("SearchHistoryStore"))
        assertFalse("the draw path never reads the history store", frame.contains("recentKeysSince"))
    }

    @Test fun theCellsReadTheSwitchFromSettingsNotFromTheFlagTheLensAnimationSamplesLater() {
        // ACTION_DOWN runs before a pan is confirmed, and recentDotsEnabled is only sampled in LensAnimation's init, so at
        // touch down it still holds the previous value (false on the first gesture): the cells would always stay empty.
        val refresh = source.substring(source.indexOf("private fun refreshRecentDotCells()"))
        val body = refresh.substring(0, refresh.indexOf("\n    }\n"))
        assertTrue(body.contains("KEY_RECENT_DOTS"))
        assertFalse("must not depend on the late-sampled flag", body.contains("recentDotsEnabled"))
    }

    @Test fun theDotIsDrawnAfterTheIconFromTheWarpedRectWithACachedPaint() {
        val icon = frame.indexOf("drawAppIcon(iconCanvas, drawRect, currentIndex)")
        val dot = frame.indexOf("drawRecentDot(")
        assertTrue(icon >= 0 && dot > icon)
        val fn = source.substring(source.indexOf("private fun drawRecentDot("))
        assertTrue(fn.take(600).contains("RecentDots.radiusPx("))
        assertTrue(fn.take(600).contains("mPaintRecentDot"))
    }

    @Test fun hitTestAndSelectionNeverReadTheDotState() {
        val hit = frame.indexOf("UtilCalculator.isInsideRect(mTouchX, mTouchY, selectRect)")
        assertTrue(hit >= 0)
        // The gate only turns the drag state into a boolean; the cells and the drawing helper come after the hit-test.
        assertFalse(frame.substring(0, hit).contains("mRecentDotCells"))
        assertFalse(frame.substring(0, hit).contains("drawRecentDot("))
    }

    @Test fun upCancelAndResetClearTheCells() {
        listOf("ACTION_UP", "ACTION_CANCEL").forEach { assertTrue("$it", touchBranch(it).contains("clearRecentDotCells()")) }
        assertTrue(source.substring(source.indexOf("private fun resetTouchState()")).take(900).contains("clearRecentDotCells()"))
    }

    @Test fun theDotDrawPathCreatesNoTemporaryObjects() {
        val fn = source.substring(source.indexOf("private fun drawRecentDot("))
        val block = fn.substring(0, fn.indexOf("\n    }\n"))
        listOf("RectF(", "FloatArray(", "BooleanArray(", "Array(", "Pair(", "listOf(", "mutableListOf(", "String.format", "Paint(").forEach {
            assertFalse("drawRecentDot contains $it", block.contains(it))
        }
    }
}
