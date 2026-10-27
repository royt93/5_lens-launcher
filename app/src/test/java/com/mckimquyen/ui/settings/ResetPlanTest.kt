package com.mckimquyen.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetPlanTest {
    private val labelVibrate = 1
    private val labelScale = 2
    private val labelNight = 3

    @Test
    fun `preview lists only entries whose visible value differs and the snapshot keeps every key the reset writes`() {
        val entries = listOf(
            ResetEntry("vibrate", labelVibrate, false, currentValue = true),
            ResetEntry("scale_lensA", labelScale, 1.2f, currentValue = 1.2f),
            ResetEntry("night", labelNight, 2, currentValue = 2)
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
    fun `entries already at their default produce an empty plan`() {
        val entries = listOf(ResetEntry("vibrate", labelVibrate, false, false), ResetEntry("night", labelNight, 2, 2))
        val plan = ResetPlan.build(mapOf("vibrate" to false), entries)

        assertTrue(plan.rows.isEmpty())
        assertTrue(plan.snapshot.values.isEmpty())
    }

    @Test
    fun `rows carry label current and default values`() {
        val plan = ResetPlan.build(mapOf("hint" to "Find"), listOf(ResetEntry("hint", labelScale, "", "Find")))

        val row = plan.rows.single()
        assertEquals(labelScale, row.labelRes)
        assertEquals("Find", row.currentValue)
        assertEquals("", row.defaultValue)
    }

    @Test
    fun `per lens keys are independent entries`() {
        val entries = listOf(
            ResetEntry("icon_size", labelScale, 40f, 40f),
            ResetEntry("icon_size_lensB", labelScale, 40f, 55f)
        )
        val plan = ResetPlan.build(mapOf("icon_size" to 40f, "icon_size_lensB" to 55f), entries)

        assertEquals(listOf("icon_size_lensB"), plan.rows.map { it.key })
        assertEquals(setOf("icon_size", "icon_size_lensB"), plan.snapshot.values.keys)
    }

    @Test
    fun `an absent key whose visible value differs is previewed and remembered as absent`() {
        // A secondary lens with no override inherits 3.0 from the shared key, so reset really changes what it shows.
        val plan = ResetPlan.build(emptyMap<String, Any>(), listOf(ResetEntry("distortion_lensB", labelScale, 2.5f, 3.0f)))

        assertEquals(listOf("distortion_lensB"), plan.rows.map { it.key })
        assertTrue(plan.snapshot.values.containsKey("distortion_lensB"))
        assertNull(plan.snapshot.values["distortion_lensB"])
    }

    @Test
    fun `an absent key that already shows the default is not previewed`() {
        val plan = ResetPlan.build(
            mapOf("vibrate" to true),
            listOf(ResetEntry("vibrate", labelVibrate, false, true), ResetEntry("night", labelNight, 2, 2))
        )

        assertEquals(listOf("vibrate"), plan.rows.map { it.key })
    }

    @Test
    fun `a companion entry is snapshotted but never previewed`() {
        val entries = listOf(
            ResetEntry("icon_pack_package_name", labelScale, "", "pack.a"),
            ResetEntry("icon_pack_label_name", labelScale, "default", "Pack A", showInPreview = false)
        )
        val plan = ResetPlan.build(mapOf("icon_pack_package_name" to "pack.a", "icon_pack_label_name" to "Pack A"), entries)

        assertEquals(listOf("icon_pack_package_name"), plan.rows.map { it.key })
        assertEquals(setOf("icon_pack_package_name", "icon_pack_label_name"), plan.snapshot.values.keys)
    }

    @Test
    fun `only a hidden companion that differs does not trigger a reset`() {
        val plan = ResetPlan.build(
            mapOf("icon_pack_label_name" to "Pack A"),
            listOf(ResetEntry("icon_pack_label_name", labelScale, "default", "Pack A", showInPreview = false))
        )

        assertTrue(plan.rows.isEmpty())
        assertTrue(plan.snapshot.values.isEmpty())
    }
}
