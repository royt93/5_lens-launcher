package com.mckimquyen.model

import android.graphics.Bitmap
import androidx.annotation.ColorInt
import androidx.annotation.Keep

/**
 * Immutable data model representing an installed application.
 *
 * @property id Unique identifier for the app
 * @property label Display label shown to users
 * @property packageName Android package name (e.g., "com.example.app")
 * @property name Application name
 * @property iconResId Resource ID for the app icon (if using resource)
 * @property icon Bitmap icon of the app (may be null if not loaded yet)
 * @property installDate Installation timestamp in milliseconds
 * @property paletteColor Dominant color extracted from app icon for theming
 *
 * Note: Call [Bitmap.recycle] on the icon when no longer needed to free memory.
 */
@Keep
data class App(
    val id: Int = 0,
    val label: CharSequence = "",
    val packageName: CharSequence = "",
    val name: CharSequence = "",
    val iconResId: Int = 0,
    val icon: Bitmap? = null,
    val installDate: Long = 0L,
    @ColorInt val paletteColor: Int = 0,
    val isOpened: Boolean = true,
    val isVisible: Boolean = true,
    val openCount: Long = 0L
) {
    /**
     * Creates a copy of this App with the specified properties changed.
     * Useful for updating specific fields while keeping others immutable.
     */
    fun copyWithIcon(newIcon: Bitmap?): App = copy(icon = newIcon)

    fun copyWithPaletteColor(@ColorInt newColor: Int): App = copy(paletteColor = newColor)

    fun copyWithLockAndVisibility(newOpened: Boolean, newVisible: Boolean, newOpenCount: Long): App = 
        copy(isOpened = newOpened, isVisible = newVisible, openCount = newOpenCount)

    /**
     * Checks if the app has a valid package name.
     */
    fun hasValidPackageName(): Boolean = packageName.isNotBlank()

    /**
     * Checks if the icon is loaded.
     */
    fun hasIcon(): Boolean = icon != null
}
