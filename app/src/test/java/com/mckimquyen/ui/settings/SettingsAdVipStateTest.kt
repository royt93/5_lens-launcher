package com.mckimquyen.ui.settings

import android.graphics.Color
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsAdVipStateTest {

    @Test
    fun `computeBadgeState returns active gold styling when isVip is true in light mode`() {
        val state = SettingsAdVipDelegate.computeBadgeState(isVip = true, isNightMode = false)
        assertTrue(state.isVipActive)
        assertEquals(R.string.vip_badge_active, state.textResId)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_GOLD_COLOR_HEX), state.backgroundColorInt)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_DARK_TEXT_HEX), state.textColorInt)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_DARK_TEXT_HEX), state.iconColorInt)
    }

    @Test
    fun `computeBadgeState returns active gold styling when isVip is true in night mode`() {
        val state = SettingsAdVipDelegate.computeBadgeState(isVip = true, isNightMode = true)
        assertTrue(state.isVipActive)
        assertEquals(R.string.vip_badge_active, state.textResId)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_GOLD_COLOR_HEX), state.backgroundColorInt)
    }

    @Test
    fun `computeBadgeState returns inactive day styling when isVip is false in light mode`() {
        val state = SettingsAdVipDelegate.computeBadgeState(isVip = false, isNightMode = false)
        assertFalse(state.isVipActive)
        assertEquals(R.string.vip_badge_get, state.textResId)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_INACTIVE_DAY_BG_HEX), state.backgroundColorInt)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_INACTIVE_DAY_TEXT_HEX), state.textColorInt)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_INACTIVE_DAY_TEXT_HEX), state.iconColorInt)
    }

    @Test
    fun `computeBadgeState returns inactive night styling when isVip is false in night mode`() {
        val state = SettingsAdVipDelegate.computeBadgeState(isVip = false, isNightMode = true)
        assertFalse(state.isVipActive)
        assertEquals(R.string.vip_badge_get, state.textResId)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_INACTIVE_NIGHT_BG_HEX), state.backgroundColorInt)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_INACTIVE_NIGHT_TEXT_HEX), state.textColorInt)
        assertEquals(Color.parseColor(SettingsAdVipDelegate.VIP_INACTIVE_NIGHT_TEXT_HEX), state.iconColorInt)
    }

    @Test
    fun `shouldShowBanner returns false when user is VIP even if consent resolved`() {
        assertFalse(SettingsAdVipDelegate.shouldShowBanner(isVip = true, consentResolved = true))
    }

    @Test
    fun `shouldShowBanner returns false when consent is not yet resolved`() {
        assertFalse(SettingsAdVipDelegate.shouldShowBanner(isVip = false, consentResolved = false))
    }

    @Test
    fun `shouldShowBanner returns true only when user is non-VIP and consent resolved`() {
        assertTrue(SettingsAdVipDelegate.shouldShowBanner(isVip = false, consentResolved = true))
    }
}
