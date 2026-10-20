package com.mckimquyen.ui

import android.os.SystemClock
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.search.AppSearchEngine
import com.mckimquyen.search.SearchHistoryStore
import com.mckimquyen.services.AppEventManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEARCH-011: an app the user hid must not show in the Recent apps panel, whether it was hidden
 * before the panel opened, while it is open, or across rotation / dismiss + reopen.
 * Restores the history store and the in-memory snapshot; the Room test restores the row it touched.
 */
@RunWith(AndroidJUnit4::class)
class RecentAppsPanelHiddenAppsTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val store get() = SearchHistoryStore(context)

    private val camera = App(label = "Camera", packageName = "pkg.s011.camera", name = "CameraActivity")
    private val notes = App(label = "Notes", packageName = "pkg.s011.notes", name = "NotesActivity")
    private var originalApps: ArrayList<App>? = null

    @Before
    fun setUp() {
        // A real PackageManager scan would replace the fixture list mid-test.
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        store.clear()
        store.recordLaunch(AppSearchEngine.componentKey(camera))
        store.recordLaunch(AppSearchEngine.componentKey(notes))
    }

    @After
    fun tearDown() {
        store.clear()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun showPanel(scenario: ActivityScenario<ActHome>, apps: List<App>): RecentAppsPanelFragment {
        val fragment = RecentAppsPanelFragment()
        scenario.onActivity { activity ->
            RAppsSingleton.instance.apps = ArrayList(apps)
            fragment.show(activity.supportFragmentManager, RecentAppsPanelFragment.TAG)
        }
        instrumentation.waitForIdleSync()
        return fragment
    }

    private fun labels(fragment: RecentAppsPanelFragment): List<String> {
        val recycler = requireNotNull(fragment.view).findViewById<RecyclerView>(R.id.rvRecentApps)
        return (0 until (recycler.adapter?.itemCount ?: 0)).mapNotNull {
            recycler.findViewHolderForAdapterPosition(it)?.itemView
                ?.findViewById<TextView>(R.id.tvSearchResultLabel)?.text?.toString()
        }
    }

    private fun awaitLabels(fragment: RecentAppsPanelFragment, expected: List<String>, what: String) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        var last = emptyList<String>()
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            last = labels(fragment)
            if (last == expected) return
            SystemClock.sleep(POLL_MS)
        }
        assertEquals(what, expected, last)
    }

    @Test
    fun appHiddenBeforeOpening_isNotListed() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario, listOf(camera, notes.copy(isVisible = false)))
            awaitLabels(fragment, listOf("Camera"), "hidden Notes must not be listed")
        }
    }

    @Test
    fun appHiddenWhilePanelIsOpen_disappearsWithoutReopening() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario, listOf(camera, notes))
            awaitLabels(fragment, listOf("Notes", "Camera"), "both visible at first")

            scenario.onActivity {
                RAppsSingleton.instance.updateAppState(notes.packageName.toString(), notes.name.toString(), isVisible = false)
                AppEventManager.notifyVisibilityChanged()
            }

            awaitLabels(fragment, listOf("Camera"), "Notes must vanish when hidden while the panel is open")
        }
    }

    @Test
    fun appUnhiddenWhilePanelIsOpen_returnsInItsRecentPosition() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario, listOf(camera, notes.copy(isVisible = false)))
            awaitLabels(fragment, listOf("Camera"), "Notes hidden at first")

            scenario.onActivity {
                RAppsSingleton.instance.updateAppState(notes.packageName.toString(), notes.name.toString(), isVisible = true)
                AppEventManager.notifyVisibilityChanged()
            }

            awaitLabels(fragment, listOf("Notes", "Camera"), "Notes must come back at the top")
        }
    }

    @Test
    fun allRecentAppsHidden_showsTheEmptyState() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario, listOf(camera.copy(isVisible = false), notes.copy(isVisible = false)))
            val view = requireNotNull(fragment.view)
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            while (SystemClock.uptimeMillis() < deadline &&
                view.findViewById<TextView>(R.id.tvRecentAppsEmpty).visibility != android.view.View.VISIBLE) {
                instrumentation.waitForIdleSync(); SystemClock.sleep(POLL_MS)
            }
            assertEquals(android.view.View.VISIBLE, view.findViewById<TextView>(R.id.tvRecentAppsEmpty).visibility)
            assertEquals(android.view.View.GONE, view.findViewById<RecyclerView>(R.id.rvRecentApps).visibility)
        }
    }

    /** Interruption: the Activity is recreated (rotation / process config change) while the panel is open. */
    @Test
    fun hiddenAppStaysHidden_afterTheActivityIsRecreatedWithThePanelOpen() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            showPanel(scenario, listOf(camera, notes.copy(isVisible = false)))

            scenario.recreate()
            instrumentation.waitForIdleSync()

            var restored: RecentAppsPanelFragment? = null
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            while (SystemClock.uptimeMillis() < deadline && restored?.view == null) {
                scenario.onActivity {
                    restored = it.supportFragmentManager.findFragmentByTag(RecentAppsPanelFragment.TAG) as? RecentAppsPanelFragment
                }
                instrumentation.waitForIdleSync(); SystemClock.sleep(POLL_MS)
            }
            val fragment = requireNotNull(restored) { "panel must be restored after recreate" }
            awaitLabels(fragment, listOf("Camera"), "hidden Notes must not reappear after recreate")
        }
    }

    /** Interruption: the panel is dismissed mid-way, the app is hidden, then the panel is opened again. */
    @Test
    fun dismissThenHideThenReopen_doesNotListTheHiddenApp() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val first = showPanel(scenario, listOf(camera, notes))
            awaitLabels(first, listOf("Notes", "Camera"), "both visible at first")
            scenario.onActivity { first.dismissAllowingStateLoss() }
            instrumentation.waitForIdleSync()

            val second = showPanel(scenario, listOf(camera, notes.copy(isVisible = false)))
            awaitLabels(second, listOf("Camera"), "hidden Notes must not be listed after reopening")
        }
    }

    /** Integration: real Room + real AppPersistent path, the same call the Apps tab eye icon makes. */
    @Test
    fun hidingThroughAppPersistent_updatesRoomAndThePanel() {
        AppDatabase.init(context)
        val identifier = AppPersistent.generateIdentifier(notes.packageName.toString(), notes.name.toString())
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario, listOf(camera, notes))
            awaitLabels(fragment, listOf("Notes", "Camera"), "both visible at first")

            try {
                scenario.onActivity {
                    AppPersistent.setAppVisibility(notes.packageName.toString(), notes.name.toString(), false)
                    AppEventManager.notifyVisibilityChanged()
                }
                val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
                var stored: Boolean? = null
                while (SystemClock.uptimeMillis() < deadline && stored != false) {
                    stored = runBlocking { AppDatabase.getInstance().appPersistentDao().findByIdentifier(identifier)?.appVisible }
                    SystemClock.sleep(POLL_MS)
                }
                assertEquals("the hide must reach Room", false, stored)
                awaitLabels(fragment, listOf("Camera"), "the panel must follow the real hide")
            } finally {
                runBlocking { AppDatabase.getInstance().appPersistentDao().findByIdentifier(identifier)?.let { AppDatabase.getInstance().appPersistentDao().delete(it) } }
            }
            assertTrue(
                "fixture row must be removed",
                runBlocking { AppDatabase.getInstance().appPersistentDao().findByIdentifier(identifier) } == null
            )
        }
    }

    /**
     * The panel now subscribes to process-wide LiveData (AppEventManager is an object). If those
     * observers outlived the view, every dismissed panel would stay reachable and run refreshList()
     * on a torn-down adapter. Prove both: the dismissed fragment becomes collectable, and a later
     * event does not touch it.
     */
    @Test
    fun dismissedPanel_isCollectable_andLaterEventsDoNotReachIt() {
        var weak: java.lang.ref.WeakReference<RecentAppsPanelFragment>? = null
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            run {
                val fragment = showPanel(scenario, listOf(camera, notes))
                awaitLabels(fragment, listOf("Notes", "Camera"), "visible before dismiss")
                weak = java.lang.ref.WeakReference(fragment)
                scenario.onActivity { fragment.dismissAllowingStateLoss() }
            }
            instrumentation.waitForIdleSync()

            // Events fired after the panel is gone must be harmless (no crash on the torn-down adapter).
            instrumentation.runOnMainSync {
                AppEventManager.notifyVisibilityChanged()
                AppEventManager.notifyAppsLoaded()
            }
            instrumentation.waitForIdleSync()

            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            while (SystemClock.uptimeMillis() < deadline && weak?.get() != null) {
                Runtime.getRuntime().gc()
                System.runFinalization()
                SystemClock.sleep(POLL_MS)
            }
            assertEquals("a dismissed panel must be garbage collected", null, weak?.get())
        }
    }

    /**
     * Cost of the new filter + observers: open the panel with the history store's maximum recent
     * list over a 600-app snapshot (half hidden), then fire 30 visibility events while it is open.
     * Bounds the main-thread time of a refresh so a slow filter shows up as a failure, and logs the
     * numbers (tag S011PERF) for the audit.
     */
    @Test
    fun refreshStaysCheap_withALargeSnapshotAndManyEvents() {
        val apps = (1..LARGE_APP_COUNT).map {
            App(label = "App$it", packageName = "pkg.s011.bulk$it", name = "Main$it", isVisible = it % 2 == 0)
        }
        store.clear()
        apps.forEach { store.recordLaunch(AppSearchEngine.componentKey(it)) }

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario, apps)
            instrumentation.waitForIdleSync()

            val times = LongArray(EVENT_COUNT)
            repeat(EVENT_COUNT) { i ->
                val start = System.nanoTime()
                instrumentation.runOnMainSync { AppEventManager.notifyVisibilityChanged() }
                instrumentation.waitForIdleSync()
                times[i] = (System.nanoTime() - start) / 1_000_000
            }
            val sorted = times.sorted()
            val median = sorted[sorted.size / 2]
            val worst = sorted.last()
            android.util.Log.i("S011PERF", "apps=$LARGE_APP_COUNT events=$EVENT_COUNT median=${median}ms worst=${worst}ms shown=${labels(fragment).size}")
            assertTrue("median refresh $median ms must stay under one second", median < MAX_MEDIAN_MS)
        }
    }

    private companion object {
        const val LARGE_APP_COUNT = 600
        const val EVENT_COUNT = 30
        const val MAX_MEDIAN_MS = 1_000L
        const val TIMEOUT_MS = 6_000L
        const val POLL_MS = 100L
    }
}
