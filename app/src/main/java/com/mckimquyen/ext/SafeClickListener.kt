package com.mckimquyen.ext

import android.os.SystemClock
import android.view.View

/**
 * A click listener that prevents multiple rapid clicks within a specified time interval.
 * This helps prevent double-click issues and accidental multiple triggers.
 *
 * @param defaultInterval The minimum time interval (in milliseconds) between consecutive clicks.
 *                        Default is 600ms which works well for most UI interactions.
 *                        Consider adjusting based on the specific use case:
 *                        - 300-400ms for fast interactions (e.g., number pad, keyboard)
 *                        - 600-800ms for standard buttons (recommended)
 *                        - 1000ms+ for critical actions (e.g., submit, purchase)
 * @param onSafeClick The callback to be invoked when a valid click is detected
 */
class SafeClickListener(
    private var defaultInterval: Int = 600,
    private val onSafeClick: (View) -> Unit
) : View.OnClickListener {

    private var lastTimeClicked: Long = 0

    override fun onClick(view: View?) {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
            return
        }
        lastTimeClicked = SystemClock.elapsedRealtime()

        view?.let { onSafeClick(it) }
    }
}

/**
 * Sets a safe click listener on this view that prevents rapid multiple clicks.
 * Uses the default interval of 600ms between consecutive clicks.
 *
 * @param onSafeClick The callback to be invoked when a valid click is detected
 */
fun View.setSafeOnClickListener(onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}
