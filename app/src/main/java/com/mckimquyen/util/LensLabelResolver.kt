package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace

/**
 * FISH-014: resolves the display name for the currently active lens workspace.
 * Pure logic, independent of Android framework/Context, fully unit-testable.
 */
object LensLabelResolver {

    @JvmStatic
    fun resolveActiveLensName(
        lenses: List<LensWorkspace>,
        activeLensId: String?
    ): String? {
        if (lenses.isEmpty()) return null
        if (activeLensId != null) {
            val matched = lenses.firstOrNull { it.id == activeLensId }
            if (matched != null) return matched.name
        }
        return lenses.first().name
    }
}
