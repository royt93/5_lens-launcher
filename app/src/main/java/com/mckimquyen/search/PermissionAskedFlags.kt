package com.mckimquyen.search

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.mckimquyen.util.UtilSettings

/**
 * SEARCH-015: the "already asked" flags only exist to stop re-prompting after a real denial. They used to be
 * set before the system dialog even appeared, so an interrupted request or a later grant+revoke hid the row
 * for good. They are now written from the permission result, and [resetAll] lets the user clear them.
 */
object PermissionAskedFlags {

    /**
     * The flags live in the default preferences file, which Android backs up, so a phone restored from a backup arrived with "already asked" set although
     * its user was never asked on it, and the row stayed hidden for good. A flag now only counts together with a marker written on THIS install, in
     * app_preferences.xml, the file both backup XMLs already exclude: after a restore the marker is missing and the stale flag is ignored.
     */
    private const val DEVICE_MARKER_PREFS = "app_preferences"
    private const val DEVICE_MARKER_PREFIX = "asked_here_"

    private fun markers(context: Context) =
        context.applicationContext.getSharedPreferences(DEVICE_MARKER_PREFS, Context.MODE_PRIVATE)

    /** Whether [key] was really set by a denial on this install. A flag without a local marker (restored from a backup) is not "asked". */
    @JvmStatic
    fun isAsked(context: Context, key: String): Boolean =
        UtilSettings(context).getBoolean(key) && markers(context).getBoolean(DEVICE_MARKER_PREFIX + key, false)

    /** Sets [key] and the local marker together; the single way a denial is recorded. */
    @JvmStatic
    fun markAsked(context: Context, key: String) {
        UtilSettings(context).save(key, true)
        markers(context).edit { putBoolean(DEVICE_MARKER_PREFIX + key, true) }
    }

    private fun clearMarker(context: Context, key: String) {
        markers(context).edit { remove(DEVICE_MARKER_PREFIX + key) }
    }

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
            clearMarker(context, key)
            return false
        }
        markAsked(context, key)
        return true
    }

    /** Clears every flag; returns whether at least one was set. */
    @JvmStatic
    fun resetAll(context: Context): Boolean {
        val settings = UtilSettings(context)
        val set = ALL_KEYS.filter { settings.getBoolean(it) }
        set.forEach { settings.save(it, false) }
        ALL_KEYS.forEach { clearMarker(context, it) }
        return set.isNotEmpty()
    }
}
