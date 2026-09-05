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
    fun historyIsBoundedToEightMostRecentUniqueKeys() {
        repeat(10) { store.recordLaunch("pkg/App$it") }
        store.recordLaunch("pkg/App5")

        assertEquals(
            listOf(
                "pkg/App5", "pkg/App9", "pkg/App8", "pkg/App7",
                "pkg/App6", "pkg/App4", "pkg/App3", "pkg/App2"
            ),
            store.recentKeys()
        )
    }
}
