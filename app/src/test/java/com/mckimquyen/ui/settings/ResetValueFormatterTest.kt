package com.mckimquyen.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ResetValueFormatterTest {
    private val formatter = ResetValueFormatter(on = "On", off = "Off", default = "Default", ms = { "$it ms" })

    @Test
    fun `booleans use the localized on and off words`() {
        assertEquals("On", formatter.format("vibrate_app_hover", true))
        assertEquals("Off", formatter.format("vibrate_app_hover", false))
    }

    @Test
    fun `numbers are shown without noise`() {
        assertEquals("2.5", formatter.format("distortion_factor", 2.5f))
        assertEquals("1.0", formatter.format("scale_factor", 1.0f))
        assertEquals("300 ms", formatter.format("animation_time", 300L))
    }

    @Test
    fun `an empty string reads as default`() {
        assertEquals("Default", formatter.format("search_hint_text", ""))
        assertEquals("Find apps", formatter.format("search_hint_text", "Find apps"))
    }

    @Test
    fun `haptic intensity and night mode keep readable names`() {
        assertEquals("Light", formatter.format("haptic_intensity", 0))
        assertEquals("Strong", formatter.format("haptic_intensity", 2))
        assertEquals("Dark", formatter.format("night_mode", 2))
        assertEquals("Light", formatter.format("night_mode", 1))
    }

    @Test
    fun `enum style names are shown as is`() {
        assertEquals("COLOR", formatter.format("background_mode", "COLOR"))
    }

    @Test
    fun `an unknown number type falls back to its text form`() {
        assertEquals("7", formatter.format("anything", 7))
    }
}
