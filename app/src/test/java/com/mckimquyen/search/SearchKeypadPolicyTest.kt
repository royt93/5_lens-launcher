package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * On a display shorter than [SearchKeypadPolicy.COMPACT_HEIGHT_DP] the soft keyboard leaves no room for the result list (Pixel 7 Pro in
 * landscape: 411 dp tall, a 967 px keyboard on 1440 px left 0 px of list), so the in-app T9 keypad stands in for it. Heights below come from
 * the measured profiles: phone portrait 891 dp, phone landscape 411 dp, tablet-like portrait 1280 dp and landscape 800 dp.
 */
class SearchKeypadPolicyTest {

    private companion object {
        const val PHONE_PORTRAIT_HEIGHT_DP = 891
        const val PHONE_LANDSCAPE_HEIGHT_DP = 411
        const val TABLET_PORTRAIT_HEIGHT_DP = 1280
        const val TABLET_LANDSCAPE_HEIGHT_DP = 800
        const val BOUNDARY = SearchKeypadPolicy.COMPACT_HEIGHT_DP
    }

    @Test fun userChoiceOnAlwaysWinsEvenOnATallScreen() {
        assertTrue(SearchKeypadPolicy.useT9Keypad(userEnabled = true, screenHeightDp = PHONE_PORTRAIT_HEIGHT_DP))
        assertTrue(SearchKeypadPolicy.useT9Keypad(userEnabled = true, screenHeightDp = TABLET_LANDSCAPE_HEIGHT_DP))
    }

    @Test fun userChoiceOffStaysOffOnTallScreens() {
        assertFalse(SearchKeypadPolicy.useT9Keypad(userEnabled = false, screenHeightDp = PHONE_PORTRAIT_HEIGHT_DP))
        assertFalse(SearchKeypadPolicy.useT9Keypad(userEnabled = false, screenHeightDp = TABLET_PORTRAIT_HEIGHT_DP))
        assertFalse(SearchKeypadPolicy.useT9Keypad(userEnabled = false, screenHeightDp = TABLET_LANDSCAPE_HEIGHT_DP))
    }

    @Test fun userChoiceOffIsOverriddenOnAShortScreen() {
        assertTrue(SearchKeypadPolicy.useT9Keypad(userEnabled = false, screenHeightDp = PHONE_LANDSCAPE_HEIGHT_DP))
    }

    @Test fun theBoundaryIsExclusive() {
        assertTrue(SearchKeypadPolicy.useT9Keypad(userEnabled = false, screenHeightDp = BOUNDARY - 1))
        assertFalse(SearchKeypadPolicy.useT9Keypad(userEnabled = false, screenHeightDp = BOUNDARY))
    }

    @Test fun anUnknownHeightDoesNotForceTheKeypad() {
        // Configuration.screenHeightDp is SCREEN_HEIGHT_DP_UNDEFINED (0) before the first layout: that must not read as "very short".
        assertFalse(SearchKeypadPolicy.useT9Keypad(userEnabled = false, screenHeightDp = 0))
        assertFalse(SearchKeypadPolicy.useT9Keypad(userEnabled = false, screenHeightDp = -1))
    }

    @Test fun theThresholdSitsBetweenTheMeasuredPhoneAndTabletLandscape() {
        assertTrue(PHONE_LANDSCAPE_HEIGHT_DP < BOUNDARY)
        assertTrue(TABLET_LANDSCAPE_HEIGHT_DP >= BOUNDARY)
        assertEquals(480, BOUNDARY) // Material window-size-class "compact height"
    }
}
