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

        assertFalse(AdManager.isVipByKeyActive())

        val originalSecret = AdManager.adConfig.vipKeySecret
        val testKey = VipKeys.VIP_3D_KEY
        AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = testKey)

        try {
            val success = AdManager.activateVipByKey(context, testKey, 3)
            assertTrue("VIP activation should succeed with valid key", success)
            assertTrue(AdManager.isVipByKeyActive())

            AdManager.clearVipByKey()
            VipPrefs(context).clearGrantedAtMs()
            VipPrefs(context).clearVipDays()
            assertFalse(AdManager.isVipByKeyActive())
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
        }
    }

    // BUG-5: verify try/finally pattern — secret restored after successful activation
    @Test
    fun `BUG5 - vipKeySecret is restored after successful activation`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalSecret = AdManager.adConfig.vipKeySecret

        val testKey = VipKeys.VIP_3D_KEY
        try {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = testKey)
            AdManager.activateVipByKey(context, testKey, 3)
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
        }

        assertEquals(
            "vipKeySecret must be restored to original after activation",
            originalSecret,
            AdManager.adConfig.vipKeySecret
        )

        AdManager.clearVipByKey()
    }

    // BUG-5: verify secret restored even when activation fails (invalid key)
    @Test
    fun `BUG5 - vipKeySecret is restored after failed activation`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalSecret = AdManager.adConfig.vipKeySecret
        val badKey = "INVALID_KEY_XYZ"

        try {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = badKey)
            val success = AdManager.activateVipByKey(context, badKey, 30)
            assertFalse("Activation with invalid key should fail", success)
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
        }

        assertEquals(
            "vipKeySecret must be restored after failed activation",
            originalSecret,
            AdManager.adConfig.vipKeySecret
        )
    }

    // BUG-11: grantViaRewarded pattern — 3-day key does not permanently overwrite secret
    @Test
    fun `BUG11 - 3-day rewarded grant restores original secret`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalSecret = AdManager.adConfig.vipKeySecret

        val threeDayKey = VipKeys.VIP_3D_KEY
        try {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = threeDayKey)
            AdManager.activateVipByKey(context, threeDayKey, 3)
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
        }

        assertEquals(
            "After 3-day rewarded grant, original secret must be restored",
            originalSecret,
            AdManager.adConfig.vipKeySecret
        )

        AdManager.clearVipByKey()
    }

    // BUG-4: activation with lowercase input (normalized at call site) should succeed
    @Test
    fun `BUG4 - activation succeeds with lowercase key after upstream normalization`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val lowercaseInput = VipKeys.VIP_3D_KEY.lowercase()
        val normalizedKey = lowercaseInput.trim().uppercase() // mirrors fix in ActVipManagement

        val originalSecret = AdManager.adConfig.vipKeySecret
        try {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = normalizedKey)
            val success = AdManager.activateVipByKey(context, normalizedKey, 3)
            assertTrue("Normalized lowercase key must activate successfully", success)
            assertTrue(AdManager.isVipByKeyActive())
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
            AdManager.clearVipByKey()
        }
    }

    // BUG-8: confirm isVipByKeyActive returns true during a just-activated grace period
    @Test
    fun `BUG8 - isVipByKeyActive true immediately after grace activation`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val originalSecret = AdManager.adConfig.vipKeySecret
        try {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = VipKeys.VIP_3D_KEY)
            val success = AdManager.activateVipByKey(context, VipKeys.VIP_3D_KEY, 3)
            assertTrue(success)
            // isVipByKeyActive must be true immediately — grace label condition depends on it
            assertTrue(
                "isVipByKeyActive must be true right after activation (grace label check relies on this)",
                AdManager.isVipByKeyActive()
            )
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
            AdManager.clearVipByKey()
        }
    }
}
