package com.mckimquyen.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FISH-031: the shortcut ring's tokens exist, are positive, and the chip colours differ between day and night. */
class FocusRingResourcesContractTest {
    private val resDir = listOf("app/src/main/res", "src/main/res").map(::File).first { it.isDirectory }

    private fun dimenDp(name: String): Float {
        val text = File(resDir, "values/dimen.xml").readText()
        val m = Regex("<dimen name=\"$name\">([0-9.]+)dp</dimen>").find(text)
        assertTrue("$name must be declared in dp in values/dimen.xml", m != null)
        return m!!.groupValues[1].toFloat()
    }

    private fun color(folder: String, name: String): String? =
        Regex("<color name=\"$name\">(#[0-9A-Fa-f]{6,8})</color>")
            .find(File(resDir, "$folder/colors.xml").readText())?.groupValues?.get(1)

    @Test fun `ring dimensions are declared and positive`() {
        for (name in listOf("focus_ring_chip_radius", "focus_ring_gap", "focus_ring_chip_stroke")) {
            assertTrue("$name must be > 0", dimenDp(name) > 0f)
        }
    }

    @Test fun `chip colours exist for day and night and differ`() {
        for (name in listOf("focus_ring_chip_bg", "focus_ring_chip_bg_active")) {
            val day = color("values", name)
            val night = color("values-night", name)
            assertTrue("$name missing in values/colors.xml", day != null)
            assertTrue("$name missing in values-night/colors.xml", night != null)
            assertNotEquals("$name must differ between day and night", day, night)
        }
    }

    @Test fun `active chip is distinguishable from the idle chip`() {
        assertNotEquals(color("values", "focus_ring_chip_bg"), color("values", "focus_ring_chip_bg_active"))
        assertNotEquals(color("values-night", "focus_ring_chip_bg"), color("values-night", "focus_ring_chip_bg_active"))
    }

    @Test fun `ring gap is smaller than the chip diameter so chips stay attached to the icon`() {
        assertTrue(dimenDp("focus_ring_gap") < 2 * dimenDp("focus_ring_chip_radius"))
        assertEquals(true, dimenDp("focus_ring_chip_radius") >= 16f)
    }
}
