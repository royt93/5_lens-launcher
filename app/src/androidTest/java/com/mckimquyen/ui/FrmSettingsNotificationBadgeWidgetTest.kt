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

@RunWith(AndroidJUnit4::class)
class FrmSettingsNotificationBadgeWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() { rawPrefs().edit().clear().commit() }

    @After
    fun tearDown() { rawPrefs().edit().clear().commit() }

    @Test
    fun switchIsOn_whenSettingHasNeverBeenSaved() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swShowNotificationBadges)
                assertTrue(sw.isChecked)
            }
        }
    }

    @Test
    fun switchIsOff_whenSettingWasPreviouslySavedFalse() {
        UtilSettings(context).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, false)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swShowNotificationBadges)
                assertFalse(sw.isChecked)
            }
        }
    }

    @Test
    fun togglingTheSwitch_persistsTheNewValue() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swShowNotificationBadges)
                sw.isChecked = false
            }
        }
        assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES))
    }

    @Test
    fun resetToDefaults_turnsItBackOn() {
        UtilSettings(context).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, false)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                (fragment as com.mckimquyen.itf.SettingsInterface).onDefaultsReset()
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swShowNotificationBadges)
                assertTrue(sw.isChecked)
            }
        }
        assertTrue(UtilSettings(context).getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES))
    }
}
