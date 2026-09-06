package com.mckimquyen.services

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class TaskUpdateAppsTest {
    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var packageManager: PackageManager

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication()
        context = application
        packageManager = context.packageManager
        RAppsSingleton.instance.clearAllData()
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.clearAllData()
    }

    @Test
    fun `completed refresh commits an immutable ready snapshot`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = arrayListOf(app("one"))
        val task = task(dispatcher) { AppRefreshSnapshot(source) }

        task.execute()
        advanceUntilIdle()
        source.add(app("mutated-after-load"))

        val ready = task.state.value as AppRefreshState.Ready
        assertEquals(1L, ready.generation)
        assertEquals(listOf("one"), ready.apps.map { it.packageName })
        assertEquals(listOf("one"), RAppsSingleton.instance.apps!!.map { it.packageName })
    }

    @Test
    fun `new request cancels old generation and only newest result commits`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val calls = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        val task = task(dispatcher) {
            if (calls.incrementAndGet() == 1) {
                firstStarted.complete(Unit)
                awaitCancellation()
            }
            AppRefreshSnapshot(listOf(app("newest")))
        }

        val oldJob = task.execute()
        runCurrent()
        assertTrue(firstStarted.isCompleted)
        task.execute()
        advanceUntilIdle()

        assertTrue(oldJob.isCancelled)
        assertEquals(listOf("newest"), RAppsSingleton.instance.apps!!.map { it.packageName })
        assertEquals(2L, (task.state.value as AppRefreshState.Ready).generation)
    }

    @Test
    fun `loader failure exposes error and preserves last good snapshot`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        RAppsSingleton.instance.apps = arrayListOf(app("last-good"))
        val task = task(dispatcher) { error("package query failed") }

        task.execute()
        advanceUntilIdle()

        val state = task.state.value as AppRefreshState.Error
        assertEquals(1L, state.generation)
        assertEquals("package query failed", state.cause.message)
        assertEquals(listOf("last-good"), RAppsSingleton.instance.apps!!.map { it.packageName })
    }

    @Test
    fun `cancel invalidates generation and prevents commit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val task = task(dispatcher, debounceMillis = 100) {
            AppRefreshSnapshot(listOf(app("must-not-commit")))
        }

        val job = task.execute()
        task.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(RAppsSingleton.instance.apps!!.isEmpty())
        assertFalse(task.state.value is AppRefreshState.Ready)
    }

    @Test
    fun `debounce burst invokes loader once for the newest generation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val calls = AtomicInteger(0)
        val task = task(dispatcher, debounceMillis = 150) {
            calls.incrementAndGet()
            AppRefreshSnapshot(listOf(app("newest")))
        }

        task.execute()
        task.execute()
        task.execute()
        advanceUntilIdle()

        assertEquals(1, calls.get())
        assertEquals(3L, (task.state.value as AppRefreshState.Ready).generation)
    }

    @Test
    fun `a newer refresh recovers from an error state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val calls = AtomicInteger(0)
        val task = task(dispatcher) {
            if (calls.incrementAndGet() == 1) error("temporary failure")
            AppRefreshSnapshot(listOf(app("recovered")))
        }

        task.execute()
        advanceUntilIdle()
        assertTrue(task.state.value is AppRefreshState.Error)

        task.execute()
        advanceUntilIdle()

        assertEquals(listOf("recovered"), RAppsSingleton.instance.apps!!.map { it.packageName })
        assertEquals(2L, (task.state.value as AppRefreshState.Ready).generation)
    }

    private fun task(
        dispatcher: TestDispatcher,
        debounceMillis: Long = 0,
        loader: suspend () -> AppRefreshSnapshot
    ) = TaskUpdateApps(
        packageManager = packageManager,
        context = context,
        application = application,
        scope = kotlinx.coroutines.CoroutineScope(dispatcher),
        ioDispatcher = dispatcher,
        mainDispatcher = dispatcher,
        debounceMillis = debounceMillis,
        loader = loader
    )

    private fun app(packageName: String) = App(
        label = packageName,
        packageName = packageName,
        name = "$packageName.Main"
    )
}
