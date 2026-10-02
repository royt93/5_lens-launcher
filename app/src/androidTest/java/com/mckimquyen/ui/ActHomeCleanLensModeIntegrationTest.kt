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
