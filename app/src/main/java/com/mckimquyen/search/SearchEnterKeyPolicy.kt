package com.mckimquyen.search

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

/**
 * Decides what the search box's editor-action listener should do with an event. A physical Enter
 * arrives as ACTION_DOWN and TextView only replays the matching ACTION_UP to the listener when the
 * DOWN was consumed, so launching on UP alone never fires. Launch on DOWN, swallow its UP, and
 * keep UP-only delivery working for devices that skip the DOWN.
 */
object SearchEnterKeyPolicy {

    enum class Decision {
        /** Open the first search result. */
        LAUNCH,

        /** Already launched on the DOWN of this gesture; take the UP without launching again. */
        CONSUME,

        /** Not ours. */
        IGNORE
    }

    data class Outcome(val decision: Decision, val downHandled: Boolean)

    @JvmStatic
    fun decide(
        actionId: Int,
        keyAction: Int?,
        keyCode: Int?,
        repeatCount: Int,
        downHandled: Boolean
    ): Outcome {
        // TextView handles both identically (doKeyDown/onKeyUp list both case labels).
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (keyAction == KeyEvent.ACTION_DOWN && repeatCount > 0) {
                // TextView replays the UP to the listener only if the listener returned true for a
                // DOWN. Consume repeats only while the first DOWN launched (flag true); otherwise
                // IGNORE so the listener returns false and no stray UP is replayed (which would be
                // read as a lone UP and launch on release).
                return if (downHandled) Outcome(Decision.CONSUME, downHandled = true)
                else Outcome(Decision.IGNORE, downHandled = false)
            }
            when (keyAction) {
                KeyEvent.ACTION_DOWN -> return Outcome(Decision.LAUNCH, downHandled = true)
                KeyEvent.ACTION_UP ->
                    return if (downHandled) Outcome(Decision.CONSUME, downHandled = false)
                    else Outcome(Decision.LAUNCH, downHandled = false)
            }
        }
        return when (actionId) {
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_DONE -> Outcome(Decision.LAUNCH, downHandled = false)
            else -> Outcome(Decision.IGNORE, downHandled)
        }
    }
}
