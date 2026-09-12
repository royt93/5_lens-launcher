package com.mckimquyen.search

import android.provider.AlarmClock
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SEARCH-002: pure unit tests for every quick-action sub-parser. `resolveBattery` needs a
 * Context (sticky broadcast) and is covered separately by an instrumented widget test instead.
 * Robolectric is only needed here because the timer sub-action returns a real `Intent`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class QuickActionEngineTest {

    // ==================================================================== Calculator

    @Test
    fun `valid expression returns the computed result`() {
        val result = QuickActionEngine.resolveCalculator("12*7")
        assertEquals(QuickAction.Info("12*7", "84"), result)
    }

    @Test
    fun `expression with parentheses and decimals`() {
        val result = QuickActionEngine.resolveCalculator("(1.5+2.5)*2")
        assertEquals(QuickAction.Info("(1.5+2.5)*2", "8"), result)
    }

    @Test
    fun `division by zero is rejected not crashed`() {
        assertNull(QuickActionEngine.resolveCalculator("12/0"))
    }

    @Test
    fun `incomplete expression is rejected`() {
        assertNull(QuickActionEngine.resolveCalculator("12*"))
        assertNull(QuickActionEngine.resolveCalculator("*12"))
        assertNull(QuickActionEngine.resolveCalculator("(1+2"))
    }

    @Test
    fun `bare number is not treated as an expression`() {
        assertNull(QuickActionEngine.resolveCalculator("5"))
        assertNull(QuickActionEngine.resolveCalculator("-5"))
    }

    @Test
    fun `non-expression text is rejected`() {
        assertNull(QuickActionEngine.resolveCalculator("facebook"))
        assertNull(QuickActionEngine.resolveCalculator(""))
    }

    @Test
    fun `negative numbers and subtraction both work`() {
        assertEquals(QuickAction.Info("-5-3", "-8"), QuickActionEngine.resolveCalculator("-5-3"))
        assertEquals(QuickAction.Info("10-3", "7"), QuickActionEngine.resolveCalculator("10-3"))
    }

    // ==================================================================== Unit conversion

    @Test
    fun `valid length conversion`() {
        val result = QuickActionEngine.resolveUnitConversion("10 km to mi")
        assertEquals("10 km to mi", result?.label)
        assertTrue("expected ~6.2137 mi, got ${result?.value}", result?.value?.startsWith("6.213") == true)
    }

    @Test
    fun `valid weight conversion`() {
        val result = QuickActionEngine.resolveUnitConversion("5 kg to lb")
        assertTrue("expected ~11.02 lb, got ${result?.value}", result?.value?.startsWith("11.02") == true)
    }

    @Test
    fun `valid temperature conversion`() {
        val result = QuickActionEngine.resolveUnitConversion("100 c to f")
        assertEquals(QuickAction.Info("100 c to f", "212 f"), result)
    }

    @Test
    fun `vietnamese sang keyword also works`() {
        assertEquals(QuickAction.Info("1 km sang m", "1000 m"), QuickActionEngine.resolveUnitConversion("1 km sang m"))
    }

    @Test
    fun `unknown unit is rejected`() {
        assertNull(QuickActionEngine.resolveUnitConversion("10 blorp to mi"))
        assertNull(QuickActionEngine.resolveUnitConversion("10 km to blorp"))
    }

    @Test
    fun `mismatched unit categories are rejected`() {
        assertNull(QuickActionEngine.resolveUnitConversion("10 km to kg"))
    }

    @Test
    fun `malformed conversion syntax is rejected`() {
        assertNull(QuickActionEngine.resolveUnitConversion("10 km"))
        assertNull(QuickActionEngine.resolveUnitConversion("km to mi"))
    }

    // ==================================================================== Timer

    @Test
    fun `vietnamese timer phrase resolves to a set-timer intent in seconds`() {
        val result = QuickActionEngine.resolveTimer("hẹn giờ 10 phút")
        assertEquals(AlarmClock.ACTION_SET_TIMER, result?.intent?.action)
        assertEquals(600, result?.intent?.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }

    @Test
    fun `english timer phrase with hours unit`() {
        val result = QuickActionEngine.resolveTimer("timer 2 hours")
        assertEquals(2 * 3600, result?.intent?.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }

    @Test
    fun `timer with seconds unit`() {
        val result = QuickActionEngine.resolveTimer("hen gio 30 giay")
        assertEquals(30, result?.intent?.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }

    @Test
    fun `timer with no unit defaults to minutes`() {
        val result = QuickActionEngine.resolveTimer("hẹn giờ 5")
        assertEquals(5 * 60, result?.intent?.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }

    @Test
    fun `timer intent never shows extra UI it just sets it silently`() {
        val result = QuickActionEngine.resolveTimer("hẹn giờ 10 phút")
        assertEquals(false, result?.intent?.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    @Test
    fun `invalid timer phrase is rejected`() {
        assertNull(QuickActionEngine.resolveTimer("hẹn giờ"))
        assertNull(QuickActionEngine.resolveTimer("hẹn giờ 0 phút"))
        assertNull(QuickActionEngine.resolveTimer("facebook"))
    }

    // ==================================================================== Settings shortcuts

    @Test
    fun `every mapped settings keyword resolves to its action`() {
        val expected = mapOf(
            "wifi" to Settings.ACTION_WIFI_SETTINGS,
            "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "am luong" to Settings.ACTION_SOUND_SETTINGS,
            "volume" to Settings.ACTION_SOUND_SETTINGS,
            "sound" to Settings.ACTION_SOUND_SETTINGS,
            "hien thi" to Settings.ACTION_DISPLAY_SETTINGS,
            "display" to Settings.ACTION_DISPLAY_SETTINGS,
            "ngon ngu" to Settings.ACTION_LOCALE_SETTINGS,
            "language" to Settings.ACTION_LOCALE_SETTINGS,
            "vi tri" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            "ngay gio" to Settings.ACTION_DATE_SETTINGS,
            "date time" to Settings.ACTION_DATE_SETTINGS,
            "mang" to Settings.ACTION_WIRELESS_SETTINGS,
            "network" to Settings.ACTION_WIRELESS_SETTINGS,
            "bao mat" to Settings.ACTION_SECURITY_SETTINGS,
            "security" to Settings.ACTION_SECURITY_SETTINGS
        )
        expected.forEach { (keyword, action) ->
            val result = QuickActionEngine.resolveSettingsShortcut(keyword)
            assertEquals("keyword '$keyword'", action, result?.intent?.action)
        }
    }

    @Test
    fun `settings keyword matching is accent and case insensitive`() {
        assertEquals(
            Settings.ACTION_DISPLAY_SETTINGS,
            QuickActionEngine.resolveSettingsShortcut("Hiển Thị")?.intent?.action
        )
    }

    @Test
    fun `unmapped settings keyword is rejected`() {
        assertNull(QuickActionEngine.resolveSettingsShortcut("unmapped keyword xyz"))
    }

    @Test
    fun `settings keyword must match exactly not just contain the word`() {
        // "Pinterest" must never be hijacked into a battery/settings quick action.
        assertNull(QuickActionEngine.resolveSettingsShortcut("wifi router app"))
    }

    // ==================================================================== Priority order

    @Test
    fun `resolve tries calculator before other sub-actions`() {
        val result = QuickActionEngine.resolveCalculator("12*7")
        assertEquals("84", result?.value)
        // A query that is simultaneously a valid expression is never also a valid conversion/
        // timer/settings phrase by construction, so this just re-confirms calculator's own gate.
        assertNull(QuickActionEngine.resolveUnitConversion("12*7"))
        assertNull(QuickActionEngine.resolveTimer("12*7"))
        assertNull(QuickActionEngine.resolveSettingsShortcut("12*7"))
    }
}
