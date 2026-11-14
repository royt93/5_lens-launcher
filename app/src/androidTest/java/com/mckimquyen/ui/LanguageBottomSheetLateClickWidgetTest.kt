package com.mckimquyen.ui

import android.os.SystemClock
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.util.LocaleHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Self-review finding: the language picker runs its row action 200 ms after the tap (a postDelayed so the user sees the check animation),
 * and that action calls requireContext() and dismiss(). If the fragment goes away inside that window the action must not run.
 */
@RunWith(AndroidJUnit4::class)
class LanguageBottomSheetLateClickWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)

    private val savedKeys = listOf(SELECTED_LANGUAGE_KEY, LANGUAGE_SELECTED_KEY)
    private val saved = HashMap<String, Any?>()

    @Before fun remember() {
        savedKeys.forEach { saved[it] = prefs.all[it] }
    }

    @After fun restore() {
        val editor = prefs.edit()
        for (key in savedKeys) {
            when (val v = saved[key]) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, v)
                is String -> editor.putString(key, v)
            }
        }
        editor.commit()
    }

    private fun otherLanguageRow(recycler: RecyclerView): Int {
        val current = LocaleHelper.getLanguage(context)
        return LocaleHelper.supportedLanguages.indexOfFirst { it.code != current }
    }

    @Test fun aTapOnARowStillSelectsTheLanguageWhenTheSheetStaysOpen() {
        // Positive control: the 200 ms action does run and does its job when nothing interrupts it.
        var picked: String? = null
        val scenario = launchFragment<LanguageBottomSheetDialogFragment>(themeResId = R.style.AppTheme_NoActionBar)
        scenario.onFragment { fragment ->
            fragment.setOnLanguageSelectedListener(object : LanguageBottomSheetDialogFragment.OnLanguageSelectedListener {
                override fun onLanguageSelected(languageCode: String) { picked = languageCode }
            })
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        var expected = ""
        scenario.onFragment { fragment ->
            val rv = fragment.requireView().findViewById<RecyclerView>(R.id.rvLanguages)
            val index = otherLanguageRow(rv)
            expected = LocaleHelper.supportedLanguages[index].code
            rv.scrollToPosition(index)
            rv.post { rv.findViewHolderForAdapterPosition(index)?.itemView?.performClick() }
        }
        waitUntil { picked != null }
        assertEquals(expected, picked)
        scenario.close()
    }

    @Test fun aTapFollowedByTheSheetBeingDestroyedInsideTheDelayDoesNotRunTheActionOrCrash() {
        val before = prefs.getString(SELECTED_LANGUAGE_KEY, null)
        var picked: String? = null
        val scenario = launchFragment<LanguageBottomSheetDialogFragment>(themeResId = R.style.AppTheme_NoActionBar)
        scenario.onFragment { fragment ->
            fragment.setOnLanguageSelectedListener(object : LanguageBottomSheetDialogFragment.OnLanguageSelectedListener {
                override fun onLanguageSelected(languageCode: String) { picked = languageCode }
            })
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onFragment { fragment ->
            val rv = fragment.requireView().findViewById<RecyclerView>(R.id.rvLanguages)
            val index = otherLanguageRow(rv)
            rv.scrollToPosition(index)
            rv.post { rv.findViewHolderForAdapterPosition(index)?.itemView?.performClick() }
        }
        // Let the click happen, then take the sheet down well before the 200 ms action is due.
        SystemClock.sleep(CLICK_SETTLE_MS)
        scenario.moveToState(Lifecycle.State.DESTROYED)
        // Wait past the delay. If the action still runs it calls requireContext() on a destroyed fragment and the process dies here.
        SystemClock.sleep(AFTER_DELAY_MS)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNull("the language must not be picked by a sheet that no longer exists", picked)
        assertEquals("the stored language must not change", before, prefs.getString(SELECTED_LANGUAGE_KEY, null))
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        while (SystemClock.uptimeMillis() < deadline && !condition()) SystemClock.sleep(POLL_MS)
        org.junit.Assert.assertTrue("timed out waiting for the 200 ms row action", condition())
    }

    private companion object {
        const val SELECTED_LANGUAGE_KEY = "Locale.Helper.Selected.Language"
        const val LANGUAGE_SELECTED_KEY = "Locale.Helper.Language.Selected"
        const val CLICK_SETTLE_MS = 40L
        const val AFTER_DELAY_MS = 600L
        const val WAIT_MS = 4_000L
        const val POLL_MS = 50L
    }
}
