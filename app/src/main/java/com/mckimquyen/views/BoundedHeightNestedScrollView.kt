package com.mckimquyen.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isGone
import androidx.core.widget.NestedScrollView

/**
 * SEARCH-010: keeps the T9 keypad at its natural height while there is room, but never lets it take
 * more than 40% of the space its parent offers, so a low-height (landscape) window keeps search
 * results visible and the keys reachable by scrolling instead of overflowing the screen.
 *
 * In a vertical [LinearLayout] that has a weighted child at or above this view, LinearLayout offers every
 * child the WHOLE column height and does not subtract the rows above it, so 40% of that alone can overflow
 * (phone landscape: 596 px of rows above + a 389 px keypad in a 974 px column overflowed by 11 px). In that
 * case the space the rows above already take is subtracted first.
 */
class BoundedHeightNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val bounded = if (mode == View.MeasureSpec.UNSPECIFIED) {
            heightMeasureSpec
        } else {
            val offered = View.MeasureSpec.getSize(heightMeasureSpec)
            val room = (offered - heightTakenAbove()).coerceAtLeast(0)
            val limit = minOf(offered * MAX_HEIGHT_PERCENT / 100, room)
            View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, bounded)
    }

    /**
     * Height (with margins) of the visible fixed rows above this view, when the parent is a vertical
     * LinearLayout that did NOT already subtract them. LinearLayout only skips the subtraction when the
     * weight total seen so far is positive, i.e. a weighted child sits at or above this view. Weighted
     * zero-height children are left out: their measured height from an earlier pass is stale.
     */
    private fun heightTakenAbove(): Int {
        val column = parent as? LinearLayout ?: return 0
        if (column.orientation != LinearLayout.VERTICAL) return 0
        val own = column.indexOfChild(this)
        var weightSeen = 0f
        var taken = 0
        for (i in 0..own) {
            val child = column.getChildAt(i)
            val lp = child.layoutParams as LinearLayout.LayoutParams
            weightSeen += lp.weight
            if (i == own || child.isGone) continue
            val weightedFiller = lp.weight > 0f && lp.height == 0
            if (!weightedFiller) taken += child.measuredHeight + lp.topMargin + lp.bottomMargin
        }
        return if (weightSeen > 0f) taken else 0
    }

    private companion object {
        const val MAX_HEIGHT_PERCENT = 40
    }
}
