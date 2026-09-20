package com.mckimquyen.theme

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying Material You (Material 3) tonal container hierarchy and contrast.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MaterialYouColorRolesTest {

    @Test
    fun testLightModeContainerHierarchy() {
        val lowest = Color.parseColor("#FFFFFF")
        val low = Color.parseColor("#F7F9FE")
        val container = Color.parseColor("#EEF0F6")
        val high = Color.parseColor("#E5E8EF")
        val highest = Color.parseColor("#DDE1E8")

        // In Light mode, luminance decreases as container elevation increases (more tonal color added)
        val lLowest = ColorUtils.calculateLuminance(lowest)
        val lLow = ColorUtils.calculateLuminance(low)
        val lContainer = ColorUtils.calculateLuminance(container)
        val lHigh = ColorUtils.calculateLuminance(high)
        val lHighest = ColorUtils.calculateLuminance(highest)

        assertTrue("Lowest luminance >= Low luminance", lLowest >= lLow)
        assertTrue("Low luminance >= Container luminance", lLow >= lContainer)
        assertTrue("Container luminance >= High luminance", lContainer >= lHigh)
        assertTrue("High luminance >= Highest luminance", lHigh >= lHighest)
    }

    @Test
    fun testDarkModeContainerHierarchy() {
        val lowest = Color.parseColor("#0C0E13")
        val low = Color.parseColor("#17191E")
        val container = Color.parseColor("#1E2025")
        val high = Color.parseColor("#282A2F")
        val highest = Color.parseColor("#33353A")

        // In Dark mode, luminance increases as container elevation increases (lighter surface)
        val lLowest = ColorUtils.calculateLuminance(lowest)
        val lLow = ColorUtils.calculateLuminance(low)
        val lContainer = ColorUtils.calculateLuminance(container)
        val lHigh = ColorUtils.calculateLuminance(high)
        val lHighest = ColorUtils.calculateLuminance(highest)

        assertTrue("Lowest luminance <= Low luminance", lLowest <= lLow)
        assertTrue("Low luminance <= Container luminance", lLow <= lContainer)
        assertTrue("Container luminance <= High luminance", lContainer <= lHigh)
        assertTrue("High luminance <= Highest luminance", lHigh <= lHighest)
    }

    @Test
    fun testSecondaryAndTertiaryContainerContrast() {
        // Light mode secondary & tertiary
        val secContainerLight = Color.parseColor("#D8E2FF")
        val onSecContainerLight = Color.parseColor("#001A41")
        val ratioSecLight = ColorUtils.calculateContrast(onSecContainerLight, secContainerLight)
        assertTrue("Light secondary container contrast >= 4.5", ratioSecLight >= 4.5)

        // Dark mode secondary & tertiary
        val secContainerDark = Color.parseColor("#294676")
        val onSecContainerDark = Color.parseColor("#D8E2FF")
        val ratioSecDark = ColorUtils.calculateContrast(onSecContainerDark, secContainerDark)
        assertTrue("Dark secondary container contrast >= 4.5", ratioSecDark >= 4.5)
    }
}
