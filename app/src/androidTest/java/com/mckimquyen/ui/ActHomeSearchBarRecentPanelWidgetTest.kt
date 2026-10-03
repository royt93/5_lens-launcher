package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-009: the SearchBar's recent-apps icon exists, is tap-reachable, and honors its own
 * settings toggle - independent of whether the whole SearchBar itself is shown (KEY_SHOW_SEARCH_BAR
 * is a separate, coarser toggle covered by existing tests).
 */
@RunWith(AndroidJUnit4::class)
class ActHomeSearchBarRecentPanelWidgetTest {

    private val settings get() = UtilSettings(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before
    fun setUp() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, true)
    }

    @After
    fun tearDown() {
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, UtilSettings.DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED)
    }

    @Test
    fun iconIsVisibleByDefaultAndOpensThePanelOnTap() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<com.google.android.material.search.SearchBar>(R.id.searchBar)
                val item = searchBar.menu.findItem(R.id.menuItemRecentAppsPanel)
                assertEquals(true, item.isVisible)

                searchBar.menu.performIdentifierAction(R.id.menuItemRecentAppsPanel, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertNotNull(
                    "Tapping the SearchBar icon must open the panel",
                    activity.supportFragmentManager.findFragmentByTag(RecentAppsPanelFragment.TAG)
                )
            }
        }
    }

    /**
     * The glyph is a hard-white vector; without an explicit tint it is near-invisible on the light
     * SearchBar surface and differs from the theme-tinted search icon beside it.
     */
    @Test
    fun iconIsTintedWithTheThemeOnSurfaceVariantColor() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<com.google.android.material.search.SearchBar>(R.id.searchBar)
                val item = searchBar.menu.findItem(R.id.menuItemRecentAppsPanel)
                val tint = androidx.core.view.MenuItemCompat.getIconTintList(item)
                assertNotNull("the recent icon must declare an icon tint", tint)
                assertEquals(
                    com.google.android.material.color.MaterialColors.getColor(
                        activity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0
                    ),
                    tint!!.defaultColor
                )
            }
        }
    }

    @Test
    fun disablingTheSettingHidesTheIconOnNextResume() {
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, false)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<com.google.android.material.search.SearchBar>(R.id.searchBar)
                val item = searchBar.menu.findItem(R.id.menuItemRecentAppsPanel)
                assertEquals(false, item.isVisible)
            }
        }
    }
}
