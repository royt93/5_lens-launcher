package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticIntensityTest {

    @Test
    fun `light has duration 20ms and amplitude 80`() {
        assertEquals(20L, HapticIntensity.LIGHT.duration)
        assertEquals(80, HapticIntensity.LIGHT.amplitude)
    }

    @Test
    fun `medium has duration 40ms and amplitude 128`() {
        assertEquals(40L, HapticIntensity.MEDIUM.duration)
        assertEquals(128, HapticIntensity.MEDIUM.amplitude)
    }

    @Test
    fun `strong has duration 70ms and amplitude 200`() {
        assertEquals(70L, HapticIntensity.STRONG.duration)
        assertEquals(200, HapticIntensity.STRONG.amplitude)
    }

    @Test
    fun `durations strictly increase`() {
        assertTrue(HapticIntensity.LIGHT.duration < HapticIntensity.MEDIUM.duration)
        assertTrue(HapticIntensity.MEDIUM.duration < HapticIntensity.STRONG.duration)
    }

    @Test
    fun `amplitudes strictly increase`() {
        assertTrue(HapticIntensity.LIGHT.amplitude < HapticIntensity.MEDIUM.amplitude)
        assertTrue(HapticIntensity.MEDIUM.amplitude < HapticIntensity.STRONG.amplitude)
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
