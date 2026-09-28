package com.mckimquyen.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * FISH-008: the multi-lens strings shipped English-only through Phase 2 and Phase 3, suppressed
 * with `tools:ignore="MissingTranslation"`. That suppression is now gone, but a suppression is
 * exactly the kind of thing that gets re-added to silence a lint failure, so this pins the real
 * requirement instead of trusting lint's configuration: every `lens_*` string exists in every
 * locale, carries the same format placeholders as English, and is not left as the English text.
 *
 * Placeholders matter beyond tidiness - `lens_delete_confirm_message` is formatted with a lens
 * name and `lens_new_name_template` with an index, so a translation that dropped or renumbered
 * one would throw at runtime in that language only.
 */
class LensStringTranslationTest {

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    private fun resDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res")
        ).first { it.isDirectory }
    }

    /** Locale resource folders only: `values-land`, `values-night`, `values-w600dp` are not. */
    private fun localeDirectories(): List<File> =
        resDirectory().listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            .orEmpty()
            .filter { Regex("^values-[a-z]{2}(-r[A-Z]{2})?$").matches(it.name) }
            .filter { File(it, "strings.xml").isFile }
            .sortedBy { it.name }

    /** Translatable `lens_*` strings in a strings.xml, keyed by name. */
    private fun lensStrings(stringsXml: File): Map<String, String> {
        val nodes = documentBuilder.parse(stringsXml).getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("name").startsWith("lens_") }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent.trim() }
    }

    private fun placeholders(value: String): List<String> =
        Regex("""%\d+\$[sd]""").findAll(value).map { it.value }.sorted().toList()

    private val baseStrings: Map<String, String>
        get() = lensStrings(File(resDirectory(), "values/strings.xml"))

    @Test
    fun `every locale translates every lens string`() {
        val expected = baseStrings.keys
        assertTrue("The base locale must define lens strings at all", expected.isNotEmpty())

        val missing = localeDirectories().mapNotNull { directory ->
            val absent = expected - lensStrings(File(directory, "strings.xml")).keys
            if (absent.isEmpty()) null else "${directory.name}: ${absent.sorted()}"
        }
        assertEquals("Locales missing lens strings", emptyList<String>(), missing)
    }

    @Test
    fun `translations keep the exact format placeholders of the base string`() {
        val base = baseStrings
        val mismatched = localeDirectories().flatMap { directory ->
            lensStrings(File(directory, "strings.xml"))
                .filterKeys { it in base }
                .filter { (key, value) -> placeholders(value) != placeholders(base.getValue(key)) }
                .map { (key, value) ->
                    "${directory.name}/$key: ${placeholders(value)} != ${placeholders(base.getValue(key))}"
                }
        }
        assertEquals("Placeholder mismatches would crash formatting", emptyList<String>(), mismatched)
    }

    @Test
    fun `translations are not left as the untranslated English text`() {
        val base = baseStrings
        // "Lens %1$d" is a proper noun plus an index; some locales legitimately keep the word.
        // "via %1$s" (lens_share_caption_via) is a real, correct word in French and Portuguese
        // too, not a copy-paste - both intentionally match the English source.
        val allowedToMatch = setOf("lens_new_name_template", "lens_share_caption_via")

        val untranslated = localeDirectories().flatMap { directory ->
            lensStrings(File(directory, "strings.xml"))
                .filterKeys { it in base && it !in allowedToMatch }
                .filter { (key, value) -> value == base.getValue(key) }
                .map { (key, _) -> "${directory.name}/$key" }
        }
        assertEquals("Strings still showing English", emptyList<String>(), untranslated)
    }

    @Test
    fun `the base lens strings no longer suppress MissingTranslation`() {
        val stringsXml = File(resDirectory(), "values/strings.xml")
        val nodes = documentBuilder.parse(stringsXml).getElementsByTagName("string")
        val suppressed = (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("name").startsWith("lens_") }
            .filter { it.getAttribute("tools:ignore").contains("MissingTranslation") }
            .map { it.getAttribute("name") }

        assertEquals(
            "Re-adding this suppression would hide the next untranslated lens string",
            emptyList<String>(),
            suppressed
        )
    }
}
