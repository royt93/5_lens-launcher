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
