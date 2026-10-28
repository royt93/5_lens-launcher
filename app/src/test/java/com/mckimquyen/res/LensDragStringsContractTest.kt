package com.mckimquyen.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FISH-038b: the drop strip's accessibility label exists in the default strings and every translated set. */
class LensDragStringsContractTest {
    private val resDir = listOf("app/src/main/res", "src/main/res").map(::File).first { it.isDirectory }
    private val key = "lens_drag_targets_description"

    @Test
    fun `every strings file that already translates move_to_lens_done also has the strip label`() {
        val files = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
            .map { File(it, "strings.xml") }
            .filter { it.isFile && it.readText().contains("name=\"move_to_lens_done\"") }
        assertTrue("expected the default set plus the translated sets", files.size >= 17)
        val missing = files.filterNot { it.readText().contains("name=\"$key\"") }.map { it.parentFile.name }
        assertEquals("locales missing $key", emptyList<String>(), missing)
    }

    @Test
    fun `the default label is not blank`() {
        val text = File(resDir, "values/strings.xml").readText()
        val match = Regex("<string name=\"$key\">([^<]+)</string>").find(text)
        assertTrue("default $key must exist and be non-blank", match != null && match.groupValues[1].isNotBlank())
    }
}
