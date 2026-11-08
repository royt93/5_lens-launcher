package com.mckimquyen.views

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Static wiring guards for FISH-042 rev2; the behaviour itself is proven by SharpClusterTest and the device tests. */
class LensViewSharpClusterSourceTest {
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
        val nextAction = source.indexOf("\n            MotionEvent.ACTION_", start + 1)
        val nextElse = source.indexOf("\n            else ->", start + 1)
        val next = listOf(nextAction, nextElse).filter { it > start }.minOrNull()
        assertTrue("$action branch has an end", next != null)
        return source.substring(start, requireNotNull(next))
    }

    private fun functionBody(signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature exists", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}' && --depth == 0) return source.substring(open, i + 1)
        }
        error("unbalanced body after $signature")
    }

    @Test fun actionUpResetsTheClusterTracker() {
        assertTrue(touchBranch("ACTION_UP").contains("mClusterTracker.reset()"))
    }

    @Test fun actionCancelResetsTheClusterTracker() {
        assertTrue(touchBranch("ACTION_CANCEL").contains("mClusterTracker.reset()"))
    }

    @Test fun theGateRequiresARealPanTheHoverSwitchAndAllowedMotion() {
        val start = source.indexOf("val clusterOn =")
        assertTrue("clusterOn exists", start >= 0)
        val gate = source.substring(start, source.indexOf("if (clusterOn) {", start))
        assertTrue("apps lens only", gate.contains("mDrawType == DrawType.APPS"))
        assertTrue("live touch", gate.contains("hasActiveTouch"))
        assertTrue("real pan", gate.contains("mMoving"))
        assertTrue("hover switch", gate.contains("UtilSettings.KEY_VIBRATE_APP_HOVER"))
        assertTrue("reduced motion", gate.contains("LensPhysicsPolicy.shouldReduceLensMotion(context)"))
    }

    @Test fun everyCellIsMarkedWithItsDistanceToTheOpticalCentre() {
        val start = source.indexOf("mClusterTracker.markCell(")
        assertTrue("markCell is called in the cell loop", start >= 0)
        val call = source.substring(start, source.indexOf("\n                }\n", start))
        assertTrue("uses the optical centre, so it agrees with DoF, vignette, peaking and fringes",
            call.contains("mOpticX, mOpticY"))
        assertTrue("never the raw touch centre", !call.contains("mTouchX") && !call.contains("mTouchY"))
    }

    @Test fun theFrameEndsWithOneDecisionThatWinsOverTheHoverPulse() {
        assertTrue("one decision per frame", source.contains("if (clusterOn) finishClusterFrame()"))
        val hover = functionBody("private fun performHoverVibration()")
        assertTrue("hover pulse is suppressed when the cluster pulse fired", hover.contains("if (!mBoundaryHapticFired) performIntensityHaptic()"))
    }

    @Test fun theClusterPulseVibratesWithTheTestedIntensityAndReportsTheSameLevel() {
        val body = functionBody("private fun performLightHaptic()")
        assertTrue("level from the pure decision", body.contains("SharpCluster.pulseIntensity("))
        assertTrue("effect uses that level", body.contains("VibrationEffect.createOneShot(level.duration, level.amplitude)"))
        assertTrue("reported amplitude is that level", body.contains("onHapticPerformed?.invoke(level.amplitude)"))
        assertTrue("no hard-coded level may bypass it", !body.contains("HapticIntensity.STRONG") && !body.contains("HapticIntensity.MEDIUM"))
    }

    @Test fun theFrameDecisionUsesTheRealClockAndNoAllocation() {
        val body = functionBody("private fun finishClusterFrame()")
        assertTrue("real time feeds the 120 ms rule", body.contains("android.os.SystemClock.uptimeMillis()"))
        listOf("Array(", "BooleanArray(", "IntArray(", "listOf(", "RectF(").forEach {
            assertTrue("finishClusterFrame must not allocate '$it'", !body.contains(it))
        }
    }

    @Test fun noRev1HoveredIconBoundaryCodeRemains() {
        assertTrue("rev1 tracker is gone", !source.contains("mBoundaryTracker") && !source.contains("FocusBoundaryHaptics"))
        assertTrue("rev1 priming seam is gone", !source.contains("primeBoundaryTrackerForTest"))
    }
}
