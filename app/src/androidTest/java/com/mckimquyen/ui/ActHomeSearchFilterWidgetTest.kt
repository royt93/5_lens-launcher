package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.SearchFilterPreset
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeSearchFilterWidgetTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private val defaultLensId = LensWorkspace.DEFAULT_LENS_ID

    private val appFav = App(label = "FavApp", packageName = "com.test.fav", name = "Main", isFavorite = true, isVisible = true)
    private val appNormal = App(label = "NormalApp", packageName = "com.test.normal", name = "Main", isFavorite = false, isVisible = true)
    private val appHidden = App(label = "HiddenApp", packageName = "com.test.hidden", name = "Main", isFavorite = false, isVisible = true)

    private var originalApps: ArrayList<App>? = null
    private var originalWorkspace: com.mckimquyen.util.WorkspacePreferencesSnapshot? = null
    private var originalFilter: String? = null


    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalWorkspace = settings.snapshotWorkspacePreferences()
        originalFilter = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getString(UtilSettings.KEY_SEARCH_FILTER_PRESET, null)
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(appFav, appNormal, appHidden)

        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, defaultLensId)
        settings.saveLensAppScope(defaultLensId, LensAppScope.SELECTED)
        settings.saveLensAppSelection(defaultLensId, setOf(LensAppScope.identifierOf(appFav), LensAppScope.identifierOf(appNormal)))
        settings.saveLensHiddenAppIds(defaultLensId, setOf(LensAppScope.identifierOf(appHidden)))
        settings.saveSearchFilterPreset(SearchFilterPreset.ALL)
    }

    @After
    fun cleanup() {
        originalWorkspace?.let { assertTrue(settings.restoreWorkspacePreferences(it)) }
        val editor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
        if (originalFilter == null) editor.remove(UtilSettings.KEY_SEARCH_FILTER_PRESET)
        else editor.putString(UtilSettings.KEY_SEARCH_FILTER_PRESET, originalFilter)
        assertTrue(editor.commit())
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun searchChipsRenderAndSwitchFiltering() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val chipGroup = activity.findViewById<ChipGroup>(R.id.chipGroupSearchFilters)
                assertNotNull(chipGroup)

                val chipAll = activity.findViewById<Chip>(R.id.chipFilterAll)
                val chipFav = activity.findViewById<Chip>(R.id.chipFilterFavorites)

                assertTrue("Mặc định All chip được chọn", chipAll.isChecked)

                val chips = listOf(
                    chipAll,
                    activity.findViewById(R.id.chipFilterThisLens),
                    chipFav,
                    activity.findViewById(R.id.chipFilterHidden)
                )
                val expectedCheckedTint = com.google.android.material.color.MaterialColors.getColor(
                    chipAll,
                    com.google.android.material.R.attr.colorOnSecondaryContainer
                )
                val expectedUncheckedTint = com.google.android.material.color.MaterialColors.getColor(
                    chipAll,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
                chips.forEach { chip ->
                    assertFalse("Filter chips must not duplicate the built-in check icon", chip.isCheckedIconVisible)
                    val iconTint = requireNotNull(chip.chipIconTint)
                    assertEquals(expectedCheckedTint, iconTint.getColorForState(intArrayOf(android.R.attr.state_checked), 0))
                    assertEquals(expectedUncheckedTint, iconTint.getColorForState(intArrayOf(-android.R.attr.state_checked), 0))
                }

                // Chuyển sang Favorites
                chipFav.performClick()
            }
            instrumentation.waitForIdleSync()

            assertEquals(SearchFilterPreset.FAVORITES, settings.getSearchFilterPreset())

            scenario.onActivity { activity ->
                val searchAdapter = activity.searchResultAdapterForTest
                val currentList = searchAdapter?.currentListForTest().orEmpty()
                assertTrue(currentList.all { it.isFavorite })
                assertEquals(1, currentList.size)
                assertEquals("FavApp", currentList.first().label)

                // Chuyển sang Hidden
                activity.findViewById<Chip>(R.id.chipFilterHidden).performClick()
            }
            instrumentation.waitForIdleSync()

            assertEquals(SearchFilterPreset.HIDDEN, settings.getSearchFilterPreset())

            scenario.onActivity { activity ->
                val searchAdapter = activity.searchResultAdapterForTest
                val currentList = searchAdapter?.currentListForTest().orEmpty()
                assertEquals(1, currentList.size)
                assertEquals("HiddenApp", currentList.first().label)
            }
        }
    }
}
