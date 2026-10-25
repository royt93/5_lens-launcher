package com.mckimquyen.ui

import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Clean-the-Lens and Show Search Bar dependency must be explicit in Settings. */
@RunWith(AndroidJUnit4::class)
class FrmSettingsCleanLensWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

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
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swCleanLensMode)
                assertNotNull("swCleanLensMode must exist in layout", sw)
                assertFalse(sw.isChecked)
            }
        }
    }

    @Test
    fun switchIsOn_whenSettingWasPreviouslySavedTrue() {
        UtilSettings(context).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                assertTrue(fragment.requireView().findViewById<SwitchCompat>(R.id.swCleanLensMode).isChecked)
            }
        }
    }

    @Test
    fun togglingTheSwitch_persistsTheNewValue() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.requireView().findViewById<SwitchCompat>(R.id.swCleanLensMode).isChecked = true
            }
        }
        assertTrue(UtilSettings(context).getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
    }

    @Test
    fun resetToDefaults_clearsTheSettingBackToOff() {
        UtilSettings(context).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                (fragment as com.mckimquyen.itf.SettingsInterface).onDefaultsReset()
                assertFalse(fragment.requireView().findViewById<SwitchCompat>(R.id.swCleanLensMode).isChecked)
            }
        }
        assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
    }

    @Test
    fun cleanModeDisablesSearchBarPreferenceAndShowsLocalizedExplanation() {
        UtilSettings(context).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        UtilSettings(context).save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val root = fragment.requireView()
                val searchSwitch = root.findViewById<SwitchCompat>(R.id.swShowSearchBar)
                assertFalse("Clean mode must disable the redundant search-bar preference", searchSwitch.isEnabled)
                assertTrue("the stored preference is not rewritten", searchSwitch.isChecked)
                val hint = root.findViewById<android.widget.TextView>(R.id.tvShowSearchBarDependencyHint)
                assertEquals(fragment.getString(R.string.setting_clean_lens_mode_hint), hint.text.toString())
                assertTrue("hint explains why the search-bar toggle is disabled", hint.isShown)
            }
        }
    }

    @Test
    fun turningCleanModeOffReenablesSearchBarSwitchAndHidesTheExplanation() {
        UtilSettings(context).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val root = fragment.requireView()
                val searchSwitch = root.findViewById<SwitchCompat>(R.id.swShowSearchBar)
                val hint = root.findViewById<android.view.View>(R.id.tvShowSearchBarDependencyHint)
                assertFalse(searchSwitch.isEnabled)
                root.findViewById<SwitchCompat>(R.id.swCleanLensMode).isChecked = false
                assertTrue(searchSwitch.isEnabled)
                assertFalse(hint.isShown)
            }
        }
        assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
    }

    @Test
    fun changingCleanModePublishesAppsEditedSoHomeCanRefresh() {
        val sentinel = Any()
        AppEventManager.notifyAppsEdited(sentinel)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.requireView().findViewById<SwitchCompat>(R.id.swCleanLensMode).isChecked = true
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue("Clean mode setting change must notify the live Home Activity", AppEventManager.appsEdited.value !== sentinel)
        }
    }
}
