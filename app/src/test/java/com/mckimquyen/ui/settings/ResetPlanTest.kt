package com.mckimquyen.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetPlanTest {
    @Test
    fun `preview lists only stored values that differ and the snapshot keeps every key the reset writes`() {
        val entries = listOf(
            ResetEntry("vibrate", "Vibration", false),
            ResetEntry("scale_lensA", "Scale", 1.2f),
            ResetEntry("night", "Night mode", 2)
        )
        val plan = ResetPlan.build(mapOf("vibrate" to true, "scale_lensA" to 1.2f), entries)

        assertEquals(listOf("vibrate"), plan.rows.map { it.key })
        // The reset writes all three keys, so undo must put all three back, "night" as absent.
        assertEquals(setOf("vibrate", "scale_lensA", "night"), plan.snapshot.values.keys)
        assertEquals(true, plan.snapshot.values["vibrate"])
        assertEquals(1.2f, plan.snapshot.values["scale_lensA"])
        assertNull(plan.snapshot.values["night"])
    }

    @Test
    fun `unchanged or never stored entries produce an empty plan`() {
        val entries = listOf(ResetEntry("vibrate", "Vibration", false), ResetEntry("night", "Night mode", 2))
        val plan = ResetPlan.build(mapOf("vibrate" to false), entries)

        assertTrue(plan.rows.isEmpty())
        assertTrue(plan.snapshot.values.isEmpty())
    }

    @Test
    fun `rows carry label current and default values`() {
        val plan = ResetPlan.build(mapOf("hint" to "Find"), listOf(ResetEntry("hint", "Search hint", "")))

        val row = plan.rows.single()
        assertEquals("Search hint", row.label)
        assertEquals("Find", row.currentValue)
        assertEquals("", row.defaultValue)
    }

    @Test
    fun `per lens keys are independent entries`() {
        val entries = listOf(ResetEntry("icon_size", "Icon size", 40f), ResetEntry("icon_size_lensB", "Icon size", 40f))
        val plan = ResetPlan.build(mapOf("icon_size" to 40f, "icon_size_lensB" to 55f), entries)

        assertEquals(listOf("icon_size_lensB"), plan.rows.map { it.key })
        assertEquals(setOf("icon_size", "icon_size_lensB"), plan.snapshot.values.keys)
    }

    @Test
    fun `an absent key is never previewed as a change`() {
        val plan = ResetPlan.build(
            mapOf("vibrate" to true),
            listOf(ResetEntry("vibrate", "Vibration", false), ResetEntry("night", "Night mode", 2))
        )

        assertEquals(listOf("vibrate"), plan.rows.map { it.key })
    }
}
