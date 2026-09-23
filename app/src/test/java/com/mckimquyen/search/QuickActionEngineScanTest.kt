package com.mckimquyen.search

import android.content.Intent
import androidx.preference.PreferenceManager
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SEARCH-008: resolveScan's branch logic (keyword match, scanner-resolves-vs-falls-back) is pure
 * given a known PackageManager resolution state, which Robolectric's real (shadowed) package
 * manager can simulate by registering/not registering an intent-filter - only the real device's
 * actually-installed-scanner-apps state needs a real device (see the androidTest-side
 * ScanQuickActionIntegrationTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class QuickActionEngineScanTest {

    private fun context() = RuntimeEnvironment.getApplication()

    private fun freshPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context()).edit().clear().commit()
    }

    @Test
    fun `non-matching query returns null`() {
        freshPrefs()
        assertNull(QuickActionEngine.resolveScan(context(), "facebook"))
    }

    @Test
    fun `no scanner app installed falls back to Play Store search`() {
        freshPrefs()
        // Fresh Robolectric shadow package manager has nothing registered for the SCAN action.
        val result = QuickActionEngine.resolveScan(context(), "qr") as QuickAction.Action

        assertEquals("qr", result.label)
        assertEquals(Intent.ACTION_VIEW, result.intent.action)
        assertEquals("market", result.intent.data?.scheme)
        assertTrue(result.intent.data?.getQueryParameter("q")?.contains("QR", ignoreCase = true) == true)
    }

    @Test
    fun `scanner app installed resolves directly to the SCAN intent`() {
        freshPrefs()
        val scanIntent = Intent("com.google.zxing.client.android.SCAN")
        val resolveInfo = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.google.zxing.client.android"
                name = "CaptureActivity"
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    packageName = "com.google.zxing.client.android"
                }
            }
        }
        shadowOf(context().packageManager).addResolveInfoForIntent(scanIntent, resolveInfo)

        val result = QuickActionEngine.resolveScan(context(), "barcode") as QuickAction.Action

        assertEquals("barcode", result.label)
        assertEquals("com.google.zxing.client.android.SCAN", result.intent.action)
    }

    @Test
    fun `vietnamese keyword without diacritics matches`() {
        freshPrefs()
        val result = QuickActionEngine.resolveScan(context(), "Quét Mã QR")
        assertTrue(result is QuickAction.Action)
    }

    @Test
    fun `disabled via settings toggle is gated at the resolve() level`() {
        freshPrefs()
        UtilSettings(context()).save(UtilSettings.KEY_QUICK_ACTION_SCAN, false)
        assertNull(QuickActionEngine.resolve(context(), "qr"))
    }
}
