package com.mckimquyen.util

import android.content.Context

class LensLockStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLocked(lensId: String): Boolean =
        runCatching { prefs.getBoolean(key(lensId), false) }.getOrDefault(false)

    fun saveLocked(lensId: String, locked: Boolean) {
        prefs.edit().putBoolean(key(lensId), locked).apply()
    }

    fun lockedLensIds(validLensIds: Set<String>): Set<String> =
        validLensIds.filterTo(mutableSetOf(), ::isLocked)

    fun delete(lensId: String) {
        prefs.edit().remove(key(lensId)).apply()
    }

    private fun key(lensId: String) = "$LOCKED_KEY_PREFIX$lensId"

    private companion object {
        const val PREFS_NAME = "lens_access_prefs"
        const val LOCKED_KEY_PREFIX = "lens_locked_"
    }
}
