package com.mckimquyen.services

import android.app.Application
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TaskSortAppsTest {
    private lateinit var application: Application

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication()
        RAppsSingleton.instance.clearAllData()
    }

    @After
    fun tearDown() = RAppsSingleton.instance.clearAllData()

    @Test
    fun `new edit cancels old sort and only newest snapshot commits`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstStarted = CompletableDeferred<Unit>()
        var calls = 0
        val task = task(dispatcher) {
            calls += 1
            if (calls == 1) {
                firstStarted.complete(Unit)
                awaitCancellation()
            }
            listOf(app("newest"))
        }

        val oldJob = task.execute()
        runCurrent()
        assertTrue(firstStarted.isCompleted)
        task.execute()
        advanceUntilIdle()

        assertTrue(oldJob.isCancelled)
        assertEquals(listOf("newest"), RAppsSingleton.instance.apps!!.map { it.packageName })
    }

    @Test
    fun `failed sort preserves the last committed snapshot`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        RAppsSingleton.instance.apps = arrayListOf(app("last-good"))
        val task = task(dispatcher) { error("sort failed") }

        task.execute()
        advanceUntilIdle()

        assertEquals(listOf("last-good"), RAppsSingleton.instance.apps!!.map { it.packageName })
    }

    @Test
    fun `explicit cancel prevents a pending sort commit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val task = task(dispatcher) { listOf(app("must-not-commit")) }

        val job = task.execute()
        task.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(RAppsSingleton.instance.apps!!.isEmpty())
    }

    @Test
    fun `sort never overwrites a snapshot committed by a refresh while it was loading`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        RAppsSingleton.instance.apps = arrayListOf(app("old"))
        var calls = 0
        val task = task(dispatcher) {
            calls += 1
            if (calls == 1) {
                // A package refresh commits a newer generation while this sort is still loading.
                RAppsSingleton.instance.replaceSnapshot(listOf(app("old"), app("installed-meanwhile")), emptyMap())
                listOf(app("old"))
            } else {
                RAppsSingleton.instance.apps!!.sortedBy { it.packageName.toString() }
            }
        }

        task.execute()
        advanceUntilIdle()

        assertEquals(
            listOf("installed-meanwhile", "old"),
            RAppsSingleton.instance.apps!!.map { it.packageName.toString() }
        )
        assertEquals("stale sort result must be discarded and recomputed once", 2, calls)
    }

    @Test
    fun `sort gives up after bounded retries when snapshot keeps moving and keeps the newest list`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        RAppsSingleton.instance.apps = arrayListOf(app("seed"))
        var calls = 0
        val task = task(dispatcher) {
            calls += 1
            RAppsSingleton.instance.replaceSnapshot(listOf(app("newer-$calls")), emptyMap())
            listOf(app("stale"))
        }

        task.execute()
        advanceUntilIdle()

        assertEquals("retries must be bounded", 3, calls)
        assertEquals(listOf("newer-3"), RAppsSingleton.instance.apps!!.map { it.packageName.toString() })
    }

    @Test
    fun `replaceAppsIfUnchanged rejects a stale version and accepts the current one`() {
        val singleton = RAppsSingleton.instance
        singleton.apps = arrayListOf(app("a"))
        val version = singleton.snapshotVersion

        singleton.replaceSnapshot(listOf(app("b")), emptyMap())
        assertEquals(false, singleton.replaceAppsIfUnchanged(version, listOf(app("stale"))))
        assertEquals(listOf("b"), singleton.apps!!.map { it.packageName.toString() })

        assertEquals(true, singleton.replaceAppsIfUnchanged(singleton.snapshotVersion, listOf(app("c"))))
        assertEquals(listOf("c"), singleton.apps!!.map { it.packageName.toString() })
    }

    private fun task(
        dispatcher: TestDispatcher,
        loader: suspend () -> List<App>
    ) = TaskSortApps(
        context = application,
        application = application,
        scope = CoroutineScope(dispatcher),
        ioDispatcher = dispatcher,
        mainDispatcher = dispatcher,
        snapshotLoader = loader
    )

    private fun app(packageName: String) = App(
        label = packageName,
        packageName = packageName,
        name = "$packageName.Main"
    )
}
