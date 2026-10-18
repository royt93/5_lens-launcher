package com.mckimquyen.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView

/**
 * SEARCH-010: keeps the T9 keypad at its natural height while there is room, but never lets it take
 * more than 40% of the space its parent offers, so a low-height (landscape) window keeps search
 * results visible and the keys reachable by scrolling instead of overflowing the screen.
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
            val limit = View.MeasureSpec.getSize(heightMeasureSpec) * MAX_HEIGHT_PERCENT / 100
            View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, bounded)
    }

    private companion object {
        const val MAX_HEIGHT_PERCENT = 40
    }
}
