package com.mckimquyen.launcher

import androidx.preference.PreferenceManager
import com.mckimquyen.R
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * FEAT-003: Unit tests verifying launcher mode domain logic,
 * preference round-tripping, and accessibility suggestion conditions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LauncherModeTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun testLauncherModeEnum_fromPrefValue_mapsCorrectly() {
        assertEquals(LauncherMode.FISHEYE, LauncherMode.fromPrefValue("fisheye"))
        assertEquals(LauncherMode.FISHEYE, LauncherMode.fromPrefValue("FISHEYE"))
        assertEquals(LauncherMode.LIST, LauncherMode.fromPrefValue("list"))
        assertEquals(LauncherMode.LIST, LauncherMode.fromPrefValue("LIST"))
        assertEquals(LauncherMode.FISHEYE, LauncherMode.fromPrefValue(null))
        assertEquals(LauncherMode.FISHEYE, LauncherMode.fromPrefValue("invalid_mode"))
    }

    @Test
    fun testLauncherModeEnum_resourceIds_areValid() {
        assertEquals(R.string.launcher_mode_fisheye, LauncherMode.FISHEYE.displayNameResId)
        assertEquals(R.string.launcher_mode_list, LauncherMode.LIST.displayNameResId)
    }

    @Test
    fun testDefaultLauncherMode_isFisheye() {
        val settings = freshSettings()
        assertEquals(LauncherMode.FISHEYE, settings.getLauncherMode())
        assertFalse(settings.isListMode())
    }

    @Test
    fun testSetLauncherMode_toAccessibleList_persistsAndUpdatesState() {
        val settings = freshSettings()
        settings.setLauncherMode(LauncherMode.LIST)

        assertEquals(LauncherMode.LIST, settings.getLauncherMode())
        assertTrue(settings.isListMode())
    }

    @Test
    fun testSwitchingBetweenModes_roundTripsCorrectly() {
        val settings = freshSettings()

        settings.setLauncherMode(LauncherMode.LIST)
        assertTrue(settings.isListMode())

        settings.setLauncherMode(LauncherMode.FISHEYE)
        assertFalse(settings.isListMode())
        assertEquals(LauncherMode.FISHEYE, settings.getLauncherMode())
    }

    @Test
    fun testA11ySuggestion_dismissalState_persists() {
        val settings = freshSettings()
        assertFalse(settings.isA11ySuggestionDismissed())

        settings.dismissA11ySuggestion()
        assertTrue(settings.isA11ySuggestionDismissed())
    }

    @Test
    fun testShouldSuggestAccessibleListMode_whenAlreadyInListMode_returnsFalse() {
        val settings = freshSettings()
        settings.setLauncherMode(LauncherMode.LIST)

        // When user is already in List mode, suggestion must not trigger
        assertFalse(settings.shouldSuggestAccessibleListMode())
    }

    @Test
    fun testShouldSuggestAccessibleListMode_whenDismissed_returnsFalse() {
        val settings = freshSettings()
        settings.setLauncherMode(LauncherMode.FISHEYE)
        settings.dismissA11ySuggestion()

        // When dismissed, suggestion must not trigger even if a11y is on
        assertFalse(settings.shouldSuggestAccessibleListMode())
    }
}
