package com.mckimquyen.feature.vip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roy.sdkadbmob.AdManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdManagerIntegrationTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear VIP keys/prefs before each test to guarantee isolated clean state
        AdManager.clearVipByKey()
        VipPrefs(context).clearGrantedAtMs()
        VipPrefs(context).clearVipDays()
    }

    @Test
    fun testAdManagerInitializationContext() {
        // After cleaning up in setUp(), VIP should not be active initially
        val isVip = AdManager.isVIPMember()
        val isVipByKey = AdManager.isVipByKeyActive()
        
        assertFalse(isVip)
        assertFalse(isVipByKey)
    }

    @Test
    fun testVipActivationLifecycle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Assert clean state
        assertFalse(AdManager.isVipByKeyActive())
        
        // Set the active secret key in configuration first (required by AdManager library)
        val originalSecret = AdManager.adConfig.vipKeySecret
        val testKey = VipKeys.VIP_3D_KEY
        AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = testKey)
        
        try {
            // Activate VIP with valid 3 days key
            val success = AdManager.activateVipByKey(context, testKey, 3)
            assertTrue("VIP activation should succeed with valid key", success)
            assertTrue(AdManager.isVipByKeyActive())
            
            // Deactivate VIP
            AdManager.clearVipByKey()
            VipPrefs(context).clearGrantedAtMs()
            VipPrefs(context).clearVipDays()
            assertFalse(AdManager.isVipByKeyActive())
        } finally {
            // Restore original config secret
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
        }
    }
}
