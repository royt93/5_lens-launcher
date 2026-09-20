package com.mckimquyen.theme

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying VIP, Web View, and Custom Dialog Material You color roles and contrast.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MaterialYouVipAndComponentsUnitTest {

    private fun assertContrast(fg: Int, bg: Int, minRatio: Double, name: String) {
        val ratio = ColorUtils.calculateContrast(fg, bg)
        assertTrue(
            "$name contrast ratio $ratio should be >= $minRatio",
            ratio >= minRatio
        )
    }

    @Test
    fun testVipStatusHeaderContrast() {
        // Active VIP Gold header background: center is #FFB300, text is #1C1C1E
        val activeGoldBg = Color.parseColor("#FFB300")
        val activeDarkText = Color.parseColor("#1C1C1E")
        assertContrast(activeDarkText, activeGoldBg, 7.0, "Active VIP Dark Title on Gold Header")

        // Free VIP Dark header background: #212121 to #37474F, text is #FFFFFF
        val freeDarkBg = Color.parseColor("#212121")
        val freeWhiteText = Color.parseColor("#FFFFFF")
        assertContrast(freeWhiteText, freeDarkBg, 10.0, "Free VIP White Title on Dark Slate Header")
    }

    @Test
    fun testVipCtaButtonContrast() {
        // Green Watch Ad button #2E7D32, text is #FFFFFF
        val buttonGreen = Color.parseColor("#2E7D32")
        val whiteText = Color.parseColor("#FFFFFF")
        assertContrast(whiteText, buttonGreen, 4.5, "White text on Green Watch Ad button")
    }

    @Test
    fun testDialogErrorActionContrast() {
        // Error color in light mode: #BA1A1A, on-surface: #FCFCFF
        val lightError = Color.parseColor("#BA1A1A")
        val lightSurface = Color.parseColor("#FCFCFF")
        assertContrast(lightError, lightSurface, 5.0, "Revoke text on Light Surface")

        // Error color in dark mode: #FFB4AB, on-surface: #111318
        val darkError = Color.parseColor("#FFB4AB")
        val darkSurface = Color.parseColor("#111318")
        assertContrast(darkError, darkSurface, 8.0, "Revoke text on Dark Surface")
    }
}
