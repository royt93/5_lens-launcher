package com.mckimquyen.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.util.LocaleHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A language row acts 200 ms after the tap. If the user presses Home inside that window the Activity has already saved its state, and a plain
 * DialogFragment.dismiss() then throws IllegalStateException("Can not perform this action after onSaveInstanceState"). The sheet is the first
 * screen a new user sees. Log tag F800LANG.
 *
 * The test starts a bare FragmentActivity-based host (ActSettings is not needed), shows the sheet, moves the Activity to the stopped state with
 * its state saved, then runs the row action the way the delayed runnable would.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSheetDismissAfterStateSavedTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F800LANG"
        const val SHEET_TAG = "LanguageTestSheet"
        const val SETTLE_MS = 700L
        const val CHOSEN = "vi"
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var originalLanguage = "en"

    @Before fun remember() { originalLanguage = LocaleHelper.getLanguage(instrumentation.targetContext) }

    @After fun restore() {
        LocaleHelper.setLocale(instrumentation.targetContext, originalLanguage)
        instrumentation.waitForIdleSync()
    }

    private fun settle() { instrumentation.waitForIdleSync(); Thread.sleep(SETTLE_MS); instrumentation.waitForIdleSync() }

    @Test fun choosingALanguageAfterTheActivityStateWasSavedDoesNotThrow() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            settle()
            var sheet: LanguageBottomSheetDialogFragment? = null
            var picked: String? = null
            scenario.onActivity { activity: FragmentActivity ->
                val s = LanguageBottomSheetDialogFragment()
                s.setOnLanguageSelectedListener(object : LanguageBottomSheetDialogFragment.OnLanguageSelectedListener {
                    override fun onLanguageSelected(languageCode: String) { picked = languageCode }
                })
                s.show(activity.supportFragmentManager, SHEET_TAG)
                sheet = s
            }
            settle()
            val shown = requireNotNull(sheet)
            var visibleBefore = false
            scenario.onActivity { visibleBefore = shown.isAdded && shown.dialog?.isShowing == true }
            assertTrue("positive control: the sheet is showing before Home is pressed", visibleBefore)

            // Home: the Activity is stopped and its state saved, but the Fragment is still added.
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            settle()
            var stateSaved = false
            var stillAdded = false
            scenario.onActivity { stateSaved = shown.parentFragmentManager.isStateSaved; stillAdded = shown.isAdded }
            android.util.Log.i(TAG, "isStateSaved=$stateSaved isAdded=$stillAdded")
            assertTrue("positive control: the fragment manager state is saved now (else this proves nothing)", stateSaved)
            assertTrue("positive control: the sheet is still added to its Activity", stillAdded)

            var thrown: Throwable? = null
            scenario.onActivity {
                try {
                    shown.applyLanguageChoice(com.mckimquyen.util.LocaleHelper.supportedLanguages.first { it.code == CHOSEN })
                } catch (t: Throwable) { thrown = t }
            }
            android.util.Log.i(TAG, "row action threw: ${thrown?.javaClass?.simpleName}: ${thrown?.message}")
            assertNull("the row action after the state was saved must not throw (${thrown?.message})", thrown)
            assertEquals("the chosen language is still reported to the owner", CHOSEN, picked)
            assertFalse("the sheet is gone or going away", shown.isAdded && shown.dialog?.isShowing == true && !shown.isRemoving)
        }
    }
}
