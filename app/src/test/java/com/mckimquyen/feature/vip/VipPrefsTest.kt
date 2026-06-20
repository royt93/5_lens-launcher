package com.mckimquyen.feature.vip

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VipPrefsTest {

    private lateinit var context: Context
    private lateinit var prefs: VipPrefs

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        prefs = VipPrefs(context)
        // Clear prefs before each test
        context.getSharedPreferences("vip_screen_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testInitialGrantedMsIsZero() {
        assertEquals(0L, prefs.getGrantedAtMs())
        assertFalse(prefs.userRedeemedAtLeastOnce())
    }

    @Test
    fun testSaveGrantedAtMs() {
        val ms = System.currentTimeMillis()
        prefs.saveGrantedAtMs(ms)
        assertEquals(ms, prefs.getGrantedAtMs())
    }

    @Test
    fun testClearGrantedAtMs() {
        val ms = System.currentTimeMillis()
        prefs.saveGrantedAtMs(ms)
        assertEquals(ms, prefs.getGrantedAtMs())

        prefs.clearGrantedAtMs()
        assertEquals(0L, prefs.getGrantedAtMs())
    }

    @Test
    fun testMarkUserRedeemed() {
        prefs.markUserRedeemed()
        assertTrue(prefs.userRedeemedAtLeastOnce())
    }

    @Test
    fun testVipDays() {
        assertEquals(0, prefs.getVipDays())

        prefs.saveVipDays(30)
        assertEquals(30, prefs.getVipDays())

        prefs.clearVipDays()
        assertEquals(0, prefs.getVipDays())
    }

    // BUG-5/11: verify clearAll does not leave stale secret-linked state
    @Test
    fun `BUG5 - full revoke clears all VIP prefs atomically`() {
        prefs.saveGrantedAtMs(System.currentTimeMillis())
        prefs.saveVipDays(3)
        prefs.markUserRedeemed()

        // Simulate revoke flow in ActVipManagement.btnRevokeVip
        prefs.clearGrantedAtMs()
        prefs.clearVipDays()

        assertEquals("grantedAtMs must be 0 after revoke", 0L, prefs.getGrantedAtMs())
        assertEquals("vipDays must be 0 after revoke", 0, prefs.getVipDays())
        // userRedeemed flag is intentionally NOT cleared on revoke (per spec — history preserved)
        assertTrue("userRedeemed flag is preserved after revoke", prefs.userRedeemedAtLeastOnce())
    }

    // BUG-8: grace detection relies on userRedeemedAtLeastOnce returning false before first redeem
    @Test
    fun `BUG8 - grace entry detected when userRedeemed is false`() {
        assertFalse(
            "New install: userRedeemed must be false (grace can be shown)",
            prefs.userRedeemedAtLeastOnce()
        )
    }

    // BUG-8: after markUserRedeemed, grace label must NOT be shown
    @Test
    fun `BUG8 - grace entry not detected after user redeems a key`() {
        prefs.markUserRedeemed()
        assertTrue(
            "After redeem: userRedeemedAtLeastOnce must be true (grace label hidden)",
            prefs.userRedeemedAtLeastOnce()
        )
    }

    @Test
    fun testSaveVipDays3DayGrant() {
        prefs.saveVipDays(3)
        assertEquals("3-day rewarded grant must persist correctly", 3, prefs.getVipDays())
    }
}

