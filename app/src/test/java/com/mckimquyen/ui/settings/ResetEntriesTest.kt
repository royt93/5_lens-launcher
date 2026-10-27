package com.mckimquyen.ui.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ResetEntriesTest {
    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private val defaultLens = LensWorkspace.DEFAULT_LENS_ID
    private val workLens = "work"

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun `lens tab writes exactly its five keys using the active lens suffix`() {
        assertEquals(
            setOf("min_icon_size", "distortion_factor", "scale_factor", "animation_time", "lens_elastic_overscroll"),
            ResetEntries.lens(settings, defaultLens).map { it.key }.toSet()
        )
        assertEquals(
            setOf("min_icon_size_work", "distortion_factor_work", "scale_factor", "animation_time", "lens_elastic_overscroll"),
            ResetEntries.lens(settings, workLens).map { it.key }.toSet()
        )
    }

    @Test
    fun `apps tab writes exactly the sort type`() {
        assertEquals(setOf("sort_type_name"), ResetEntries.apps(settings).map { it.key }.toSet())
    }

    @Test
    fun `settings tab writes exactly the keys the old reset wrote`() {
        val expected = setOf(
            "vibrate_app_hover", "vibrate_app_launch", "haptic_intensity", "show_name_app_hover", "show_touch_selection",
            "show_focus_halo", "show_search_bar", "recent_apps_quick_panel_enabled", "keep_screen_on", "clean_lens_mode",
            "depth_of_field_blur", "smart_focus_bias_work", "quick_action_calculator_enabled", "quick_action_unit_enabled", "quick_action_timer_enabled",
            "quick_action_battery_enabled", "quick_action_settings_enabled", "search_hint_text", "show_new_tag_app",
            "show_notification_badges", "background_mode", "background_color", "show_touch_selection_color",
            "icon_pack_package_name", "icon_pack_label_name", "night_mode"
        )
        val entries = ResetEntries.settings(settings, workLens)

        assertEquals(expected, entries.map { it.key }.toSet())
        assertEquals("keys must be unique", entries.size, entries.map { it.key }.toSet().size)
    }

    @Test
    fun `a fresh install is already at every default so nothing would be previewed`() {
        val all = ResetEntries.lens(settings, defaultLens) + ResetEntries.apps(settings) + ResetEntries.settings(settings, defaultLens)

        val changed = all.filter { it.showInPreview && it.currentValue != it.defaultValue }.map { it.key }
        assertTrue("fresh prefs must match defaults but these differ: $changed", changed.isEmpty())
    }

    @Test
    fun `changed values show up as preview rows`() {
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.save(UtilSettings.KEY_SEARCH_HINT_TEXT, "Find")
        settings.save(UtilSettings.KEY_ANIMATION_TIME, 300L)

        val raw = PreferenceManager.getDefaultSharedPreferences(context).all
        val lensRows = ResetPlan.build(raw, ResetEntries.lens(settings, defaultLens)).rows.map { it.key }
        val settingsRows = ResetPlan.build(raw, ResetEntries.settings(settings, defaultLens)).rows.map { it.key }

        assertEquals(listOf("animation_time"), lensRows)
        assertEquals(listOf("vibrate_app_hover", "search_hint_text"), settingsRows)
    }

    @Test
    fun `applying every entry default leaves the app showing every default`() {
        // Make every previewed value differ from its default, apply the defaults, then read the values back.
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
        raw.edit().putBoolean("vibrate_app_hover", true).putBoolean("vibrate_app_launch", false)
            .putInt("haptic_intensity", 2).putString("search_hint_text", "Find").putLong("animation_time", 300L)
            .putFloat("scale_factor", 1.4f).putInt("night_mode", 2).putString("show_touch_selection_color", "#FF000000")
            .putString("icon_pack_package_name", "pack.a").putString("sort_type_name", "LABEL_DESCENDING").commit()

        val tabs = { ResetEntries.lens(settings, defaultLens) + ResetEntries.apps(settings) + ResetEntries.settings(settings, defaultLens) }
        assertTrue("setup must create something to reset", tabs().any { it.showInPreview && it.currentValue != it.defaultValue })

        ResetApplier.apply(settings, tabs())

        val stillChanged = tabs().filter { it.showInPreview && it.currentValue != it.defaultValue }.map { it.key }
        assertTrue("these still differ after reset: $stillChanged", stillChanged.isEmpty())
    }

    @Test
    fun `applying defaults writes every key of the entry list`() {
        val entries = ResetEntries.lens(settings, workLens) + ResetEntries.settings(settings, workLens)

        ResetApplier.apply(settings, entries)

        val stored = PreferenceManager.getDefaultSharedPreferences(context).all.keys
        val missing = entries.map { it.key }.filterNot { it in stored }
        assertTrue("reset must write these keys explicitly: $missing", missing.isEmpty())
    }
}
