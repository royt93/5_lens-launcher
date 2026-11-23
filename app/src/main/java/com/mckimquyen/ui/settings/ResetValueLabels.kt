package com.mckimquyen.ui.settings

import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.SortType
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.UtilSettings

/**
 * The words the Settings screen already uses for a few enum-valued settings, so the reset preview prints the same words instead of an enum
 * constant. Each lambda is given the decoded enum; [labelFor] returns null for any other key or a value that does not decode, and the caller
 * then keeps its own text.
 */
class ResetValueLabels(
    private val haptic: (HapticIntensity) -> String,
    private val sort: (SortType) -> String,
    private val background: (BackgroundMode) -> String
) {
    fun labelFor(key: String, value: Any): String? = when {
        key == UtilSettings.KEY_HAPTIC_INTENSITY && value is Int -> HapticIntensity.entries.getOrNull(value)?.let(haptic)
        key == UtilSettings.KEY_SORT_TYPE_NAME && value is String -> SortType.entries.firstOrNull { it.name == value }?.let(sort)
        key == UtilSettings.KEY_BACKGROUND_MODE && value is String -> BackgroundMode.entries.firstOrNull { it.name == value }?.let(background)
        else -> null
    }
}
