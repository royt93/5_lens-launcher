package com.mckimquyen.util

import android.os.Build
import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HapticIntensityTest {

    @Test
    fun `light maps to clock tick on every api level`() {
        for (sdk in listOf(Build.VERSION_CODES.N_MR1, Build.VERSION_CODES.R, 37)) {
            assertEquals(HapticFeedbackConstants.CLOCK_TICK, HapticIntensity.LIGHT.feedbackConstant(sdk))
        }
    }

    @Test
    fun `medium maps to virtual key which is the pre-existing behavior`() {
        for (sdk in listOf(Build.VERSION_CODES.N_MR1, Build.VERSION_CODES.R, 37)) {
            assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, HapticIntensity.MEDIUM.feedbackConstant(sdk))
        }
    }

    @Test
    fun `strong uses long press below api 30`() {
        assertEquals(
            HapticFeedbackConstants.LONG_PRESS,
            HapticIntensity.STRONG.feedbackConstant(Build.VERSION_CODES.Q)
        )
        assertEquals(
            HapticFeedbackConstants.LONG_PRESS,
            HapticIntensity.STRONG.feedbackConstant(HapticIntensity.CONFIRM_MIN_SDK - 1)
        )
    }

    @Test
    fun `strong uses confirm from api 30`() {
        assertEquals(
            HapticFeedbackConstants.CONFIRM,
            HapticIntensity.STRONG.feedbackConstant(HapticIntensity.CONFIRM_MIN_SDK)
        )
        assertEquals(HapticFeedbackConstants.CONFIRM, HapticIntensity.STRONG.feedbackConstant(37))
    }

    @Test
    fun `the three levels produce three distinct constants on api 30 plus`() {
        val constants = HapticIntensity.entries.map { it.feedbackConstant(37) }.toSet()
        assertEquals(HapticIntensity.entries.size, constants.size)
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

    @Test
    fun `confirm threshold is api 30`() {
        assertEquals(Build.VERSION_CODES.R, HapticIntensity.CONFIRM_MIN_SDK)
        assertNotEquals(0, HapticIntensity.CONFIRM_MIN_SDK)
    }
}
