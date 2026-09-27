package com.mckimquyen.ui

import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-006 widget proof for default, persistence, and reset behavior. */
@RunWith(AndroidJUnit4::class)
class FrmSettingsSmartFocusWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() {
        rawPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
    }

    @Test
    fun switchIsOff_whenSettingHasNeverBeenSaved() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                assertFalse(fragment.requireView().findViewById<SwitchCompat>(R.id.swSmartFocus).isChecked)
            }
        }
    }

    @Test
    fun switchIsOn_whenSettingWasPreviouslySavedTrue() {
        UtilSettings(context).save(UtilSettings.KEY_SMART_FOCUS_BIAS, true)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                assertTrue(fragment.requireView().findViewById<SwitchCompat>(R.id.swSmartFocus).isChecked)
            }
        }
    }

    @Test
    fun togglingSwitch_persistsNewValue() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.requireView().findViewById<SwitchCompat>(R.id.swSmartFocus).isChecked = true
            }
        }
        assertTrue(UtilSettings(context).getBoolean(UtilSettings.KEY_SMART_FOCUS_BIAS))
    }

    // ---- FISH-008 Phase 3: the switch targets whichever lens Home is currently on ----

    @Test
    fun switchReflectsTheActiveLens_notTheGlobalValue() {
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, "work")
        settings.saveSmartFocusBias("work", true)
        // The shared/default value stays off - only the active lens is on.
        assertFalse(settings.isSmartFocusBias(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID))

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                assertTrue(
                    "The switch must show the active lens's own state",
                    fragment.requireView().findViewById<SwitchCompat>(R.id.swSmartFocus).isChecked
                )
            }
        }
    }

    @Test
    fun togglingSwitch_writesToTheActiveLensOnly() {
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, "work")

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.requireView().findViewById<SwitchCompat>(R.id.swSmartFocus).isChecked = true
            }
        }

        assertTrue(settings.isSmartFocusBias("work"))
        assertFalse(
            "Another lens must not be dragged along",
            settings.isSmartFocusBias("personal")
        )
        assertFalse(
            "The shared default must not be overwritten by a per-lens toggle",
            settings.isSmartFocusBias(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID)
        )
    }

    @Test
    fun resetToDefaults_turnsSmartFocusOff() {
        UtilSettings(context).save(UtilSettings.KEY_SMART_FOCUS_BIAS, true)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                (fragment as com.mckimquyen.itf.SettingsInterface).onDefaultsReset()
                assertFalse(fragment.requireView().findViewById<SwitchCompat>(R.id.swSmartFocus).isChecked)
            }
        }
        assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_SMART_FOCUS_BIAS))
    }
}
