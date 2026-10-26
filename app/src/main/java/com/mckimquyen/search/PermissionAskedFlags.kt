package com.mckimquyen.search

import android.content.Context
import android.content.pm.PackageManager
import com.mckimquyen.util.UtilSettings

/**
 * SEARCH-015: the "already asked" flags only exist to stop re-prompting after a real denial. They used to be
 * set before the system dialog even appeared, so an interrupted request or a later grant+revoke hid the row
 * for good. They are now written from the permission result, and [resetAll] lets the user clear them.
 */
object PermissionAskedFlags {

    /** Every flag that hides a quick-action permission row once set. DND is excluded: it never hides its row. */
    val ALL_KEYS = listOf(
        UtilSettings.KEY_FLASHLIGHT_PERMISSION_REQUESTED,
        UtilSettings.KEY_WIFI_SSID_PERMISSION_REQUESTED,
        UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED,
        UtilSettings.KEY_DND_PERMISSION_REQUESTED
    )

    /**
     * Applies a `onRequestPermissionsResult` outcome to [key]. [deciding] is the permission whose state counts
     * (for Wi-Fi that is FINE location: a coarse-only grant still cannot read the SSID). Returns true when the
     * flag was set, i.e. the user really declined.
     */
    @JvmStatic
    fun recordResult(
        context: Context,
        key: String,
        permissions: Array<out String>,
        grantResults: IntArray,
        deciding: String
    ): Boolean {
        val index = permissions.indexOf(deciding)
        if (index < 0 || index >= grantResults.size) return false
        val settings = UtilSettings(context)
        val granted = grantResults[index] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (settings.getBoolean(key)) settings.save(key, false)
            return false
        }
        settings.save(key, true)
        return true
    }

    /** Clears every flag; returns whether at least one was set. */
    @JvmStatic
    fun resetAll(context: Context): Boolean {
        val settings = UtilSettings(context)
        val set = ALL_KEYS.filter { settings.getBoolean(it) }
        set.forEach { settings.save(it, false) }
        return set.isNotEmpty()
    }
}
