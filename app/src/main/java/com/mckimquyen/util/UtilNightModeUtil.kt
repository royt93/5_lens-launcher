package com.mckimquyen.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.NightMode

/**
 * Utility for converting between night mode values and display names
 */
object UtilNightModeUtil {

    private const val DISPLAY_NAME_LIGHT = "Light"
    private const val DISPLAY_NAME_DARK = "Dark"
    private const val DISPLAY_NAME_AUTO = "Auto"
    private const val DISPLAY_NAME_FOLLOW_SYSTEM = "Follow System"

    @JvmStatic
    fun getNightModeDisplayName(@NightMode nightMode: Int): String = when (nightMode) {
        AppCompatDelegate.MODE_NIGHT_NO -> DISPLAY_NAME_LIGHT
        AppCompatDelegate.MODE_NIGHT_YES -> DISPLAY_NAME_DARK
        AppCompatDelegate.MODE_NIGHT_AUTO_TIME -> DISPLAY_NAME_AUTO
        else -> DISPLAY_NAME_FOLLOW_SYSTEM
    }

    @JvmStatic
    @NightMode
    fun getNightModeFromDisplayName(displayName: String?): Int = when (displayName) {
        DISPLAY_NAME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DISPLAY_NAME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        DISPLAY_NAME_AUTO -> AppCompatDelegate.MODE_NIGHT_AUTO_TIME
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
