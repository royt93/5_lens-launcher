package com.mckimquyen.search

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEARCH-013: đo chi phí gõ phím liên tiếp đối với danh bạ 1000 contacts giả lập.
 * So sánh:
 * - Baseline cũ: mỗi ký tự gõ gọi thẳng firstContactForQuery đồng bộ trên main thread (9 lượt).
 * - Candidate mới: qua ContactSearchController với debounce 150ms; 9 keystroke dồn lại chỉ query 1 lần trên Dispatchers.IO.
 * Không đọc danh bạ thật của thiết bị, không sửa quyền READ_CONTACTS hệ thống.
 */
@RunWith(AndroidJUnit4::class)
class ContactSearchLatencyTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        ContactSearchEngine.setPermissionGrantedForTesting(true)
        val fixture = (1..BENCHMARK_CONTACTS).map { i ->
            ContactSearchResult("Contact Name $i", "+84 900 00${i.toString().padStart(4, '0')}")
        } + listOf(ContactSearchResult("Zoe Target Contact", "+84 999 888 777"))
        ContactSearchEngine.setContactsForTesting(fixture)
    }

    @After
    fun teardown() {
        ContactSearchEngine.setContactsForTesting(null)
        ContactSearchEngine.setPermissionGrantedForTesting(null)
    }

    @Test
    fun debouncedSearchSavesQueriesAndOffloadsFromMainThread() = runBlocking<Unit> {
        val keystrokes = listOf(
            "c", "ca", "cal", "call", "call ", "call z", "call zo", "call zoe", "call zoe target"
        )

        // 1. Baseline cũ: mỗi keystroke gọi firstContactForQuery trực tiếp trên main thread
        var syncCalls = 0
        val legacyStart = System.nanoTime()
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            keystrokes.forEach { query ->
                syncCalls++
                ContactSearchEngine.firstContactForQuery(context, query)
            }
        }
        val legacyNanos = System.nanoTime() - legacyStart

        // 2. Candidate mới: qua controller, keystroke liên tiếp với khoảng cách 20ms
        var asyncCalls = 0
        val threadNames = java.util.Collections.synchronizedList(mutableListOf<String>())
        val finalDelivered = CompletableDeferred<ContactSearchResult>()

        val controller = ContactSearchController(
            context = context,
            scope = this + Dispatchers.Default,
            ioDispatcher = Dispatchers.IO,
            mainDispatcher = Dispatchers.Main.immediate,
            debounceMs = DEBOUNCE_MS,
            searchFunction = { ctx, q ->
                asyncCalls++
                threadNames.add(Thread.currentThread().name)
                ContactSearchEngine.firstContactForQuery(ctx, q)
            },
            onStateChanged = { state ->
                if (state is ContactSearchUiState.Result && state.contact.displayName.contains("Zoe")) {
                    finalDelivered.complete(state.contact)
                }
            }
        )

        val candidateStart = System.nanoTime()
        keystrokes.forEach { query ->
            kotlinx.coroutines.withContext(Dispatchers.Main) { controller.onQueryChanged(query) }
            delay(TYPING_INTERVAL_MS)
        }
        val delivered = withTimeout(BENCHMARK_TIMEOUT_MS) { finalDelivered.await() }
        val candidateNanos = System.nanoTime() - candidateStart
        kotlinx.coroutines.withContext(Dispatchers.Main) { controller.close() }

        assertNotNull(delivered)
        assertEquals("9 keystroke dồn lại chỉ được query 1 lần", 1, asyncCalls)
        assertEquals("baseline cũ phải query 9 lần", keystrokes.size, syncCalls)
        assertTrue(
            "query candidate phải chạy trên thread worker của Dispatchers.IO",
            threadNames.none { it == "main" }
        )

        Log.i(
            TAG,
            "burst9Keystrokes contacts=$BENCHMARK_CONTACTS " +
                "legacySyncQueries=$syncCalls legacyMs=${legacyNanos / NANOS_PER_MS} " +
                "candidateAsyncQueries=$asyncCalls candidateTotalWaitMs=${candidateNanos / NANOS_PER_MS}"
        )
    }

    private companion object {
        const val TAG = "SEARCH013"
        const val BENCHMARK_CONTACTS = 1000
        const val DEBOUNCE_MS = 150L
        const val TYPING_INTERVAL_MS = 20L
        const val BENCHMARK_TIMEOUT_MS = 5_000L
        const val NANOS_PER_MS = 1_000_000L
    }
}
