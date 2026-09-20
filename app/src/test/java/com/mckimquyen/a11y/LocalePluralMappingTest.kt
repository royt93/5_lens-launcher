package com.mckimquyen.a11y

import com.mckimquyen.R
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.util.LocaleHelper
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A11Y-001: Unit tests for Locale, Plural and Content Description mappings.
 *
 * Verifies resource identifiers, supported RTL languages, and parses XML resource
 * definitions across English, Vietnamese, and Arabic locales to ensure all plural forms
 * and placeholder formats are valid.
 */
class LocalePluralMappingTest {

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    private fun findResDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res")
        ).first { it.isDirectory }
    }

    @Test
    fun `hideContentDescriptionResFor - maps visible state to hide and hidden state to show`() {
        // When app is currently visible, the button action is to hide it
        val resVisible = AppAdapter.hideContentDescriptionResFor(true)
        assertEquals(R.string.desc_hide_app, resVisible)

        // When app is currently hidden, the button action is to show it
        val resHidden = AppAdapter.hideContentDescriptionResFor(false)
        assertEquals(R.string.desc_show_app, resHidden)
    }

    @Test
    fun `lockContentDescriptionResFor - maps unlocked state to lock and locked to unlock`() {
        val resOpened = AppAdapter.lockContentDescriptionResFor(true)
        assertEquals(R.string.lock, resOpened)

        val resLocked = AppAdapter.lockContentDescriptionResFor(false)
        assertEquals(R.string.unlock, resLocked)
    }

    @Test
    fun `supportedLanguages - contains RTL Arabic and key supported locales`() {
        val languages = LocaleHelper.supportedLanguages
        val arabic = languages.find { it.code == "ar" }
        assertNotNull("Arabic language must be supported for RTL", arabic)
        assertEquals("العربية", arabic?.nativeName)

        val vietnamese = languages.find { it.code == "vi" }
        assertNotNull("Vietnamese must be supported", vietnamese)

        val english = languages.find { it.code == "en" }
        assertNotNull("English must be supported", english)
    }

    @Test
    fun `xml strings - placeholder formatting strings are defined in base strings xml`() {
        val resDir = findResDirectory()
        val baseStringsFile = File(resDir, "values/strings.xml")
        assertTrue(baseStringsFile.isFile)

        val doc = documentBuilder.parse(baseStringsFile)
        val stringNodes = doc.getElementsByTagName("string")
        val stringsMap = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            stringsMap[name] = node.textContent.trim()
        }

        val versionFormat = stringsMap["about_version_format"]
        assertNotNull("about_version_format must exist", versionFormat)
        assertTrue(versionFormat!!.contains("%1\$s") && versionFormat.contains("%2\$d"))

        val dpFormat = stringsMap["unit_dp_format"]
        assertNotNull("unit_dp_format must exist", dpFormat)
        assertTrue(dpFormat!!.contains("%1\$d") && dpFormat.contains("dp"))

        val msFormat = stringsMap["unit_ms_format"]
        assertNotNull("unit_ms_format must exist", msFormat)
        assertTrue(msFormat!!.contains("%1\$d") && msFormat.contains("ms"))

        val descHide = stringsMap["desc_hide_app"]
        assertNotNull("desc_hide_app must exist", descHide)
        assertTrue(descHide!!.contains("%1\$s"))

        val descShow = stringsMap["desc_show_app"]
        assertNotNull("desc_show_app must exist", descShow)
        assertTrue(descShow!!.contains("%1\$s"))
    }

    @Test
    fun `xml plurals - apps_count and search_results_count defined across en, vi and ar`() {
        val resDir = findResDirectory()

        fun verifyPluralsIn(folder: String, expectedQuantities: Set<String>) {
            val file = File(resDir, "$folder/strings.xml")
            assertTrue("Expected file $file", file.isFile)
            val doc = documentBuilder.parse(file)
            val pluralsNodes = doc.getElementsByTagName("plurals")

            val pluralsFound = mutableMapOf<String, MutableSet<String>>()
            for (i in 0 until pluralsNodes.length) {
                val pNode = pluralsNodes.item(i)
                val pName = pNode.attributes?.getNamedItem("name")?.nodeValue ?: continue
                val items = mutableSetOf<String>()
                val childNodes = pNode.childNodes
                for (j in 0 until childNodes.length) {
                    val cNode = childNodes.item(j)
                    if (cNode.nodeName == "item") {
                        val quantity = cNode.attributes?.getNamedItem("quantity")?.nodeValue
                        if (quantity != null) items.add(quantity)
                    }
                }
                pluralsFound[pName] = items
            }

            assertTrue("apps_count plural missing in $folder", pluralsFound.containsKey("apps_count"))
            assertTrue(
                "apps_count in $folder missing expected quantities",
                pluralsFound["apps_count"]!!.containsAll(expectedQuantities)
            )

            assertTrue("search_results_count plural missing in $folder", pluralsFound.containsKey("search_results_count"))
            assertTrue(
                "search_results_count in $folder missing expected quantities",
                pluralsFound["search_results_count"]!!.containsAll(expectedQuantities)
            )
        }

        // English has one and other
        verifyPluralsIn("values", setOf("one", "other"))

        // Vietnamese has other
        verifyPluralsIn("values-vi", setOf("other"))

        // Arabic has full 6 plural forms
        verifyPluralsIn("values-ar", setOf("zero", "one", "two", "few", "many", "other"))
    }
}
