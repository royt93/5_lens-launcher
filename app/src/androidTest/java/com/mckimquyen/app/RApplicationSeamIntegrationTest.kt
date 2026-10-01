package com.mckimquyen.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test proving RApplication.isTestEnvironment() returns true on a real Android
 * device under AndroidJUnit4 runner.
 */
@RunWith(AndroidJUnit4::class)
class RApplicationSeamIntegrationTest {

    @Test
    fun isTestEnvironment_returnsTrueUnderAndroidJUnitRunner() {
        assertTrue(
            "isTestEnvironment() must detect instrumentation runner when executing on device",
            RApplication.isTestEnvironment()
        )
    }

    @Test
    fun sDisableAutoAppRefresh_canBeToggledAtRuntime() {
        val original = RApplication.sDisableAutoAppRefresh
        try {
            RApplication.sDisableAutoAppRefresh = true
            assertTrue("can set to true", RApplication.sDisableAutoAppRefresh)

            RApplication.sDisableAutoAppRefresh = false
            assertFalse("can set to false", RApplication.sDisableAutoAppRefresh)
        } finally {
            RApplication.sDisableAutoAppRefresh = original
        }
    }
}
