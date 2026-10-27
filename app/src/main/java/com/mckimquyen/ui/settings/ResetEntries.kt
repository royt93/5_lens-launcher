package com.mckimquyen.ui.settings

import com.mckimquyen.R
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.UtilSettings

/**
 * UI-034: every preference a tab's reset writes, as (key, label, default, current). The reset itself and the preview
 * both read these lists, so they cannot drift apart. [current] is the value the user sees now.
 */
object ResetEntries {
    fun lens(us: UtilSettings, lensId: String): List<ResetEntry> = listOf(
        ResetEntry(
            us.lensKey(UtilSettings.KEY_ICON_SIZE, lensId), R.string.setting_min_icon_size,
            us.autoDefaultIconSize, us.getIconSize(lensId)
        ),
        ResetEntry(
            us.lensKey(UtilSettings.KEY_DISTORTION_FACTOR, lensId), R.string.setting_distortion_factor,
            UtilSettings.DEFAULT_DISTORTION_FACTOR, us.getDistortionFactor(lensId)
        ),
        ResetEntry(
            UtilSettings.KEY_SCALE_FACTOR, R.string.setting_scale_factor,
            UtilSettings.DEFAULT_SCALE_FACTOR, us.getFloat(UtilSettings.KEY_SCALE_FACTOR)
        ),
        ResetEntry(
            UtilSettings.KEY_ANIMATION_TIME, R.string.setting_animation_time,
            UtilSettings.DEFAULT_ANIMATION_TIME, us.getLong(UtilSettings.KEY_ANIMATION_TIME)
        ),
        ResetEntry(
            UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL, R.string.setting_lens_elastic_overscroll,
            UtilSettings.DEFAULT_LENS_ELASTIC_OVERSCROLL, us.getLensElasticOverscroll()
        )
    )

    fun apps(us: UtilSettings): List<ResetEntry> = listOf(
        ResetEntry(
            UtilSettings.KEY_SORT_TYPE_NAME, R.string.setting_sort_apps,
            UtilSettings.DEFAULT_SORT_TYPE_ENUM.name, us.sortType.name
        )
    )

    fun settings(us: UtilSettings, lensId: String): List<ResetEntry> {
        fun flag(key: String, label: Int, default: Boolean) = ResetEntry(key, label, default, us.getBoolean(key))
        return listOf(
            flag(UtilSettings.KEY_VIBRATE_APP_HOVER, R.string.setting_vibrate_app_hover, UtilSettings.DEFAULT_VIBRATE_APP_HOVER),
            flag(UtilSettings.KEY_VIBRATE_APP_LAUNCH, R.string.setting_vibrate_app_launch, UtilSettings.DEFAULT_VIBRATE_APP_LAUNCH),
            ResetEntry(
                UtilSettings.KEY_HAPTIC_INTENSITY, R.string.setting_haptic_intensity,
                HapticIntensity.DEFAULT.ordinal, us.getHapticIntensity().ordinal
            ),
            flag(UtilSettings.KEY_SHOW_NAME_APP_HOVER, R.string.setting_show_name_app_hover, UtilSettings.DEFAULT_SHOW_NAME_APP_HOVER),
            flag(UtilSettings.KEY_SHOW_TOUCH_SELECTION, R.string.setting_show_touch_selection, UtilSettings.DEFAULT_SHOW_TOUCH_SELECTION),
            flag(UtilSettings.KEY_SHOW_FOCUS_HALO, R.string.setting_show_focus_halo, UtilSettings.DEFAULT_SHOW_FOCUS_HALO),
            flag(UtilSettings.KEY_SHOW_SEARCH_BAR, R.string.setting_show_search_bar, UtilSettings.DEFAULT_SHOW_SEARCH_BAR),
            flag(
                UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, R.string.setting_show_recent_apps_icon,
                UtilSettings.DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED
            ),
            flag(UtilSettings.KEY_KEEP_SCREEN_ON, R.string.setting_keep_screen_on, UtilSettings.DEFAULT_KEEP_SCREEN_ON),
            flag(UtilSettings.KEY_CLEAN_LENS_MODE, R.string.setting_clean_lens_mode, UtilSettings.DEFAULT_CLEAN_LENS_MODE),
            flag(UtilSettings.KEY_DEPTH_OF_FIELD, R.string.setting_depth_of_field, UtilSettings.DEFAULT_DEPTH_OF_FIELD),
            ResetEntry(
                us.lensKey(UtilSettings.KEY_SMART_FOCUS_BIAS, lensId), R.string.setting_smart_focus,
                UtilSettings.DEFAULT_SMART_FOCUS_BIAS, us.isSmartFocusBias(lensId)
            ),
            flag(UtilSettings.KEY_QUICK_ACTION_CALCULATOR, R.string.setting_quick_action_calculator, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED),
            flag(UtilSettings.KEY_QUICK_ACTION_UNIT, R.string.setting_quick_action_unit, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED),
            flag(UtilSettings.KEY_QUICK_ACTION_TIMER, R.string.setting_quick_action_timer, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED),
            flag(UtilSettings.KEY_QUICK_ACTION_BATTERY, R.string.setting_quick_action_battery, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED),
            flag(UtilSettings.KEY_QUICK_ACTION_SETTINGS, R.string.setting_quick_action_settings_shortcut, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED),
            ResetEntry(
                UtilSettings.KEY_SEARCH_HINT_TEXT, R.string.setting_search_hint_title,
                "", us.getString(UtilSettings.KEY_SEARCH_HINT_TEXT).orEmpty()
            ),
            flag(UtilSettings.KEY_SHOW_NEW_APP_TAG, R.string.setting_show_new_app_tag, UtilSettings.DEFAULT_SHOW_NEW_APP_TAG),
            flag(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, R.string.setting_show_notification_badges, UtilSettings.DEFAULT_SHOW_NOTIFICATION_BADGES),
            ResetEntry(
                UtilSettings.KEY_BACKGROUND_MODE, R.string.setting_background,
                UtilSettings.DEFAULT_BACKGROUND_MODE.name, us.backgroundMode.name
            ),
            ResetEntry(
                UtilSettings.KEY_BACKGROUND_COLOR, R.string.setting_background,
                UtilSettings.DEFAULT_BACKGROUND_COLOR, us.getString(UtilSettings.KEY_BACKGROUND_COLOR).orEmpty()
            ),
            ResetEntry(
                UtilSettings.KEY_HIGHLIGHT_COLOR, R.string.setting_highlight_color,
                UtilSettings.DEFAULT_HIGHLIGHT_COLOR, us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR).orEmpty()
            ),
            ResetEntry(
                UtilSettings.KEY_ICON_PACK_PACKAGE_NAME, R.string.setting_icon_pack,
                UtilSettings.DEFAULT_ICON_PACK_PACKAGE_NAME,
                us.storedIconPackPackageName().ifEmpty {
                    us.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME).orEmpty()
                        .takeUnless { it == UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME }
                        .orEmpty()
                }
            ),
            ResetEntry(
                UtilSettings.KEY_ICON_PACK_LABEL_NAME, R.string.setting_icon_pack,
                UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME, us.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME).orEmpty(),
                showInPreview = false
            ),
            ResetEntry(
                UtilSettings.KEY_NIGHT_MODE, R.string.setting_night_mode,
                UtilSettings.DEFAULT_NIGHT_MODE, us.nightMode
            )
        )
    }
}
