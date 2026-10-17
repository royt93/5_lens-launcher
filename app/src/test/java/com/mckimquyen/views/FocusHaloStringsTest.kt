package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FocusHaloStringsTest {

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val key = "setting_show_focus_halo"

    private fun resDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res")
        ).first { it.isDirectory }
    }

    private fun localeDirectories(): List<File> =
        resDirectory().listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            .orEmpty()
            .filter { it.name == "values" || Regex("^values-[a-z]{2}(-r[A-Z]{2})?$").matches(it.name) }
            .filter { File(it, "strings.xml").isFile }
            .sortedBy { it.name }

    @Test
    fun all17LocalesContainFocusHaloLabelNonEmpty() {
        val dirs = localeDirectories()
        assertEquals("Phải đủ 17 values/values-* thư mục locale", 17, dirs.size)
        for (dir in dirs) {
            val nodes = documentBuilder.parse(File(dir, "strings.xml")).getElementsByTagName("string")
            val text = (0 until nodes.length)
                .map { nodes.item(it) as org.w3c.dom.Element }
                .firstOrNull { it.getAttribute("name") == key }
                ?.textContent
            assertTrue("${dir.name}/strings.xml thiếu key $key", text != null)
            assertTrue("${dir.name}/strings.xml key $key rỗng", text?.isNotBlank() == true)
        }
    }
}
