package com.mckimquyen.feature.vip

import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mckimquyen.R
import com.mckimquyen.ui.ActSettings
import com.roy.sdkadbmob.AdManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FVipManagementWidgetTest {

    @Before
    fun setUp() {
        AdManager.clearVipByKey()
    }

    @After
    fun tearDown() {
        AdManager.clearVipByKey()
    }

    @Test
    fun testVipScreenUIElementsDisplayed() {
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)

        scenario.onActivity { activity ->
            val tvStatusTitle = activity.findViewById<View>(R.id.tvStatusTitle)
            assertNotNull("tvStatusTitle should be present", tvStatusTitle)
            assertEquals(View.VISIBLE, tvStatusTitle.visibility)

            val edtVipKey = activity.findViewById<View>(R.id.edtVipKey)
            assertNotNull("edtVipKey should be present", edtVipKey)
            assertEquals(View.VISIBLE, edtVipKey.visibility)

            val btnActivateVipKey = activity.findViewById<View>(R.id.btnActivateVipKey)
            assertNotNull("btnActivateVipKey should be present", btnActivateVipKey)
            assertEquals(View.VISIBLE, btnActivateVipKey.visibility)

            val btnWatchAdVip = activity.findViewById<View>(R.id.btnWatchAdVip)
            assertNotNull("btnWatchAdVip should be present", btnWatchAdVip)
            assertEquals(View.VISIBLE, btnWatchAdVip.visibility)
        }

        scenario.close()
    }

    // BUG-4+5: Activate button starts disabled (EditText empty), enables on input
    @Test
    fun `BUG4 - activate button disabled initially and enabled after text input`() {
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)

        scenario.onActivity { activity ->
            val btnActivate = activity.findViewById<Button>(R.id.btnActivateVipKey)
            assertNotNull(btnActivate)
            assertFalse("Activate button must be disabled when EditText is empty", btnActivate.isEnabled)
        }

        scenario.onActivity { activity ->
            val edtVipKey = activity.findViewById<android.widget.EditText>(R.id.edtVipKey)
            edtVipKey.setText("TEST")
        }

        scenario.onActivity { activity ->
            val btnActivate = activity.findViewById<Button>(R.id.btnActivateVipKey)
            assertTrue("Activate button must be enabled after text entered", btnActivate.isEnabled)
        }

        scenario.close()
    }

    // BUG-7: Watch Ad button visible and enabled for free user
    @Test
    fun `BUG7 - watch ad button is enabled for free user`() {
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)

        scenario.onActivity { activity ->
            val btnWatchAd = activity.findViewById<Button>(R.id.btnWatchAdVip)
            assertNotNull(btnWatchAd)
            assertTrue("Watch ad button must be enabled for free user", btnWatchAd.isEnabled)
        }

        scenario.close()
    }

    // BUG-7: Watch Ad button disabled when VIP active
    @Test
    fun `BUG7 - watch ad button disabled when VIP is active`() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val originalSecret = AdManager.adConfig.vipKeySecret
        try {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = VipKeys.VIP_3D_KEY)
            AdManager.activateVipByKey(context, VipKeys.VIP_3D_KEY, 3)
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
        }

        val scenario = ActivityScenario.launch(ActVipManagement::class.java)
        scenario.onActivity { activity ->
            val btnWatchAd = activity.findViewById<Button>(R.id.btnWatchAdVip)
            assertNotNull(btnWatchAd)
            assertFalse("Watch ad button must be disabled when VIP is active", btnWatchAd.isEnabled)
        }

        scenario.close()
    }

    // BUG-8: Revoke button disabled for free user
    @Test
    fun `BUG8 - revoke button disabled for free user`() {
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)

        scenario.onActivity { activity ->
            val btnRevoke = activity.findViewById<Button>(R.id.btnRevokeVip)
            assertNotNull(btnRevoke)
            assertFalse("Revoke button must be disabled when user is free", btnRevoke.isEnabled)
        }

        scenario.close()
    }

    // BUG-8: Revoke button enabled for VIP user
    @Test
    fun `BUG8 - revoke button enabled when VIP active`() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val originalSecret = AdManager.adConfig.vipKeySecret
        try {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = VipKeys.VIP_3D_KEY)
            AdManager.activateVipByKey(context, VipKeys.VIP_3D_KEY, 3)
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
        }

        val scenario = ActivityScenario.launch(ActVipManagement::class.java)
        scenario.onActivity { activity ->
            val btnRevoke = activity.findViewById<Button>(R.id.btnRevokeVip)
            assertNotNull(btnRevoke)
            assertTrue("Revoke button must be enabled when VIP active", btnRevoke.isEnabled)
        }

        scenario.close()
    }

    // BUG-10: Activity destroys cleanly without crash (animators cancelled)
    @Test
    fun `BUG10 - activity finishes without crash from animator leak`() {
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)
        // Give slide-in animator time to start (1100ms duration)
        Thread.sleep(200)
        // Close within animation window — should not throw
        scenario.close()
    }

    @Test
    fun testVipSettingsItemClickNavigatesToVipActivity() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewpager)
            viewPager.setCurrentItem(2, false)
        }

        Thread.sleep(500)

        scenario.onActivity { activity ->
            val llVipPremium = activity.findViewById<View>(R.id.llVipPremium)
            assertNotNull("llVipPremium should exist", llVipPremium)
            llVipPremium.performClick()
        }

        Thread.sleep(1000)

        var currentActivity: android.app.Activity? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val resumedActivities = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
            if (resumedActivities.iterator().hasNext()) {
                currentActivity = resumedActivities.iterator().next()
            }
        }

        assertNotNull("An activity should be in RESUMED state", currentActivity)
        assertTrue("Launched activity should be ActVipManagement", currentActivity is ActVipManagement)

        scenario.close()
    }
}
