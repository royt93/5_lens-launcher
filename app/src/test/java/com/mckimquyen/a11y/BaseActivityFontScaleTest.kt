package com.mckimquyen.a11y

import com.mckimquyen.ui.BaseActivity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A11Y-001: Unit tests for system font scale policy in [BaseActivity].
 *
 * Verifies that incoming system font scales are respected up through 200% (2.0f),
 * while safely bounding extreme values below 0.85f or above 2.0f to protect
 * screen layout and canvas geometry.
 */
class BaseActivityFontScaleTest {

    @Test
    fun `clampFontScale - standard 100 percent scale is preserved`() {
        val result = BaseActivity.clampFontScale(1.0f)
        assertEquals(1.0f, result, 0.001f)
    }

    @Test
    fun `clampFontScale - large 130 percent scale is preserved`() {
        val result = BaseActivity.clampFontScale(1.3f)
        assertEquals(1.3f, result, 0.001f)
    }

    @Test
    fun `clampFontScale - extra large 150 percent scale is preserved`() {
        val result = BaseActivity.clampFontScale(1.5f)
        assertEquals(1.5f, result, 0.001f)
    }

    @Test
    fun `clampFontScale - maximum accessibility 200 percent scale is preserved`() {
        val result = BaseActivity.clampFontScale(2.0f)
        assertEquals(2.0f, result, 0.001f)
    }

    @Test
    fun `clampFontScale - extreme high scale above 200 percent is capped at 2_0f`() {
        val result = BaseActivity.clampFontScale(2.5f)
        assertEquals(2.0f, result, 0.001f)

        val resultExtreme = BaseActivity.clampFontScale(3.2f)
        assertEquals(2.0f, resultExtreme, 0.001f)
    }

    @Test
    fun `clampFontScale - min supported boundary 85 percent is preserved`() {
        val result = BaseActivity.clampFontScale(0.85f)
        assertEquals(0.85f, result, 0.001f)
    }

    @Test
    fun `clampFontScale - extreme small scale below 85 percent is clamped to 0_85f`() {
        val result = BaseActivity.clampFontScale(0.5f)
        assertEquals(0.85f, result, 0.001f)

        val resultZero = BaseActivity.clampFontScale(0.0f)
        assertEquals(0.85f, resultZero, 0.001f)
    }
}
