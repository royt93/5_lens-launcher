package com.mckimquyen.views

/**
 * FISH-047: pure decision math for the faint name labels of the icons right next to the selected one. Nothing here allocates
 * per call; the caller owns the output array.
 */
object NeighbourLabels {
    /** Left and right cell of the selected icon, same row only. */
    const val MAX_NEIGHBOURS = 2

    /** Paint alpha of a neighbour label (about 55%), so it reads as secondary to the selected icon's own label. */
    const val ALPHA = 140

    /** Clear space kept between two labels. */
    const val GAP_DP = 8f

    /**
     * Same gate as the main label (name setting, not clean mode, finger moving) plus this effect's own switch. The caller already
     * sits behind the main label's gate, so the clean-mode term is redundant today; it is kept so this decision stays correct if
     * it is ever called from somewhere else (a device mutation without it is therefore NOT caught, by design).
     */
    fun shouldApply(enabled: Boolean, nameShown: Boolean, cleanMode: Boolean, moving: Boolean): Boolean =
        enabled && nameShown && !cleanMode && moving

    /**
     * Writes the cell indices left and right of [selected] into [out] and returns how many. Only cells on the same grid row
     * and below [count] qualify, so the first and last column, an unfilled last row and a one-column grid get fewer.
     */
    fun neighbours(selected: Int, cols: Int, count: Int, out: IntArray): Int {
        if (cols <= 1 || count <= 0 || selected < 0 || selected >= count) return 0
        var n = 0
        val col = selected % cols
        if (col > 0) out[n++] = selected - 1
        if (col < cols - 1 && selected + 1 < count) out[n++] = selected + 1
        return n
    }

    /** True when two labels centred [selectedCx] and [neighbourCx] apart do not touch, keeping [gap] between them. */
    fun fits(selectedCx: Float, neighbourCx: Float, selectedLabelW: Float, neighbourLabelW: Float, gap: Float): Boolean {
        if (!selectedCx.isFinite() || !neighbourCx.isFinite() || !selectedLabelW.isFinite() || !neighbourLabelW.isFinite()) return false
        if (selectedLabelW < 0f || neighbourLabelW < 0f) return false
        return Math.abs(neighbourCx - selectedCx) >= (selectedLabelW + neighbourLabelW) / 2f + gap
    }
}
