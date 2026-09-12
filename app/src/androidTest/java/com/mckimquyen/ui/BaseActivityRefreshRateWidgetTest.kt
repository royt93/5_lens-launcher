package com.mckimquyen.ui

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget/instrumentation tests cho DISPLAY-001 (adaptive refresh-rate policy).
 *
 * `wantsHighRefreshRate()` là `protected`, không side-effect thuần Kotlin/JVM nên
 * test bằng reflection thay vì mở public API chỉ để test. Hành vi thật
 * (preferredDisplayModeId được set khi resume, reset về 0 khi pause) chỉ quan sát
 * được trên thiết bị thật chạy Android R (API 30) trở lên — guard bằng `assumeTrue`.
 */
@RunWith(AndroidJUnit4::class)
class BaseActivityRefreshRateWidgetTest {

    private fun wantsHighRefreshRate(activity: BaseActivity): Boolean {
        val method = BaseActivity::class.java.getDeclaredMethod("wantsHighRefreshRate")
        method.isAccessible = true
        return method.invoke(activity) as Boolean
    }

    @Test
    fun testActHome_wantsHighRefreshRate_isTrue() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            assertEquals(true, wantsHighRefreshRate(activity))
        }
        scenario.close()
    }

    @Test
    fun testActSettings_staticScreen_wantsHighRefreshRate_isFalse() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            assertEquals(false, wantsHighRefreshRate(activity))
        }
        scenario.close()
    }

    @Test
    fun testActHome_onResume_requestsHighRefreshRate_thenReleasesOnPause() {
        assumeTrue("Adaptive refresh rate policy only applies on API 30+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)

        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            assertNotEquals(
                "ActHome must request a non-default display mode while resumed",
                0,
                activity.window.attributes.preferredDisplayModeId
            )
        }

        scenario.moveToState(Lifecycle.State.CREATED) // triggers onPause()

        scenario.onActivity { activity ->
            assertEquals(
                "ActHome must release its preferred display mode once paused",
                0,
                activity.window.attributes.preferredDisplayModeId
            )
        }

        scenario.close()
    }

    @Test
    fun testActSettings_onResume_neverForcesADisplayMode() {
        assumeTrue("Adaptive refresh rate policy only applies on API 30+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)

        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            assertEquals(
                "Static/settings screens must let the system choose the display mode",
                0,
                activity.window.attributes.preferredDisplayModeId
            )
        }
        scenario.close()
    }
}
