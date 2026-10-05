package com.mckimquyen.search

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.mckimquyen.search.SearchEnterKeyPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Physical Enter reaches the editor-action listener as ACTION_DOWN first; TextView only replays
 * ACTION_UP to the listener when it returned true for the DOWN. Matching UP alone (the old code)
 * therefore never launched anything on Android 13 (TECNO BG6, found via logcat).
 */
class SearchEnterKeyPolicyTest {

    private fun decide(
        actionId: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
        keyAction: Int? = null,
        keyCode: Int? = null,
        repeatCount: Int = 0,
        downHandled: Boolean = false
    ) = SearchEnterKeyPolicy.decide(actionId, keyAction, keyCode, repeatCount, downHandled)

    @Test
    fun `enter down launches and remembers it`() {
        val out = decide(keyAction = KeyEvent.ACTION_DOWN, keyCode = KeyEvent.KEYCODE_ENTER)
        assertEquals(Decision.LAUNCH, out.decision)
        assertEquals(true, out.downHandled)
    }

    @Test
    fun `enter up right after a handled down is consumed without a second launch`() {
        val out = decide(keyAction = KeyEvent.ACTION_UP, keyCode = KeyEvent.KEYCODE_ENTER, downHandled = true)
        assertEquals(Decision.CONSUME, out.decision)
        assertEquals(false, out.downHandled)
    }

    @Test
    fun `enter up alone still launches for devices that only deliver up`() {
        val out = decide(keyAction = KeyEvent.ACTION_UP, keyCode = KeyEvent.KEYCODE_ENTER)
        assertEquals(Decision.LAUNCH, out.decision)
        assertEquals(false, out.downHandled)
    }

    @Test
    fun `a full down then up gesture launches exactly once`() {
        val down = decide(keyAction = KeyEvent.ACTION_DOWN, keyCode = KeyEvent.KEYCODE_ENTER)
        val up = decide(
            keyAction = KeyEvent.ACTION_UP, keyCode = KeyEvent.KEYCODE_ENTER, downHandled = down.downHandled
        )
        assertEquals(1, listOf(down, up).count { it.decision == Decision.LAUNCH })
    }

    @Test
    fun `a second gesture after a consumed up launches again`() {
        val up = decide(keyAction = KeyEvent.ACTION_UP, keyCode = KeyEvent.KEYCODE_ENTER, downHandled = true)
        val next = decide(
            keyAction = KeyEvent.ACTION_DOWN, keyCode = KeyEvent.KEYCODE_ENTER, downHandled = up.downHandled
        )
        assertEquals(Decision.LAUNCH, next.decision)
    }

    @Test
    fun `supported ime actions launch and clear any stale down flag`() {
        for (action in listOf(
            EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_DONE
        )) {
            val out = decide(actionId = action, downHandled = true)
            assertEquals("action $action", Decision.LAUNCH, out.decision)
            assertEquals("action $action", false, out.downHandled)
        }
    }

    @Test
    fun `ime action next is ignored`() {
        assertEquals(Decision.IGNORE, decide(actionId = EditorInfo.IME_ACTION_NEXT).decision)
    }

    @Test
    fun `other keys and unspecified actions are ignored`() {
        assertEquals(
            Decision.IGNORE,
            decide(keyAction = KeyEvent.ACTION_DOWN, keyCode = KeyEvent.KEYCODE_A).decision
        )
        assertEquals(Decision.IGNORE, decide().decision)
    }

    @Test
    fun `an ignored event keeps the down flag untouched`() {
        val out = decide(keyAction = KeyEvent.ACTION_DOWN, keyCode = KeyEvent.KEYCODE_A, downHandled = true)
        assertEquals(Decision.IGNORE, out.decision)
        assertEquals(true, out.downHandled)
    }

    @Test
    fun `holding enter down repeats are consumed without relaunching, preserving an already-handled down`() {
        val out = decide(
            keyAction = KeyEvent.ACTION_DOWN, keyCode = KeyEvent.KEYCODE_ENTER, repeatCount = 1, downHandled = true
        )
        assertEquals(Decision.CONSUME, out.decision)
        assertEquals(true, out.downHandled)
    }

    @Test
    fun `holding enter down repeats after a no-result down stay unhandled, not resurrected`() {
        // #8 (re-review): a repeat DOWN following a DOWN that decided LAUNCH-but-found-nothing
        // (which resets the flag to false) must not resurrect it to true.
        val out = decide(
            keyAction = KeyEvent.ACTION_DOWN, keyCode = KeyEvent.KEYCODE_ENTER, repeatCount = 1, downHandled = false
        )
        assertEquals(Decision.CONSUME, out.decision)
        assertEquals(false, out.downHandled)
    }
}
