package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class T9KeypadAccessibilityTest {

    private fun resDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res")
        ).first { it.isDirectory }
    }

    @Test
    fun backspaceKeyDeclaresLocalizedContentDescription() {
        val layout = File(resDirectory(), "layout/act_home.xml").readText()
        val backspace = Regex("""<com\.google\.android\.material\.button\.MaterialButton[^>]*@\+id/btnT9_Backspace[^>]*>""")
            .find(layout)?.value
        assertTrue("Không tìm thấy phím xóa một ký tự", backspace != null)
        assertTrue(
            "Phím xóa một ký tự chỉ có glyph, TalkBack cần mô tả riêng",
            backspace!!.contains("""android:contentDescription="@string/t9_key_backspace_description"""")
        )
    }

    @Test
    fun allSeventeenLocalesTranslateBackspaceDescription() {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val dirs = resDirectory().listFiles { f -> f.isDirectory && f.name.startsWith("values") }
            .orEmpty()
            .filter { it.name == "values" || Regex("^values-[a-z]{2}(-r[A-Z]{2})?$").matches(it.name) }
            .filter { File(it, "strings.xml").isFile }
        assertEquals("Phải đủ 17 locale", 17, dirs.size)
        dirs.forEach { dir ->
            val nodes = builder.parse(File(dir, "strings.xml")).getElementsByTagName("string")
            val text = (0 until nodes.length)
                .map { nodes.item(it) as org.w3c.dom.Element }
                .firstOrNull { it.getAttribute("name") == "t9_key_backspace_description" }
                ?.textContent
            assertTrue("${dir.name} thiếu t9_key_backspace_description", !text.isNullOrBlank())
        }
    }
}
