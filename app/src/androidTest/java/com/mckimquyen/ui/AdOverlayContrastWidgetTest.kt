package com.mckimquyen.ui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-027 on a real device: the open-app ad overlay draws its spinner and disclosure text over `?attr/colorPrimary`. Measures the
 * colours that are actually painted, in light, in night, and with whatever dynamic-colour palette the device resolved.
 */
@RunWith(AndroidJUnit4::class)
class AdOverlayContrastWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)
    private var hadNightKey = false
    private var originalNight = 0
    private var hadReadPolicy = false
    private var hadReadPolicyKey = false

    @Before fun setUp() {
        hadNightKey = prefs.contains(UtilSettings.KEY_NIGHT_MODE)
        originalNight = prefs.getInt(UtilSettings.KEY_NIGHT_MODE, 0)
        // A fresh install shows two first-launch overlays on ActSettings (policy dialog, language sheet); they are irrelevant here.
        hadReadPolicyKey = prefs.contains(UtilSettings.KEY_READ_POLICY)
        hadReadPolicy = prefs.getBoolean(UtilSettings.KEY_READ_POLICY, false)
        prefs.edit().putBoolean(UtilSettings.KEY_READ_POLICY, true).putBoolean(LANGUAGE_SELECTED_KEY, true).commit()
    }

    @After fun tearDown() {
        prefs.edit().apply {
            if (hadNightKey) putInt(UtilSettings.KEY_NIGHT_MODE, originalNight) else remove(UtilSettings.KEY_NIGHT_MODE)
            if (hadReadPolicyKey) putBoolean(UtilSettings.KEY_READ_POLICY, hadReadPolicy) else remove(UtilSettings.KEY_READ_POLICY)
            remove(LANGUAGE_SELECTED_KEY)
        }.commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private class Painted(val background: Int, val text: Int, val spinner: Int, val textRatio: Double, val spinnerRatio: Double)

    private fun measure(nightMode: Int): Painted {
        prefs.edit().putInt(UtilSettings.KEY_NIGHT_MODE, nightMode).commit()
        var result: Painted? = null
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val overlay = activity.findViewById<ViewGroup>(R.id.flAdOpenApp)
                overlay.visibility = View.VISIBLE
                val background = MaterialColors.getColor(activity, androidx.appcompat.R.attr.colorPrimary, Color.MAGENTA)
                val text = firstOf<TextView>(overlay).currentTextColor
                val spinner = firstOf<ProgressBar>(overlay).indeterminateTintList?.defaultColor ?: Color.MAGENTA
                result = Painted(
                    background, text, spinner,
                    ColorUtils.calculateContrast(opaque(text, background), background),
                    ColorUtils.calculateContrast(opaque(spinner, background), background)
                )
            }
        }
        return requireNotNull(result)
    }

    private fun opaque(fg: Int, bg: Int) = ColorUtils.compositeColors(fg, bg)

    private inline fun <reified T : View> firstOf(root: ViewGroup): T = findFirst(root) { it is T } as T

    private fun findFirst(root: ViewGroup, match: (View) -> Boolean): View {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (match(child)) return child
            if (child is ViewGroup) {
                val inner = findFirstOrNull(child, match)
                if (inner != null) return inner
            }
        }
        error("no matching view in the overlay")
    }

    private fun findFirstOrNull(root: ViewGroup, match: (View) -> Boolean): View? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (match(child)) return child
            if (child is ViewGroup) findFirstOrNull(child, match)?.let { return it }
        }
        return null
    }

    @Test fun inLightTheTextAndTheSpinnerAreReadableOverThePrimaryColour() {
        val p = measure(AppCompatDelegate.MODE_NIGHT_NO)
        assertTrue("light text ${p.textRatio}:1 over #${Integer.toHexString(p.background)}", p.textRatio >= MIN_RATIO)
        assertTrue("light spinner ${p.spinnerRatio}:1", p.spinnerRatio >= MIN_RATIO_NON_TEXT)
    }

    @Test fun inNightTheTextAndTheSpinnerAreReadableOverThePrimaryColour() {
        val p = measure(AppCompatDelegate.MODE_NIGHT_YES)
        assertTrue("night text ${p.textRatio}:1 over #${Integer.toHexString(p.background)}", p.textRatio >= MIN_RATIO)
        assertTrue("night spinner ${p.spinnerRatio}:1", p.spinnerRatio >= MIN_RATIO_NON_TEXT)
    }

    @Test fun theNightPrimaryIsReallyTheLightPinkThatMadeFixedWhiteUnreadable() {
        // Positive control: night primary is light, so a fixed white would fail on exactly the colour measured above.
        val p = measure(AppCompatDelegate.MODE_NIGHT_YES)
        val whiteOverBackground = ColorUtils.calculateContrast(Color.WHITE, p.background)
        assertTrue("fixed white over the night primary would be $whiteOverBackground:1, and must be under 4.5", whiteOverBackground < MIN_RATIO)
    }

    private companion object {
        const val MIN_RATIO = 4.5
        // WCAG 1.4.11 asks 3:1 for a graphical object such as the spinner.
        const val MIN_RATIO_NON_TEXT = 3.0
        const val LANGUAGE_SELECTED_KEY = "Locale.Helper.Language.Selected"
    }
}
