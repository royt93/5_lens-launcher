package com.mckimquyen.util

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LocaleHelperTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testSupportedLanguagesList() {
        val languages = LocaleHelper.supportedLanguages
        assertEquals(17, languages.size)
        
        val codes = languages.map { it.code }
        assertTrue(codes.contains("en"))
        assertTrue(codes.contains("vi"))
        assertTrue(codes.contains("th"))
        assertTrue(codes.contains("zh"))
        assertTrue(codes.contains("ja"))
        assertTrue(codes.contains("ko"))
        assertTrue(codes.contains("ru"))
        assertTrue(codes.contains("de"))
        assertTrue(codes.contains("fr"))
        assertTrue(codes.contains("es"))
        assertTrue(codes.contains("pt"))
        assertTrue(codes.contains("hi"))
        assertTrue(codes.contains("km"))
        assertTrue(codes.contains("lo"))
        assertTrue(codes.contains("ar"))
        assertTrue(codes.contains("in"))
        assertTrue(codes.contains("it"))
    }

    @Test
    fun allSupportedLocalesProvideLocalizedNoInternetMessage() {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val resourcesDirectory = sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res")
        ).first { it.isDirectory }
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        fun noInternetMessage(languageCode: String): String {
            val valuesDirectory = if (languageCode == "en") "values" else "values-$languageCode"
            val stringsFile = File(resourcesDirectory, "$valuesDirectory/strings.xml")
            assertTrue("Missing strings.xml for $languageCode", stringsFile.isFile)
            val stringNodes = documentBuilder.parse(stringsFile).getElementsByTagName("string")
            for (index in 0 until stringNodes.length) {
                val node = stringNodes.item(index)
                if (node.attributes?.getNamedItem("name")?.nodeValue == "no_internet") {
                    return node.textContent.trim()
                }
            }
            fail("Missing no_internet translation for $languageCode")
            return ""
        }

        val englishMessage = noInternetMessage("en")

        LocaleHelper.supportedLanguages.forEach { language ->
            val localizedMessage = noInternetMessage(language.code)

            assertTrue("${language.code} no_internet must not be blank", localizedMessage.isNotBlank())
            if (language.code != "en") {
                assertNotEquals(
                    "${language.code} must not fall back to English",
                    englishMessage,
                    localizedMessage
                )
            }
        }
    }

    @Test
    fun testDefaultLanguage() {
        val language = LocaleHelper.getLanguage(context)
        val systemLocale = Locale.getDefault().language
        if (systemLocale.isNotEmpty() && systemLocale != "en") {
            assertTrue(language.isNotEmpty())
        } else {
            assertEquals("en", language)
        }
    }

    @Test
    fun testPersistLanguage() {
        val targetLang = "vi"
        LocaleHelper.setLocale(context, targetLang)
        assertEquals(targetLang, LocaleHelper.getLanguage(context))
    }

    @Test
    fun testLanguageSelectionFlag() {
        // Assert initial state is false or clear it
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        preferences.edit().remove("Locale.Helper.Language.Selected").commit()
        
        assertFalse(LocaleHelper.isLanguageSelected(context))

        LocaleHelper.setLanguageSelected(context)

        assertTrue(LocaleHelper.isLanguageSelected(context))
    }
}
