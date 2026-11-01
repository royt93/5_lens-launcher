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
        return angles.mapIndexed { index, angle ->
            val x = (iconCenterX + orbit * cos(angle)).coerceIn(chipRadius, viewportWidth - chipRadius)
            val y = (iconCenterY + orbit * sin(angle)).coerceIn(chipRadius, viewportHeight - chipRadius)
            FocusChipSlot(index, RectF(x - chipRadius, y - chipRadius, x + chipRadius, y + chipRadius), angle)
        }
    }

    /** Index of the chip under ([x], [y]) or -1. */
    fun chipAt(slots: List<FocusChipSlot>, x: Float, y: Float): Int =
        slots.firstOrNull { it.rect.contains(x, y) }?.index ?: -1

    private const val MAX_CHIPS = 3
}
