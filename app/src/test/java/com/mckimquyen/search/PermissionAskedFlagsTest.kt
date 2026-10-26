package com.mckimquyen.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * SEARCH-015: "already asked" flags are recorded only after the system dialog returned a denial, and the user
 * can clear them again from Settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PermissionAskedFlagsTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val granted = intArrayOf(PackageManager.PERMISSION_GRANTED)
    private val denied = intArrayOf(PackageManager.PERMISSION_DENIED)
    private val contacts = arrayOf(Manifest.permission.READ_CONTACTS)

    @Before
    fun clean() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun flag(key: String) = UtilSettings(context).getBoolean(key)

    @Test
    fun `a denial result records the flag`() {
        assertTrue(
            PermissionAskedFlags.recordResult(
                context, UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, contacts, denied, Manifest.permission.READ_CONTACTS
            )
        )
        assertTrue(flag(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED))
    }

    @Test
    fun `a grant result records nothing`() {
        assertFalse(
            PermissionAskedFlags.recordResult(
                context, UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, contacts, granted, Manifest.permission.READ_CONTACTS
            )
        )
        assertFalse(flag(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED))
    }

    @Test
    fun `a grant clears a flag left by an earlier denial`() {
        UtilSettings(context).save(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, true)

        PermissionAskedFlags.recordResult(
            context, UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, contacts, granted, Manifest.permission.READ_CONTACTS
        )

        assertFalse(flag(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED))
    }

    @Test
    fun `an interrupted request with no results does not count as asked`() {
        assertFalse(
            PermissionAskedFlags.recordResult(
                context, UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, emptyArray(), IntArray(0), Manifest.permission.READ_CONTACTS
            )
        )
        assertFalse(flag(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED))
    }

    @Test
    fun `a result that does not mention the deciding permission is ignored`() {
        assertFalse(
            PermissionAskedFlags.recordResult(
                context, UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED,
                arrayOf(Manifest.permission.CAMERA), denied, Manifest.permission.READ_CONTACTS
            )
        )
        assertFalse(flag(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED))
    }

    @Test
    fun `wifi counts as denied when only coarse location was granted`() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val onlyCoarse = intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED)

        assertTrue(
            PermissionAskedFlags.recordResult(
                context, UtilSettings.KEY_WIFI_SSID_PERMISSION_REQUESTED, permissions, onlyCoarse,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
        assertTrue(flag(UtilSettings.KEY_WIFI_SSID_PERMISSION_REQUESTED))
    }

    @Test
    fun `reset clears all four flags and reports whether anything changed`() {
        val settings = UtilSettings(context)
        PermissionAskedFlags.ALL_KEYS.forEach { settings.save(it, true) }
        assertEquals(4, PermissionAskedFlags.ALL_KEYS.size)

        assertTrue(PermissionAskedFlags.resetAll(context))

        PermissionAskedFlags.ALL_KEYS.forEach { assertFalse("$it should be cleared", flag(it)) }
        assertFalse("nothing left to reset", PermissionAskedFlags.resetAll(context))
    }

    @Test
    fun `reset leaves unrelated settings alone`() {
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_QUICK_ACTION_FLASHLIGHT, false)
        settings.save(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, true)

        PermissionAskedFlags.resetAll(context)

        assertFalse(flag(UtilSettings.KEY_QUICK_ACTION_FLASHLIGHT))
    }

    @Test
    fun `deny then reset then ask again is possible for contacts`() {
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertTrue(ContactSearchEngine.shouldShowPermissionRequest(context, "contact Jane"))

        PermissionAskedFlags.recordResult(
            context, UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, contacts, denied, Manifest.permission.READ_CONTACTS
        )
        assertFalse("hidden after a denial", ContactSearchEngine.shouldShowPermissionRequest(context, "contact Jane"))

        PermissionAskedFlags.resetAll(context)
        assertTrue("offered again after reset", ContactSearchEngine.shouldShowPermissionRequest(context, "contact Jane"))
    }

    @Test
    fun `grant then revoke in system settings brings the contact row back`() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        PermissionAskedFlags.recordResult(
            context, UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, contacts, granted, Manifest.permission.READ_CONTACTS
        )

        Shadows.shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)

        assertTrue(ContactSearchEngine.shouldShowPermissionRequest(context, "contact Jane"))
    }
}
