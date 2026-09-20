package com.mckimquyen.theme

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying Material You (Material 3) color roles and WCAG contrast compliance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MaterialYouThemeUnitTest {

    private fun assertWcagContrast(foreground: Int, background: Int, minRatio: Double = 4.5, context: String) {
        val ratio = ColorUtils.calculateContrast(foreground, background)
        assertTrue(
            "WCAG contrast check failed for $context: ratio was $ratio (expected >= $minRatio)",
            ratio >= minRatio
        )
    }

    @Test
    fun testLightModeTokensContrast() {
        // Light mode colors from values/colors.xml
        val lightPrimary = Color.parseColor("#1565C0")
        val lightOnPrimary = Color.parseColor("#FFFFFF")
        val lightSurface = Color.parseColor("#FCFCFF")
        val lightOnSurface = Color.parseColor("#1A1C1E")
        val lightSurfaceContainer = Color.parseColor("#EEF0F6")
        val lightSurfaceContainerHigh = Color.parseColor("#E5E8EF")

        // 1. colorPrimary vs colorOnPrimary (WCAG AA >= 4.5)
        assertWcagContrast(lightOnPrimary, lightPrimary, 4.5, "Light colorPrimary vs colorOnPrimary")

        // 2. colorSurface vs colorOnSurface (WCAG AA >= 4.5)
        assertWcagContrast(lightOnSurface, lightSurface, 4.5, "Light colorSurface vs colorOnSurface")

        // 3. colorSurfaceContainer vs colorOnSurface (WCAG AA >= 4.5)
        assertWcagContrast(lightOnSurface, lightSurfaceContainer, 4.5, "Light colorSurfaceContainer vs colorOnSurface")

        // 4. colorSurfaceContainerHigh vs colorOnSurface (WCAG AA >= 4.5)
        assertWcagContrast(lightOnSurface, lightSurfaceContainerHigh, 4.5, "Light colorSurfaceContainerHigh vs colorOnSurface")
    }

    @Test
    fun testDarkModeTokensContrast() {
        // Dark mode colors from values-night/colors.xml
        val darkPrimary = Color.parseColor("#A8C7FA")
        val darkOnPrimary = Color.parseColor("#062E6F")
        val darkSurface = Color.parseColor("#111318")
        val darkOnSurface = Color.parseColor("#E2E2E6")
        val darkSurfaceContainer = Color.parseColor("#1E2025")
        val darkSurfaceContainerHigh = Color.parseColor("#282A2F")

        // 1. colorPrimary vs colorOnPrimary (WCAG AA >= 4.5)
        assertWcagContrast(darkOnPrimary, darkPrimary, 4.5, "Dark colorPrimary vs colorOnPrimary")

        // 2. colorSurface vs colorOnSurface (WCAG AA >= 4.5)
        assertWcagContrast(darkOnSurface, darkSurface, 4.5, "Dark colorSurface vs colorOnSurface")

        // 3. colorSurfaceContainer vs colorOnSurface (WCAG AA >= 4.5)
        assertWcagContrast(darkOnSurface, darkSurfaceContainer, 4.5, "Dark colorSurfaceContainer vs colorOnSurface")

        // 4. colorSurfaceContainerHigh vs colorOnSurface (WCAG AA >= 4.5)
        assertWcagContrast(darkOnSurface, darkSurfaceContainerHigh, 4.5, "Dark colorSurfaceContainerHigh vs colorOnSurface")
    }

    @Test
    fun testPopupContrastWithOnSurface() {
        // Light popup surface vs onSurface
        val lightPopupBg = Color.parseColor("#EEF0F6")
        val lightText = Color.parseColor("#1A1C1E")
        assertWcagContrast(lightText, lightPopupBg, 4.5, "Light Popup Menu Text")

        // Dark popup surface vs onSurface
        val darkPopupBg = Color.parseColor("#1E2025")
        val darkText = Color.parseColor("#E2E2E6")
        assertWcagContrast(darkText, darkPopupBg, 4.5, "Dark Popup Menu Text")
    }
}
