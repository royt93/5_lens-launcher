package com.mckimquyen.util

/**
 * UI-024: shared display-cap formatting so LensView (fisheye grid) and AppAdapter (Apps tab)
 * render the exact same text for the exact same count - one function, not two copies of the
 * same `if`. Never called for count == 0 - both render sites gate visibility on count > 0 first.
 */
object NotificationBadgeFormatter {
    private const val DISPLAY_CEILING = 99

    @JvmStatic
    fun format(count: Int): String = if (count > DISPLAY_CEILING) "99+" else count.toString()
}
