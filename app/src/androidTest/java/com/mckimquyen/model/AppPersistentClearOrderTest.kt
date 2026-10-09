package com.mckimquyen.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPersistentClearOrderTest {

    private val dao get() = AppDatabase.getInstance().appPersistentDao()
    private val lensA = "clear-order-lens-a"
    private val lensB = "clear-order-lens-b"

    @Before
    fun setup() = clean()

    @After
    fun tearDown() = clean()

    private fun clean() = runBlocking {
        AppDatabase.init(InstrumentationRegistry.getInstrumentation().targetContext)
        dao.deleteForLens(lensA)
        dao.deleteForLens(lensB)
        Unit
    }

    @Test
    fun clearOrderResetsOnlyTheGivenLens() = runBlocking {
        dao.insert(AppPersistent.defaults("com.test.order", "A", lensA).copy(orderNumber = 3))
        dao.insert(AppPersistent.defaults("com.test.order", "A", lensB).copy(orderNumber = 5))

        val changed = dao.clearOrderForLens(lensA)

        assertEquals(1, changed)
        assertEquals(-1, dao.getAllForLens(lensA).single().orderNumber)
        assertEquals(5, dao.getAllForLens(lensB).single().orderNumber)
    }

    @Test
    fun clearOrderOnALensWithNoRowsChangesNothing() = runBlocking {
        assertEquals(0, dao.clearOrderForLens(lensA))
    }

    @Test
    fun clearOrderResetsEveryRowOfTheLensAndKeepsTheirOtherFields() = runBlocking {
        dao.insert(AppPersistent.defaults("com.test.order", "A", lensA).copy(orderNumber = 0, isFavorite = true))
        dao.insert(AppPersistent.defaults("com.test.order", "B", lensA).copy(orderNumber = 1, appVisible = false))

        assertEquals(2, dao.clearOrderForLens(lensA))

        val rows = dao.getAllForLens(lensA).associateBy { it.identifier }
        assertEquals(setOf(-1), rows.values.map { it.orderNumber }.toSet())
        assertEquals(true, rows.getValue(AppPersistent.generateIdentifier("com.test.order", "A")).isFavorite)
        assertEquals(false, rows.getValue(AppPersistent.generateIdentifier("com.test.order", "B")).appVisible)
    }
}
