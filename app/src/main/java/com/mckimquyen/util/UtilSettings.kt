package com.mckimquyen.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.NightMode
import androidx.preference.PreferenceManager
import com.mckimquyen.BuildConfig
import com.mckimquyen.enums.SortType

/**
 * Utility class để quản lý SharedPreferences settings
 *
 * Fix: 4.4 - Sử dụng Application Context thay vì Activity Context để tránh memory leak
 */
class UtilSettings(context: Context) {
    // Sử dụng Application Context thay vì giữ Activity Context để tránh memory leak
    private val mContext: Context = context.applicationContext

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
        const val DEFAULT_BACKGROUND = "Wallpaper"
        const val DEFAULT_BACKGROUND_COLOR = "#FFF8BBD0"
        const val DEFAULT_HIGHLIGHT_COLOR = "#FFF50057"
        const val DEFAULT_ICON_PACK_LABEL_NAME = "Default Icon Pack"
        const val DEFAULT_SORT_TYPE = 0
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
        const val KEY_BACKGROUND = "background"
        const val KEY_BACKGROUND_COLOR = "background_color"
        const val KEY_HIGHLIGHT_COLOR = "show_touch_selection_color"
        const val KEY_ICON_PACK_LABEL_NAME = "icon_pack_label_name"
        const val KEY_SORT_TYPE = "sort_type"
        const val KEY_NIGHT_MODE = "night_mode"

