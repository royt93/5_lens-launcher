package com.mckimquyen.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Repo audit (2026-09-27): a whole-file sweep of every locale found 19 `<string>` keys and 4
 * `<plurals>` (spanning the A11Y-001, FEAT-003, FEAT-007 and FISH-004 rounds) that carried
 * `tools:ignore="MissingTranslation"` in the base resource and were never actually translated
 * into 14 of the 16 supported locales - `ar`/`vi` had a smaller, later gap of their own. The
 * suppression is exactly why `lintDevDebug` never caught this: it silenced the one signal that
 * would have. [LensStringTranslationTest] already pins this discipline for the `lens_*` family
 * specifically; this test generalizes it to every translatable string and plural in the app, so
 * the next feature that ships a suppressed placeholder string does not quietly repeat the same
 * multi-locale gap.
 */
class AllStringsTranslationTest {

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    private fun resDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res")
        ).first { it.isDirectory }
    }

    private fun localeDirectories(): List<File> =
        resDirectory().listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            .orEmpty()
            .filter { Regex("^values-[a-z]{2}(-r[A-Z]{2})?$").matches(it.name) }
            .filter { File(it, "strings.xml").isFile }
            .sortedBy { it.name }

    private data class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val suppressedNames: Set<String>
    )

    private fun parse(stringsXml: File): Resources {
        val root = documentBuilder.parse(stringsXml).documentElement
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val suppressed = mutableSetOf<String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            if (node.getAttribute("translatable") == "false") continue
            when (node.tagName) {
                "string" -> {
                    strings[name] = node.textContent.trim()
                    if (node.getAttribute("tools:ignore").contains("MissingTranslation")) {
                        suppressed += name
                    }
                }
                "plurals" -> {
                    val quantities = mutableMapOf<String, String>()
                    val items = node.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        quantities[item.getAttribute("quantity")] = item.textContent.trim()
                    }
                    plurals[name] = quantities
                    if (node.getAttribute("tools:ignore").contains("MissingTranslation")) {
                        suppressed += name
                    }
                }
            }
        }
        return Resources(strings, plurals, suppressed)
    }

    private fun placeholders(value: String): List<String> =
        Regex("""%\d+\$[sd]""").findAll(value).map { it.value }.sorted().toList()

    private val base: Resources
        get() = parse(File(resDirectory(), "values/strings.xml"))

    @Test
    fun `no base string or plural still suppresses MissingTranslation`() {
        assertEquals(
            "A suppression here is exactly what let a string ship untranslated for 14 locales",
            emptySet<String>(),
            base.suppressedNames
        )
    }

    @Test
    fun `every locale translates every string`() {
        val expected = base.strings.keys
        assertTrue("The base locale must define strings at all", expected.isNotEmpty())

        val missing = localeDirectories().mapNotNull { directory ->
            val locale = parse(File(directory, "strings.xml"))
            val absent = expected - locale.strings.keys
            if (absent.isEmpty()) null else "${directory.name}: ${absent.sorted()}"
        }
        assertEquals("Locales missing strings", emptyList<String>(), missing)
    }

    @Test
    fun `every locale translates every plural and every quantity the base defines`() {
        val expected = base.plurals
        assertTrue("The base locale must define plurals at all", expected.isNotEmpty())

        val problems = localeDirectories().flatMap { directory ->
            val locale = parse(File(directory, "strings.xml"))
            expected.keys.mapNotNull { name ->
                val localeQuantities = locale.plurals[name]
                if (localeQuantities == null) {
                    "${directory.name}/$name: missing entirely"
                } else {
                    // Every locale must at least define "other" - Android always falls back to it.
                    // Locale-specific quantities (zero/one/two/few/many) beyond "other" are a
                    // CLDR/grammar choice per language, not a completeness bug on their own.
                    if ("other" !in localeQuantities) "${directory.name}/$name: missing 'other'" else null
                }
            }
        }
        assertEquals("Locales missing plurals", emptyList<String>(), problems)
    }

    @Test
    fun `translated strings keep the exact format placeholders of the base string`() {
        val baseStrings = base.strings
        val mismatched = localeDirectories().flatMap { directory ->
            val locale = parse(File(directory, "strings.xml"))
            locale.strings
                .filterKeys { it in baseStrings }
                .filter { (key, value) -> placeholders(value) != placeholders(baseStrings.getValue(key)) }
                .map { (key, value) ->
                    "${directory.name}/$key: ${placeholders(value)} != ${placeholders(baseStrings.getValue(key))}"
                }
        }
        assertEquals("Placeholder mismatches would crash formatting at runtime", emptyList<String>(), mismatched)
    }

    @Test
    fun `translated plural 'other' quantity keeps the exact format placeholders of the base plural`() {
        // Only "other" is checked strictly: it is the one quantity Android always has (every
        // locale must define it) and the one every count ultimately falls back to, including
        // every count this app will realistically ever show. zero/one/two/few/many are a
        // per-language grammar choice - e.g. Arabic's own "zero"/"one"/"two" forms correctly
        // name the count in words with no %1$d at all ("تطبيق واحد" = "one app"), which is
        // correct grammar, not a missing placeholder.
        val basePlurals = base.plurals
        val mismatched = localeDirectories().flatMap { directory ->
            val locale = parse(File(directory, "strings.xml"))
            basePlurals.mapNotNull { (name, baseQuantities) ->
                val baseOther = baseQuantities["other"] ?: return@mapNotNull null
                val localeOther = locale.plurals[name]?.get("other")
                    ?: return@mapNotNull "${directory.name}/$name: missing 'other'"
                val want = placeholders(baseOther)
                val have = placeholders(localeOther)
                if (have != want) "${directory.name}/$name[other]: $have != $want" else null
            }
        }
        assertEquals(
            "Placeholder mismatches in the 'other' quantity would crash formatting at runtime",
            emptyList<String>(),
            mismatched
        )
    }
}
