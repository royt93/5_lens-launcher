package com.mckimquyen.ui

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.appbar.MaterialToolbar
import com.mckimquyen.R
import com.mckimquyen.feature.vip.ActVipManagement
import com.mckimquyen.views.SuperWebViewActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented widget & integration test for ActVipManagement, SuperWebViewActivity,
 * SplashAct, and ActAbout confirming Material You (M3) components.
 */
@RunWith(AndroidJUnit4::class)
class RemainingScreensMaterialYouTest {

    @Test
    fun testActVipManagement_launchesWithMaterial3Components() {
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)
        scenario.onActivity { activity ->
            assertFalse("ActVipManagement must not be finishing", activity.isFinishing)

            val cardStatusHeader = activity.findViewById<View>(R.id.cardStatusHeader)
            assertNotNull(cardStatusHeader)
            assertTrue("cardStatusHeader must be MaterialCardView", cardStatusHeader is MaterialCardView)

            val progressVip = activity.findViewById<View>(R.id.progressVip)
            assertNotNull(progressVip)
            assertTrue("progressVip must be LinearProgressIndicator", progressVip is LinearProgressIndicator)

            val btnWatchAdVip = activity.findViewById<View>(R.id.btnWatchAdVip)
            assertNotNull(btnWatchAdVip)
            assertTrue("btnWatchAdVip must be MaterialButton", btnWatchAdVip is MaterialButton)

            val btnActivateVipKey = activity.findViewById<View>(R.id.btnActivateVipKey)
            assertNotNull(btnActivateVipKey)
            assertTrue("btnActivateVipKey must be MaterialButton", btnActivateVipKey is MaterialButton)
        }
        scenario.close()
    }

    @Test
    fun testSuperWebViewActivity_launchesWithMaterial3Components() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SuperWebViewActivity::class.java).apply {
            putExtra(SuperWebViewActivity.KEY_TITLE, "Privacy Policy")
            putExtra(SuperWebViewActivity.KEY_URL, "https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f")
        }
        val scenario = ActivityScenario.launch<SuperWebViewActivity>(intent)
        scenario.onActivity { activity ->
            assertFalse("SuperWebViewActivity must not be finishing with valid allowlisted url", activity.isFinishing)

            val toolbar = activity.findViewById<View>(R.id.toolbar)
            assertNotNull(toolbar)
            assertTrue("toolbar must be MaterialToolbar", toolbar is MaterialToolbar)

            val progressIndicator = activity.findViewById<View>(R.id.progressIndicator)
            assertNotNull(progressIndicator)
            assertTrue("progressIndicator must be LinearProgressIndicator", progressIndicator is LinearProgressIndicator)

            val retryButton = activity.findViewById<View>(R.id.retryButton)
            assertNotNull(retryButton)
            assertTrue("retryButton must be MaterialButton", retryButton is MaterialButton)
        }
        scenario.close()
    }

    @Test
    fun testSplashAct_material3Components() {
        val scenario = ActivityScenario.launch(SplashAct::class.java)
        scenario.onActivity { activity ->
            val logoContainer = activity.findViewById<View>(R.id.logoContainer)
            assertNotNull(logoContainer)
            assertTrue("logoContainer must be MaterialCardView", logoContainer is MaterialCardView)

            val progressIndicator = activity.findViewById<View>(R.id.progressIndicator)
            assertNotNull(progressIndicator)
            assertTrue("progressIndicator must be CircularProgressIndicator", progressIndicator is CircularProgressIndicator)

            val loadingText = activity.findViewById<TextView>(R.id.loadingText)
            assertNotNull(loadingText)
            assertEquals(activity.getString(R.string.loading), loadingText.text.toString())
        }
        scenario.close()
    }
}
