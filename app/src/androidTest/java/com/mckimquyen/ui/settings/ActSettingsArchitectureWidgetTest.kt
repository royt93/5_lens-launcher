package com.mckimquyen.ui.settings

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.R
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ARCH-001: Widget tests asserting decomposed ActSettings UI components and interactions.
 */
@RunWith(AndroidJUnit4::class)
class ActSettingsArchitectureWidgetTest {

    @Test
    fun testVipBadgeVisualAndClickIntegration() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            val chipVipBadge = activity.findViewById<View>(R.id.chipVipBadge)
            assertNotNull("VIP badge view must exist on toolbar", chipVipBadge)
            assertEquals("VIP badge must be visible", View.VISIBLE, chipVipBadge.visibility)

            val tvStatus = chipVipBadge.findViewById<TextView>(R.id.tvVipBadgeStatus)
            assertNotNull("VIP status text must exist", tvStatus)
            val expectedText = activity.getString(
                if (com.roy.sdkadbmob.AdManager.isVIPMember()) R.string.vip_badge_active else R.string.vip_badge_get
            )
            assertEquals(expectedText, tvStatus.text.toString())
        }
        scenario.close()
    }

    @Test
    fun testPageChangeCallbackTogglesSortFabVisibility() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // 1. Initial tab 0 (Lens tab) -> FAB sort must be hidden or not visible
        scenario.onActivity { activity ->
            val tabs = activity.findViewById<TabLayout>(R.id.tabs)
            tabs.getTabAt(0)?.select()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val fabSort = activity.findViewById<FloatingActionButton>(R.id.fabSort)
            assertTrue("FAB sort must be hidden on tab 0", fabSort.visibility != View.VISIBLE || fabSort.isOrWillBeHidden)
        }

        // 2. Select tab 1 (Apps tab) -> FAB sort must be shown
        scenario.onActivity { activity ->
            val tabs = activity.findViewById<TabLayout>(R.id.tabs)
            tabs.getTabAt(1)?.select()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val fabSort = activity.findViewById<FloatingActionButton>(R.id.fabSort)
            assertTrue("FAB sort must be shown on tab 1", fabSort.isOrWillBeShown || fabSort.visibility == View.VISIBLE)
        }

        // 3. Select tab 2 (Settings tab) -> FAB sort must be hidden
        scenario.onActivity { activity ->
            val tabs = activity.findViewById<TabLayout>(R.id.tabs)
            tabs.getTabAt(2)?.select()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val fabSort = activity.findViewById<FloatingActionButton>(R.id.fabSort)
            assertTrue("FAB sort must be hidden on tab 2", fabSort.visibility != View.VISIBLE || fabSort.isOrWillBeHidden)
        }

        scenario.close()
    }

    @Test
    fun testMenuRoutingResetDefaultsExecutesCleanly() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            // Trigger ResetDefaults via SettingsMenuHost
            activity.resetTabDefaults(0)
            activity.resetTabDefaults(1)
            activity.resetTabDefaults(2)
        }
        scenario.close()
    }
}
