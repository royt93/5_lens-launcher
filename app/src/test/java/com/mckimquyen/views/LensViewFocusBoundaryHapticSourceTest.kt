package com.mckimquyen.views

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Static wiring guards; behavior itself is covered by FocusBoundaryHapticsTest and widget tests. */
class LensViewFocusBoundaryHapticSourceTest {
    private val source: String by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        sequenceOf(
            File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"),
            File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt")
        ).first { it.isFile }.readText()
    }

    private fun touchBranch(action: String): String {
        val start = source.indexOf("MotionEvent.$action ->")
        assertTrue("$action branch exists", start >= 0)
        // Stop at the next top-level branch of the same `when`; a nested mention in a comment must not end it.
        val nextAction = source.indexOf("\n            MotionEvent.ACTION_", start + 1)
        val nextElse = source.indexOf("\n            else ->", start + 1)
        val next = listOf(nextAction, nextElse).filter { it > start }.minOrNull()
        assertTrue("$action branch has an end", next != null)
        return source.substring(start, requireNotNull(next))
    }

    @Test fun actionUpResetsBoundaryTracker() {
        assertTrue("ACTION_UP must reset the gesture-scoped boundary tracker",
            touchBranch("ACTION_UP").contains("mBoundaryTracker.reset()"))
    }

    @Test fun actionCancelResetsBoundaryTracker() {
        assertTrue("ACTION_CANCEL must reset the gesture-scoped boundary tracker",
            touchBranch("ACTION_CANCEL").contains("mBoundaryTracker.reset()"))
    }

    @Test fun boundaryDistanceUsesTheSameOpticalCentreAsTheRenderedEffects() {
        val anchor = "// FISH-042: focus-boundary haptic (before hover haptic so it can suppress it)."
        val start = source.indexOf(anchor)
        assertTrue("boundary haptic block exists", start >= 0)
        val end = source.indexOf("        mMustVibrate = if (selectIndex >= 0)", start)
        assertTrue("boundary haptic block has a clear end", end > start)
        val block = source.substring(start, end)
        assertTrue("boundary distance must use the visual optical centre directly without allocating a Pair",
            block.contains("DepthOfField.focusDistance(\n                mOpticX, mOpticY,"))
        assertTrue("boundary distance must not use raw touch coordinates",
            !block.contains("DepthOfField.focusDistance(\n                mTouchX, mTouchY,"))
    }
}
