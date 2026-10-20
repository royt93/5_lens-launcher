package com.mckimquyen.a11y

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** DB-006: the rollback message must exist and be translated in every shipped locale. */
class SaveFailedStringTest {
    private fun res(): File {
        val wd = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(File(wd, "src/main/res"), File(wd, "app/src/main/res")).first { it.isDirectory }
    }

    private fun value(dir: File): String? {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, "strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == "error_save_failed" }?.textContent
    }

    @Test
    fun everyLocaleTranslatesTheSaveFailedMessage() {
        val locales = res().listFiles { f -> f.isDirectory && Regex("^values(-[a-z]{2})?$").matches(f.name) && File(f, "strings.xml").exists() }!!
        assertEquals("17 shipped locales including default", 17, locales.size)
        val base = requireNotNull(value(File(res(), "values")))
        locales.forEach { dir ->
            val text = value(dir)
            assertTrue("${dir.name} is missing error_save_failed", !text.isNullOrBlank())
            if (dir.name != "values") assertTrue("${dir.name} still shows English: $text", text != base)
        }
    }
}
