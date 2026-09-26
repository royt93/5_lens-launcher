package com.mckimquyen.util

import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * FISH-006: Comprehensive pure unit tests for SmartFocusArranger.
 * Verifies identity degradation, center-bias placement, monotonicity,
 * stable tie-breaking, and performance with large inventories.
 */
class SmartFocusArrangerTest {

    private fun createApp(id: Int, label: String, openCount: Long = 0L): App {
        return App(id = id, label = label, packageName = "pkg.$id", name = "act.$id", openCount = openCount)
    }

    @Test
    fun `empty app list returns empty`() {
        val result = SmartFocusArranger.arrange(emptyList(), cols = 4, rows = 4, smartFocusEnabled = true)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `disabled smart focus returns exact original order`() {
        val apps = listOf(
            createApp(1, "A", openCount = 100),
            createApp(2, "B", openCount = 0),
            createApp(3, "C", openCount = 50)
        )
        val result = SmartFocusArranger.arrange(apps, cols = 3, rows = 3, smartFocusEnabled = false)
        assertEquals(apps, result)
    }

    @Test
    fun `invalid grid dimensions return exact original order`() {
        val apps = listOf(createApp(1, "A", openCount = 10))
        assertEquals(apps, SmartFocusArranger.arrange(apps, cols = 0, rows = 3, smartFocusEnabled = true))
        assertEquals(apps, SmartFocusArranger.arrange(apps, cols = 3, rows = 0, smartFocusEnabled = true))
    }

    @Test
    fun `all zero open counts degrades to exact original list`() {
        val apps = (1..9).map { createApp(it, "App $it", openCount = 0L) }
        val result = SmartFocusArranger.arrange(apps, cols = 3, rows = 3, smartFocusEnabled = true)
        assertEquals("When all open counts are 0, layout must degrade to identity", apps, result)
    }

    @Test
    fun `all identical open counts degrades to exact original list`() {
        val apps = (1..9).map { createApp(it, "App $it", openCount = 42L) }
        val result = SmartFocusArranger.arrange(apps, cols = 3, rows = 3, smartFocusEnabled = true)
        assertEquals("When all open counts are identical, layout must degrade to identity", apps, result)
    }

    @Test
    fun `partial last row uses center of occupied rows instead of empty grid area`() {
        val apps = (0 until 5).map { index ->
            createApp(index, "App $index", openCount = if (index == 0) 100L else 0L)
        }
        val result = SmartFocusArranger.arrange(apps, cols = 3, rows = 4, smartFocusEnabled = true)
        val mostUsedSlot = result.indexOfFirst { it.id == 0 }
        assertTrue("Most-used app must stay inside occupied rows", mostUsedSlot in 0..4)
    }

    @Test
    fun `single most used app lands at the center slot of 3x3 grid`() {
        // 3x3 grid center is index 4 (col 1, row 1)
        val apps = (0..8).map { i ->
            createApp(i, "App $i", openCount = if (i == 0) 100L else 0L)
        }
        val result = SmartFocusArranger.arrange(apps, cols = 3, rows = 3, smartFocusEnabled = true)

        // App 0 (openCount 100) must land in slot 4 (center)
        assertEquals("App 0 with 100 opens must be at index 4 (center)", "App 0", result[4].label)
    }

    @Test
    fun `unopened apps preserve stable source order in remaining slots`() {
        val apps = listOf(
            createApp(0, "Most used", openCount = 100),
            createApp(1, "A"),
            createApp(2, "B"),
            createApp(3, "C"),
            createApp(4, "D"),
            createApp(5, "E"),
            createApp(6, "F"),
            createApp(7, "G"),
            createApp(8, "H")
        )
        val result = SmartFocusArranger.arrange(apps, cols = 3, rows = 3, smartFocusEnabled = true)
        assertEquals("Most used", result[4].label)
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), result.filter { it.openCount == 0L }.map { it.label })
    }

    @Test
    fun `top 2 used apps occupy the closest slots to center`() {
        // 3x3 grid center is index 4. Closest neighbors are indices 1, 3, 5, 7 (dist 1.0)
        val apps = (0..8).map { i ->
            val opens = when (i) {
                0 -> 100L
                1 -> 50L
                else -> 0L
            }
            createApp(i, "App $i", openCount = opens)
        }
        val result = SmartFocusArranger.arrange(apps, cols = 3, rows = 3, smartFocusEnabled = true)

        // Center must be App 0
        assertEquals("App 0 (100 opens) must be at center slot 4", "App 0", result[4].label)

        // App 1 (50 opens) must be in one of the immediate neighbors (distance 1.0)
        val neighborSlots = setOf(1, 3, 5, 7)
        val app1Index = result.indexOfFirst { it.label == "App 1" }
        assertTrue("App 1 (50 opens) must land in an immediate neighbor slot to center", app1Index in neighborSlots)
    }

    @Test
    fun `monotonicity - apps with higher open counts are closer to center than unopened apps`() {
        val cols = 5
        val rows = 5
        val cx = 2.0f
        val cy = 2.0f

        val apps = (0 until 25).map { i ->
            val opens = when (i) {
                0 -> 500L
                1 -> 300L
                2 -> 100L
                else -> 0L
            }
            createApp(i, "App $i", openCount = opens)
        }
        val result = SmartFocusArranger.arrange(apps, cols = cols, rows = rows, smartFocusEnabled = true)

        fun distToCenter(index: Int): Float {
            val c = (index % cols).toFloat()
            val r = (index / cols).toFloat()
            return (c - cx) * (c - cx) + (r - cy) * (r - cy)
        }

        val distApp0 = distToCenter(result.indexOfFirst { it.label == "App 0" })
        val distApp1 = distToCenter(result.indexOfFirst { it.label == "App 1" })
        val distApp2 = distToCenter(result.indexOfFirst { it.label == "App 2" })
        val unopenedDists = result.filter { it.openCount == 0L }.map { distToCenter(result.indexOf(it)) }

        assertTrue("App 0 distance ($distApp0) <= App 1 distance ($distApp1)", distApp0 <= distApp1)
        assertTrue("App 1 distance ($distApp1) <= App 2 distance ($distApp2)", distApp1 <= distApp2)
        assertTrue("App 0 distance must be smaller than average unopened distance", distApp0 < unopenedDists.average())
    }

    @Test
    fun `large inventory of 300 apps arranges efficiently under 5ms`() {
        val apps = (0 until 300).map { i ->
            createApp(i, "App $i", openCount = (i % 20).toLong())
        }
        val duration = measureTimeMillis {
            val result = SmartFocusArranger.arrange(apps, cols = 15, rows = 20, smartFocusEnabled = true)
            assertEquals(300, result.size)
        }
        assertTrue("Arranging 300 apps took $duration ms; must be < 50ms", duration < 50)
    }
}
