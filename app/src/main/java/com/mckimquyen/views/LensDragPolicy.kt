package com.mckimquyen.views

import com.mckimquyen.model.LensWorkspace

/**
 * FISH-038b: pure decisions for "hold an icon, then drag it onto another lens". With one lens
 * nothing changes: the app menu still opens when the hold times out.
 */
internal object LensDragPolicy {
    /** A held icon that moved past touch slop becomes a drag, but only when another lens exists. */
    fun shouldStartDrag(holdTriggered: Boolean, hasOtherLenses: Boolean, movedPastSlop: Boolean): Boolean =
        holdTriggered && hasOtherLenses && movedPastSlop

    /** With 2+ lenses a hold that ends without moving opens the app menu on release. */
    fun shouldOpenMenuOnRelease(holdTriggered: Boolean, dragStarted: Boolean, hasOtherLenses: Boolean): Boolean =
        holdTriggered && !dragStarted && hasOtherLenses

    /** On a single lens the hold opens the menu at the timeout, as before this feature. */
    fun shouldOpenMenuOnTimeout(hasOtherLenses: Boolean): Boolean = !hasOtherLenses

    /** Every lens except the one the app is dragged from. */
    fun targets(lenses: List<LensWorkspace>, sourceLensId: String): List<LensWorkspace> =
        lenses.filter { it.id != sourceLensId }
}
