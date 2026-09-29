package com.mckimquyen.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * FISH-013: proves the durable-flag fix for the auto-export-lens race. Simulates "process died
 * between FrmLens's Share tap and export completing" by writing the pending flag directly to
 * disk (bypassing the Intent extra entirely, same as a real cold ActivityManager-driven recreate
 * would leave it) and launching a fresh ActHome with no intent extra at all - only the durable
 * disk state should be enough to resurrect and complete the export.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeAutoExportPersistenceIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var utilSettings: UtilSettings

    @Before
    fun setup() {
        utilSettings = UtilSettings(context)
        cleanDb()
    }

    @After
    fun tearDown() {
        utilSettings.clearPendingAutoExportLens()
        cleanDb()
    }

    private fun cleanDb(): Unit = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    @Test
    fun pendingFlagOnDisk_resurrectsExportOnColdLaunch_andClearsFlag() {
        // Simulate process kill between FrmLens's tap and export completion: the flag is written
        // directly to disk, with NO Intent extra passed to ActivityScenario at all.
        utilSettings.setPendingAutoExportLens(true)
        assertTrue(utilSettings.hasPendingAutoExportLens())

        val exportCount = AtomicInteger(0)
        val exportLatch = CountDownLatch(1)
        var capturedIntent: Intent? = null

        // The resurrected export fires from disk state during this instance's very first
        // onCreate() - before scenario.onActivity{} would get a chance to run post-launch, so
        // (unlike ActHomeAutoExportWidgetTest's initial-launch phase, which delivers the extra
        // via a *second* startActivity() after the seam is installed) the seam must be installed
        // via ActivityLifecycleCallbacks.onActivityCreated, which fires synchronously as part of
        // Activity creation - strictly before the bindLensView/refreshLensList post() callbacks
        // that actually perform the export get a chance to run on the message queue. Same
        // technique ActHomeAutoExportWidgetTest's own recreate() phase already relies on.
        val application = context.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is ActHome) {
                    activity.lensShareLauncher = ActHome.LensShareLauncher { intent ->
                        capturedIntent = intent
                        exportCount.incrementAndGet()
                        exportLatch.countDown()
                    }
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        val scenario: ActivityScenario<ActHome>
        try {
            scenario = ActivityScenario.launch(ActHome::class.java)
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }

        assertTrue(
            "resurrected pending auto-export must trigger export shortly after launch",
            exportLatch.await(5, TimeUnit.SECONDS)
        )
        assertEquals(1, exportCount.get())
        assertNotNull("resurrected export must still produce a real chooser intent", capturedIntent)

        assertFalse(
            "pending flag must be cleared from disk after export completes",
            utilSettings.hasPendingAutoExportLens()
        )

        scenario.close()
    }

    @Test
    fun exportWithNoBoundLensView_clearsPendingFlagInsteadOfResurrectingForever() {
        // Zero lenses means ViewPager2 never binds a page, so ActHome.lensViews stays null -
        // exactly the "view == null" unrecoverable branch in exportActiveLensImage(), which
        // shares its clear+Toast path with "the requesting lens no longer exists". Calling the
        // package-visible method directly (rather than trying to time a real DB-delete race
        // against ViewPager2's bind) makes this deterministic instead of flaky.
        runBlocking { dao.getAll().forEach { dao.delete(it) } }
        utilSettings.setPendingAutoExportLens(true)

        val scenario = ActivityScenario.launch(ActHome::class.java)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.onActivity { activity -> activity.exportActiveLensImage() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertFalse(
            "an export call with no bound lens view must still clear the durable pending flag" +
                " instead of resurrecting forever",
            utilSettings.hasPendingAutoExportLens()
        )

        scenario.close()
    }
}
