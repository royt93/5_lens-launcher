package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class T9SearchStringsTest {

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val expectedKeys = setOf(
        "setting_enable_t9_search_keypad",
        "setting_enable_t9_search_keypad_desc"
    )

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
    fun all17LocalesContainT9StringsNonEmpty() {
        val dirs = localeDirectories()
        assertEquals("Phải đủ 17 values/values-* thư mục locale", 17, dirs.size)
        for (dir in dirs) {
            val file = File(dir, "strings.xml")
            val doc = documentBuilder.parse(file)
            val nodes = doc.getElementsByTagName("string")
            val found = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as org.w3c.dom.Element
                val name = elem.getAttribute("name")
                if (name in expectedKeys) {
                    found[name] = elem.textContent
                }
            }
            for (key in expectedKeys) {
                assertTrue("File ${dir.name}/strings.xml thiếu key $key", found.containsKey(key))
                assertTrue("File ${dir.name}/strings.xml key $key không được rỗng", found[key]?.isNotBlank() == true)
            }
        }
    }
}
