package com.mckimquyen.ui.settings

import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.UtilNightModeUtil
import com.mckimquyen.util.UtilSettings

/** UI-034: turns a raw setting value into the short text shown in the reset preview. */
class ResetValueFormatter(
    private val on: String,
    private val off: String,
    private val default: String,
    private val ms: (Long) -> String
) {
    fun format(key: String, value: Any): String = when {
        key == UtilSettings.KEY_HAPTIC_INTENSITY && value is Int ->
            HapticIntensity.from(value).name.lowercase().replaceFirstChar { it.uppercase() }
        key == UtilSettings.KEY_NIGHT_MODE && value is Int -> UtilNightModeUtil.getNightModeDisplayName(value)
        value is Boolean -> if (value) on else off
        value is Long -> ms(value)
        value is String && value.isEmpty() -> default
        else -> value.toString()
    }
}
