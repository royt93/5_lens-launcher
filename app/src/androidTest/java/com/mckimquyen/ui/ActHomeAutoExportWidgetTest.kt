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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * B3 (test-audit): [ActHome.consumeAutoExportExtra]'s own doc comment explains why the one-shot
 * flag exists - `ActHome` is `launchMode="singleTask"`, so a later rotation/recreate must never
 * re-trigger the FISH-011 auto-export (from Settings -> tab Lens's share button). Nothing in the
 * test tree exercised the receiving side (only `FrmLensPerLensWidgetTest` proves the *sending*
 * intent carries the extra). Follows the same `lensShareLauncher` test-seam pattern as
 * `ActHomeLensShareIntegrationTest`.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeAutoExportWidgetTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // Found while running the full suite (not in isolation): this test originally trusted
    // whatever lens DB state ~350 other tests left behind. When that state had no lens matching
    // ActHome's active-lens id, exportActiveLensImage() returns early (`matched == null`) and
    // lensShareLauncher is never called - the test then times out waiting on a latch that was
    // never going to fire, looking exactly like a real regression. Pin a known single-default-lens
    // state instead, same as ActHomeLensShareIntegrationTest already does for the same reason.
    @Before
    fun setup() = cleanDb()

    @After
    fun tearDown() = cleanDb()

    private fun cleanDb(): Unit = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    @Test
    fun autoExportExtra_firesExactlyOnce_andNeverAgainAfterRecreate() {
        // ActHome is launchMode="singleTask" - in real usage (FrmLens's share button) the task
        // is already alive, so this always arrives via onNewIntent, never a fresh onCreate. A
        // cold ActivityScenario.launch(intentWithExtra) instead races the production fix that
        // now fires the export as early as the first page bind (found running this test: the
        // export can win that race before this test even gets a chance to install
        // lensShareLauncher, so a real system chooser fired instead of our test seam). Launching
        // plain, then delivering the extra through a real second startActivity() call - which
        // singleTask routes to onNewIntent on this same instance through the actual OS dispatch,
        // unlike calling activity.onNewIntent(...) directly (tried first; that desynced
        // ActivityScenario's own lifecycle tracking enough to break recreate() below) - both
        // matches production and gives us a deterministic window to install the seam first.
        val scenario = ActivityScenario.launch(ActHome::class.java)

        val exportCount = AtomicInteger(0)
        val firstFireLatch = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.lensShareLauncher = ActHome.LensShareLauncher {
                exportCount.incrementAndGet()
                firstFireLatch.countDown()
            }
        }
        InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
            Intent(InstrumentationRegistry.getInstrumentation().targetContext, ActHome::class.java).apply {
                putExtra(ActHome.EXTRA_AUTO_EXPORT_LENS, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        assertTrue(
            "the auto-export extra must trigger exactly one export shortly after onNewIntent",
            firstFireLatch.await(5, TimeUnit.SECONDS)
        )
        assertEquals(1, exportCount.get())

        // Found running this test (100% reproducible, not a flake - confirmed by diffing against
        // ActHomeMultiLensWidgetTest, whose plain launch()+recreate() with no intermediate
        // onNewIntent passes in ~2s): once a real onNewIntent has been externally delivered to a
        // singleTask Activity via context.startActivity() above, ActivityScenario's own lifecycle
        // tracking never recovers - scenario.recreate()'s internal moveToState(RESUMED) wait hangs
        // to its timeout even though the real on-screen Activity is fully resumed (proven by
        // firstFireLatch above firing off real UI-thread work). This is a test-harness limitation,
        // not a production bug: ActHome.onNewIntent() calls setIntent(intent) and
        // intent.removeExtra(...) mutates that same Intent object in place, so the one-shot
        // guarantee holds structurally regardless of how recreate() is driven. Drive the real
        // recreate() directly - resolving the pre-recreate activity via scenario.onActivity{}
        // still works (recreate() itself is well underway before the earlier hang, which came only
        // from the post-wait) - and pick up the new instance through the app's own
        // ActivityLifecycleCallbacks instead of trusting ActivityScenario post-onNewIntent.
        val newInstanceLatch = CountDownLatch(1)
        val recreatedActivity = AtomicReference<ActHome?>(null)
        val application = context.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is ActHome) {
                    activity.lensShareLauncher = ActHome.LensShareLauncher { exportCount.incrementAndGet() }
                    recreatedActivity.set(activity)
                    newInstanceLatch.countDown()
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
        try {
            scenario.onActivity { it.recreate() }
            assertTrue("recreate() must produce a new ActHome instance", newInstanceLatch.await(5, TimeUnit.SECONDS))
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }

        // Poll for an absence rather than one blind sleep: the one-shot flag is consumed on the
        // *original* intent (onCreate reads getIntent(), not onNewIntent - recreate() replays the
        // same intent without calling onNewIntent), so a regression here would show up as a
        // second export landing at an unpredictable point during/after the recreate.
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            assertEquals(
                "recreate() must never re-trigger the one-shot auto-export",
                1,
                exportCount.get()
            )
            Thread.sleep(100)
        }

        // ActivityScenario no longer recognizes this Activity after external onNewIntent changed
        // its intent (see the lifecycle workaround above), so close() blocks forever waiting for a
        // transition it deliberately ignores. Finish the real recreated Activity directly instead.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { recreatedActivity.get()?.finish() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
