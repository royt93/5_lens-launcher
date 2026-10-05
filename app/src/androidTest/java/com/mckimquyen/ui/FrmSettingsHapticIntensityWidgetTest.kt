package com.mckimquyen.ui

import android.widget.CompoundButton
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
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
        // M2: seed a different level first so checking idFor(level) is always a real change -
        // without this, levels that happen to equal the fragment's own MEDIUM default would
        // "pass" even if the check() call never persisted anything.
        for (level in HapticIntensity.entries) {
            val otherLevel = HapticIntensity.entries.first { it != level }
            UtilSettings(context).saveHapticIntensity(otherLevel)
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
        launch { _, group ->
            // M8: the group container itself (not just its children) must be enabled.
            assertTrue(group.isEnabled)
            assertTrue(group.children().all { it.isEnabled })
        }
    }

    @Test
    fun groupIsDisabledWhenBothHapticSwitchesAreOff() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        launch { _, group ->
            // M8: the group container itself (not just its children) must be disabled.
            assertFalse(group.isEnabled)
            assertTrue(group.children().none { it.isEnabled })
        }
    }

    @Test
    fun turningASwitchOn_reenablesTheGroupLive() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        launch { fragment, group ->
            assertFalse(group.isEnabled)
            assertTrue(group.children().none { it.isEnabled })
            fragment.requireView().findViewById<CompoundButton>(R.id.swVibrateAppHover).isChecked = true
            assertTrue(group.isEnabled)
            assertTrue(group.children().all { it.isEnabled })
            fragment.requireView().findViewById<CompoundButton>(R.id.swVibrateAppHover).isChecked = false
            assertFalse(group.isEnabled)
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

    /**
     * The three labels must show in full: a label ellipsized to "Lig..." tells the user nothing.
     * I2: the default English labels are not the worst case - Russian/French/Italian translations
     * run longer ("Сильная", "Moyenne", "Leggera"), so this inflates the real row's layout
     * (frm_settings.xml, not the full fragment) once per locale under a fixed fontScale=1.0
     * configuration and lays it out at the real device width.
     * #5 (re-review): `Locale.getDefault()` alone does not actually cover English - this device's
     * default locale is Vietnamese. `Locale.ENGLISH` is now listed explicitly; the device default
     * is kept as an extra entry alongside it.
     */
    @Test
    fun labelsAreNeverEllipsized() {
        val targetContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (locale in listOf(
            java.util.Locale.ENGLISH,
            java.util.Locale.getDefault(),
            java.util.Locale.forLanguageTag("ru"),
            java.util.Locale.forLanguageTag("fr"),
            java.util.Locale.forLanguageTag("it")
        )) {
            val config = android.content.res.Configuration(targetContext.resources.configuration).apply {
                setLocale(locale)
                fontScale = 1.0f
            }
            val localizedContext = targetContext.createConfigurationContext(config)
            val themedContext = android.view.ContextThemeWrapper(localizedContext, R.style.AppTheme_NoActionBar)
            val root = android.view.LayoutInflater.from(themedContext)
                .inflate(R.layout.frm_settings, null, false)
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    localizedContext.resources.displayMetrics.widthPixels, android.view.View.MeasureSpec.EXACTLY
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val group = root.findViewById<MaterialButtonToggleGroup>(R.id.groupHapticIntensity)
            for (level in HapticIntensity.entries) {
                val button = group.findViewById<MaterialButton>(idFor(level))
                val layout = requireNotNull(button.layout) { "locale $locale: button $level has no text layout" }
                var ellipsized = 0
                for (line in 0 until layout.lineCount) ellipsized += layout.getEllipsisCount(line)
                assertEquals("locale $locale: label '${button.text}' of $level is ellipsized", 0, ellipsized)
            }
        }
    }

    private fun MaterialButtonToggleGroup.children() = (0 until childCount).map { getChildAt(it) }

    // ---- I3: FrmSettings.onPreviewHapticPerformed proves a real tap previews the haptic, and
    // only a real tap - not reduced motion, not programmatic binding. ----

    private fun runShell(command: String): String {
        val pfd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        return java.io.BufferedReader(
            java.io.InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd))
        ).use { it.readText() }
    }

    /** Forces `LensPhysicsPolicy.shouldReduceLensMotion` true for the duration of [block]. */
    private fun withReducedMotionForced(block: () -> Unit) {
        val original = runShell("settings get global animator_duration_scale").trim()
        runShell("settings put global animator_duration_scale 0")
        try {
            block()
        } finally {
            runShell(
                "settings put global animator_duration_scale " +
                    if (original.isBlank() || original == "null") "1" else original
            )
        }
    }

    /**
     * I3 re-review finding #3: forces `LensPhysicsPolicy.shouldReduceLensMotion` false (the exact
     * inverse of [withReducedMotionForced]) so the positive preview-haptic tests are deterministic
     * on a device with battery saver / thermal throttling / animator scale already at 0, instead of
     * silently passing for the wrong reason.
     */
    private fun withNormalMotionForced(block: () -> Unit) {
        val original = runShell("settings get global animator_duration_scale").trim()
        runShell("settings put global animator_duration_scale 1")
        try {
            assertFalse(
                "test precondition: device must not be in reduced-motion mode",
                com.mckimquyen.util.LensPhysicsPolicy.shouldReduceLensMotion(context)
            )
            block()
        } finally {
            runShell(
                "settings put global animator_duration_scale " +
                    if (original.isBlank() || original == "null") "1" else original
            )
        }
    }

    @Test
    fun tappingAButton_firesThePreviewHapticCallback() = withNormalMotionForced {
        val performed = mutableListOf<Int>()
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.onPreviewHapticPerformed = { performed.add(it) }
                fragment.requireView().findViewById<android.view.View>(R.id.btnHapticStrong).performClick()
            }
        }
        assertEquals(listOf(HapticIntensity.STRONG.feedbackConstant), performed)
    }

    @Test
    fun tappingAButton_underReducedMotion_doesNotFireThePreviewHapticCallback() = withReducedMotionForced {
        val performed = mutableListOf<Int>()
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.onPreviewHapticPerformed = { performed.add(it) }
                fragment.requireView().findViewById<android.view.View>(R.id.btnHapticStrong).performClick()
            }
        }
        assertTrue("reduced motion must suppress the preview callback", performed.isEmpty())
    }

    @Test
    fun programmaticBinding_doesNotFireThePreviewHapticCallback() = withNormalMotionForced {
        UtilSettings(context).saveHapticIntensity(HapticIntensity.STRONG)
        val performed = mutableListOf<Int>()
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                // assignValues() already ran once during launch (above); exercise it again plus
                // onDefaultsReset(), both of which drive groupHapticIntensity.check() under the
                // bindingHapticGroup guard rather than through a real user tap.
                fragment.onPreviewHapticPerformed = { performed.add(it) }
                val settingsInterface = fragment as com.mckimquyen.itf.SettingsInterface
                settingsInterface.onValuesUpdated()
                settingsInterface.onDefaultsReset()
            }
        }
        assertTrue("programmatic binding must never fire the preview callback", performed.isEmpty())
    }
}
