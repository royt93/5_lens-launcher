package com.mckimquyen.search

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ContactSearchControllerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        ContactSearchEngine.setPermissionGrantedForTesting(true)
        ContactSearchEngine.setContactsForTesting(
            listOf(
                ContactSearchResult("Jane Nguyen", "+84 912 345 678"),
                ContactSearchResult("Bob Smith", "+1 555 123 4567")
            )
        )
    }

    @After
    fun teardown() {
        ContactSearchEngine.setContactsForTesting(null)
        ContactSearchEngine.setPermissionGrantedForTesting(null)
    }

    @Test
    fun nonContactQueryDoesNotTriggerSearchAndDeliversEmptyState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = DEBOUNCE_MS,
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("calculator")
        advanceUntilIdle()

        assertEquals(1, states.size)
        assertTrue(states.single() is ContactSearchUiState.Hidden)
    }

    @Test
    fun rapidKeystrokesDebounceAndOnlyExecuteSearchForFinalQuery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var searchCalls = 0
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = DEBOUNCE_MS,
            searchFunction = { ctx, q ->
                searchCalls++
                ContactSearchEngine.firstContactForQuery(ctx, q)
            },
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("call j")
        advanceTimeBy(50)
        controller.onQueryChanged("call ja")
        advanceTimeBy(50)
        controller.onQueryChanged("call jane")

        assertEquals("chưa hết debounce không được query", 0, searchCalls)

        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        advanceUntilIdle()

        assertEquals("chỉ query một lần sau debounce cho ký tự cuối", 1, searchCalls)
        val finalState = states.last() as ContactSearchUiState.Result
        assertEquals("Jane Nguyen", finalState.contact.displayName)
    }

    @Test
    fun cancellingJobPreventsStaleResultFromPublishing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = DEBOUNCE_MS,
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("call jane")
        advanceTimeBy(DEBOUNCE_MS)
        controller.cancel()
        advanceUntilIdle()

        assertTrue(
            "khi cancel trước khi hoàn tất, không phát Result cho query cũ",
            states.none { it is ContactSearchUiState.Result }
        )
    }

    @Test
    fun permissionRequestStateIsDeliveredImmediatelyWithoutDebounce() = runTest {
        ContactSearchEngine.setPermissionGrantedForTesting(false)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = DEBOUNCE_MS,
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("call jane")
        // Không advanceTimeBy(DEBOUNCE_MS), state PermissionRequest phải có ngay
        runCurrent()

        assertEquals(1, states.size)
        assertTrue(states.single() is ContactSearchUiState.PermissionRequest)
    }

    @Test
    fun olderSlowSearchCannotOverwriteNewerQueryResult() = runBlocking {
        val worker = java.util.concurrent.Executors.newFixedThreadPool(2)
        val workerDispatcher = worker.asCoroutineDispatcher()
        val states = java.util.Collections.synchronizedList(mutableListOf<ContactSearchUiState>())
        val oldSearchEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseOldSearch = java.util.concurrent.CountDownLatch(1)
        val newDelivered = kotlinx.coroutines.CompletableDeferred<Unit>()
        try {
            val controller = ContactSearchController(
                context = context,
                scope = this,
                ioDispatcher = workerDispatcher,
                mainDispatcher = Dispatchers.Unconfined,
                debounceMs = 0L,
                searchFunction = { ctx, q ->
                    if (q.toString() == "call jane") {
                        oldSearchEntered.complete(Unit)
                        // Provider chậm thật: giữ thread worker, không phản hồi cancel.
                        releaseOldSearch.await(WORKER_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
                    ContactSearchEngine.firstContactForQuery(ctx, q)
                },
                onStateChanged = {
                    states.add(it)
                    if (it is ContactSearchUiState.Result && it.contact.displayName == "Bob Smith") newDelivered.complete(Unit)
                }
            )

            controller.onQueryChanged("call jane")
            withTimeout(WORKER_TIMEOUT_MS) { oldSearchEntered.await() }
            controller.onQueryChanged("call bob")
            withTimeout(WORKER_TIMEOUT_MS) { newDelivered.await() }
            releaseOldSearch.countDown()
            // Cho query cũ thời gian hoàn tất rồi mới kiểm; nó không được phát kết quả.
            kotlinx.coroutines.delay(SETTLE_MS)

            val results = states.filterIsInstance<ContactSearchUiState.Result>()
            assertEquals(listOf("Bob Smith"), results.map { it.contact.displayName })
        } finally {
            releaseOldSearch.countDown()
            workerDispatcher.close()
        }
    }

    @Test
    fun searchRunsOnWorkerThreadNotCallerThread() = runBlocking {
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, WORKER_THREAD_NAME) }
        val workerDispatcher = worker.asCoroutineDispatcher()
        val callerThread = Thread.currentThread().name
        val searchedOn = java.util.concurrent.atomic.AtomicReference<String>()
        val delivered = kotlinx.coroutines.CompletableDeferred<ContactSearchUiState>()
        try {
            val controller = ContactSearchController(
                context = context,
                scope = this,
                ioDispatcher = workerDispatcher,
                mainDispatcher = Dispatchers.Unconfined,
                debounceMs = 0L,
                searchFunction = { _, _ ->
                    searchedOn.set(Thread.currentThread().name)
                    ContactSearchResult("Jane Nguyen", "+84 912 345 678")
                },
                onStateChanged = { delivered.complete(it) }
            )
            controller.onQueryChanged("call jane")
            withTimeout(WORKER_TIMEOUT_MS) { delivered.await() }
            assertTrue(
                "search phải chạy trên worker thread, thực tế: ${searchedOn.get()}",
                searchedOn.get().startsWith(WORKER_THREAD_NAME)
            )
            assertTrue("search không được chạy trên thread gọi", !searchedOn.get().startsWith(callerThread))
        } finally {
            workerDispatcher.close()
        }
    }

    @Test
    fun workerFailureDeliversHiddenState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val failing = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = 0L,
            searchFunction = { _, _ -> throw IllegalStateException("provider failed") },
            onStateChanged = { states.add(it) }
        )
        failing.onQueryChanged("call jane")
        advanceUntilIdle()
        assertEquals(listOf<ContactSearchUiState>(ContactSearchUiState.Hidden), states)
    }

    @Test
    fun closeCancelsScopeAndIgnoresSubsequentQueries() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = 0L,
            searchFunction = { _, _ -> ContactSearchResult("Jane Nguyen", "+84 912 345 678") },
            onStateChanged = { states.add(it) }
        )

        controller.close()
        controller.onQueryChanged("call jane")
        advanceUntilIdle()

        assertTrue("sau khi close, controller không phát state nào", states.isEmpty())
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val WORKER_THREAD_NAME = "contact-search-test-worker"
        const val WORKER_TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 200L
    }
}
