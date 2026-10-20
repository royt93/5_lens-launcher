package com.mckimquyen.util

import androidx.core.graphics.toColorInt

/**
 * UI-026: the color prefs (`background_color`, `show_touch_selection_color`) are plain strings that
 * can come from an imported backup or a hand-edited file. `Color.parseColor`, `toColorInt` and
 * `substring(3)` all throw on an empty or malformed value, and the callers run in `onResume` and
 * `onDraw`, so one bad value used to crash the launcher on every start with no way to recover
 * inside the app. Every read of those prefs goes through here and degrades to a default.
 */
object SafeColor {
    private const val RGB_LENGTH = 7 // "#RRGGBB"
    private const val ARGB_LENGTH = 9 // "#AARRGGBB"
    private const val ALPHA_PREFIX_LENGTH = 3 // "#AA"

    /** The color for [hex], or [fallback] when it is null, blank or not a valid `#RRGGBB`/`#AARRGGBB`. */
    @JvmStatic
    fun parse(hex: String?, fallback: Int): Int = parseOrNull(hex) ?: fallback

    @JvmStatic
    fun isValid(hex: String?): Boolean = parseOrNull(hex) != null

    /**
     * Text shown in Settings: `#AARRGGBB` is shown without its alpha pair, as before; `#RRGGBB`
     * is shown as stored. An invalid value shows [fallbackHex] so the screen still renders.
     */
    @JvmStatic
    fun displayHex(hex: String?, fallbackHex: String): String {
        if (!isValid(hex)) return fallbackHex
        val value = requireNotNull(hex)
        return if (value.length == ARGB_LENGTH) "#${value.substring(ALPHA_PREFIX_LENGTH)}" else value
    }

    private fun parseOrNull(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        if (hex.length != RGB_LENGTH && hex.length != ARGB_LENGTH) return null
        if (hex[0] != '#') return null
        return try {
            hex.toColorInt()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
