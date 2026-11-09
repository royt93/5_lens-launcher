package com.mckimquyen.ui

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** UI-034 on a real device: preview the reset, cancel, confirm, then undo, including a key that was absent. */
@RunWith(AndroidJUnit4::class)
class ResetDefaultsWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val APPS_TAB = 1
        const val SETTINGS_TAB = 2
        const val SENTINEL_KEY = "ui034_unrelated_sentinel"
        const val SENTINEL_VALUE = "keep-me"
        const val TEST_HINT = "UI034 hint"
        const val SNACKBAR_WAIT_MS = 4_000L
        const val SNACKBAR_POLL_MS = 50L
        const val DIALOG_SETTLE_WAIT_MS = 4_000L
        const val DIALOG_SETTLED_POLLS = 3
        const val LANGUAGE_SELECTED_KEY = "Locale.Helper.Language.Selected"
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)

    private var hadReadPolicy = false
    private var hadReadPolicyKey = false
    private var hadLanguageSelected = false
    private var hadLanguageSelectedKey = false

    @Before
    fun setUp() {
        // A freshly installed app shows two first-launch overlays when ActSettings opens: the Terms and Privacy Policy dialog and
        // the language picker bottom sheet. Either takes window focus and swallows the click on the reset dialog these tests open
        // on top (seen on a Samsung S24 Ultra, where the main app is not installed beforehand). Mark both as done, as they would
        // be on a device that has run this build before, and put the original values back afterwards.
        hadLanguageSelectedKey = prefs.contains(LANGUAGE_SELECTED_KEY)
        hadLanguageSelected = prefs.getBoolean(LANGUAGE_SELECTED_KEY, false)
        hadReadPolicyKey = prefs.contains(UtilSettings.KEY_READ_POLICY)
        hadReadPolicy = prefs.getBoolean(UtilSettings.KEY_READ_POLICY, false)
        prefs.edit().remove(UtilSettings.KEY_SEARCH_HINT_TEXT).remove(UtilSettings.KEY_VIBRATE_APP_HOVER)
            .putString(SENTINEL_KEY, SENTINEL_VALUE).putBoolean(UtilSettings.KEY_READ_POLICY, true)
            .putBoolean(LANGUAGE_SELECTED_KEY, true).commit()
    }

    @After
    fun tearDown() {
        val editor = prefs.edit().remove(UtilSettings.KEY_SEARCH_HINT_TEXT).remove(UtilSettings.KEY_VIBRATE_APP_HOVER)
            .remove(SENTINEL_KEY)
        if (hadReadPolicyKey) editor.putBoolean(UtilSettings.KEY_READ_POLICY, hadReadPolicy) else editor.remove(UtilSettings.KEY_READ_POLICY)
        if (hadLanguageSelectedKey) editor.putBoolean(LANGUAGE_SELECTED_KEY, hadLanguageSelected) else editor.remove(LANGUAGE_SELECTED_KEY)
        editor.commit()
    }

    private fun openResetOnSettingsTab(scenario: ActivityScenario<ActSettings>) {
        scenario.onActivity { it.findViewById<ViewPager2>(R.id.viewpager).setCurrentItem(SETTINGS_TAB, false) }
        instrumentation.waitForIdleSync()
        scenario.onActivity { it.resetTabDefaults(SETTINGS_TAB) }
        instrumentation.waitForIdleSync()
        awaitDialogSettled()
    }

    /**
     * The reset dialog animates in. A click injected while it is still moving lands where the confirm button used to be and the
     * dialog stays open with nothing written (on a Samsung S24 Ultra the confirm test failed 7 of 10 runs without this wait).
     * Poll until the dialog's root has window focus and the button has kept the same screen position, size and alpha for a few
     * polls in a row; no fixed sleep.
     */
    private fun awaitDialogSettled() {
        val deadline = android.os.SystemClock.uptimeMillis() + DIALOG_SETTLE_WAIT_MS
        var last: String? = null
        var stable = 0
        while (stable < DIALOG_SETTLED_POLLS && android.os.SystemClock.uptimeMillis() < deadline) {
            var now: String? = null
            try {
                onView(withText(R.string.reset_preview_confirm)).perform(object : androidx.test.espresso.ViewAction {
                    override fun getConstraints() = androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.view.View::class.java)
                    override fun getDescription() = "read the confirm button's on-screen geometry"
                    override fun perform(uiController: androidx.test.espresso.UiController, view: android.view.View) {
                        val xy = IntArray(2).also { view.getLocationOnScreen(it) }
                        val root = view.rootView
                        now = "${xy[0]},${xy[1]},${view.width},${view.height},${root.alpha},${root.scaleX},${root.scaleY},${view.hasWindowFocus()}"
                    }
                })
            } catch (_: androidx.test.espresso.NoMatchingViewException) {
                // dialog not on screen yet
            }
            stable = if (now != null && now == last && now!!.endsWith("true")) stable + 1 else 0
            last = now
            Thread.sleep(SNACKBAR_POLL_MS)
        }
        assertTrue("the reset dialog must settle", stable >= DIALOG_SETTLED_POLLS)
    }

    /** The Snackbar lives in the Activity window, so click it there instead of through Espresso's root matcher. */
    private fun clickSnackbarAction(scenario: ActivityScenario<ActSettings>) {
        val deadline = android.os.SystemClock.uptimeMillis() + SNACKBAR_WAIT_MS
        var clicked = false
        while (!clicked && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val action = activity.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_action)
                if (action != null && action.isShown && action.text == activity.getString(R.string.reset_undo)) {
                    action.performClick()
                    clicked = true
                }
            }
            if (!clicked) Thread.sleep(SNACKBAR_POLL_MS)
        }
        assertTrue("the Undo action must appear in the Snackbar", clicked)
    }

    @Test
    fun cancelLeavesEveryValueUntouched() {
        prefs.edit().putString(UtilSettings.KEY_SEARCH_HINT_TEXT, TEST_HINT).putBoolean(UtilSettings.KEY_VIBRATE_APP_HOVER, true).commit()
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            openResetOnSettingsTab(scenario)
            onView(withText(R.string.reset_preview_title)).check(matches(isDisplayed()))
            onView(withText(android.R.string.cancel)).perform(click())
            instrumentation.waitForIdleSync()
        }
        assertEquals(TEST_HINT, prefs.getString(UtilSettings.KEY_SEARCH_HINT_TEXT, null))
        assertEquals(true, prefs.getBoolean(UtilSettings.KEY_VIBRATE_APP_HOVER, false))
    }

    @Test
    fun resetThenUndoRestoresValuesAndAbsentKeysAndLeavesOtherPrefsAlone() {
        prefs.edit().putString(UtilSettings.KEY_SEARCH_HINT_TEXT, TEST_HINT).commit()
        assertFalse("setup: this key must start absent", prefs.contains(UtilSettings.KEY_VIBRATE_APP_HOVER))
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            openResetOnSettingsTab(scenario)
            onView(withText(R.string.reset_preview_confirm)).perform(click())
            instrumentation.waitForIdleSync()

            assertEquals("", prefs.getString(UtilSettings.KEY_SEARCH_HINT_TEXT, null))
            assertTrue("reset writes the default explicitly", prefs.contains(UtilSettings.KEY_VIBRATE_APP_HOVER))
            assertEquals(SENTINEL_VALUE, prefs.getString(SENTINEL_KEY, null))

            clickSnackbarAction(scenario)
            instrumentation.waitForIdleSync()
        }
        assertEquals(TEST_HINT, prefs.getString(UtilSettings.KEY_SEARCH_HINT_TEXT, null))
        assertFalse("undo must remove a key that was absent", prefs.contains(UtilSettings.KEY_VIBRATE_APP_HOVER))
        assertEquals(SENTINEL_VALUE, prefs.getString(SENTINEL_KEY, null))
    }

    @Test
    fun anAlreadyDefaultTabShowsTheNoticeAndWritesNothing() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { UtilSettings(it).let { us -> com.mckimquyen.ui.settings.ResetApplier.apply(us, com.mckimquyen.ui.settings.ResetEntries.apps(us)) } }
            scenario.onActivity { it.findViewById<ViewPager2>(R.id.viewpager).setCurrentItem(APPS_TAB, false) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { it.resetTabDefaults(APPS_TAB) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val text = activity.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)
                assertEquals(activity.getString(R.string.reset_already_default), text?.text?.toString())
            }
        }
    }
}
