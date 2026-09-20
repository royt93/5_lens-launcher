package com.mckimquyen.util

import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.ads.AdRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TestDeviceHelperTest {

    @Test
    fun testKnownTestDeviceIdsContainsTecnoBg6() {
        assertTrue(
            "Known test devices must include TECNO BG6 hashed ID",
            TestDeviceHelper.KNOWN_TEST_DEVICE_IDS.contains("6B822442D4E755EB60D4E3943510317A")
        )
    }

    @Test
    fun testKnownTestDeviceIdsContainsEmulator() {
        assertTrue(
            "Known test devices must include Emulator ID",
            TestDeviceHelper.KNOWN_TEST_DEVICE_IDS.contains(AdRequest.DEVICE_ID_EMULATOR)
        )
    }

    @Test
    fun testHashMd5Upper() {
        // Known MD5 test vector
        // MD5 of "f822d00b0f755713" -> "6B822442D4E755EB60D4E3943510317A"
        val hashed = TestDeviceHelper.hashMd5Upper("f822d00b0f755713")
        assertEquals("6B822442D4E755EB60D4E3943510317A", hashed)

        // Empty string handling
        assertEquals("", TestDeviceHelper.hashMd5Upper(""))
    }

    @Test
    fun testGetAllTestDeviceIds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val allIds = TestDeviceHelper.getAllTestDeviceIds(context)

        assertNotNull(allIds)
        assertTrue("Test device IDs list should not be empty", allIds.isNotEmpty())
        assertTrue(allIds.contains("6B822442D4E755EB60D4E3943510317A"))
    }

    @Test
    fun testConfigureTestDevicesDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Should execute cleanly without exceptions
        TestDeviceHelper.configureTestDevices(context)
    }
}
