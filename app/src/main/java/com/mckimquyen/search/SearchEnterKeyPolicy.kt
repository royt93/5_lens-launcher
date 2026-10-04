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
    fun decide(actionId: Int, keyAction: Int?, keyCode: Int?, downHandled: Boolean): Outcome {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
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
