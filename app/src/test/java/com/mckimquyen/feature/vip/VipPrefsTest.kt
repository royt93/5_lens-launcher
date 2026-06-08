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
}

