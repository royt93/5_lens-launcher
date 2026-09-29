package com.mckimquyen.ui.settings

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A-consent (test-audit): `grep -rl "Consent" app/src/test app/src/androidTest` returned zero
 * files before this one - [SettingsAdVipDelegate.checkShowAd] is already-shipped code (not the
 * future ADS-001 consent state machine, which genuinely doesn't exist yet and correctly has no
 * tests). Its offline branch had never been exercised: does dismissing the splash/consent overlay
 * and marking consent resolved actually happen, or could an offline user get stuck behind it?
 *
 * Uses a fresh [SettingsAdVipDelegate] instance (not `ActSettings`'s own private one, which the
 * real `onCreate()` already drove once against whatever the test device's real connectivity is)
 * against the real inflated views from a launched [ActSettings], so the assertions are about the
 * delegate's own logic, not about what the test device happens to be connected to right now.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAdVipDelegateIntegrationTest {

    @Test
    fun checkShowAd_offlineNetwork_resolvesConsentImmediatelyWithoutBlockingTheUi() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            val delegate = SettingsAdVipDelegate()
            val flAdOpenApp = activity.findViewById<LinearLayout>(R.id.flAdOpenApp)
            flAdOpenApp.visibility = View.VISIBLE
            val bannerContainer = activity.findViewById<android.view.ViewGroup>(R.id.bannerContainer)
            val tvLabelAd = activity.findViewById<TextView>(R.id.tvLabelAd)

            delegate.checkShowAd(activity, flAdOpenApp, bannerContainer, tvLabelAd, isNetworkAvailable = false)

            assertEquals(
                "the splash/consent overlay must be dismissed immediately when offline",
                View.GONE,
                flAdOpenApp.visibility
            )
            assertTrue(
                "a network failure must resolve consent immediately, never leave the UI stuck " +
                    "behind an unresolved-consent state waiting on a request that was never made",
                delegate.isConsentResolved
            )
        }
        scenario.close()
    }
}
