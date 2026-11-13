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

/** FISH-048: the keys launched inside a time window, newest first, capped; the existing list behaviour is untouched. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SearchHistoryStoreRecentSinceTest {
    private var now = START_MILLIS
    private lateinit var store: SearchHistoryStore
    private val context get() = RuntimeEnvironment.getApplication()
    private val day = TimeUnit.HOURS.toMillis(24)

    @Before fun setUp() {
        context.getSharedPreferences("app_search_history", 0).edit { clear() }
        store = SearchHistoryStore(context) { now }
    }

    @After fun tearDown() {
        context.getSharedPreferences("app_search_history", 0).edit { clear() }
    }

    @Test fun onlyLaunchesInsideTheWindowAreReturnedNewestFirst() {
        store.recordLaunch("pkg/Old")
        now += day + 1
        store.recordLaunch("pkg/A")
        now += 1_000
        store.recordLaunch("pkg/B")
        assertEquals(listOf("pkg/B", "pkg/A"), store.recentKeysSince(day, 10))
    }

    @Test fun theWindowBoundaryIsInclusiveAndOneMillisecondLaterIsOut() {
        store.recordLaunch("pkg/A")
        now += day
        assertEquals("exactly at the window is kept", listOf("pkg/A"), store.recentKeysSince(day, 10))
        now += 1
        assertTrue("one millisecond past it is dropped", store.recentKeysSince(day, 10).isEmpty())
    }

    @Test fun theLimitKeepsTheNewestAndAZeroOrNegativeLimitReturnsNothing() {
        listOf("pkg/A", "pkg/B", "pkg/C", "pkg/D").forEach { store.recordLaunch(it); now += 10 }
        assertEquals(listOf("pkg/D", "pkg/C", "pkg/B"), store.recentKeysSince(day, 3))
        assertTrue(store.recentKeysSince(day, 0).isEmpty())
        assertTrue(store.recentKeysSince(day, -1).isEmpty())
    }

    @Test fun aNonPositiveWindowReturnsNothing() {
        store.recordLaunch("pkg/A")
        assertTrue(store.recentKeysSince(0L, 10).isEmpty())
        assertTrue(store.recentKeysSince(-5L, 10).isEmpty())
    }

    @Test fun aRelaunchMovesTheKeyToTheFrontAndTheEmptyStoreReturnsNothing() {
        assertTrue(store.recentKeysSince(day, 3).isEmpty())
        store.recordLaunch("pkg/A"); now += 10
        store.recordLaunch("pkg/B"); now += 10
        store.recordLaunch("pkg/A")
        assertEquals(listOf("pkg/A", "pkg/B"), store.recentKeysSince(day, 3))
    }

    @Test fun theExistingRecentKeysListIsUnchanged() {
        store.recordLaunch("pkg/A"); now += 10
        store.recordLaunch("pkg/B")
        assertEquals(listOf("pkg/B", "pkg/A"), store.recentKeys())
        store.recentKeysSince(day, 1)
        assertEquals("asking for a window never trims the stored list", listOf("pkg/B", "pkg/A"), store.recentKeys())
    }

    private companion object { const val START_MILLIS = 1_700_000_000_000L }
}
