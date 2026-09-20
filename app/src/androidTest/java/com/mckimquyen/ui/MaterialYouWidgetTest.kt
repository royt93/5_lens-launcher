package com.mckimquyen.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import com.mckimquyen.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Widget Tests for Material You (Material 3) updated components:
 * 1. Search shortcut chip (item_search_shortcut_chip.xml)
 * 2. Ad banner badge (layout_ad_banner.xml)
 * 3. Popup menu theme contrast (AppTheme.PopupOverlay)
 * 4. Material You Card tokens
 */
@RunWith(AndroidJUnit4::class)
class MaterialYouWidgetTest {

    private lateinit var themedContext: Context

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        themedContext = ContextThemeWrapper(targetContext, R.style.AppTheme)
    }

    @Test
    fun testSearchShortcutChipLayout() {
        val view = LayoutInflater.from(themedContext).inflate(R.layout.item_search_shortcut_chip, null, false)
        assertNotNull("Search shortcut chip must inflate", view)

        val density = themedContext.resources.displayMetrics.density

        // Verify padding (10dp horizontal, 6dp vertical)
        val expectedHorizontalPadding = Math.round(10 * density)
        val expectedVerticalPadding = Math.round(6 * density)
        assertEquals("Horizontal start padding should be 10dp", expectedHorizontalPadding, view.paddingStart)
        assertEquals("Horizontal end padding should be 10dp", expectedHorizontalPadding, view.paddingEnd)
        assertEquals("Vertical top padding should be 6dp", expectedVerticalPadding, view.paddingTop)
        assertEquals("Vertical bottom padding should be 6dp", expectedVerticalPadding, view.paddingBottom)

        // Verify child views
        val icon = view.findViewById<ImageView>(R.id.ivShortcutIcon)
        assertNotNull("ivShortcutIcon must exist", icon)
        assertEquals("Icon width should be 20dp", Math.round(20 * density), icon.layoutParams.width)
        assertEquals("Icon height should be 20dp", Math.round(20 * density), icon.layoutParams.height)

        val label = view.findViewById<TextView>(R.id.tvShortcutLabel)
        assertNotNull("tvShortcutLabel must exist", label)
        assertEquals("Label max lines must be 1", 1, label.maxLines)

        // Verify ripple background
        assertTrue("Background must be a RippleDrawable", view.background is RippleDrawable)
    }

    @Test
    fun testAdBannerBadgeLayout() {
        val view = LayoutInflater.from(themedContext).inflate(R.layout.layout_ad_banner, null, false)
        assertNotNull("Ad banner layout must inflate", view)

        val tvLabelAd = view.findViewById<TextView>(R.id.tvLabelAd)
        assertNotNull("tvLabelAd must exist", tvLabelAd)
        assertEquals("Text must match ad_label string", themedContext.getString(R.string.ad_label), tvLabelAd.text.toString())

        val density = themedContext.resources.displayMetrics.density
        val expectedHorizontalPadding = Math.round(8 * density)
        val expectedVerticalPadding = Math.round(2 * density)

        assertEquals("Ad badge padding start should be 8dp", expectedHorizontalPadding, tvLabelAd.paddingStart)
        assertEquals("Ad badge padding end should be 8dp", expectedHorizontalPadding, tvLabelAd.paddingEnd)
        assertEquals("Ad badge padding top should be 2dp", expectedVerticalPadding, tvLabelAd.paddingTop)
        assertEquals("Ad badge padding bottom should be 2dp", expectedVerticalPadding, tvLabelAd.paddingBottom)
    }

    @Test
    fun testPopupMenuOverlayContrast() {
        val popupContext = ContextThemeWrapper(themedContext, R.style.AppTheme_PopupOverlay)

        val textColor = MaterialColors.getColor(popupContext, android.R.attr.textColorPrimary, 0)
        val surfaceColor = MaterialColors.getColor(popupContext, com.google.android.material.R.attr.colorSurfaceContainer, 0)

        assertNotEquals("Text color must be resolved", 0, textColor)
        assertNotEquals("Surface container color must be resolved", 0, surfaceColor)

        val contrast = ColorUtils.calculateContrast(textColor, surfaceColor)
        assertTrue(
            "Popup text contrast against surface container must be >= 4.5 (got $contrast)",
            contrast >= 4.5
        )
    }

    @Test
    fun testMaterialYouCardTokens() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.frm_lens, null, false) as android.view.ViewGroup
        val container = root.getChildAt(0) as android.view.ViewGroup
        val cardView = container.getChildAt(1) as com.google.android.material.card.MaterialCardView
        val density = themedContext.resources.displayMetrics.density

        val expectedRadius = 16 * density
        assertEquals("Card radius should match 16dp", expectedRadius, cardView.radius, 1.0f)
    }
}
