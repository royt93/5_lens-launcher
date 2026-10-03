package com.mckimquyen.views

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup

/**
 * UI-025: a transient, invisible 1x1 view a `PopupMenu` can anchor to. `LensView` draws every icon
 * on one canvas and the lens menu opens at a touch point, so neither has a child view of its own.
 * The caller picks the `LayoutParams` type its parent needs; everything else lives here once.
 */
internal object PointAnchor {

    const val SIZE_PX = 1

    /**
     * Adds an anchor at ([x], [y]) in [parent]'s coordinates and lays it out immediately:
     * `PopupMenu` reads the anchor's screen position before the next layout pass runs.
     */
    fun attach(parent: ViewGroup, x: Int, y: Int, params: ViewGroup.MarginLayoutParams): View {
        val anchor = View(parent.context)
        params.width = SIZE_PX
        params.height = SIZE_PX
        params.leftMargin = x
        params.topMargin = y
        parent.addView(anchor, params)
        val exactly = View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY)
        anchor.measure(exactly, exactly)
        anchor.layout(x, y, x + SIZE_PX, y + SIZE_PX)
        return anchor
    }

    /**
     * Removes [anchor]. While its parent is inside `dispatchDetachedFromWindow`, mutating the
     * child array makes that loop hit a null child (NullPointerException, found on a device), so
     * the removal is posted to the main looper instead. Not `parent.post`: that only queues until
     * the parent is attached to a window, so on a detached parent it would never run.
     */
    fun remove(anchor: View, deferUntilParentFinishes: Boolean) {
        val parent = anchor.parent as? ViewGroup ?: return
        if (deferUntilParentFinishes) {
            Handler(Looper.getMainLooper()).post { parent.removeView(anchor) }
        } else {
            parent.removeView(anchor)
        }
    }
}
