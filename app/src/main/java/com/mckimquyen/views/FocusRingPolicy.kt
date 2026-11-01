package com.mckimquyen.views

/** What releasing the finger does while (or after) the FISH-031 shortcut ring was up. */
sealed interface FocusRingReleaseAction {
    data class RunShortcut(val index: Int) : FocusRingReleaseAction
    data object OpenAppMenu : FocusRingReleaseAction
    data object None : FocusRingReleaseAction
}

/** FISH-031: every ring decision as a pure function so [LensView] only wires state to it. */
object FocusRingPolicy {
    /** The ring needs at least one shortcut and is hidden in Clean Mode (like the FISH-024 halo). */
    fun shouldShowRing(cleanMode: Boolean, shortcutCount: Int): Boolean = !cleanMode && shortcutCount > 0

    /** Reduced motion keeps the chips but drops the extra magnification step. */
    fun shouldSuppressExtraScale(reduceMotion: Boolean): Boolean = reduceMotion

    /** A finger resting on a chip is choosing it, never starting the drag to another lens. */
    fun shouldAllowDragStart(hasOtherLenses: Boolean, activeChipIndex: Int, movedPastSlop: Boolean): Boolean =
        hasOtherLenses && activeChipIndex < 0 && movedPastSlop

    fun resolveRelease(hasRingActive: Boolean, activeChipIndex: Int, dragStarted: Boolean): FocusRingReleaseAction = when {
        dragStarted -> FocusRingReleaseAction.None
        hasRingActive && activeChipIndex >= 0 -> FocusRingReleaseAction.RunShortcut(activeChipIndex)
        else -> FocusRingReleaseAction.OpenAppMenu
    }
}
