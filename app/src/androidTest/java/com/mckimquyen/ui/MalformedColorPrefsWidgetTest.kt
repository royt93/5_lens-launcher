package com.mckimquyen.ui

import android.widget.TextView
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-026: empty or malformed stored colors (imported backup, hand-edited prefs) must not crash the
 * launcher on start or Settings. Touches only the three color keys and puts each back exactly.
 */
@RunWith(AndroidJUnit4::class)
class MalformedColorPrefsWidgetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)
    private var saved: Map<String, Any?> = emptyMap()

    @Before
    fun setUp() {
        saved = KEYS.associateWith { prefs.all[it] }
    }

    @After
    fun tearDown() {
        val editor = prefs.edit()
        saved.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                null -> editor.remove(key)
                else -> error("unexpected pref type for $key")
            }
        }
        check(editor.commit()) { "could not restore color prefs" }
    }

    private fun corrupt(background: String, highlight: String) {
        UtilSettings(context).save(BackgroundMode.COLOR)
        check(prefs.edit()
            .putString(UtilSettings.KEY_BACKGROUND_COLOR, background)
            .putString(UtilSettings.KEY_HIGHLIGHT_COLOR, highlight)
            .commit())
    }

    @Test
    fun actHomeStartsWithEmptyColorPrefs() {
        corrupt(background = "", highlight = "")
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity -> assertEquals(false, activity.isFinishing) }
        }
    }

    @Test
    fun actHomeStartsWithGarbageColorPrefs() {
        corrupt(background = "zzz", highlight = "#12")
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity -> assertEquals(false, activity.isFinishing) }
        }
    }

    @Test
    fun settingsTabShowsTheDefaultColorTextInsteadOfCrashing() {
        corrupt(background = "", highlight = "#12")
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onFragment { fragment ->
                val view = fragment.requireView()
                assertEquals("#F8BBD0", view.findViewById<TextView>(R.id.tvSelectedBackground).text.toString())
                assertEquals("#F50057", view.findViewById<TextView>(R.id.tvSelectedHighlightColor).text.toString())
            }
        }
    }

    @Test
    fun settingsTabStillShowsAValidStoredColor() {
        corrupt(background = "#FF112233", highlight = "#FF445566")
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onFragment { fragment ->
                val view = fragment.requireView()
                assertEquals("#112233", view.findViewById<TextView>(R.id.tvSelectedBackground).text.toString())
                assertEquals("#445566", view.findViewById<TextView>(R.id.tvSelectedHighlightColor).text.toString())
            }
        }
    }

    private companion object {
        // save(BackgroundMode) writes KEY_BACKGROUND_MODE (a String), not the legacy KEY_BACKGROUND.
        val KEYS = listOf(UtilSettings.KEY_BACKGROUND_MODE, UtilSettings.KEY_BACKGROUND_COLOR, UtilSettings.KEY_HIGHLIGHT_COLOR)
    }
}
