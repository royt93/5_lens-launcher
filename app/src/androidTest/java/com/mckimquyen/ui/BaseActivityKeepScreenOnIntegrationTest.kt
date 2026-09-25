package com.mckimquyen.ui

import android.view.WindowManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.feature.vip.ActVipManagement
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Owner request (2026-09-25): "Keep Screen On" now applies to every screen, not just
 * `ActHome` (FEAT-005's original scope) - centralized in `BaseActivity.onResume()` so any
 * Activity extending it gets the flag for free. `ActHomeKeepScreenOnIntegrationTest` already
 * proves `ActHome` itself still works after the move; this proves a genuinely *different*
 * Activity (`ActSettings`) picks it up too, so the claim is verified, not assumed from
 * reading the inheritance chain.
 */
@RunWith(AndroidJUnit4::class)
class BaseActivityKeepScreenOnIntegrationTest {

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
    fun flagIsClear_onActSettings_whenSettingIsOff() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            assertEquals(false, hasKeepScreenOnFlag(activity))
        }
        scenario.close()
    }

    @Test
    fun flagIsSet_onActSettings_whenSettingIsOn() {
        UtilSettings(context).save(UtilSettings.KEY_KEEP_SCREEN_ON, true)

        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            assertEquals(
                "a different Activity than ActHome must also get the flag - this is the whole point of centralizing it in BaseActivity",
                true,
                hasKeepScreenOnFlag(activity)
            )
        }
        scenario.close()
    }

    @Test
    fun flagIsSet_onActAbout_whenSettingIsOn() {
        UtilSettings(context).save(UtilSettings.KEY_KEEP_SCREEN_ON, true)

        val scenario = ActivityScenario.launch(ActAbout::class.java)
        scenario.onActivity { activity ->
            assertEquals(
                "a third, unrelated Activity must also get the flag - proving this isn't a two-Activity coincidence",
                true,
                hasKeepScreenOnFlag(activity)
            )
        }
        scenario.close()
    }

    @Test
    fun flagIsSet_onActVipManagement_whenSettingIsOn() {
        UtilSettings(context).save(UtilSettings.KEY_KEEP_SCREEN_ON, true)

        val scenario = ActivityScenario.launch(ActVipManagement::class.java)
        scenario.onActivity { activity ->
            assertEquals(
                "ActVipManagement used to extend AppCompatActivity directly and missed the flag",
                true,
                hasKeepScreenOnFlag(activity)
            )
        }
        scenario.close()
    }

    @Test
    fun flagIsClear_onActVipManagement_whenSettingIsOff() {
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)
        scenario.onActivity { activity ->
            assertEquals(false, hasKeepScreenOnFlag(activity))
        }
        scenario.close()
    }

    @Test
    fun flagIsCleared_onResume_afterSettingTurnedOff() {
        UtilSettings(context).save(UtilSettings.KEY_KEEP_SCREEN_ON, true)

        val scenario = ActivityScenario.launch(ActVipManagement::class.java)
        scenario.onActivity { assertEquals(true, hasKeepScreenOnFlag(it)) }

        UtilSettings(context).save(UtilSettings.KEY_KEEP_SCREEN_ON, false)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        scenario.onActivity { activity ->
            assertEquals(
                "toggling the setting off must clear the flag on the next resume",
                false,
                hasKeepScreenOnFlag(activity)
            )
        }
        scenario.close()
    }
}
