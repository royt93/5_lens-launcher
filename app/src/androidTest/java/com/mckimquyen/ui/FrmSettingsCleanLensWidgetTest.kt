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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clean the Lens widget proof: the switch reflects the persisted setting on init,
 * toggling persists the new value, and reset-to-defaults turns it back off.
 */
@RunWith(AndroidJUnit4::class)
class FrmSettingsCleanLensWidgetTest {

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
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swCleanLensMode)
                assertTrue(sw.isChecked)
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
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swCleanLensMode)
                assertFalse(sw.isChecked)
            }
        }
        assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
    }
}
