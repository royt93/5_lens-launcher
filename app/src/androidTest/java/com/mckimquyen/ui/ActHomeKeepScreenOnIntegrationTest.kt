package com.mckimquyen.ui

import android.view.WindowManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-005 integration proof: the real Window's FLAG_KEEP_SCREEN_ON is set/cleared on ActHome
 * according to the persisted setting, re-checked on every resume (mirrors UI-001's
 * updateSearchBarVisibility pattern) so a change made in ActSettings takes effect when the user
 * returns Home - without leaking into other activities, since the flag lives on ActHome's own
 * Window and is simply irrelevant once another Activity's Window is the one on screen.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeKeepScreenOnIntegrationTest {

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

    private fun hasKeepScreenOnFlag(activity: android.app.Activity): Boolean =
        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0

    @Test
    fun flagIsClear_whenSettingIsOff_theDefault() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            assertEquals(false, hasKeepScreenOnFlag(activity))
        }
        scenario.close()
    }

    @Test
    fun flagIsSet_whenSettingIsOn_beforeLaunch() {
        UtilSettings(context).save(UtilSettings.KEY_KEEP_SCREEN_ON, true)

        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            assertEquals(true, hasKeepScreenOnFlag(activity))
        }
        scenario.close()
    }

    @Test
    fun flagUpdatesOnResume_whenSettingChangesWhileActHomeIsRecreated() {
        // Simulates the real cross-activity flow: user is on ActHome (off), goes to ActSettings
        // and flips the toggle, then returns Home - re-launching stands in for the resume that
        // real navigation would trigger, since updateKeepScreenOnFlag() is re-read every resume.
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity -> assertEquals(false, hasKeepScreenOnFlag(activity)) }

        UtilSettings(context).save(UtilSettings.KEY_KEEP_SCREEN_ON, true)
        scenario.recreate()
        scenario.onActivity { activity -> assertEquals(true, hasKeepScreenOnFlag(activity)) }

        scenario.close()
    }
}
