package com.mckimquyen.theme

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * UI-027: the open-app ad overlay (`flAdOpenApp` in act_settings.xml) paints its background with the theme primary colour,
 * so its spinner and disclosure text must use the matching on-primary colour. A fixed white is unreadable on the night
 * primary (#FFB3B8, 1.69:1). These tests read the real resource files, not copied hex values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AdOverlayContrastTest {
    private val resDir by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        sequenceOf(File(dir, "src/main/res"), File(dir, "app/src/main/res")).first { it.isDirectory }
    }

    private fun colorOf(valuesDir: String, name: String): Int {
        val xml = File(resDir, "$valuesDir/colors.xml").readText()
        val hex = Regex("""<color name="$name">(#[0-9A-Fa-f]{6,8})</color>""").find(xml)?.groupValues?.get(1)
        return Color.parseColor(requireNotNull(hex) { "$name missing in $valuesDir/colors.xml" })
    }

    private val overlayBlock by lazy {
        val xml = File(resDir, "layout/act_settings.xml").readText()
        val start = xml.indexOf("""android:id="@+id/flAdOpenApp"""")
        require(start >= 0) { "flAdOpenApp not found" }
        // ends at the closing tag of the overlay LinearLayout, i.e. the last </LinearLayout> before the FrameLayout closes
        val end = xml.indexOf("</FrameLayout>", start)
        xml.substring(start, end)
    }

    @Test fun theOverlayBackgroundIsStillThePrimaryColour() {
        assertTrue(overlayBlock.contains("""android:background="?attr/colorPrimary""""))
    }

    @Test fun theSpinnerAndTheDisclosureTextUseTheOnPrimaryColourNotAFixedWhite() {
        assertTrue("spinner tint is on-primary", overlayBlock.contains("""android:indeterminateTint="?attr/colorOnPrimary""""))
        assertTrue("disclosure text is on-primary", overlayBlock.contains("""android:textColor="?attr/colorOnPrimary""""))
        assertFalse("no fixed white left in the overlay", overlayBlock.contains("@color/colorWhite"))
    }

    @Test fun onPrimaryReadsAtLeast4Point5ToOneOnPrimaryInLightAndNight() {
        for (dir in listOf("values", "values-night")) {
            val ratio = ColorUtils.calculateContrast(colorOf(dir, "colorOnPrimary"), colorOf(dir, "colorPrimary"))
            assertTrue("$dir on-primary over primary was $ratio", ratio >= 4.5)
        }
    }

    @Test fun theFixedWhiteThatWasThereFailsTheSameCheckInNightWhichIsTheRegression() {
        // Positive control: proves the contrast check can fail on this very pair, so passing above means something.
        val ratio = ColorUtils.calculateContrast(Color.parseColor("#FFFFFF"), colorOf("values-night", "colorPrimary"))
        assertTrue("white over night primary was $ratio, expected the reported 1.69:1", ratio < 2.0)
        assertEquals(1.69, ratio, 0.01)
    }
}
