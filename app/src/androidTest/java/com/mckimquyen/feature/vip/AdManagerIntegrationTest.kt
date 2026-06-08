package com.mckimquyen.feature.vip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roy.sdkadbmob.AdManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdManagerIntegrationTest {

    @Test
    fun testAdManagerInitializationContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Assert AdManager properties that should be set by RApplication's onCreate
        // Check if VIP flag works natively without crashing
        val isVip = AdManager.isVIPMember()
        val isVipByKey = AdManager.isVipByKeyActive()
        
        assertFalse(isVip)
        assertFalse(isVipByKey)
    }

    @Test
    fun testVipActivationLifecycle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Setup initial state
        com.mckimquyen.feature.vip.VipPrefs(context).clearGrantedAtMs()
        assertFalse(AdManager.isVipByKeyActive())
        
        // Activate VIP
        val success = AdManager.activateVipByKey(context, AdKeys.VIP_SECRET, 3)
        assertTrue("VIP activation should succeed with valid key", success)
        assertTrue(AdManager.isVipByKeyActive())
        
        // Deactivate VIP
        AdManager.clearVipByKey()
        com.mckimquyen.feature.vip.VipPrefs(context).clearGrantedAtMs()
        assertFalse(AdManager.isVipByKeyActive())
    }
}
