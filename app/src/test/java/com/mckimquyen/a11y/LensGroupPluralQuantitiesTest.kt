package com.mckimquyen.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins CLDR plural categories for `lens_group_lens_count` so lint's MissingQuantity/UnusedQuantity
 * stay clean and no locale silently drops a category it needs.
 */
class LensGroupPluralQuantitiesTest {

    private fun quantities(locale: String): Map<String, String> {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val res = sequenceOf(File(workingDirectory, "src/main/res"), File(workingDirectory, "app/src/main/res"))
            .first { it.isDirectory }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(res, "$locale/strings.xml"))
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val plural = plurals.item(i) as Element
            if (plural.getAttribute("name") != "lens_group_lens_count") continue
            val items = plural.getElementsByTagName("item")
            return (0 until items.length).associate {
                val item = items.item(it) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
        error("lens_group_lens_count missing in $locale")
    }

    @Test
    fun languagesWithoutPluralFormsOnlyDefineOther() {
        listOf("values-ja", "values-ko", "values-zh", "values-vi", "values-in", "values-th", "values-lo", "values-km")
            .forEach { assertEquals("$it chỉ cần 'other'", setOf("other"), quantities(it).keys) }
    }

    @Test
    fun romanceLanguagesDefineManyForMillionsWithDeForm() {
        mapOf(
            "values-es" to "de lentes",
            "values-fr" to "de lentilles",
            "values-it" to "di lenti",
            "values-pt" to "de lentes"
        ).forEach { (locale, suffix) ->
            val q = quantities(locale)
            assertTrue("$locale thiếu 'many'", "many" in q)
            assertTrue("$locale 'many' phải dùng dạng \"$suffix\": ${q["many"]}", q.getValue("many").endsWith(suffix))
            assertTrue("$locale phải giữ one/other", "one" in q && "other" in q)
        }
    }
}
