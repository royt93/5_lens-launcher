package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.util.LocaleHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration Test cho tính năng Multi-Language (Hoạt động đa ngôn ngữ)
 */
@RunWith(AndroidJUnit4::class)
class MultiLanguageIntegrationTest {

    @Test
    fun testActSettings_languageSelectionViewsPresent() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            assertNotNull(activity)
            
            val llLanguage = activity.findViewById<android.view.View>(R.id.llLanguage)
            val tvSelectedLanguage = activity.findViewById<android.widget.TextView>(R.id.tvSelectedLanguage)
            
            assertNotNull("llLanguage setting row must be present", llLanguage)
            assertNotNull("tvSelectedLanguage display text must be present", tvSelectedLanguage)
            assertEquals(android.view.View.VISIBLE, llLanguage.visibility)
        }

        scenario.close()
    }

    @Test
    fun testLocaleHelper_appliesLocaleAndLoadsLocalizedStrings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalLanguage = LocaleHelper.getLanguage(context)

        try {
            // Test 1: Switch to Vietnamese
            val viContext = LocaleHelper.setLocale(context, "vi")
            val viTitle = viContext.resources.getString(R.string.setting_language_title)
            assertEquals("Ngôn ngữ ứng dụng", viTitle)

            // Test 2: Switch to English
            val enContext = LocaleHelper.setLocale(context, "en")
            val enTitle = enContext.resources.getString(R.string.setting_language_title)
            assertEquals("App Language", enTitle)

            // Test 3: Switch to Russian
            val ruContext = LocaleHelper.setLocale(context, "ru")
            val ruTitle = ruContext.resources.getString(R.string.setting_language_title)
            assertEquals("Язык приложения", ruTitle)

            // Test 4: Switch to Chinese
            val zhContext = LocaleHelper.setLocale(context, "zh")
            val zhTitle = zhContext.resources.getString(R.string.setting_language_title)
            assertEquals("应用语言", zhTitle)

            // Test 5: Switch to French
            val frContext = LocaleHelper.setLocale(context, "fr")
            val frTitle = frContext.resources.getString(R.string.setting_language_title)
            assertEquals("Langue de l'application", frTitle)
        } finally {
            // Restore original language
            LocaleHelper.setLocale(context, originalLanguage)
        }
    }
}
