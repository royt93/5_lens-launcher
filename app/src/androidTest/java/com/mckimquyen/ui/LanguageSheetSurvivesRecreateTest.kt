package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.util.LocaleHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit finding E2: the language sheet reports the chosen language to a plain `listener` field that the Activity sets when it creates the sheet.
 * After the Activity is recreated (dark mode, font size, locale change while the sheet is open) the framework restores the sheet without that
 * field, so the choice was stored but `recreate()` never ran and the screen stayed in the old language until the next launch.
 * Here the Activity is recreated with the sheet showing, a language is chosen on the RESTORED sheet, and the Activity must be recreated again.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSheetSurvivesRecreateTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SETTLE_MS = 700L
        const val CHOSEN = "vi"
        const val SHEET_TAG = "LanguageBottomSheet"
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var originalLanguage = "en"

    @Before fun remember() { originalLanguage = LocaleHelper.getLanguage(instrumentation.targetContext) }

    @After fun restore() {
        LocaleHelper.setLocale(instrumentation.targetContext, originalLanguage)
        instrumentation.waitForIdleSync()
    }

    private fun settle() { instrumentation.waitForIdleSync(); Thread.sleep(SETTLE_MS); instrumentation.waitForIdleSync() }

    @Test fun aRestoredSheetStillRecreatesTheActivityWhenALanguageIsChosen() = chooseOnRestoredSheet(alreadyCurrent = false)

    /**
     * On API 33+ setApplicationLocales makes the system recreate the Activity when the language CHANGES, which would hide the missing listener, so the
     * second case picks the language that is already current: no system recreation, only the owner's listener can refresh the screen.
     */
    @Test fun aRestoredSheetRecreatesTheActivityEvenWhenTheChosenLanguageIsAlreadyCurrent() = chooseOnRestoredSheet(alreadyCurrent = true)

    private fun chooseOnRestoredSheet(alreadyCurrent: Boolean) {
        if (alreadyCurrent) {
            LocaleHelper.setLocale(instrumentation.targetContext, CHOSEN)
            settle()
        }
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            settle()
            scenario.onActivity { activity ->
                val sheet = LanguageBottomSheetDialogFragment()
                sheet.setOnLanguageSelectedListener(object : LanguageBottomSheetDialogFragment.OnLanguageSelectedListener {
                    override fun onLanguageSelected(languageCode: String) { activity.recreate() }
                })
                sheet.show(activity.supportFragmentManager, SHEET_TAG)
            }
            settle()

            var firstActivity: android.app.Activity? = null
            scenario.onActivity { firstActivity = it }
            scenario.recreate()
            settle()

            var restoredSheet: LanguageBottomSheetDialogFragment? = null
            var secondActivity: android.app.Activity? = null
            scenario.onActivity {
                secondActivity = it
                restoredSheet = it.supportFragmentManager.findFragmentByTag(SHEET_TAG) as? LanguageBottomSheetDialogFragment
            }
            assertNotSame("positive control: the Activity really was recreated", firstActivity, secondActivity)
            val sheet = assertNotNull_(restoredSheet, "positive control: the framework restored the sheet after recreation")
            assertTrue("positive control: the restored sheet is showing", sheet.isAdded)

            var thirdSeen = false
            scenario.onActivity { current ->
                sheet.applyLanguageChoice(LocaleHelper.supportedLanguages.first { it.code == CHOSEN })
                assertEquals("the choice itself is stored", CHOSEN, LocaleHelper.getLanguage(current))
            }
            settle()
            scenario.onActivity { thirdSeen = it !== secondActivity }
            assertTrue("choosing a language on a restored sheet must recreate the Activity so the UI changes now", thirdSeen)
        }
    }


    private fun <T> assertNotNull_(value: T?, message: String): T {
        assertNotNull(message, value)
        return value!! // asserted non-null on the line above
    }
}
