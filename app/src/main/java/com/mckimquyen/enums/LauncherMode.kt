package com.mckimquyen.enums

import com.mckimquyen.R

/**
 * FEAT-003: Launcher mode selection.
 * FISHEYE: The classic interactive fisheye lens canvas.
 * LIST: Fully accessible, scrollable list mode optimized for TalkBack, keyboard, and large text.
 */
enum class LauncherMode(val displayNameResId: Int, val prefValue: String) {
    FISHEYE(R.string.launcher_mode_fisheye, "fisheye"),
    LIST(R.string.launcher_mode_list, "list");

    companion object {
        @JvmStatic
        fun fromPrefValue(value: String?): LauncherMode =
            entries.firstOrNull { it.prefValue.equals(value, ignoreCase = true) } ?: FISHEYE
    }
}
