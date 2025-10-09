package com.mckimquyen.model

import androidx.annotation.Keep

/**
 * Immutable data model representing a grid layout configuration.
 *
 * @property itemCount Total number of items in the grid
 * @property itemCountHorizontal Number of items per row (columns)
 * @property itemCountVertical Number of items per column (rows)
 * @property itemSize Size of each grid item in pixels
 * @property spacingHorizontal Horizontal spacing between items in pixels
 * @property spacingVertical Vertical spacing between items in pixels
 */
@Keep
data class Grid(
    val itemCount: Int = 0,
    val itemCountHorizontal: Int = 0,
    val itemCountVertical: Int = 0,
    val itemSize: Float = 0f,
    val spacingHorizontal: Float = 0f,
    val spacingVertical: Float = 0f
) {
    init {
        require(itemCount >= 0) { "itemCount must be non-negative, got: $itemCount" }
        require(itemCountHorizontal >= 0) { "itemCountHorizontal must be non-negative, got: $itemCountHorizontal" }
        require(itemCountVertical >= 0) { "itemCountVertical must be non-negative, got: $itemCountVertical" }
        require(itemSize >= 0f) { "itemSize must be non-negative, got: $itemSize" }
        require(spacingHorizontal >= 0f) { "spacingHorizontal must be non-negative, got: $spacingHorizontal" }
        require(spacingVertical >= 0f) { "spacingVertical must be non-negative, got: $spacingVertical" }
    }

    /**
     * Checks if this grid has a valid configuration.
     */
    fun isValid(): Boolean =
        itemCount > 0 &&
        itemCountHorizontal > 0 &&
        itemCountVertical > 0 &&
        itemSize > 0f

    /**
     * Calculates the total width of the grid including spacing.
     */
    fun calculateTotalWidth(): Float =
        (itemCountHorizontal * itemSize) + ((itemCountHorizontal - 1) * spacingHorizontal)

    /**
     * Calculates the total height of the grid including spacing.
     */
    fun calculateTotalHeight(): Float =
        (itemCountVertical * itemSize) + ((itemCountVertical - 1) * spacingVertical)

    /**
     * Checks if the grid layout is square (equal rows and columns).
     */
    fun isSquare(): Boolean = itemCountHorizontal == itemCountVertical

    /**
     * Returns the aspect ratio (width / height) of the grid.
     */
    fun aspectRatio(): Float {
        val height = calculateTotalHeight()
        return if (height > 0f) calculateTotalWidth() / height else 0f
    }
}
