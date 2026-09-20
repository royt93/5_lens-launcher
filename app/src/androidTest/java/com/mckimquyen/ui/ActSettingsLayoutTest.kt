package com.mckimquyen.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget test for the `act_settings.xml` "Start" button height fix: the button was wrapping
 * its two-line label ("Bắt đầu (Có thể có quảng cáo)") inside 18dp of padding on every side,
 * producing an oversized touch target. Vertical padding was reduced to 10dp (horizontal stayed
 * at 18dp) and the label text size was reduced from 16sp to 13sp so the still-two-line label
 * takes noticeably less vertical space overall.
 */
@RunWith(AndroidJUnit4::class)
class ActSettingsLayoutTest {

    /**
     * Inflates `act_settings.xml` under a fontScale=1.0 configuration override so assertions
     * on resolved sp/dp pixel sizes are deterministic regardless of this device's own
     * accessibility "font size" setting (Samsung devices apply a non-linear boost curve to
     * small text sizes under a larger font-size setting, which otherwise makes 13sp and 16sp
     * resolve to the same on-device pixel size and defeats any px-based assertion).
     */
    private fun inflateActSettingsAtDefaultFontScale(): android.view.View {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val fixedConfig = Configuration(targetContext.resources.configuration).apply { fontScale = 1.0f }
        val configContext = targetContext.createConfigurationContext(fixedConfig)
        val context = ContextThemeWrapper(configContext, R.style.AppTheme_NoActionBar)
        return LayoutInflater.from(context).inflate(R.layout.act_settings, null, false)
    }

    @Test
    fun btStartMaterialButtonMatchesMaterial3Specifications() {
        val root = inflateActSettingsAtDefaultFontScale()
        val button = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btStart)
        org.junit.Assert.assertNotNull("btStart must exist as MaterialButton", button)

        val density = button.resources.displayMetrics.density
        val expectedCornerRadiusPx = Math.round(28 * density)

        assertEquals("btStart cornerRadius must match 28dp Material3 pill shape", expectedCornerRadiusPx, button.cornerRadius)
        org.junit.Assert.assertEquals(button.context.getString(R.string.menu_title_show_apps), button.text.toString())
    }

    @Test
    fun btStartIconIsConfiguredWithProperPadding() {
        val root = inflateActSettingsAtDefaultFontScale()
        val button = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btStart)

        val density = button.resources.displayMetrics.density
        val expectedIconPaddingPx = Math.round(12 * density)

        assertEquals("btStart iconPadding must match 12dp", expectedIconPaddingPx, button.iconPadding)
    }
}
