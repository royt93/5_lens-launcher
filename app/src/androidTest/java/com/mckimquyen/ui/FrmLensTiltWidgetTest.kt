package com.mckimquyen.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmLensTiltWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun settingIsOffByDefaultAndShowsMotionSicknessWarning() {
        val settings = UtilSettings(context)
        settings.saveTiltParallax(false)
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            val view = requireNotNull(fragment.view)
            val toggle = view.findViewById<MaterialSwitch>(R.id.swLensTiltParallax)
            val warning = view.findViewById<android.widget.TextView>(R.id.tvLensTiltParallaxDesc)
            assertNotNull(toggle)
            assertNotNull(warning)
            assertFalse(toggle.isChecked)
            assertTrue(warning.text.toString().contains(context.getString(R.string.setting_tilt_parallax_desc)))
        }
        scenario.close()
    }

    @Test fun toggleWritesTheGlobalPreferenceAndSurvivesRecreation() {
        val settings = UtilSettings(context)
        settings.saveTiltParallax(false)
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { it.requireView().findViewById<MaterialSwitch>(R.id.swLensTiltParallax).performClick() }
        assertTrue(settings.getTiltParallax())
        scenario.recreate()
        scenario.onFragment { assertTrue(it.requireView().findViewById<MaterialSwitch>(R.id.swLensTiltParallax).isChecked) }
        scenario.close()
    }

    @Test fun toggleIsIncludedInTheResetEntriesAndHasOffDefault() {
        val settings = UtilSettings(context)
        val keys = com.mckimquyen.ui.settings.ResetEntries.lens(settings, LensWorkspace.DEFAULT_LENS_ID)
            .map { it.key }.toSet()
        assertTrue(UtilSettings.KEY_TILT_PARALLAX in keys)
        assertFalse(UtilSettings.DEFAULT_TILT_PARALLAX)
        settings.saveTiltParallax(true)
        com.mckimquyen.ui.settings.ResetApplier.apply(settings,
            com.mckimquyen.ui.settings.ResetEntries.lens(settings, LensWorkspace.DEFAULT_LENS_ID))
        assertFalse("reset returns the opt-in feature to its safe default", settings.getTiltParallax())
    }
}
