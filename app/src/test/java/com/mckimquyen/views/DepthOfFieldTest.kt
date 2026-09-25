package com.mckimquyen.views

import com.mckimquyen.views.DepthOfField.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FISH-007: every branch of the pure depth-of-field math, no Context needed. */
class DepthOfFieldTest {

    private companion object {
        const val API_30 = 30
        const val API_31 = 31
        const val W = 1000f
        const val H = 2000f
        const val EPS = 1e-4f
        const val DENSITY = 2.5f
    }

    // ---------------------------------------------------------------- renderMode

    @Test
    fun `renderMode covers every enabled-visible-api-hardware case`() {
        for (enabled in listOf(true, false)) for (visible in listOf(true, false))
            for (sdk in listOf(API_30, API_31)) for (hw in listOf(true, false)) {
                val expected = when {
                    !enabled || !visible -> Mode.OFF
                    sdk >= API_31 && hw -> Mode.BLUR
                    else -> Mode.ALPHA
                }
                assertEquals("enabled=$enabled visible=$visible sdk=$sdk hw=$hw",
                    expected, DepthOfField.renderMode(enabled, visible, sdk, hw))
            }
    }

    @Test
    fun `below API 31 falls back to alpha, never silently OFF`() {
        assertEquals(Mode.ALPHA, DepthOfField.renderMode(true, true, API_30, true))
    }

    // ---------------------------------------------------------------- focusDistance

    @Test
    fun `focus point itself has zero distance`() {
        assertEquals(0f, DepthOfField.focusDistance(300f, 700f, 300f, 700f, W, H), EPS)
    }

    @Test
    fun `farthest corner clamps to one`() {
        assertEquals(1f, DepthOfField.focusDistance(0f, 0f, W, H, W, H), EPS)
    }

    @Test
    fun `distance is normalized by the farther edge like the warp`() {
        // touch at x=250: farther edge is 750 away, cell 375 px right -> 0.5 on that axis only.
        assertEquals(0.5f, DepthOfField.focusDistance(250f, 1000f, 625f, 1000f, W, H), EPS)
    }

    @Test
    fun `zero-size view does not divide by zero`() {
        assertEquals(0f, DepthOfField.focusDistance(0f, 0f, 0f, 0f, 0f, 0f), EPS)
    }

    @Test
    fun `distance is monotonic moving away from focus`() {
        var previous = -1f
        for (x in 500..1000 step 50) {
            val d = DepthOfField.focusDistance(500f, 1000f, x.toFloat(), 1000f, W, H)
            assertTrue("x=$x", d >= previous)
            previous = d
        }
    }

    // ---------------------------------------------------------------- transition

    @Test
    fun `normal motion follows the lens animation`() {
        assertEquals(0.37f, DepthOfField.transition(0.37f, reduceMotion = false), EPS)
        assertEquals(1f, DepthOfField.transition(1.5f, reduceMotion = false), EPS)
        assertEquals(0f, DepthOfField.transition(-0.5f, reduceMotion = false), EPS)
    }

    @Test
    fun `reduced motion snaps - no intermediate blur values`() {
        assertEquals(1f, DepthOfField.transition(0.01f, reduceMotion = true), EPS)
        assertEquals(1f, DepthOfField.transition(0.5f, reduceMotion = true), EPS)
        assertEquals(0f, DepthOfField.transition(0f, reduceMotion = true), EPS)
    }

    // ---------------------------------------------------------------- intensity

    @Test
    fun `inside focus zone is fully sharp`() {
        assertEquals(0f, DepthOfField.intensity(0f, 1f), EPS)
        assertEquals(0f, DepthOfField.intensity(DepthOfField.FOCUS_ZONE, 1f), EPS)
    }

    @Test
    fun `max distance is max intensity and clamps beyond`() {
        assertEquals(1f, DepthOfField.intensity(1f, 1f), EPS)
        assertEquals(1f, DepthOfField.intensity(2f, 1f), EPS)
    }

    @Test
    fun `intensity is monotonic in distance`() {
        var previous = -1f
        for (i in 0..20) {
            val v = DepthOfField.intensity(i / 20f, 1f)
            assertTrue("d=${i / 20f}", v >= previous)
            previous = v
        }
    }

    @Test
    fun `transition scales intensity and zero transition means no blur`() {
        assertEquals(0.5f, DepthOfField.intensity(1f, 0.5f), EPS)
        assertEquals(0f, DepthOfField.intensity(1f, 0f), EPS)
    }

    // ---------------------------------------------------------------- band / radius / alpha

    @Test
    fun `band maps intensity to sharp plus quantized blur levels`() {
        assertEquals(0, DepthOfField.band(0f))
        assertEquals(0, DepthOfField.band(-1f))
        assertEquals(1, DepthOfField.band(0.01f))
        assertEquals(1, DepthOfField.band(0.5f))
        assertEquals(DepthOfField.BLUR_BAND_COUNT, DepthOfField.band(0.51f))
        assertEquals(DepthOfField.BLUR_BAND_COUNT, DepthOfField.band(1f))
        assertEquals(DepthOfField.BLUR_BAND_COUNT, DepthOfField.band(5f))
    }

    @Test
    fun `blur radius is zero when sharp and max at the top band`() {
        assertEquals(0f, DepthOfField.blurRadiusPx(0, DENSITY), EPS)
        assertEquals(DepthOfField.MAX_BLUR_RADIUS_DP * DENSITY,
            DepthOfField.blurRadiusPx(DepthOfField.BLUR_BAND_COUNT, DENSITY), EPS)
        assertTrue(DepthOfField.blurRadiusPx(1, DENSITY) < DepthOfField.blurRadiusPx(2, DENSITY))
    }

    @Test
    fun `downsampled layer rounds up so scaled-back layer always covers the view`() {
        assertEquals(360, DepthOfField.layerSize(1440, 4))
        assertEquals(781, DepthOfField.layerSize(3121, 4))
        assertEquals(1, DepthOfField.layerSize(1, 4))
        assertEquals(0, DepthOfField.layerSize(0, 4))
        assertEquals(0, DepthOfField.layerSize(-5, 4))
        for (size in 1..50) assertTrue(DepthOfField.layerSize(size, 4) * 4 >= size)
    }

    @Test
    fun `fallback alpha is opaque at focus and floored far away`() {
        assertEquals(DepthOfField.OPAQUE_ALPHA, DepthOfField.fallbackAlpha(0f))
        val floor = Math.round(DepthOfField.OPAQUE_ALPHA * DepthOfField.MIN_FALLBACK_ALPHA)
        assertEquals(floor, DepthOfField.fallbackAlpha(1f))
        assertEquals(floor, DepthOfField.fallbackAlpha(9f))
        assertTrue(DepthOfField.fallbackAlpha(0.5f) in floor..DepthOfField.OPAQUE_ALPHA)
    }
}
