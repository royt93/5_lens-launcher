package com.mckimquyen.ui

import android.widget.CompoundButton
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButtonToggleGroup
import com.mckimquyen.R
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-017 widget proof for the haptic intensity row in the Settings tab. */
@RunWith(AndroidJUnit4::class)
class FrmSettingsHapticIntensityWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() { rawPrefs().edit().clear().commit() }

    @After
    fun tearDown() { rawPrefs().edit().clear().commit() }

    private fun idFor(level: HapticIntensity) = when (level) {
        HapticIntensity.LIGHT -> R.id.btnHapticLight
        HapticIntensity.MEDIUM -> R.id.btnHapticMedium
        HapticIntensity.STRONG -> R.id.btnHapticStrong
    }

    private fun launch(block: (FrmSettings, MaterialButtonToggleGroup) -> Unit) {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                block(fragment, fragment.requireView().findViewById(R.id.groupHapticIntensity))
            }
        }
    }

    @Test
    fun neverSaved_selectsMedium() {
        launch { _, group -> assertEquals(R.id.btnHapticMedium, group.checkedButtonId) }
    }

    @Test
    fun everySavedLevel_isReflected() {
        for (level in HapticIntensity.entries) {
            UtilSettings(context).saveHapticIntensity(level)
            launch { _, group -> assertEquals("level $level", idFor(level), group.checkedButtonId) }
        }
    }

    @Test
    fun pickingALevel_persistsIt() {
        for (level in HapticIntensity.entries) {
            launch { _, group -> group.check(idFor(level)) }
            assertEquals(level, UtilSettings(context).getHapticIntensity())
        }
    }

    @Test
    fun alwaysExactlyOneLevelSelected_evenWhenTheSelectedOneIsTappedAgain() {
        launch { _, group ->
            group.findViewById<android.view.View>(R.id.btnHapticMedium).performClick()
            assertEquals(R.id.btnHapticMedium, group.checkedButtonId)
        }
    }

    @Test
    fun groupIsEnabledWhenAtLeastOneHapticSwitchIsOn() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, true)
        launch { _, group -> assertTrue(group.children().all { it.isEnabled }) }
    }

    @Test
    fun groupIsDisabledWhenBothHapticSwitchesAreOff() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        launch { _, group -> assertTrue(group.children().none { it.isEnabled }) }
    }

    @Test
    fun turningASwitchOn_reenablesTheGroupLive() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        launch { fragment, group ->
            assertTrue(group.children().none { it.isEnabled })
            fragment.requireView().findViewById<CompoundButton>(R.id.swVibrateAppHover).isChecked = true
            assertTrue(group.children().all { it.isEnabled })
            fragment.requireView().findViewById<CompoundButton>(R.id.swVibrateAppHover).isChecked = false
            assertFalse(group.children().any { it.isEnabled })
        }
    }

    @Test
    fun resetToDefaults_returnsToMedium() {
        UtilSettings(context).saveHapticIntensity(HapticIntensity.STRONG)
        launch { fragment, group ->
            assertEquals(R.id.btnHapticStrong, group.checkedButtonId)
            (fragment as com.mckimquyen.itf.SettingsInterface).onDefaultsReset()
            assertEquals(R.id.btnHapticMedium, group.checkedButtonId)
        }
        assertEquals(HapticIntensity.DEFAULT, UtilSettings(context).getHapticIntensity())
    }

    @Test
    fun rowLabelsAreLocalizedStrings_notBlank() {
        launch { _, group ->
            for (level in HapticIntensity.entries) {
                val text = (group.findViewById<com.google.android.material.button.MaterialButton>(idFor(level))).text
                assertTrue("label for $level must not be blank", text.isNotBlank())
            }
        }
    }

    private fun MaterialButtonToggleGroup.children() = (0 until childCount).map { getChildAt(it) }
}
