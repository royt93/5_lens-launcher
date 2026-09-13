package com.mckimquyen.util

import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SEARCH-003: UtilApp.appInfoIntent/uninstallIntent are shared by AppAdapter's popup menu and
 * SearchResultAdapter's row action menu - one definition, tested once here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilAppActionIntentsTest {

    @Test
    fun `app info intent targets the correct package and settings screen`() {
        val intent = UtilApp.appInfoIntent("com.example.app")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:com.example.app", intent.data.toString())
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun `uninstall intent goes through the standard system confirmation, never a silent call`() {
        val intent = UtilApp.uninstallIntent("com.example.app")
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals("package:com.example.app", intent.data.toString())
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
