package com.mckimquyen.ui

import android.widget.TextView
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-024 regression: toggling "Show notification badges" off must hide the badge on an
 * *already-bound, still-visible* Apps-tab row immediately - not only after the row is later
 * recycled or the app list itself changes. [AppAdapter.updateApps] diffs old vs new App data
 * with DiffUtil, which correctly reports "no change" here (the setting toggle never touches
 * RAppsSingleton's app list), so routing this through the existing appsEdited/appsLoaded
 * pipeline would never rebind the row. Found via manual device smoke testing.
 */
@RunWith(AndroidJUnit4::class)
class FrmAppsNotificationBadgeRefreshWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun seedApps() {
        RAppsSingleton.instance.replaceSnapshot(
            listOf(App(packageName = "com.example.gmail", name = "MainActivity", label = "Gmail", notificationCount = 4)),
            emptyMap()
        )
        UtilSettings(context).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
    }

    @After
    fun tearDown() {
        UtilSettings(context).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
    }

    @Test
    fun togglingTheSettingOffHidesAnAlreadyBoundBadgeWithoutReopeningTheTab() {
        val scenario = launchFragmentInContainer<FrmApps>(themeResId = R.style.AppTheme)
        lateinit var badge: TextView
        scenario.onFragment { fragment ->
            val rv = fragment.requireView().findViewById<RecyclerView>(R.id.rvApps)
            badge = rv.findViewHolderForAdapterPosition(0)!!.itemView.findViewById(R.id.tvAppNotificationBadge)
            assertEquals(android.view.View.VISIBLE, badge.visibility)
        }

        UtilSettings(context).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppEventManager.notifyNotificationBadgeSettingChanged()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(android.view.View.GONE, badge.visibility)
        scenario.close()
    }
}
