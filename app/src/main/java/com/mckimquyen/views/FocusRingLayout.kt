package com.mckimquyen.views

import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One shortcut chip around the held icon: [rect] is both what is drawn and what is hit-tested. */
data class FocusChipSlot(val index: Int, val rect: RectF, val centerAngleRad: Float)

/**
 * FISH-031: pure geometry of the shortcut ring. Chips sit on an arc above the held icon (screen y grows
 * downward, so "above" is a negative sine). Near the top edge the arc flips below the icon; near the side
 * edges each chip is clamped into the viewport.
 */
object FocusRingLayout {
    /** Extra scale the held icon gets while the ring is up (one step above the lens magnification). */
    const val EXTRA_MAGNIFICATION: Float = 1.25f

    private const val QUARTER_TURN = (PI / 2.0).toFloat()
    private const val SIXTH_TURN = (PI / 6.0).toFloat()

    fun computeSlots(
        iconCenterX: Float,
        iconCenterY: Float,
        iconRadius: Float,
        chipCount: Int,
        chipRadius: Float,
        ringGap: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): List<FocusChipSlot> {
        if (chipCount <= 0) return emptyList()
        val orbit = iconRadius + ringGap + chipRadius
        // ponytail: at most 3 chips (AppShortcutsProvider.MAX_SHORTCUTS); more would need a wider arc.
        val above = when (minOf(chipCount, MAX_CHIPS)) {
            1 -> listOf(-QUARTER_TURN)
            2 -> listOf(-QUARTER_TURN - SIXTH_TURN, -QUARTER_TURN + SIXTH_TURN)
            else -> listOf(-QUARTER_TURN - 2 * SIXTH_TURN, -QUARTER_TURN, -QUARTER_TURN + 2 * SIXTH_TURN)
        }
        val flipBelow = iconCenterY - orbit - chipRadius < 0f
        val angles = if (flipBelow) above.map { -it } else above
        val xs = angles.map { iconCenterX + orbit * cos(it) }
        val ys = angles.map { iconCenterY + orbit * sin(it) }
        // Shift the whole arc as one group. Clamping each chip on its own drags the outer chips toward
        // the middle one near a side edge, so their circles overlap and a touch between them is ambiguous.
        val shiftX = groupShift(xs.min() - chipRadius, xs.max() + chipRadius, viewportWidth)
        val shiftY = groupShift(ys.min() - chipRadius, ys.max() + chipRadius, viewportHeight)
        return angles.mapIndexed { index, angle ->
            // If the whole arc cannot fit in one dimension (e.g. synthetic 80px viewport with 48px
            // chip diameters), keep legacy per-chip clamping for that dimension; normal phone viewports
            // shift the group and preserve the arc's spacing.
            val x = if (xs.max() - xs.min() + 2f * chipRadius > viewportWidth) {
                xs[index].coerceIn(chipRadius, viewportWidth - chipRadius)
            } else xs[index] + shiftX
            val y = if (ys.max() - ys.min() + 2f * chipRadius > viewportHeight) {
                ys[index].coerceIn(chipRadius, viewportHeight - chipRadius)
            } else ys[index] + shiftY
            FocusChipSlot(index, RectF(x - chipRadius, y - chipRadius, x + chipRadius, y + chipRadius), angle)
        }
    }

    /** How far a group spanning [low]..[high] must move to fit inside 0..[extent]; 0 if it already fits or cannot fit. */
    private fun groupShift(low: Float, high: Float, extent: Float): Float = when {
        high - low >= extent -> 0f
        low < 0f -> -low
        high > extent -> extent - high
        else -> 0f
    }

    /**
     * Index of the chip under ([x], [y]) or -1. Chips are drawn as circles inscribed in [FocusChipSlot.rect],
     * so the hit-test uses that circle too: with real sizes neighbouring squares overlap while the circles
     * do not, and a square test would pick a chip the finger is not on.
     */
    fun chipAt(slots: List<FocusChipSlot>, x: Float, y: Float): Int =
        slots.firstOrNull { slot ->
            val radius = slot.rect.width() / 2f
            val dx = x - slot.rect.centerX()
            val dy = y - slot.rect.centerY()
            dx * dx + dy * dy <= radius * radius
        }?.index ?: -1

    private const val MAX_CHIPS = 3
}
