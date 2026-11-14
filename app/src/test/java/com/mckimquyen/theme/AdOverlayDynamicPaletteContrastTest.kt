package com.mckimquyen.theme

import androidx.core.graphics.ColorUtils
import com.google.android.material.color.utilities.Scheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI-027 follow-up. The open-app ad overlay paints `?attr/colorOnPrimary` over `?attr/colorPrimary`. On Android 12+ both roles come from
 * a dynamic palette the system derives from the wallpaper with the same Material colour utilities this app links, so the pairing has to
 * hold for palettes other than the app's own static ones. This generates real Material 3 light and dark schemes from a spread of seed
 * colours (a stand-in for wallpapers, NOT a real wallpaper palette: the locked test device runs Android 11, which has no dynamic colour)
 * and checks the pair the overlay uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AdOverlayDynamicPaletteContrastTest {
    // Reds, greens, blues, yellows, purples, pinks, plus a near-black, a near-white and two greys, as a stand-in for wallpaper seeds.
    private val seeds = intArrayOf(
        0xFFB51939.toInt(), 0xFFFFB3B8.toInt(), 0xFF2E7D32.toInt(), 0xFF1565C0.toInt(), 0xFFFFB300.toInt(), 0xFF6A1B9A.toInt(),
        0xFFE91E63.toInt(), 0xFF00BCD4.toInt(), 0xFFFF5722.toInt(), 0xFF795548.toInt(), 0xFF212121.toInt(), 0xFFF5F5F5.toInt(),
        0xFF9E9E9E.toInt(), 0xFF607D8B.toInt(), 0xFFCDDC39.toInt(), 0xFF3F51B5.toInt()
    )

    private fun ratio(fg: Int, bg: Int) = ColorUtils.calculateContrast(fg, bg)

    @Test fun onPrimaryOverPrimaryReadsAtLeast4Point5ToOneInEveryGeneratedLightAndDarkScheme() {
        for (seed in seeds) {
            val light = Scheme.light(seed)
            val dark = Scheme.dark(seed)
            val l = ratio(light.onPrimary, light.primary)
            val d = ratio(dark.onPrimary, dark.primary)
            assertTrue("light seed #${Integer.toHexString(seed)}: on-primary over primary was $l", l >= MIN_RATIO)
            assertTrue("dark seed #${Integer.toHexString(seed)}: on-primary over primary was $d", d >= MIN_RATIO)
        }
    }

    @Test fun theFixedWhiteTheOverlayUsedToHaveFailsInEveryGeneratedDarkScheme() {
        // Positive control on the same palettes: the check can fail, and the old fixed white was unsafe on all of them, not on one colour.
        // (Measured 1.70 to 1.71:1 for all 16 seeds.)
        val failing = seeds.count { ratio(0xFFFFFFFF.toInt(), Scheme.dark(it).primary) < MIN_RATIO }
        assertEquals("fixed white should fail on every dark scheme", seeds.size, failing)
    }

    private companion object {
        const val MIN_RATIO = 4.5
    }
}
