package com.mckimquyen.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.NightMode
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.mckimquyen.BuildConfig
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.SortType

/**
 * Utility class để quản lý SharedPreferences settings
 *
 * Fix: 4.4 - Sử dụng Application Context thay vì Activity Context để tránh memory leak
 */
class UtilSettings(context: Context) {
    // Sử dụng Application Context thay vì giữ Activity Context để tránh memory leak
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    companion object {
        const val DEFAULT_ICON_SIZE = 18.0f
        const val DEFAULT_DISTORTION_FACTOR = 2.5f
        const val DEFAULT_SCALE_FACTOR = 1.0f
        const val DEFAULT_ANIMATION_TIME: Long = 200
        const val DEFAULT_VIBRATE_APP_HOVER = false
        const val DEFAULT_VIBRATE_APP_LAUNCH = true
        const val DEFAULT_SHOW_NAME_APP_HOVER = true
        const val DEFAULT_SHOW_TOUCH_SELECTION = false
        const val DEFAULT_SHOW_NEW_APP_TAG = true
        const val DEFAULT_SHOW_SEARCH_BAR = true
        const val DEFAULT_BACKGROUND = "Wallpaper"
        const val DEFAULT_BACKGROUND_COLOR = "#FFF8BBD0"
        const val DEFAULT_HIGHLIGHT_COLOR = "#FFF50057"
        // PREF-001: a stable, never-displayed sentinel - not the translated "Default Icon Pack"
        // label - so switching device/app language can never desync it from what's persisted.
        const val DEFAULT_ICON_PACK_LABEL_NAME = "__default_icon_pack__"
        const val DEFAULT_SORT_TYPE = 0
        val DEFAULT_SORT_TYPE_ENUM: SortType = SortType.entries[DEFAULT_SORT_TYPE]
        val DEFAULT_BACKGROUND_MODE = BackgroundMode.WALLPAPER
        const val DEFAULT_NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_NO

        // These values are for the progress bars, their real values = (MAX_VALUE / INTERVAL (eg. 2)) + MIN_VALUE
        const val MAX_ICON_SIZE = 45
        const val MAX_DISTORTION_FACTOR = 9
        const val MAX_SCALE_FACTOR = 5
        const val MAX_ANIMATION_TIME = 600
        const val SHOW_NEW_APP_TAG_DURATION =
            12 * 60 * 60 * 1000 /* An app has the new tag for twelve hours. If openCount >= 1, the new tag is not drawn. */
        const val MIN_ICON_SIZE = 10.0f
        const val MIN_DISTORTION_FACTOR = 0.5f
        const val MIN_SCALE_FACTOR = 1.0f
        const val MIN_ANIMATION_TIME: Long = 100
        const val DEFAULT_FLOAT = Float.MIN_VALUE
        const val DEFAULT_LONG = Long.MIN_VALUE
        const val DEFAULT_BOOLEAN = false
        const val DEFAULT_STRING = ""
        const val KEY_ICON_SIZE = "min_icon_size"
        const val KEY_DISTORTION_FACTOR = "distortion_factor"
        const val KEY_SCALE_FACTOR = "scale_factor"
        const val KEY_ANIMATION_TIME = "animation_time"
        const val KEY_VIBRATE_APP_HOVER = "vibrate_app_hover"
        const val KEY_VIBRATE_APP_LAUNCH = "vibrate_app_launch"
        const val KEY_SHOW_NAME_APP_HOVER = "show_name_app_hover"
        const val KEY_SHOW_TOUCH_SELECTION = "show_touch_selection"
        const val KEY_SHOW_NEW_APP_TAG = "show_new_tag_app"
        const val KEY_SHOW_SEARCH_BAR = "show_search_bar"
        const val KEY_BACKGROUND = "background"
        const val KEY_BACKGROUND_COLOR = "background_color"
        const val KEY_HIGHLIGHT_COLOR = "show_touch_selection_color"
        const val KEY_ICON_PACK_LABEL_NAME = "icon_pack_label_name"
        const val KEY_SORT_TYPE = "sort_type"
        const val KEY_NIGHT_MODE = "night_mode"

        // PREF-001: stable, locale/reorder-independent keys. Store the enum's .name, not its
        // ordinal or a translated display string. Legacy KEY_SORT_TYPE/KEY_BACKGROUND values are
        // migrated on first read (see sortType/backgroundMode below) and never written again.
        const val KEY_SORT_TYPE_NAME = "sort_type_name"
        const val KEY_BACKGROUND_MODE = "background_mode"

        const val KEY_READ_POLICY = "KEY_READ_POLICY${BuildConfig.VERSION_CODE}"
    }

    fun save(name: String?, value: Int) {
        prefs.edit { putInt(name, value) }
    }

    fun save(name: String?, value: Float) {
        prefs.edit { putFloat(name, value) }
    }

    fun save(name: String?, value: Long) {
        prefs.edit { putLong(name, value) }
    }

    fun save(name: String?, value: String?) {
        prefs.edit { putString(name, value) }
    }

    fun save(name: String?, value: Boolean) {
        prefs.edit { putBoolean(name, value) }
    }

    fun save(value: SortType) {
        save(KEY_SORT_TYPE_NAME, value.name)
    }

    fun save(value: BackgroundMode) {
        save(KEY_BACKGROUND_MODE, value.name)
    }

