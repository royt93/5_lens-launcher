package com.mckimquyen.ui

import android.content.Context
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInputMethodManager

/**
 * The window-focus hook: when the window regains focus while the search is open and the T9 keypad stands in for the soft keyboard,
 * the soft keyboard is hidden again. Robolectric's [ShadowInputMethodManager] stands in for the real IME, so this covers that the
 * hook is wired to the focus event and to the policy, not what a real keyboard does (that is checked on a phone with a real split,
 * see doc/task/evidence/brainstorm-2026-11-14/SUMMARY_split-screen-keyboard-hook.md). ActHome is built up to start, without onResume,
 * because BaseActivity.onResume asks for a display that Robolectric's Activity context does not have.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.mckimquyen.app.RApplication::class)
class ActHomeSoftKeyboardFocusHookTest {
    private companion object {
        const val SHORT_QUALIFIERS = "w411dp-h400dp"
        const val TALL_QUALIFIERS = "w411dp-h891dp"
    }

    private class Rig(val activity: ActHome, val search: SearchView, val ime: ShadowInputMethodManager) {
        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun keyboardUp() {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(search.editText, 0)
        }
        fun focusGained() { activity.onWindowFocusChanged(true); idle() }
    }

    private fun rig(t9Switch: Boolean, openSearch: Boolean): Rig {
        UtilSettings(org.robolectric.RuntimeEnvironment.getApplication()).saveEnableT9SearchKeypad(t9Switch)
        val activity = Robolectric.buildActivity(ActHome::class.java).create().start().postCreate(null).visible().get()
        val search = activity.findViewById<SearchView>(R.id.searchView)
        val r = Rig(activity, search, shadowOf(activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager))
        if (openSearch) {
            activity.openSearchFromHome()
            r.idle()
            assertEquals("positive control: the search must be showing", SearchView.TransitionState.SHOWN, search.currentTransitionState)
        }
        return r
    }

    @Config(qualifiers = SHORT_QUALIFIERS)
    @Test fun focusGainHidesTheSoftKeyboardWhileTheKeypadStandsInForIt() {
        val r = rig(t9Switch = false, openSearch = true)
        r.keyboardUp()
        assertTrue("positive control: the soft keyboard is up before the focus event", r.ime.isSoftInputVisible)
        r.focusGained()
        assertEquals("a window under the compact height shows the keypad, so the keyboard must go", false, r.ime.isSoftInputVisible)
    }

    @Config(qualifiers = TALL_QUALIFIERS)
    @Test fun focusGainLeavesAKeyboardTheUserWantsAtFullHeight() {
        val r = rig(t9Switch = false, openSearch = true)
        r.keyboardUp()
        assertTrue("positive control: the soft keyboard is up before the focus event", r.ime.isSoftInputVisible)
        r.focusGained()
        assertTrue("tall window, T9 off: the keypad is not in use, the keyboard stays", r.ime.isSoftInputVisible)
    }

    @Config(qualifiers = TALL_QUALIFIERS)
    @Test fun focusGainHidesTheSoftKeyboardWhenTheUserTurnedT9On() {
        val r = rig(t9Switch = true, openSearch = true)
        r.keyboardUp()
        assertTrue("positive control: the soft keyboard is up before the focus event", r.ime.isSoftInputVisible)
        r.focusGained()
        assertEquals("T9 switched on by the user: the keypad is in use at any height", false, r.ime.isSoftInputVisible)
    }

    @Config(qualifiers = SHORT_QUALIFIERS)
    @Test fun focusGainDoesNothingWhileTheSearchIsNotShowing() {
        val r = rig(t9Switch = false, openSearch = false)
        r.keyboardUp()
        assertTrue("positive control: the soft keyboard is up before the focus event", r.ime.isSoftInputVisible)
        r.focusGained()
        assertTrue("search closed: the hook must not touch the keyboard even in a short window", r.ime.isSoftInputVisible)
    }
}
