package com.mckimquyen.search

import androidx.core.content.edit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** SEARCH-016: recents expire after [SearchHistoryStore.TTL_DAYS] days; pre-TTL data is not lost. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SearchHistoryStoreTtlTest {

    private var now = START_MILLIS
    private lateinit var store: SearchHistoryStore

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences("app_search_history", 0).edit { clear() }
        store = SearchHistoryStore(context) { now }
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("app_search_history", 0).edit { clear() }
    }

    private fun advanceDays(days: Long) {
        now += TimeUnit.DAYS.toMillis(days)
    }

    @Test
    fun `entry just inside the window is kept`() {
        store.recordLaunch("pkg/A")
        advanceDays(SearchHistoryStore.TTL_DAYS - 1)

        assertEquals(listOf("pkg/A"), store.recentKeys())
    }

    @Test
    fun `entry exactly at the ttl boundary is kept and one millisecond later is dropped`() {
        store.recordLaunch("pkg/A")
        now += TimeUnit.DAYS.toMillis(SearchHistoryStore.TTL_DAYS)
        assertEquals("boundary is inclusive", listOf("pkg/A"), store.recentKeys())

        now += 1
        assertTrue("one millisecond past the ttl must expire", store.recentKeys().isEmpty())
    }

    @Test
    fun `only the expired entries are dropped and order is preserved`() {
        store.recordLaunch("pkg/Old")
        advanceDays(SearchHistoryStore.TTL_DAYS + 1)
        store.recordLaunch("pkg/New1")
        advanceDays(1)
        store.recordLaunch("pkg/New2")

        assertEquals(listOf("pkg/New2", "pkg/New1"), store.recentKeys())
    }

    @Test
    fun `relaunching an entry refreshes its timestamp`() {
        store.recordLaunch("pkg/A")
        advanceDays(SearchHistoryStore.TTL_DAYS - 1)
        store.recordLaunch("pkg/A")
        advanceDays(SearchHistoryStore.TTL_DAYS - 1)

        assertEquals("second launch restarted the clock", listOf("pkg/A"), store.recentKeys())
    }

    @Test
    fun `expired entries are physically removed from storage on read`() {
        store.recordLaunch("pkg/A")
        advanceDays(SearchHistoryStore.TTL_DAYS + 1)
        store.recentKeys()

        val raw = context.getSharedPreferences("app_search_history", 0).getString("recent_component_keys", null)
        assertTrue("expired history must not linger on disk, was: $raw", raw.isNullOrEmpty())
    }

    @Test
    fun `data saved before timestamps existed is kept and starts its clock on first read`() {
        context.getSharedPreferences("app_search_history", 0).edit {
            putString("recent_component_keys", listOf("pkg/Legacy1", "pkg/Legacy2").joinToString("\u001F"))
        }

        assertEquals(listOf("pkg/Legacy1", "pkg/Legacy2"), store.recentKeys())

        advanceDays(SearchHistoryStore.TTL_DAYS - 1)
        assertEquals("legacy entries survive until the ttl from first sight", 2, store.recentKeys().size)

        advanceDays(2)
        assertTrue("then they expire like any other entry", store.recentKeys().isEmpty())
    }

    @Test
    fun `corrupt timestamps are treated as legacy not as a crash`() {
        context.getSharedPreferences("app_search_history", 0).edit {
            putString("recent_component_keys", "pkg/A\u001Fpkg/B")
            putString("recent_component_times", "not-a-number\u001F")
        }

        assertEquals(listOf("pkg/A", "pkg/B"), store.recentKeys())
    }

    @Test
    fun `removeKey and clear keep keys and timestamps consistent`() {
        store.recordLaunch("pkg/A")
        store.recordLaunch("pkg/B")
        store.removeKey("pkg/A")
        assertEquals(listOf("pkg/B"), store.recentKeys())

        advanceDays(SearchHistoryStore.TTL_DAYS + 1)
        assertTrue(store.recentKeys().isEmpty())

        store.recordLaunch("pkg/C")
        store.clear()
        assertTrue(store.recentKeys().isEmpty())
        store.recordLaunch("pkg/D")
        assertEquals(listOf("pkg/D"), store.recentKeys())
    }

    @Test
    fun `history stays bounded to the most recent entries`() {
        repeat(20) { store.recordLaunch("pkg/App$it") }

        assertEquals(16, store.recentKeys().size)
        assertEquals("pkg/App19", store.recentKeys().first())
    }

    private companion object {
        const val START_MILLIS = 1_700_000_000_000L
    }
}
