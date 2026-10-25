package com.mckimquyen.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for RApplication test-seams and environment detection.
 */
class RApplicationSeamUnitTest {

    @Test
    fun sDisableAutoAppRefresh_canBeToggled() {
        val original = RApplication.sDisableAutoAppRefresh
        try {
            RApplication.sDisableAutoAppRefresh = true
            assertTrue(RApplication.sDisableAutoAppRefresh)

            RApplication.sDisableAutoAppRefresh = false
            assertFalse(RApplication.sDisableAutoAppRefresh)
        } finally {
            RApplication.sDisableAutoAppRefresh = original
        }
    }

    @Test
    fun isTestEnvironment_returnsBooleanWithoutCrashing() {
        // Must never throw under plain JVM test runner
        val isTest = RApplication.isTestEnvironment()
        // Result is either true (if instrumentation registry present) or false
        assertTrue(isTest || !isTest)
    }

    @Test
    fun localeReceiver_getterIsExposed() {
        val app = RApplication()
        org.junit.Assert.assertNull(app.localeReceiverForTest)
    }
}
