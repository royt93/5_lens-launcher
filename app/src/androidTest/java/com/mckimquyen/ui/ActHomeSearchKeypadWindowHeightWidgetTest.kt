package com.mckimquyen.ui

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.search.SearchKeypadPolicy
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The search decides between the in-app T9 keypad and the soft keyboard from the height of the WINDOW, not from the Activity's
 * Configuration: put into split-screen after launching full screen, the Configuration kept screenHeightDp = 891 for a 440 dp window
 * (measured on the Pixel 7 Pro) and the keypad never showed. A real split cannot be made from a test, so the window is shortened with
 * [android.view.Window.setLayout], which leaves the Configuration alone and shortens the decor view: the same disagreement. The
 * user's T9 switch is OFF in every test here, so any keypad on screen comes from the window height. Log tag F422WIN.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeSearchKeypadWindowHeightWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F422WIN"
        const val SHORT_WINDOW_DP = 400
        const val FAKE_APP_COUNT = 40
        const val WAIT_MS = 8_000L
        const val POLL_MS = 50L
        const val SETTLE_MS = 900L
    }

    private data class Snap(val decorDp: Int, val configDp: Int, val keypadVisible: Boolean, val imeVisible: Boolean)

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var hadStored = false
    private var originalT9 = false
    private var originalApps: ArrayList<App>? = null

    @Before fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadStored = prefs.contains(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        originalT9 = UtilSettings(context).getEnableT9SearchKeypad()
        UtilSettings(context).saveEnableT9SearchKeypad(false)
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = ArrayList((0 until FAKE_APP_COUNT).map { App(label = "App$it", packageName = "com.win.a$it", name = "Main", isVisible = true) })
    }

    @After fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadStored) putBoolean(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD, originalT9) else remove(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        }.commit()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun settle() {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    private fun openSearch(scenario: ActivityScenario<ActHome>) {
        scenario.onActivity { it.openSearchFromHome() }
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        var shown = false
        while (!shown && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { shown = it.findViewById<SearchView>(R.id.searchView).currentTransitionState == SearchView.TransitionState.SHOWN }
            if (!shown) Thread.sleep(POLL_MS)
        }
        assertTrue("the SearchView must finish opening", shown)
        settle()
    }

    private fun resizeWindow(scenario: ActivityScenario<ActHome>, heightPx: Int) {
        scenario.onActivity { it.window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, heightPx) }
        settle()
    }

    private fun snap(scenario: ActivityScenario<ActHome>, label: String): Snap {
        var s: Snap? = null
        scenario.onActivity { a ->
            val d = a.resources.displayMetrics.density
            val insets = ViewCompat.getRootWindowInsets(a.window.decorView)
            s = Snap(
                decorDp = (a.window.decorView.height / d).toInt(),
                configDp = a.resources.configuration.screenHeightDp,
                keypadVisible = a.findViewById<View>(R.id.scrollT9Keypad).visibility == View.VISIBLE,
                imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
            )
        }
        val r = requireNotNull(s)
        Log.i(TAG, "$label decorDp=${r.decorDp} configDp=${r.configDp} keypadVisible=${r.keypadVisible} imeVisible=${r.imeVisible}")
        return r
    }

    private fun shortPx(): Int = (SHORT_WINDOW_DP * context.resources.displayMetrics.density).toInt()

    @Test fun aFullHeightWindowKeepsTheSoftKeyboardWhenT9IsOff() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            openSearch(scenario)
            val s = snap(scenario, "full")
            assertTrue("positive control: the window is taller than the compact height, was ${s.decorDp} dp", s.decorDp >= SearchKeypadPolicy.COMPACT_HEIGHT_DP)
            assertFalse("a tall window with T9 off must not show the keypad", s.keypadVisible)
        }
    }

    @Test fun openingTheSearchInAShortWindowShowsTheKeypadAndNoSoftKeyboard() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            resizeWindow(scenario, shortPx())
            val before = snap(scenario, "short-before-open")
            assertTrue("positive control: the window really is shorter than the compact height, was ${before.decorDp} dp", before.decorDp < SearchKeypadPolicy.COMPACT_HEIGHT_DP)
            openSearch(scenario)
            val s = snap(scenario, "short-after-open")
            assertTrue("a window under ${SearchKeypadPolicy.COMPACT_HEIGHT_DP} dp must show the keypad even with T9 off (decor ${s.decorDp} dp, Configuration ${s.configDp} dp)", s.keypadVisible)
            assertFalse("the soft keyboard must not open while the keypad stands in for it", s.imeVisible)
        }
    }

    @Test fun shrinkingAndGrowingTheWindowWhileTheSearchIsOpenSwapsTheKeypad() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            openSearch(scenario)
            assertFalse("full height, T9 off: no keypad", snap(scenario, "open-full").keypadVisible)
            resizeWindow(scenario, shortPx())
            val short = snap(scenario, "open-then-short")
            assertTrue("positive control: the window shrank below the compact height, was ${short.decorDp} dp", short.decorDp < SearchKeypadPolicy.COMPACT_HEIGHT_DP)
            assertTrue("shrunk below the compact height while open: the keypad must appear (decor ${short.decorDp} dp, Configuration ${short.configDp} dp)", short.keypadVisible)
            resizeWindow(scenario, ViewGroup.LayoutParams.MATCH_PARENT)
            val grown = snap(scenario, "open-then-grown")
            assertTrue("positive control: the window grew back, was ${grown.decorDp} dp", grown.decorDp >= SearchKeypadPolicy.COMPACT_HEIGHT_DP)
            assertFalse("grown back to full height with T9 off: the keypad must go away", grown.keypadVisible)
        }
    }

    @Test fun theConfigurationDoesNotFollowTheShortenedWindow() {
        // Why the tests above prove the fix: if the Configuration followed the window, a Configuration-based decision would pass them too.
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            resizeWindow(scenario, shortPx())
            val s = snap(scenario, "config-check")
            Log.i(TAG, "Configuration follows window: ${s.configDp < SearchKeypadPolicy.COMPACT_HEIGHT_DP}")
            assertEquals("positive control for the reproduction", true, s.decorDp < SearchKeypadPolicy.COMPACT_HEIGHT_DP)
            assertTrue("the Configuration must still read as tall for this reproduction to match the split-screen bug (it read ${s.configDp} dp)", s.configDp >= SearchKeypadPolicy.COMPACT_HEIGHT_DP)
        }
    }
}
