package com.mckimquyen.util

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.hypot

/**
 * Unit tests for [ApertureRevealHelper] calculations and edge cases.
 */
@RunWith(RobolectricTestRunner::class)
class ApertureRevealHelperTest {

    @Test
    fun `null anchor defaults to container center`() {
        val params = ApertureRevealHelper.calculateParams(null, 1000, 800)
        assertEquals(500, params.centerX)
        assertEquals(400, params.centerY)
        val expectedRadius = hypot(500.0, 400.0).toFloat()
        assertEquals(expectedRadius, params.maxRadius, 0.01f)
    }

    @Test
    fun `empty anchor rect defaults to container center`() {
        val emptyRect = Rect(0, 0, 0, 0)
        val params = ApertureRevealHelper.calculateParams(emptyRect, 600, 400)
        assertEquals(300, params.centerX)
        assertEquals(200, params.centerY)
    }

    @Test
    fun `anchor at top-left sets center to anchor center and radius to opposite corner`() {
        val anchorRect = Rect(0, 0, 100, 100) // center at 50, 50
        val params = ApertureRevealHelper.calculateParams(anchorRect, 1000, 800)
        assertEquals(50, params.centerX)
        assertEquals(50, params.centerY)
        // distance from (50, 50) to (1000, 800) is hypot(950, 750)
        val expectedRadius = hypot(950.0, 750.0).toFloat()
        assertEquals(expectedRadius, params.maxRadius, 0.01f)
    }

    @Test
    fun `anchor at bottom-right sets radius to top-left corner`() {
        val anchorRect = Rect(900, 700, 1000, 800) // center at 950, 750
        val params = ApertureRevealHelper.calculateParams(anchorRect, 1000, 800)
        assertEquals(950, params.centerX)
        assertEquals(750, params.centerY)
        // distance from (950, 750) to (0, 0) is hypot(950, 750)
        val expectedRadius = hypot(950.0, 750.0).toFloat()
        assertEquals(expectedRadius, params.maxRadius, 0.01f)
    }

    @Test
    fun `anchor outside container bounds is clamped to container`() {
        val outsideRect = Rect(1200, -200, 1300, -100)
        val params = ApertureRevealHelper.calculateParams(outsideRect, 1000, 800)
        assertEquals(1000, params.centerX)
        assertEquals(0, params.centerY)
        assertTrue(params.maxRadius > 0f)
    }

    @Test
    fun `zero or negative container dimensions are handled gracefully`() {
        val params = ApertureRevealHelper.calculateParams(null, 0, -10)
        assertTrue(params.centerX >= 0)
        assertTrue(params.centerY >= 0)
        assertTrue(params.maxRadius >= 1f)
    }

    @Test
    fun `shouldAnimate requires all three conditions`() {
        assertTrue(ApertureRevealHelper.shouldAnimate(reduceMotion = false, hasWindow = true, isAttached = true))
        org.junit.Assert.assertFalse(ApertureRevealHelper.shouldAnimate(reduceMotion = true, hasWindow = true, isAttached = true))
        org.junit.Assert.assertFalse(ApertureRevealHelper.shouldAnimate(reduceMotion = false, hasWindow = false, isAttached = true))
        org.junit.Assert.assertFalse(ApertureRevealHelper.shouldAnimate(reduceMotion = false, hasWindow = true, isAttached = false))
    }
}
