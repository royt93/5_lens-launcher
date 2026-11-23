package com.mckimquyen.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The first-run Terms dialog text must be translated whole: a translated locale may not keep a run of English words from the default.
 * The default `values` file is the fallback for every language we do not ship, so it must itself be English, not another locale's text.
 */
class ReadPolicyStringsTest {
    private companion object {
        const val KEY = "read_policy"
        const val DEFAULT_LOCALE_DIR = "values"
        const val LOCALE_COUNT = 17
        const val ENGLISH_RUN_WORDS = 2
        const val SHARE_KEY = "share_app_message"
        const val SHARE_VIA_KEY = "share_via"
        const val ASCII_LIMIT = 128
    }

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    private fun resDirectory(): File {
        val wd = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(File(wd, "src/main/res"), File(wd, "app/src/main/res")).first { it.isDirectory }
    }

    private fun textByLocale(): Map<String, String> =
        resDirectory().listFiles { f -> f.isDirectory && (f.name == DEFAULT_LOCALE_DIR || Regex("^values-[a-z]{2}(-r[A-Z]{2})?$").matches(f.name)) }
            .orEmpty().filter { File(it, "strings.xml").isFile }.sortedBy { it.name }
            .associate { dir ->
                val nodes = documentBuilder.parse(File(dir, "strings.xml")).getElementsByTagName("string")
                val text = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }.firstOrNull { it.getAttribute("name") == KEY }?.textContent
                dir.name to text.orEmpty()
            }

    // The resource text keeps "\\n" as two characters, so drop it first or it glues an "n" onto the next word.
    private fun words(text: String): List<String> = text.replace("\\n", " ").lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }

    private fun englishRuns(english: String): Set<String> =
        words(english).windowed(ENGLISH_RUN_WORDS).map { it.joinToString(" ") }.toSet()

    @Test fun everyLocaleHasANonBlankReadPolicy() {
        val byLocale = textByLocale()
        assertEquals(LOCALE_COUNT, byLocale.size)
        byLocale.forEach { (dir, text) -> assertTrue("$dir/strings.xml: $KEY must not be blank", text.isNotBlank()) }
    }

    @Test fun noTranslatedLocaleKeepsARunOfEnglishWords() {
        val byLocale = textByLocale()
        val runs = englishRuns(byLocale.getValue(DEFAULT_LOCALE_DIR))
        assertTrue("positive control: the English text must yield runs to look for", runs.size > ENGLISH_RUN_WORDS)
        val english = byLocale.getValue(DEFAULT_LOCALE_DIR)
        val leaked = byLocale.filterKeys { it != DEFAULT_LOCALE_DIR }.mapValues { (_, text) -> leaks(english, text) }.filterValues { it.isNotEmpty() }
        assertTrue("these locales keep English words from the default $KEY: $leaked", leaked.isEmpty())
    }

    private fun leaks(english: String, translated: String): Set<String> =
        words(translated).windowed(ENGLISH_RUN_WORDS).map { it.joinToString(" ") }.toSet().intersect(englishRuns(english))

    @Test fun theDetectorSeesALeakInAMixedSentenceAndNotInACleanOne() {
        // Negative control, with the same helper the real check uses, on the shape of the real bug (an English clause inside a translated sentence).
        val english = "Thank you for your trust. By selecting Agree and Continue, you certify that you have read"
        assertTrue(leaks(english, "Cảm ơn bạn. By selecting \"Đồng ý\", bạn xác nhận").isNotEmpty())
        assertTrue(leaks(english, "Cảm ơn bạn. Bằng việc chọn \"Đồng ý\", bạn xác nhận").isEmpty())
    }

    private fun stringsOf(dir: String): Map<String, String> {
        val nodes = documentBuilder.parse(File(resDirectory(), "$dir/strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test fun theDefaultShareMessageIsNotVietnamese() = assertDefaultIsEnglish(SHARE_KEY)

    @Test fun theDefaultShareChooserTitleIsNotVietnamese() = assertDefaultIsEnglish(SHARE_VIA_KEY)

    private fun assertDefaultIsEnglish(key: String) {
        val default = stringsOf(DEFAULT_LOCALE_DIR).getValue(key)
        val vietnamese = stringsOf("values-vi").getValue(key)
        assertTrue("positive control: the vi file keeps its own Vietnamese text for $key", vietnamese.isNotBlank())
        assertTrue("the fallback $key must not be the Vietnamese text (it is shown to every unsupported language): $default", default != vietnamese)
        assertTrue("the fallback $key must be ASCII English, found: $default", default.all { it.code < ASCII_LIMIT })
    }
}
