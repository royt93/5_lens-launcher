package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeRingRotateWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private val dao get() = AppDatabase.getInstance().appPersistentDao()
    private var originalApps: ArrayList<App>? = null
    private var roomSnapshot: LensManagerRoomSnapshot? = null

    private val fixture = listOf(
        row("f", PinnedZone.START, false), row("d", PinnedZone.NONE, true), row("e", PinnedZone.NONE, true),
        row("a", PinnedZone.NONE, false), row("b", PinnedZone.NONE, false), row("c", PinnedZone.NONE, false)
    )
    private fun row(n: String, zone: PinnedZone, fav: Boolean) = App(
        id = n.hashCode(), label = n.uppercase(), packageName = "com.test.ring.$n", name = "Main",
        isVisible = true, pinnedZone = zone, isFavorite = fav
    )

    @Before fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        roomSnapshot = LensManagerRoomSnapshot.capture(context)
        originalApps = RAppsSingleton.instance.apps
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        settings.setLauncherMode(LauncherMode.FISHEYE)
        settings.saveLensFrozen(lensId, false)
        settings.saveSmartFocusBias(lensId, false)
        settings.saveLensAppScope(lensId, LensAppScope.ALL)
        settings.saveLensHiddenAppIds(lensId, emptySet())
        runBlocking {
            dao.deleteForLens(lensId)
            fixture.forEachIndexed { i, a -> dao.insert(AppPersistent.defaults(a.packageName.toString(), "Main", lensId)
                .copy(orderNumber = i, isFavorite = a.isFavorite, pinnedZone = a.pinnedZone.name)) }
        }
        RAppsSingleton.instance.apps = ArrayList(fixture)
    }

    @After fun tearDown() {
        UtilSettings(context).saveLensFrozen(lensId, false)
        roomSnapshot?.restore(context)
        RAppsSingleton.instance.apps = originalApps
    }

    private fun storedLabels() = runBlocking { dao.getAllForLens(lensId) }.sortedBy { it.orderNumber }
        .map { it.packageName?.substringAfterLast('.')?.uppercase() }

    private fun awaitGrid(scenario: ActivityScenario<ActHome>): LensView {
        var view: LensView? = null
        val deadline = System.currentTimeMillis() + WAIT_MS
        var size = 0
        while (System.currentTimeMillis() < deadline && size != fixture.size) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { view = it.findViewById(R.id.lensViews); size = view?.displayedApps?.size ?: 0 }
            if (size != fixture.size) Thread.sleep(POLL_MS)
        }
        assertEquals("the grid never held the fixture", fixture.size, size)
        return requireNotNull(view) { "assigned in onActivity above" }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && !condition()) { instrumentation.waitForIdleSync(); Thread.sleep(POLL_MS) }
        assertTrue("timed out: $what", condition())
    }

    private fun labels(view: LensView) = view.displayedApps.map { it.label.toString() }

    @Test fun oneHostStepWritesTheGroupRotatedOrderThenFreezesAndKeepsItOnTheView() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            assertFalse(UtilSettings(context).isLensFrozen(lensId))
            var ok = false
            scenario.onActivity { ok = view.rotateAndCommit(1) }
            assertTrue(ok)
            waitUntil("frozen after the confirmed write") { UtilSettings(context).isLensFrozen(lensId) }
            waitUntil("rows rewritten") { storedLabels() == listOf("F", "E", "D", "B", "C", "A") }
            scenario.onActivity { assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(view)) }
            assertNull(view.ringPendingTokenForTest())
        }
    }

    @Test fun theFrozenFlagIsNotSetBeforeTheWriteIsConfirmed() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val gate = java.util.concurrent.CountDownLatch(1)
            val parked = java.util.concurrent.CountDownLatch(1)
            AppPersistent.beforeOrderCommitForTest = { parked.countDown(); gate.await(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) }
            try {
                scenario.onActivity { assertTrue(view.rotateAndCommit(1)) }
                assertTrue(parked.await(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS))
                assertFalse("write is still parked, so the lens must not be frozen yet", UtilSettings(context).isLensFrozen(lensId))
                gate.countDown()
                waitUntil("frozen after release") { UtilSettings(context).isLensFrozen(lensId) }
            } finally {
                AppPersistent.beforeOrderCommitForTest = null
                gate.countDown()
            }
        }
    }

    @Test fun anAlreadyFrozenLensStillRotatesAndStaysFrozen() {
        UtilSettings(context).saveLensFrozen(lensId, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            scenario.onActivity { assertTrue(view.rotateAndCommit(1)) }
            waitUntil("rows rewritten") { storedLabels() == listOf("F", "E", "D", "B", "C", "A") }
            assertTrue(UtilSettings(context).isLensFrozen(lensId))
            scenario.onActivity { assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(view)) }
        }
    }

    @Test fun smartFocusDoesNotReshuffleAfterARotation() {
        UtilSettings(context).saveSmartFocusBias(lensId, true)
        runBlocking { dao.insert(AppPersistent.defaults("com.test.ring.c", "Main", lensId).copy(orderNumber = 5, openCount = 999)) }
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            scenario.onActivity { assertTrue(view.rotateAndCommit(1)) }
            waitUntil("frozen") { UtilSettings(context).isLensFrozen(lensId) }
            scenario.onActivity { view.refreshSmartFocus() }
            scenario.onActivity {
                assertFalse(view.smartFocusAppliedForTest)
                assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(view))
            }
        }
    }

    @Test fun aFailedWriteRestoresTheViewAndNeverFreezes() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val before = labels(view)
            val missing = "never-created-lens"                                   // no LENS_WORKSPACE row: the write must fail
            scenario.onActivity { view.lensId = missing; view.setApps(ArrayList(fixture)); assertTrue(view.rotateAndCommit(1)) }
            waitUntil("view restored after the failed write") { labels(view) == before && view.ringPendingTokenForTest() == null }
            assertFalse(UtilSettings(context).isLensFrozen(missing))
            assertTrue(runBlocking { dao.getAllForLens(missing) }.isEmpty())
            scenario.onActivity { view.lensId = lensId }
        }
    }

    @Test fun anAnswerForAnotherLensAfterAPageRecycleNeverResolvesTheRecycledView() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val gate = java.util.concurrent.CountDownLatch(1)
            val parked = java.util.concurrent.CountDownLatch(1)
            AppPersistent.beforeOrderCommitForTest = { parked.countDown(); gate.await(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) }
            try {
                scenario.onActivity { assertTrue(view.rotateAndCommit(1)) }
                assertTrue(parked.await(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS))
                val recycledFor = "recycled-for-another-lens"
                scenario.onActivity { view.lensId = recycledFor }                // the pager reused the view for another lens
                assertNull("re-binding the view dropped the pending request", view.ringPendingTokenForTest())
                gate.countDown()
                waitUntil("the write for the original lens is confirmed and frozen") { UtilSettings(context).isLensFrozen(lensId) }
                waitUntil("rows rewritten") { storedLabels() == listOf("F", "E", "D", "B", "C", "A") }
                instrumentation.waitForIdleSync()
                assertFalse("the recycled view's lens must not be frozen by the other lens' answer", UtilSettings(context).isLensFrozen(recycledFor))
                assertNull(view.ringPendingTokenForTest())
                scenario.onActivity { view.lensId = lensId }
            } finally {
                AppPersistent.beforeOrderCommitForTest = null
                gate.countDown()
            }
        }
    }

    @Test fun pausingHomeCancelsAnInProgressPreview() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val before = labels(view)
            scenario.onActivity { view.toggleFocusLockAtForTest(view.width / 2f, view.height / 2f) }
            scenario.onActivity { activity -> activity.startRingPreviewForTest(1) }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)      // onPause
            scenario.onActivity { assertEquals(before, labels(view)); assertFalse(view.isRingRotatingForTest()) }
        }
    }

    private companion object { const val WAIT_MS = 8_000L; const val POLL_MS = 50L }
}
