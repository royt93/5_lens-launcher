package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class T9SearchStringsTest {

    private companion object {
        const val SHORT_SCREEN_NOTE_KEY = "setting_enable_t9_search_keypad_short_screen_note"
        const val DEFAULT_LOCALE_DIR = "values"
    }

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val expectedKeys = setOf(
        "setting_enable_t9_search_keypad",
        "setting_enable_t9_search_keypad_desc",
        SHORT_SCREEN_NOTE_KEY
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
        val noteByLocale = mutableMapOf<String, String>()
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
                if (name == SHORT_SCREEN_NOTE_KEY) noteByLocale[dir.name] = elem.textContent
            }
            for (key in expectedKeys) {
                assertTrue("File ${dir.name}/strings.xml thiếu key $key", found.containsKey(key))
                assertTrue("File ${dir.name}/strings.xml key $key không được rỗng", found[key]?.isNotBlank() == true)
            }
        }
        // A pasted English copy would pass the non-blank check above; every other locale must differ from the default text.
        val english = requireNotNull(noteByLocale[DEFAULT_LOCALE_DIR]) { "values/strings.xml thiếu $SHORT_SCREEN_NOTE_KEY" }
        for ((locale, text) in noteByLocale) {
            if (locale != DEFAULT_LOCALE_DIR) assertTrue("$locale/strings.xml: $SHORT_SCREEN_NOTE_KEY là bản tiếng Anh dán lại", text != english)
        }
    }
}
