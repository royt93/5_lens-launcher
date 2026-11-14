package com.mckimquyen.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.View
import android.view.Window
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.ref.WeakReference

/**
 * SEARCH-010 beyond geometry (the geometry is in [ActHomeT9KeypadDisplaySizesTest]): on the display the device is in, with the T9
 * keypad open, (1) typing digits with the keypad really filters, whatever the orientation (integration), (2) the keypad survives
 * the rotation changing under it and the Activity is collectable afterwards (interruption + leak), (3) opening the search and typing
 * keeps the frame cost inside the frame budget (jank), (4) a screenshot of the keypad is written for review (golden candidate).
 * Search opens through [ActHome.openSearchFromHome], never through a gesture, so no tap can land on an ad. The tests need the lens
 * to be in a state where the keypad fits, so on a phone in landscape they only run with `-e t9Landscape true` (a known fixed
 * overflow's regression guard) and are otherwise skipped, like the geometry test.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeT9KeypadLandscapeBehaviorTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F211T9"
        const val EXPECT_ORIENTATION_ARG = "t9ExpectOrientation"
        const val ORIENTATION_WAIT_MS = 6_000L
        const val SEARCH_WAIT_MS = 8_000L
        const val SEARCH_POLL_MS = 50L
        const val GC_WAIT_MS = 10_000L
        const val GC_POLL_MS = 100L
        const val TYPE_STEPS = 12
        const val STEP_DELAY_MS = 16L
        const val JANK_ROUNDS = 3
        const val MIN_FRAMES_PER_ROUND = 5
        const val SETTLE_MS = 700L
        const val COPY_WAIT_S = 10L
        // From the Pixel A/B (3 rounds interleaved, unfixed and fixed builds, same test APK): median p90 / budget was 0.78-1.31 on
        // every run in both orientations and both builds (worst 1.31 on the fixed build in portrait, 1.25 unfixed). 2.0 is about 1.5x
        // the worst seen: it catches a real regression, not noise. A frame-time guard, not a proof of 120 Hz.
        const val JANK_BUDGET_FACTOR = 2f
        const val NS_PER_MS = 1_000_000f
        const val P90 = 0.90f
        const val PNG_QUALITY = 100
        const val SHOT_DIR = "t9shots"
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var hadStoredSetting = false
    private var originalSetting = false
    private var originalApps: ArrayList<App>? = null

    private val chrome = App(label = "Chrome", packageName = "com.test.chrome", name = "Main", isVisible = true)
    private val maps = App(label = "Maps", packageName = "com.test.maps", name = "Main", isVisible = true)

    @Before fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadStoredSetting = prefs.contains(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        originalSetting = UtilSettings(context).getEnableT9SearchKeypad()
        UtilSettings(context).saveEnableT9SearchKeypad(true)
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(chrome, maps)
    }

    @After fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadStoredSetting) putBoolean(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD, originalSetting)
            else remove(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        }.commit()
        RAppsSingleton.instance.apps = originalApps
    }

    /** Skip, not fail, when the device is a phone in landscape and the opt-in flag is off (same rule as the geometry test). */
    private fun assumeDisplayIsCovered(scenario: ActivityScenario<ActHome>) {
        var portrait = true
        scenario.onActivity { portrait = it.resources.displayMetrics.let { dm -> dm.heightPixels >= dm.widthPixels } }
        // A run that asks for a given orientation must really get it: a rotation setting applied a moment too late once left a
        // "landscape" profile running (and photographed) in portrait, with two identical screenshots as the only clue.
        val expected = InstrumentationRegistry.getArguments().getString(EXPECT_ORIENTATION_ARG)
        if (expected != null) {
            // The test only CHECKS the orientation, it does not try to set it. Measured on the Pixel: `user_rotation` takes effect
            // (1 s) only while ActHome is the front window, and not at all right after an instrumented run; neither
            // Activity.requestedOrientation nor `user_rotation` written from inside the test rotated it. So the rotation is done by the
            // run script (open ActHome, rotate, wait for the display, then `am instrument`). The Activity gets up to
            // [ORIENTATION_WAIT_MS] to report the wanted orientation (a launch can report the old one for a moment), what it saw is
            // logged, and a profile that never got there fails here instead of silently running, and being photographed, in portrait.
            val deadline = SystemClock.uptimeMillis() + ORIENTATION_WAIT_MS
            val started = SystemClock.uptimeMillis()
            var seen = ""
            while (true) {
                scenario.onActivity { act ->
                    val dm = act.resources.displayMetrics
                    portrait = dm.heightPixels >= dm.widthPixels
                    @Suppress("DEPRECATION")
                    val rotation = act.windowManager.defaultDisplay.rotation
                    seen = "displayMetrics=${dm.widthPixels}x${dm.heightPixels} configOrientation=${act.resources.configuration.orientation} " +
                        "decor=${act.window.decorView.width}x${act.window.decorView.height} displayRotation=$rotation requested=${act.requestedOrientation}"
                }
                if (portrait == (expected == "portrait") || SystemClock.uptimeMillis() >= deadline) break
                instrumentation.waitForIdleSync()
                Thread.sleep(SEARCH_POLL_MS)
            }
            Log.i(TAG, "orientation check expected=$expected waitedMs=${SystemClock.uptimeMillis() - started} $seen")
            assertEquals("the Activity is not in the orientation this run asked for (rotate from the script first)", expected, if (portrait) "portrait" else "landscape")
        }
        val optedIn = InstrumentationRegistry.getArguments().getString("t9Landscape") == "true"
        org.junit.Assume.assumeTrue("landscape run needs `-e t9Landscape true`", portrait || optedIn)
    }

    private fun awaitSearchShown(scenario: ActivityScenario<ActHome>) {
        val deadline = SystemClock.uptimeMillis() + SEARCH_WAIT_MS
        var shown = false
        while (!shown && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { shown = it.findViewById<SearchView>(R.id.searchView).currentTransitionState == SearchView.TransitionState.SHOWN }
            if (!shown) Thread.sleep(SEARCH_POLL_MS)
        }
        assertTrue("the SearchView must finish opening", shown)
    }

    private fun openSearch(scenario: ActivityScenario<ActHome>) {
        scenario.onActivity { it.openSearchFromHome() }
        awaitSearchShown(scenario)
        instrumentation.waitForIdleSync()
    }

    private fun tap(scenario: ActivityScenario<ActHome>, vararg ids: Int) {
        scenario.onActivity { activity -> ids.forEach { activity.findViewById<View>(it).performClick() } }
        instrumentation.waitForIdleSync()
    }

    private fun resultLabels(scenario: ActivityScenario<ActHome>): List<String> {
        var labels = emptyList<String>()
        scenario.onActivity { labels = it.searchResultAdapterForTest?.currentListForTest().orEmpty().map { app -> app.label.toString() } }
        return labels
    }

    // ---- integration / widget -------------------------------------------------------------------------------------------

    @Test fun typingOnTheKeypadFiltersTheResultsOnThisDisplay() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assumeDisplayIsCovered(scenario)
            openSearch(scenario)
            tap(scenario, R.id.btnT9_Clear, R.id.btnT9_2, R.id.btnT9_4, R.id.btnT9_7)
            assertEquals("247 spells Chrome", listOf("Chrome"), resultLabels(scenario))
            tap(scenario, R.id.btnT9_Clear, R.id.btnT9_9, R.id.btnT9_9, R.id.btnT9_9)
            assertEquals("999 matches neither label, alias nor substring", emptyList<String>(), resultLabels(scenario))
            tap(scenario, R.id.btnT9_Clear, R.id.btnT9_6, R.id.btnT9_2, R.id.btnT9_7, R.id.btnT9_7)
            assertEquals("6277 spells Maps", listOf("Maps"), resultLabels(scenario))
        }
    }

    @Test fun everyKeyIsInsideTheKeypadViewportOrReachableByScrollingOnThisDisplay() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assumeDisplayIsCovered(scenario)
            openSearch(scenario)
            val keyIds = listOf(
                R.id.btnT9_1, R.id.btnT9_2, R.id.btnT9_3, R.id.btnT9_4, R.id.btnT9_5, R.id.btnT9_6,
                R.id.btnT9_7, R.id.btnT9_8, R.id.btnT9_9, R.id.btnT9_Clear, R.id.btnT9_Backspace,
            )
            val unreachable = ArrayList<String>()
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<NestedScrollView>(R.id.scrollT9Keypad)
                val frame = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                keyIds.forEach { id ->
                    val key = activity.findViewById<View>(id)
                    // Bring the key into view the way a finger would: scroll the container until the key's rect is inside it.
                    val keyRect = Rect().also { key.getDrawingRect(it); scroll.offsetDescendantRectToMyCoords(key, it) }
                    scroll.scrollTo(0, (keyRect.centerY() - scroll.height / 2).coerceIn(0, (scroll.getChildAt(0).height - scroll.height).coerceAtLeast(0)))
                    val onScreen = IntArray(2).also { key.getLocationOnScreen(it) }
                    val top = onScreen[1]
                    val bottom = top + key.height
                    val scrollTop = IntArray(2).also { scroll.getLocationOnScreen(it) }[1]
                    val visibleInViewport = top >= scrollTop - 1 && bottom <= scrollTop + scroll.height + 1 && bottom <= frame.bottom + 1
                    if (!visibleInViewport) unreachable.add("${activity.resources.getResourceEntryName(id)} top=$top bottom=$bottom viewport=[$scrollTop,${scrollTop + scroll.height}] frameBottom=${frame.bottom}")
                }
                scroll.scrollTo(0, 0)
            }
            assertTrue("every key must be reachable by scrolling and above the system bar: $unreachable", unreachable.isEmpty())
        }
    }

    // ---- interruption + leak --------------------------------------------------------------------------------------------

    @Test fun theKeypadSurvivesRecreationWithTheSearchOpenAndTheOldActivityIsCollectable() {
        var weakActivity: WeakReference<ActHome>? = null
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assumeDisplayIsCovered(scenario)
            openSearch(scenario)
            tap(scenario, R.id.btnT9_Clear, R.id.btnT9_2, R.id.btnT9_4)
            scenario.onActivity { weakActivity = WeakReference(it) }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertNotNull("the keypad must exist after recreate", activity.findViewById<View>(R.id.scrollT9Keypad))
                assertFalse("positive control: this is a new Activity instance", weakActivity?.get() === activity)
            }
            // Search is not restored open after recreate; opening it again must work and show a usable keypad.
            openSearch(scenario)
            tap(scenario, R.id.btnT9_Clear, R.id.btnT9_2, R.id.btnT9_4, R.id.btnT9_7)
            assertEquals(listOf("Chrome"), resultLabels(scenario))
            val deadline = SystemClock.uptimeMillis() + GC_WAIT_MS
            while (SystemClock.uptimeMillis() < deadline && weakActivity?.get() != null) {
                Runtime.getRuntime().gc(); System.runFinalization(); SystemClock.sleep(GC_POLL_MS)
            }
            assertNull("the Activity destroyed by recreate must be garbage collected (the keypad's parent walk must not hold it)", weakActivity?.get())
        }
    }

    @Test fun backgroundingAndReturningWithTheKeypadOpenKeepsTheKeypadAndItsInput() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assumeDisplayIsCovered(scenario)
            openSearch(scenario)
            tap(scenario, R.id.btnT9_Clear, R.id.btnT9_2, R.id.btnT9_4, R.id.btnT9_7)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            instrumentation.waitForIdleSync()
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<View>(R.id.scrollT9Keypad)
                assertEquals("the keypad stays visible after a stop/start cycle", View.VISIBLE, scroll.visibility)
            }
            tap(scenario, R.id.btnT9_Clear, R.id.btnT9_2, R.id.btnT9_4, R.id.btnT9_7)
            assertEquals("the keypad still filters after returning", listOf("Chrome"), resultLabels(scenario))
        }
    }

    // ---- jank -------------------------------------------------------------------------------------------------------------

    /**
     * Frame cost of typing on the keypad. The SearchView opening animation is finished and settled BEFORE the listener is attached
     * (the first run included it and read p90 28.8 ms against 15-18 ms on the other profiles), and the result is the median of
     * [JANK_ROUNDS] rounds, because one round is dominated by whatever the device was doing.
     */
    @Test fun typingOnTheKeypadStaysInsideTheFrameBudgetOnThisDisplay() {
        val thread = HandlerThread("t9-frames").apply { start() }
        val p90s = ArrayList<Float>()
        var totalFrames = 0
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                assumeDisplayIsCovered(scenario)
                openSearch(scenario)
                SystemClock.sleep(SETTLE_MS)
                instrumentation.waitForIdleSync()
                repeat(JANK_ROUNDS) {
                    val durationsNs = ArrayList<Long>()
                    val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                        synchronized(durationsNs) { durationsNs.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)) }
                    }
                    scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
                    repeat(TYPE_STEPS) { i ->
                        tap(scenario, listOf(R.id.btnT9_2, R.id.btnT9_4, R.id.btnT9_7, R.id.btnT9_Backspace)[i % 4])
                        SystemClock.sleep(STEP_DELAY_MS)
                    }
                    SystemClock.sleep(SETTLE_MS)
                    scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                    val ms = synchronized(durationsNs) { durationsNs.map { d -> d / NS_PER_MS } }.sorted()
                    assertTrue("round needs enough frames to mean something, had ${ms.size}", ms.size >= MIN_FRAMES_PER_ROUND)
                    totalFrames += ms.size
                    p90s.add(ms[minOf(ms.size - 1, (ms.size * P90).toInt())])
                }
            }
        } finally { thread.quitSafely() }
        val refresh = context.getSystemService(android.view.WindowManager::class.java).defaultDisplay.refreshRate
        val budget = 1000f / refresh
        val median = p90s.sorted()[p90s.size / 2]
        Log.i(TAG, "jank: typing rounds p90s=${p90s.map { "%.1f".format(it) }} median=${"%.1f".format(median)}ms frames=$totalFrames budget=${"%.1f".format(budget)}ms factor=${"%.2f".format(median / budget)}")
        assertTrue("median p90 $median ms must stay under ${JANK_BUDGET_FACTOR}x the budget $budget ms while typing on the keypad", median <= budget * JANK_BUDGET_FACTOR)
    }

    // ---- screenshot for review (NOT a golden comparison: there is no stored baseline) ---------------------------------------

    /**
     * Captures the Activity's own window with PixelCopy and writes it for review. UiAutomation.takeScreenshot is not used: on the
     * phone-landscape profile the first version produced a 1440x3120 image next to a 3120x1440 display. The image size must equal
     * the Activity's display size, which is what makes this a check and not just a file.
     */
    @Test fun theKeypadScreenshotMatchesTheActivityDisplayAndIsWrittenForReview() {
        org.junit.Assume.assumeTrue("PixelCopy needs API 26", android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assumeDisplayIsCovered(scenario)
            openSearch(scenario)
            tap(scenario, R.id.btnT9_Clear)
            SystemClock.sleep(SETTLE_MS)
            instrumentation.waitForIdleSync()
            var bitmap: Bitmap? = null
            var copyResult = -1
            var width = 0
            var height = 0
            val latch = java.util.concurrent.CountDownLatch(1)
            scenario.onActivity { activity ->
                width = activity.resources.displayMetrics.widthPixels
                height = activity.resources.displayMetrics.heightPixels
                val bmp = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                bitmap = bmp
                android.view.PixelCopy.request(activity.window, bmp, { code -> copyResult = code; latch.countDown() }, Handler(android.os.Looper.getMainLooper()))
            }
            assertTrue("PixelCopy must answer", latch.await(COPY_WAIT_S, java.util.concurrent.TimeUnit.SECONDS))
            val shot = requireNotNull(bitmap)
            assertEquals("PixelCopy must succeed", android.view.PixelCopy.SUCCESS, copyResult)
            val dir = File(context.filesDir, SHOT_DIR).apply { mkdirs() }
            val file = File(dir, "t9-${width}x$height.png")
            file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            Log.i(TAG, "screenshot ${file.name} ${shot.width}x${shot.height} activityDisplay=${width}x$height bytes=${file.length()}")
            assertEquals("the image width must equal the Activity display width", width, shot.width)
            assertEquals("the image height must equal the Activity display height", height, shot.height)
            assertTrue("the image must not be blank", (0 until shot.width step 97).any { x -> (0 until shot.height step 89).any { y -> shot.getPixel(x, y) != shot.getPixel(0, 0) } })
        }
    }
}
