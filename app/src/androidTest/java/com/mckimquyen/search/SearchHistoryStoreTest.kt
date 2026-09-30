package com.mckimquyen.search

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchHistoryStoreTest {
    private lateinit var store: SearchHistoryStore

    @Before
    fun setUp() {
        store = SearchHistoryStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun recentLaunchesPersistInOrderAndCanBeReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        store.recordLaunch("pkg/First")
        store.recordLaunch("pkg/Second")
        store.recordLaunch("pkg/First")

        assertEquals(listOf("pkg/First", "pkg/Second"), SearchHistoryStore(context).recentKeys())
        store.clear()
        assertTrue(SearchHistoryStore(context).recentKeys().isEmpty())
    }

    @Test
    fun blankKeysAreIgnored() {
        store.recordLaunch("")
        store.recordLaunch("   ")

        assertTrue(store.recentKeys().isEmpty())
    }

    @Test
    fun historyIsBoundedToSixteenMostRecentUniqueKeys() {
        repeat(18) { store.recordLaunch("pkg/App$it") }
        store.recordLaunch("pkg/App5")

        assertEquals(
            listOf(
                "pkg/App5", "pkg/App17", "pkg/App16", "pkg/App15", "pkg/App14",
                "pkg/App13", "pkg/App12", "pkg/App11", "pkg/App10", "pkg/App9",
                "pkg/App8", "pkg/App7", "pkg/App6", "pkg/App4", "pkg/App3", "pkg/App2"
            ),
            store.recentKeys()
        )
    }

    @Test
    fun recentListTrimsAtSixteenNotEight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (1..17).forEach { store.recordLaunch("pkg/App$it") }

        val recent = SearchHistoryStore(context).recentKeys()
        assertEquals(16, recent.size)
        // Most-recent-first: App17 was recorded last, App1 fell off the sixteen-item window.
        assertEquals("pkg/App17", recent.first())
        assertTrue("pkg/App1" !in recent)
    }

    @Test
    fun removeKeyDropsExactlyThatEntryAndPreservesOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store.recordLaunch("pkg/First")
        store.recordLaunch("pkg/Second")
        store.recordLaunch("pkg/Third")

        store.removeKey("pkg/Second")

        assertEquals(listOf("pkg/Third", "pkg/First"), SearchHistoryStore(context).recentKeys())
    }

    @Test
    fun removeKeyOnAbsentKeyIsANoOp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store.recordLaunch("pkg/First")

        store.removeKey("pkg/DoesNotExist")

        assertEquals(listOf("pkg/First"), SearchHistoryStore(context).recentKeys())
    }
}
