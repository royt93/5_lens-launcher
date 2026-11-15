package com.mckimquyen.ui

import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.util.UtilSettings
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Probe, not a regression test: asks the app to show its interstitial exactly once (ActSettings.openAbout goes through
 * AdManager.showInterstitial) and then only waits, so that `run-ad-guard-probe.sh` can check from outside which window appears and whether
 * KEYCODE_BACK closes it. The test asserts nothing about ads: whether an ad is shown is up to the ad SDKs, and it never touches a view of an
 * ad. It runs only with `-e adProbe true`, otherwise it is skipped, so ordinary suites never request an ad.
 */
@RunWith(AndroidJUnit4::class)
class ActSettingsAdGuardProbeTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F278ADPROBE"
        const val ARG = "adProbe"
        const val WAIT_MS = 25_000L
        const val STEP_MS = 500L
        const val LANGUAGE_SELECTED_KEY = "Locale.Helper.Language.Selected"
    }

    @Before fun seedFirstLaunchKeys() {
        assumeTrue("opt-in probe: pass `-e $ARG true`", InstrumentationRegistry.getArguments().getString(ARG) == "true")
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().targetContext).edit()
            .putBoolean(UtilSettings.KEY_READ_POLICY, true).putBoolean(LANGUAGE_SELECTED_KEY, true).commit()
    }

    @Test fun requestOneInterstitialAndWaitForTheGuard() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Log.i(TAG, "requesting one interstitial through ActSettings.openAbout()")
            scenario.onActivity { it.openAbout() }
            val end = SystemClock.uptimeMillis() + WAIT_MS
            while (SystemClock.uptimeMillis() < end) SystemClock.sleep(STEP_MS)
            Log.i(TAG, "wait finished")
        }
    }
}
