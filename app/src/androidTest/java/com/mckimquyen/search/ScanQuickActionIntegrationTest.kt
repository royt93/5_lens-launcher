package com.mckimquyen.search

import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEARCH-008 real-device proof: resolveScan()'s PackageManager.resolveActivity() call is subject
 * to real API 30+ package-visibility (<queries> in AndroidManifest.xml) - Robolectric's shadow
 * PackageManager doesn't enforce that restriction, so only a real device proves the <queries>
 * entry actually works. Which branch resolves (direct scan vs. Play Store fallback) depends on
 * whether the designated device happens to have a compatible scanner app installed - both
 * branches are asserted to produce a valid, well-formed Intent either way.
 */
@RunWith(AndroidJUnit4::class)
class ScanQuickActionIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun clearPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun realPackageManager_resolvesToScanIntentOrPlayStoreFallback_neverNull() {
        val result = QuickActionEngine.resolveScan(context, "qr") as QuickAction.Action

        assertEquals("qr", result.label)
        val scannerAppInstalled = Intent("com.google.zxing.client.android.SCAN")
            .resolveActivity(context.packageManager) != null

        if (scannerAppInstalled) {
            assertEquals(
                "device has a scanner app - must resolve directly to the SCAN intent",
                "com.google.zxing.client.android.SCAN",
                result.intent.action
            )
        } else {
            assertEquals(
                "no scanner app on this device - must fall back to Play Store search",
                Intent.ACTION_VIEW,
                result.intent.action
            )
            assertEquals("market", result.intent.data?.scheme)
            assertTrue(result.intent.data.toString().contains("QR"))
        }
    }

    @Test
    fun disabledViaSetting_resolvesToNullEvenWithMatchingKeyword() {
        com.mckimquyen.util.UtilSettings(context)
            .save(com.mckimquyen.util.UtilSettings.KEY_QUICK_ACTION_SCAN, false)

        assertEquals(null, QuickActionEngine.resolve(context, "qr"))
    }
}
