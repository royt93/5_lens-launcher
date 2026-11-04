package com.mckimquyen.util

import android.view.Surface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TiltParallaxTest {
    private val area = 1000f
    private val maxPx = TiltParallax.MAX_OFFSET_FRACTION * area
    private val identity = floatArrayOf(0f, 0f, 0f, 1f)

    /** Quaternion xoay quanh trục X (nghiêng dọc) hoặc Y (nghiêng ngang) của hệ máy. */
    private fun aroundX(rad: Float) = floatArrayOf(sin(rad / 2), 0f, 0f, cos(rad / 2))
    private fun aroundY(rad: Float) = floatArrayOf(0f, sin(rad / 2), 0f, cos(rad / 2))

    private fun TiltParallax.Filter.feed(q: FloatArray, times: Int, rotation: Int = Surface.ROTATION_0) =
        repeat(times) { update(q[0], q[1], q[2], q[3], rotation, area) }

    @Test fun identityQuaternionGivesZeroOffset() {
        val f = TiltParallax.Filter()
        f.feed(identity, 50)
        assertEquals(0f, f.offsetX, 0.001f)
        assertEquals(0f, f.offsetY, 0.001f)
    }

    @Test fun firstSampleBecomesTheReferencePose() {
        val f = TiltParallax.Filter()
        f.feed(aroundY(0.3f), 100)
        assertEquals("holding the first pose stays centered", 0f, f.offsetX, 0.5f)
        assertEquals(0f, f.offsetY, 0.5f)
    }

    @Test fun tiltBeyondMaxAngleClampsAtMaxOffset() {
        val f = TiltParallax.Filter()
        f.feed(identity, 1)
        f.feed(aroundY(TiltParallax.MAX_ANGLE_RADIANS * 3f), 300)
        assertEquals(maxPx, abs(f.offsetX), 0.5f)
        assertEquals(0f, f.offsetY, 0.5f)
    }

    @Test fun oppositeTiltMovesTheOffsetTheOtherWay() {
        val left = TiltParallax.Filter().also { it.feed(identity, 1); it.feed(aroundY(0.2f), 300) }
        val right = TiltParallax.Filter().also { it.feed(identity, 1); it.feed(aroundY(-0.2f), 300) }
        assertTrue(left.offsetX * right.offsetX < 0f)
        assertEquals(abs(left.offsetX), abs(right.offsetX), 0.5f)
    }

    @Test fun tiltingTheDeviceToTheRightMovesTheCenterRightAndUpMovesItUp() {
        // Quy ước ghi ở TiltParallax: nghiêng sang phải => offsetX > 0; nghiêng đầu máy lên => offsetY > 0.
        // Quaternion quanh +Y một góc dương là nghiêng sang phải trong hệ máy; quanh +X dương là đầu máy lên.
        val right = TiltParallax.Filter().also { it.feed(identity, 1); it.feed(aroundY(0.25f), 300) }
        val up = TiltParallax.Filter().also { it.feed(identity, 1); it.feed(aroundX(0.25f), 300) }
        assertTrue("right tilt moves the center right: ${right.offsetX}", right.offsetX > 1f)
        assertTrue("up tilt moves the center up: ${up.offsetY}", up.offsetY > 1f)
    }

    @Test fun theFirstStepTowardsANewPoseIsOnlyAFractionOfTheTarget() {
        val f = TiltParallax.Filter()
        f.feed(identity, 1)
        val q = aroundY(TiltParallax.MAX_ANGLE_RADIANS * 3f)
        f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area)
        assertEquals("one sample moves by the smoothing factor of the full target",
            maxPx * TiltParallax.SMOOTHING_FACTOR, abs(f.offsetX), 0.5f)
        assertTrue("it has not jumped to the target", abs(f.offsetX) < maxPx * 0.5f)
    }

    @Test fun tinyShakeInsideTheDeadZoneDoesNotMove() {
        val f = TiltParallax.Filter()
        f.feed(identity, 1)
        f.feed(aroundY(TiltParallax.DEAD_ZONE_RADIANS * 0.5f), 300)
        assertEquals(0f, f.offsetX, 0.001f)
    }

    @Test fun smoothingApproachesTheTargetWithoutOvershoot() {
        val f = TiltParallax.Filter()
        f.feed(identity, 1)
        val q = aroundY(0.2f)
        var previous = 0f
        repeat(100) {
            f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area)
            assertTrue("never decreases while approaching", abs(f.offsetX) >= previous - 0.0001f)
            previous = abs(f.offsetX)
        }
        val target = ((0.2f - TiltParallax.DEAD_ZONE_RADIANS).coerceAtLeast(0f) / TiltParallax.MAX_ANGLE_RADIANS)
            .coerceAtMost(1f) * maxPx
        assertTrue("never passes the target", previous <= target + 0.5f)
        assertEquals(target, previous, target * 0.05f)
    }

    @Test fun nonFiniteInputKeepsStateAndNeverProducesNaN() {
        val f = TiltParallax.Filter()
        f.feed(identity, 1)
        f.feed(aroundY(0.2f), 40)
        val x = f.offsetX
        val y = f.offsetY
        assertFalse(f.update(Float.NaN, 0f, 0f, 1f, Surface.ROTATION_0, area))
        assertFalse(f.update(0f, Float.POSITIVE_INFINITY, 0f, 1f, Surface.ROTATION_0, area))
        assertFalse(f.update(0f, 0f, 0f, 1f, Surface.ROTATION_0, 0f))
        assertFalse(f.update(0f, 0f, 0f, 1f, Surface.ROTATION_0, -5f))
        assertEquals(x, f.offsetX, 0f)
        assertEquals(y, f.offsetY, 0f)
        assertFalse(f.offsetX.isNaN() || f.offsetY.isNaN())
    }

    @Test fun screenRotationSwapsTheDominantAxis() {
        val upright = TiltParallax.Filter().also { it.feed(identity, 1); it.feed(aroundY(0.25f), 300) }
        val sideways = TiltParallax.Filter().also {
            it.feed(identity, 1, Surface.ROTATION_90)
            it.feed(aroundY(0.25f), 300, Surface.ROTATION_90)
        }
        assertTrue("upright tilts horizontally", abs(upright.offsetX) > abs(upright.offsetY) * 5f)
        assertTrue("rotated 90 tilts vertically", abs(sideways.offsetY) > abs(sideways.offsetX) * 5f)
    }

    @Test fun verticalTiltMovesOnlyTheVerticalOffset() {
        val f = TiltParallax.Filter().also { it.feed(identity, 1); it.feed(aroundX(0.25f), 300) }
        assertTrue(abs(f.offsetY) > abs(f.offsetX) * 5f)
    }

    @Test fun resetReturnsToCenterAndTakesANewReference() {
        val f = TiltParallax.Filter()
        f.feed(identity, 1)
        f.feed(aroundY(0.3f), 100)
        assertTrue(abs(f.offsetX) > 1f)
        f.reset()
        assertEquals(0f, f.offsetX, 0f)
        assertEquals(0f, f.offsetY, 0f)
        f.feed(aroundY(0.3f), 100)
        assertEquals("the pose after reset is the new reference", 0f, f.offsetX, 0.5f)
    }

    @Test fun updateReportsAChangeOnlyBeyondTheThreshold() {
        val f = TiltParallax.Filter()
        assertFalse("first sample only sets the reference", f.update(0f, 0f, 0f, 1f, Surface.ROTATION_0, area))
        val q = aroundY(0.2f)
        assertTrue(f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area))
        repeat(500) { f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area) }
        assertFalse("settled offset no longer reports changes", f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area))
    }
}
