package com.mckimquyen.util

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HapticIntensityTest {

    @Test
    fun `light maps to clock tick`() {
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, HapticIntensity.LIGHT.feedbackConstant)
    }

    @Test
    fun `medium maps to virtual key which is the pre-existing behavior`() {
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, HapticIntensity.MEDIUM.feedbackConstant)
    }

    /**
     * Measured on TECNO BG6 (Android 13) via `dumpsys vibrator_manager`: CONFIRM and VIRTUAL_KEY both
     * played CLICK, so "Strong" felt identical to "Medium". LONG_PRESS played HEAVY_CLICK there.
     */
    @Test
    fun `strong maps to long press`() {
        assertEquals(HapticFeedbackConstants.LONG_PRESS, HapticIntensity.STRONG.feedbackConstant)
    }

    @Test
    fun `the three levels produce three distinct constants`() {
        val constants = HapticIntensity.entries.map { it.feedbackConstant }.toSet()
        assertEquals(HapticIntensity.entries.size, constants.size)
    }

    @Test
    fun `no level uses confirm which needs api 30 and played the same effect as medium on device`() {
        for (level in HapticIntensity.entries) {
            assertNotEquals(HapticFeedbackConstants.CONFIRM, level.feedbackConstant)
        }
    }

    @Test
    fun `default is medium`() {
        assertEquals(HapticIntensity.MEDIUM, HapticIntensity.DEFAULT)
    }

    @Test
    fun `from decodes every valid ordinal`() {
        for (level in HapticIntensity.entries) {
            assertEquals(level, HapticIntensity.from(level.ordinal))
        }
    }

    @Test
    fun `from falls back to default for negative and out of range ordinals`() {
        assertEquals(HapticIntensity.DEFAULT, HapticIntensity.from(-1))
        assertEquals(HapticIntensity.DEFAULT, HapticIntensity.from(HapticIntensity.entries.size))
        assertEquals(HapticIntensity.DEFAULT, HapticIntensity.from(Int.MAX_VALUE))
        assertEquals(HapticIntensity.DEFAULT, HapticIntensity.from(Int.MIN_VALUE))
    }

}
