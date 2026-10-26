package com.mckimquyen.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RApplication
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEARCH-015: the real [ActHome] writes an "already asked" flag from the permission result, not when the
 * request is fired. Drives onRequestPermissionsResult directly with the request codes ActHome uses.
 */
@RunWith(AndroidJUnit4::class)
class ActHomePermissionResultFlagsIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val REQUEST_CODE_CAMERA = 1001
        const val REQUEST_CODE_WIFI_SSID = 1002
        const val REQUEST_CODE_CONTACTS = 1003
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings get() = UtilSettings(context)

    @Before
    fun setUp() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        PermissionFlagKeys.all.forEach { settings.save(it, false) }
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            PermissionFlagKeys.all.forEach(::remove)
        }.commit()
    }

    private fun deliver(code: Int, permission: Array<String>, result: IntArray) {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.onRequestPermissionsResult(code, permission, result) }
        }
    }

    @Test
    fun deniedContactsResultSetsTheFlagAndGrantedClearsIt() {
        deliver(REQUEST_CODE_CONTACTS, arrayOf(Manifest.permission.READ_CONTACTS), intArrayOf(PackageManager.PERMISSION_DENIED))
        assertTrue(settings.getBoolean(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED))

        deliver(REQUEST_CODE_CONTACTS, arrayOf(Manifest.permission.READ_CONTACTS), intArrayOf(PackageManager.PERMISSION_GRANTED))
        assertFalse(settings.getBoolean(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED))
    }

    @Test
    fun deniedCameraResultSetsTheFlag() {
        deliver(REQUEST_CODE_CAMERA, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_DENIED))
        assertTrue(settings.getBoolean(UtilSettings.KEY_FLASHLIGHT_PERMISSION_REQUESTED))
    }

    @Test
    fun wifiCoarseOnlyGrantCountsAsDenied() {
        deliver(
            REQUEST_CODE_WIFI_SSID,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED)
        )
        assertTrue(settings.getBoolean(UtilSettings.KEY_WIFI_SSID_PERMISSION_REQUESTED))
    }

    @Test
    fun interruptedRequestWithNoResultsLeavesTheFlagUnset() {
        deliver(REQUEST_CODE_CONTACTS, emptyArray(), IntArray(0))
        assertFalse(settings.getBoolean(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED))
    }

    private object PermissionFlagKeys {
        val all = listOf(
            UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED,
            UtilSettings.KEY_FLASHLIGHT_PERMISSION_REQUESTED,
            UtilSettings.KEY_WIFI_SSID_PERMISSION_REQUESTED
        )
    }
}
