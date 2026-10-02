package com.mckimquyen.ui

import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeCleanLensModeIntegrationTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun cleanLensMode_whenTrue_hidesSearchBarAndPageIndicator() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<View>(R.id.searchBar)
                val indicator = activity.findViewById<TabLayout>(R.id.lensPageIndicator)

                assertEquals("SearchBar must be GONE in clean lens mode", View.GONE, searchBar.visibility)
                assertEquals("Indicator must be GONE in clean lens mode", View.GONE, indicator.visibility)
            }
        }
    }

    @Test
    fun cleanLensMode_whenFalse_restoresSearchBar() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<View>(R.id.searchBar)
                assertEquals(
                    "SearchBar must follow KEY_SHOW_SEARCH_BAR when clean lens mode is off",
                    View.VISIBLE,
                    searchBar.visibility
                )
            }
        }
    }
}
