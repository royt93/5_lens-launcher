package com.mckimquyen.adt

import com.mckimquyen.R

/**
 * UI-031: Layout policy for app list row under large font scales.
 * When fontScale >= 1.5x (large/accessibility text), inline action buttons (hide/lock)
 * collapse into ivAppMenu to give the app title full remaining row width.
 */
object AppRowFontScalePolicy {
    const val COLLAPSE_THRESHOLD = 1.5f

    @JvmStatic
    fun shouldCollapseRowActions(fontScale: Float): Boolean = fontScale >= COLLAPSE_THRESHOLD

    @JvmStatic
    fun rowLabelEndAnchor(isCollapsed: Boolean): Int =
        if (isCollapsed) R.id.ivAppMenu else R.id.btAppLock
}
