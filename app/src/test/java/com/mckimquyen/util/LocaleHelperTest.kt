package com.mckimquyen.util

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

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
