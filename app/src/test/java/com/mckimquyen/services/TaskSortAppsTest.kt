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