        const val KEY_READ_POLICY = "KEY_READ_POLICY${BuildConfig.VERSION_CODE}"
    }

    private var mPrefs: SharedPreferences? = null
    private fun sharedPreferences(): SharedPreferences {
        if (mPrefs == null) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext)
        }
        return mPrefs!!
    }

    fun save(
        name: String?, value: Int,
    ) {
        sharedPreferences().edit().putInt(name, value).apply()
    }

    @get:NightMode
    val nightMode: Int
        get() = when (sharedPreferences().getInt(
            KEY_NIGHT_MODE, DEFAULT_NIGHT_MODE
        )) {
            AppCompatDelegate.MODE_NIGHT_AUTO -> AppCompatDelegate.MODE_NIGHT_AUTO
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

    fun save(name: String?, value: Float) {
        sharedPreferences().edit().putFloat(name, value).apply()
    }

    fun getFloat(name: String?): Float {
        return when (name) {
            KEY_ICON_SIZE -> {
                if (sharedPreferences().getFloat(
                        name,
                        DEFAULT_ICON_SIZE
                    ) < MIN_ICON_SIZE
                ) {
                    save(
                        name = name, value = MIN_ICON_SIZE
                    )
                } else if (sharedPreferences().getFloat(
                        name,
                        DEFAULT_ICON_SIZE
                    ) > getMaxFloatValue(name)
                ) {
                    save(
                        name = name, value = getMaxFloatValue(name)
                    )
                }
                sharedPreferences().getFloat(name, DEFAULT_ICON_SIZE)
            }

            KEY_DISTORTION_FACTOR -> {
                if (sharedPreferences().getFloat(
                        name,
                        DEFAULT_DISTORTION_FACTOR
                    ) < MIN_DISTORTION_FACTOR
                ) {
                    save(
                        name = name, value = MIN_DISTORTION_FACTOR
                    )
                } else if (sharedPreferences().getFloat(
                        name,
                        DEFAULT_DISTORTION_FACTOR
                    ) > getMaxFloatValue(name)
                ) {
                    save(
                        name = name, value = getMaxFloatValue(name)
                    )
                }
                sharedPreferences().getFloat(
                    name,
                    DEFAULT_DISTORTION_FACTOR
                )
            }

            KEY_SCALE_FACTOR -> {
                if (sharedPreferences().getFloat(
                        name,
                        DEFAULT_SCALE_FACTOR
                    ) < MIN_SCALE_FACTOR
                ) {
                    save(
                        name = name, value = MIN_SCALE_FACTOR
                    )
                } else if (sharedPreferences().getFloat(
                        name,
                        DEFAULT_SCALE_FACTOR
                    ) > getMaxFloatValue(name)
                ) {
                    save(
                        name = name, value = getMaxFloatValue(name)
                    )
                }
                sharedPreferences().getFloat(
                    name,
                    DEFAULT_SCALE_FACTOR
                )
            }

            else -> sharedPreferences().getFloat(
                name,
                DEFAULT_FLOAT
            )
        }
    }

    fun save(
        name: String?, value: Long,
    ) {
        sharedPreferences().edit().putLong(name, value).apply()
    }

    fun getLong(name: String): Long {
        return if (name == KEY_ANIMATION_TIME) {
            if (sharedPreferences().getLong(
                    name,
                    DEFAULT_ANIMATION_TIME
                ) < MIN_ANIMATION_TIME
            ) {
                save(
                    name = name, value = MIN_ANIMATION_TIME
                )
            } else if (sharedPreferences().getLong(
                    name,
                    DEFAULT_ANIMATION_TIME
                ) > getMaxLongValue(name)
            ) {
                save(
                    name = name, value = getMaxLongValue(name)
                )
            }
            sharedPreferences().getLong(
                name,
                DEFAULT_ANIMATION_TIME
            )
        } else {
            sharedPreferences().getLong(
                name,
                DEFAULT_LONG
            )
        }
    }

    fun save(
        name: String?, value: String?,
    ) {
        sharedPreferences().edit().putString(name, value).apply()
    }

    fun getString(name: String?): String? {
        return when (name) {
            KEY_BACKGROUND -> sharedPreferences().getString(
                name,
                DEFAULT_BACKGROUND
            )

            KEY_BACKGROUND_COLOR -> sharedPreferences().getString(
                name,
                DEFAULT_BACKGROUND_COLOR
            )

            KEY_HIGHLIGHT_COLOR -> sharedPreferences().getString(
                name,
                DEFAULT_HIGHLIGHT_COLOR
            )

            KEY_ICON_PACK_LABEL_NAME -> sharedPreferences().getString(
                name,
                DEFAULT_ICON_PACK_LABEL_NAME
            )

            else -> sharedPreferences().getString(
                name,
                DEFAULT_STRING
            )
        }
    }

    fun save(name: String?, value: Boolean) {
        sharedPreferences().edit().putBoolean(name, value).apply()
    }

    fun getBoolean(name: String?): Boolean {
        return when (name) {
            KEY_VIBRATE_APP_HOVER -> sharedPreferences().getBoolean(
                name,
                DEFAULT_VIBRATE_APP_HOVER
            )

            KEY_VIBRATE_APP_LAUNCH -> sharedPreferences().getBoolean(
                name,
                DEFAULT_VIBRATE_APP_LAUNCH
            )

            KEY_SHOW_NAME_APP_HOVER -> sharedPreferences().getBoolean(
                name,
                DEFAULT_SHOW_NAME_APP_HOVER
            )

            KEY_SHOW_TOUCH_SELECTION -> sharedPreferences().getBoolean(
                name,
                DEFAULT_SHOW_TOUCH_SELECTION
            )

            KEY_SHOW_NEW_APP_TAG -> sharedPreferences().getBoolean(
                name,
                DEFAULT_SHOW_NEW_APP_TAG
            )

            else -> sharedPreferences().getBoolean(
                name,
                DEFAULT_BOOLEAN
            )
        }
    }

    fun save(value: SortType) {
        save(
            name = KEY_SORT_TYPE, value = value.ordinal
        )
    }

    val sortType: SortType
        get() {
            val ordinal = sharedPreferences().getInt(
                KEY_SORT_TYPE,
                DEFAULT_SORT_TYPE
            )
            return SortType.values()[ordinal]
        }

    private fun getMaxFloatValue(name: String?): Float {
        return when (name) {
            KEY_ICON_SIZE -> MAX_ICON_SIZE.toFloat() + MIN_ICON_SIZE
            KEY_DISTORTION_FACTOR -> MAX_DISTORTION_FACTOR.toFloat() / 2 + MIN_DISTORTION_FACTOR
            KEY_SCALE_FACTOR -> MAX_SCALE_FACTOR.toFloat() / 2 + MIN_SCALE_FACTOR
            else -> DEFAULT_FLOAT
        }
    }

    private fun getMaxLongValue(name: String): Long {
        return if (name == KEY_ANIMATION_TIME) {
            MAX_ANIMATION_TIME.toLong() / 2 + MIN_ANIMATION_TIME
        } else {
            DEFAULT_LONG
        }
    }
}