    @get:NightMode
    val nightMode: Int
        get() = when (prefs.getInt(KEY_NIGHT_MODE, DEFAULT_NIGHT_MODE)) {
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

    fun getFloat(name: String?): Float = when (name) {
        KEY_ICON_SIZE -> getFloatWithValidation(name, DEFAULT_ICON_SIZE, MIN_ICON_SIZE, MAX_ICON_SIZE.toFloat() + MIN_ICON_SIZE)
        KEY_DISTORTION_FACTOR -> getFloatWithValidation(name, DEFAULT_DISTORTION_FACTOR, MIN_DISTORTION_FACTOR, MAX_DISTORTION_FACTOR / 2f + MIN_DISTORTION_FACTOR)
        KEY_SCALE_FACTOR -> getFloatWithValidation(name, DEFAULT_SCALE_FACTOR, MIN_SCALE_FACTOR, MAX_SCALE_FACTOR / 2f + MIN_SCALE_FACTOR)
        else -> prefs.getFloat(name, DEFAULT_FLOAT)
    }

    fun getLong(name: String): Long = when (name) {
        KEY_ANIMATION_TIME -> {
            val value = prefs.getLong(name, DEFAULT_ANIMATION_TIME)
            when {
                value < MIN_ANIMATION_TIME -> {
                    save(name, MIN_ANIMATION_TIME)
                    MIN_ANIMATION_TIME
                }
                value > MAX_ANIMATION_TIME / 2L + MIN_ANIMATION_TIME -> {
                    val maxValue = MAX_ANIMATION_TIME / 2L + MIN_ANIMATION_TIME
                    save(name, maxValue)
                    maxValue
                }
                else -> value
            }
        }
        else -> prefs.getLong(name, DEFAULT_LONG)
    }

    fun getString(name: String?): String? = when (name) {
        KEY_BACKGROUND -> prefs.getString(name, DEFAULT_BACKGROUND)
        KEY_BACKGROUND_COLOR -> prefs.getString(name, DEFAULT_BACKGROUND_COLOR)
        KEY_HIGHLIGHT_COLOR -> prefs.getString(name, DEFAULT_HIGHLIGHT_COLOR)
        KEY_ICON_PACK_LABEL_NAME -> prefs.getString(name, DEFAULT_ICON_PACK_LABEL_NAME)
        else -> prefs.getString(name, DEFAULT_STRING)
    }

    fun getBoolean(name: String?): Boolean = when (name) {
        KEY_VIBRATE_APP_HOVER -> prefs.getBoolean(name, DEFAULT_VIBRATE_APP_HOVER)
        KEY_VIBRATE_APP_LAUNCH -> prefs.getBoolean(name, DEFAULT_VIBRATE_APP_LAUNCH)
        KEY_SHOW_NAME_APP_HOVER -> prefs.getBoolean(name, DEFAULT_SHOW_NAME_APP_HOVER)
        KEY_SHOW_TOUCH_SELECTION -> prefs.getBoolean(name, DEFAULT_SHOW_TOUCH_SELECTION)
        KEY_SHOW_NEW_APP_TAG -> prefs.getBoolean(name, DEFAULT_SHOW_NEW_APP_TAG)
        KEY_SHOW_SEARCH_BAR -> prefs.getBoolean(name, DEFAULT_SHOW_SEARCH_BAR)
        else -> prefs.getBoolean(name, DEFAULT_BOOLEAN)
    }

    val sortType: SortType
        get() {
            val storedName = prefs.getString(KEY_SORT_TYPE_NAME, null)
            if (storedName != null) {
                return runCatching { SortType.valueOf(storedName) }.getOrDefault(DEFAULT_SORT_TYPE_ENUM)
            }
            // Legacy migration: old installs stored SortType.ordinal as a raw Int, which breaks
            // (wrong value, or ArrayIndexOutOfBounds) if the enum is ever reordered/resized.
            val legacyOrdinal = prefs.getInt(KEY_SORT_TYPE, DEFAULT_SORT_TYPE)
            val migrated = SortType.entries.getOrNull(legacyOrdinal) ?: DEFAULT_SORT_TYPE_ENUM
            save(migrated)
            return migrated
        }

    val backgroundMode: BackgroundMode
        get() {
            val storedName = prefs.getString(KEY_BACKGROUND_MODE, null)
            if (storedName != null) {
                return runCatching { BackgroundMode.valueOf(storedName) }.getOrDefault(DEFAULT_BACKGROUND_MODE)
            }
            // Legacy migration: old installs stored the dialog's displayed English item text
            // ("Wallpaper"/"Color") as the domain value itself - fragile the moment that text is
            // ever localized. Unknown/corrupt legacy values fall back to the default, never crash.
            val legacy = prefs.getString(KEY_BACKGROUND, null)
            val migrated = when (legacy) {
                "Color" -> BackgroundMode.COLOR
                "Wallpaper" -> BackgroundMode.WALLPAPER
                else -> DEFAULT_BACKGROUND_MODE
            }
            save(migrated)
            return migrated
        }

    private fun getFloatWithValidation(name: String?, defaultValue: Float, minValue: Float, maxValue: Float): Float {
        val value = prefs.getFloat(name, defaultValue)
        return when {
            value < minValue -> {
                save(name, minValue)
                minValue
            }
            value > maxValue -> {
                save(name, maxValue)
                maxValue
            }
            else -> value
        }
    }
}
