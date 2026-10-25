package com.mckimquyen.ui

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.model.App
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-029: toggling Clean Lens in Settings refreshes Home when it resumes. */
@RunWith(AndroidJUnit4::class)
class ActHomeCleanLensLiveUpdateWidgetTest {
    @get:Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings get() = UtilSettings(context)
    private val onlyApp = App(label = "Single App", packageName = "com.test.clean.mode", name = "Main")

    @Before
    fun setUp() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        settings.setLauncherMode(LauncherMode.FISHEYE)
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        RAppsSingleton.instance.apps = arrayListOf(onlyApp)
    }

    private fun awaitSearchBar(scenario: ActivityScenario<ActHome>, expected: Int) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var actual = View.INVISIBLE
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { actual = it.findViewById<View>(R.id.searchBar).visibility }
            if (actual == expected) return
            Thread.sleep(POLL_MS)
        }
        assertEquals("Home search bar did not update after Settings event", expected, actual)
    }

    @Test
    fun cleanLensChangesInSettingsRefreshHomeAfterResume() {
        ActivityScenario.launch(ActHome::class.java).use { home ->
            awaitSearchBar(home, View.VISIBLE)
            launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { settingsScenario ->
                settingsScenario.onFragment { fragment ->
                    fragment.requireView().findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swCleanLensMode).isChecked = true
                }
            }
            awaitSearchBar(home, View.GONE)

            launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { settingsScenario ->
                settingsScenario.onFragment { fragment ->
                    fragment.requireView().findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swCleanLensMode).isChecked = false
                }
            }
            awaitSearchBar(home, View.VISIBLE)
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 50L
    }
}
