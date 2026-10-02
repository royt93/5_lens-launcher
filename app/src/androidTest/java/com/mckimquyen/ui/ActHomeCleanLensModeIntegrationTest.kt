package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clean the Lens across the real Room + SharedPreferences + ActHome boundary. A second lens is
 * seeded so the page dots / lens-name label are genuinely eligible to show; otherwise a GONE
 * assertion would pass for the wrong reason (single lens already hides them).
 */
@RunWith(AndroidJUnit4::class)
class ActHomeCleanLensModeIntegrationTest {

    private companion object {
        const val SECOND_LENS_ID = "clean-lens-second"
        const val SECOND_LENS_NAME = "Second"
        const val SECOND_LENS_ORDER = 1
        const val WAIT_TIMEOUT_MS = 5_000L
        const val POLL_MS = 100L
        const val SETTLE_AFTER_HIDDEN_MS = 1_000L
        const val SYSTEM_BACK_PRESSES_TO_CLOSE_SEARCH = 2
        const val BACK_PRESS_GAP_MS = 700L
    }

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
        resetLenses()
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = SECOND_LENS_ID, name = SECOND_LENS_NAME, orderIndex = SECOND_LENS_ORDER))
        }
    }

    @After
    fun tearDown() {
        settings.deleteLensSettings(SECOND_LENS_ID)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        resetLenses()
    }

    private fun resetLenses() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    /** Polls until the indicator reaches the expected visibility (lens list loads async from Room). */
    private fun awaitIndicatorVisibility(scenario: ActivityScenario<ActHome>, expected: Int): Int {
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        var last = -1
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { last = it.findViewById<TabLayout>(R.id.lensPageIndicator).visibility }
            if (last == expected) return last
            android.os.SystemClock.sleep(POLL_MS)
        }
        return last
    }

    /**
     * Waits for SearchView's real transition state, not isShowing(): isShowing() already reads false
     * during HIDING, before the HIDDEN callback that restores the search bar has run.
     */
    private fun awaitSearchState(scenario: ActivityScenario<ActHome>, expectedShowing: Boolean) {
        val target = if (expectedShowing) {
            com.google.android.material.search.SearchView.TransitionState.SHOWN
        } else {
            com.google.android.material.search.SearchView.TransitionState.HIDDEN
        }
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        var state = target
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                state = it.findViewById<com.google.android.material.search.SearchView>(R.id.searchView)
                    .currentTransitionState
            }
            if (state == target) return
            android.os.SystemClock.sleep(POLL_MS)
        }
        assertEquals("SearchView did not reach requested transition state", target, state)
    }

    @Test
    fun pullDownCallback_opensSearchWhenCleanModeHidesSearchBar() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val lens = activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                org.junit.Assert.assertNotNull(lens.onSearchSwipeDownListener)
                lens.onSearchSwipeDownListener!!.onSearchSwipeDown()
            }
            awaitSearchState(scenario, true)
        }
    }

    @Test
    fun closingSearch_restoresVisibleBarWhenCleanOffAndSearchBarEnabled() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchState(scenario, true)
            scenario.onActivity {
                it.findViewById<com.google.android.material.search.SearchView>(R.id.searchView).hide()
            }
            awaitSearchState(scenario, false)
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }

    @Test
    fun closingSearch_restoresGoneBarWhenCleanOn() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchState(scenario, true)
            scenario.onActivity {
                it.findViewById<com.google.android.material.search.SearchView>(R.id.searchView).hide()
            }
            awaitSearchState(scenario, false)
            // Material re-shows the SearchBar a moment AFTER the HIDDEN callback, so asserting the
            // instant HIDDEN is reached passes while a real user still ends up seeing the bar.
            android.os.SystemClock.sleep(SETTLE_AFTER_HIDDEN_MS)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }

    /**
     * The real system BACK path: Material's SearchView handles it through its own back orchestrator
     * (ActHome's OnBackPressedCallback never even runs for it), and that route re-shows the SearchBar
     * after the HIDDEN callback. Closing via SearchView.hide() - what the other tests do - does not.
     */
    @Test
    fun closingSearchWithSystemBack_keepsBarGoneWhenCleanOn() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchState(scenario, true)
            // First BACK dismisses the IME that SearchView opens; the second closes the search itself.
            repeat(SYSTEM_BACK_PRESSES_TO_CLOSE_SEARCH) {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                android.os.SystemClock.sleep(BACK_PRESS_GAP_MS)
            }
            awaitSearchState(scenario, false)
            android.os.SystemClock.sleep(SETTLE_AFTER_HIDDEN_MS)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(
                    "system BACK must not resurrect a bar Clean mode hides",
                    View.GONE,
                    it.findViewById<View>(R.id.searchBar).visibility
                )
            }
        }
    }

    @Test
    fun cleanModeOff_withTwoLenses_showsPageIndicatorAndLensName() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(
                "control: 2 lenses + clean off must show the dots, else the clean-on assertion proves nothing",
                View.VISIBLE,
                awaitIndicatorVisibility(scenario, View.VISIBLE)
            )
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<TextView>(R.id.tvLensName).visibility)
            }
        }
    }

    @Test
    fun cleanModeOn_withTwoLenses_hidesPageIndicatorAndLensName() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            // Let the async lens load finish, then require it stayed hidden.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(WAIT_TIMEOUT_MS / 5)
            assertEquals(View.GONE, awaitIndicatorVisibility(scenario, View.GONE))
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<TextView>(R.id.tvLensName).visibility)
            }
        }
    }

    @Test
    fun cleanModeOn_hidesSearchBarEvenWhenSearchBarSettingIsOn() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }

    @Test
    fun cleanModeOff_searchBarFollowsItsOwnSetting() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }

    @Test
    fun cleanModeSwitchedOnAfterLaunch_hidesChromeOnResume() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitIndicatorVisibility(scenario, View.VISIBLE)

            settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)

            assertEquals(View.GONE, awaitIndicatorVisibility(scenario, View.GONE))
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }
}
