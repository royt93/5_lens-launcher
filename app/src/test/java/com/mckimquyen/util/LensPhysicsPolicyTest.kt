package com.mckimquyen.util

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FISH-004: pure-function coverage for the reduced-motion policy (mirrors
 * BaseActivityRefreshRatePolicyTest's approach for DISPLAY-001) and a regression guard that
 * every preset stays within the sliders' real, XML-declared min/max in frm_lens.xml - a preset
 * value outside those bounds would throw when FrmLens tries to set Slider.value.
 */
class LensPhysicsPolicyTest {

    @Test
    fun `no reduction when nothing is active`() {
        assertFalse(
            LensPhysicsPolicy.shouldReduceLensMotion(
                reducedMotionEnabled = false,
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_NONE
            )
        )
    }

    @Test
    fun `reduces when reduced motion alone is enabled`() {
        assertTrue(
            LensPhysicsPolicy.shouldReduceLensMotion(
                reducedMotionEnabled = true,
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_NONE
            )
        )
    }

    @Test
    fun `reduces when battery saver alone is on`() {
        assertTrue(
            LensPhysicsPolicy.shouldReduceLensMotion(
                reducedMotionEnabled = false,
                batterySaverOn = true,
                thermalStatus = PowerManager.THERMAL_STATUS_NONE
            )
        )
    }

    @Test
    fun `does not reduce below the moderate thermal threshold`() {
        assertFalse(
            LensPhysicsPolicy.shouldReduceLensMotion(
                reducedMotionEnabled = false,
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_LIGHT
            )
        )
    }

    @Test
    fun `reduces at moderate thermal status and above`() {
        assertTrue(
            LensPhysicsPolicy.shouldReduceLensMotion(
                reducedMotionEnabled = false,
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_MODERATE
            )
        )
        assertTrue(
            LensPhysicsPolicy.shouldReduceLensMotion(
                reducedMotionEnabled = false,
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_SEVERE
            )
        )
    }

    @Test
    fun `reduces when every condition is active at once`() {
        assertTrue(
            LensPhysicsPolicy.shouldReduceLensMotion(
                reducedMotionEnabled = true,
                batterySaverOn = true,
                thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL
            )
        )
    }

    @Test
    fun `STANDARD preset matches today's existing defaults exactly`() {
        assertEquals(UtilSettings.DEFAULT_DISTORTION_FACTOR, LensPhysicsPreset.STANDARD.distortionFactor)
        assertEquals(UtilSettings.DEFAULT_SCALE_FACTOR, LensPhysicsPreset.STANDARD.scaleFactor)
        assertEquals(UtilSettings.DEFAULT_ANIMATION_TIME, LensPhysicsPreset.STANDARD.animationTimeMs)
    }

    @Test
    fun `every preset stays within the sliders' real XML-declared bounds`() {
        // frm_lens.xml: sbDistortionFactor valueFrom=0.5 valueTo=5.0
        //               sbScaleFactor valueFrom=1.0 valueTo=2.0
        //               sbAnimationTime valueFrom=100 valueTo=400
        for (preset in LensPhysicsPreset.entries) {
            assertTrue(
                "${preset.name} distortionFactor out of slider bounds",
                preset.distortionFactor in 0.5f..5.0f
            )
            assertTrue(
                "${preset.name} scaleFactor out of slider bounds",
                preset.scaleFactor in 1.0f..2.0f
            )
            assertTrue(
                "${preset.name} animationTimeMs out of slider bounds",
                preset.animationTimeMs in 100L..400L
            )
        }
    }
}
