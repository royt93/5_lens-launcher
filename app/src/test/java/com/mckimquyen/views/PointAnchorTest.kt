package com.mckimquyen.views

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * UI-025: the 1x1 popup anchor used to be written twice (LensView and ActHome) with only the
 * LayoutParams type differing. One helper now owns add + measure + layout + remove, so a fix to
 * its lifecycle cannot be applied to one copy and forgotten in the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PointAnchorTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun frame() = FrameLayout(context).apply {
        measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        )
        layout(0, 0, 1000, 1000)
    }

    @Test
    fun `adds exactly one 1x1 child at the requested point`() {
        val parent = frame()

        val anchor = PointAnchor.attach(parent, 120, 340, FrameLayout.LayoutParams(1, 1))

        assertEquals(1, parent.childCount)
        assertSame(anchor, parent.getChildAt(0))
        assertEquals(120, anchor.left)
        assertEquals(340, anchor.top)
        assertEquals(121, anchor.right)
        assertEquals(341, anchor.bottom)
    }

    @Test
    fun `positions the anchor through the layout params margins too`() {
        val parent = frame()

        val anchor = PointAnchor.attach(parent, 7, 9, FrameLayout.LayoutParams(1, 1))

        val params = anchor.layoutParams as FrameLayout.LayoutParams
        assertEquals(7, params.leftMargin)
        assertEquals(9, params.topMargin)
    }

    @Test
    fun `remove detaches immediately when the parent is not detaching`() {
        val parent = frame()
        val anchor = PointAnchor.attach(parent, 1, 1, FrameLayout.LayoutParams(1, 1))

        PointAnchor.remove(anchor, deferUntilParentFinishes = false)

        assertEquals(0, parent.childCount)
        assertNull(anchor.parent)
    }

    @Test
    fun `remove defers while the parent is mid-detach so its child loop is not mutated`() {
        val parent = frame()
        val anchor = PointAnchor.attach(parent, 1, 1, FrameLayout.LayoutParams(1, 1))

        PointAnchor.remove(anchor, deferUntilParentFinishes = true)
        assertNotNull("must still be attached until the looper runs", anchor.parent)

        ShadowLooper.idleMainLooper()
        assertEquals(0, parent.childCount)
        assertNull(anchor.parent)
    }

    @Test
    fun `remove on an already detached anchor does nothing`() {
        val anchor = View(context)

        PointAnchor.remove(anchor, deferUntilParentFinishes = false)

        assertNull(anchor.parent)
    }

    @Test
    fun `remove works with any ViewGroup parent`() {
        val parent: ViewGroup = frame()
        val anchor = PointAnchor.attach(parent, 0, 0, FrameLayout.LayoutParams(1, 1))

        PointAnchor.remove(anchor, deferUntilParentFinishes = false)

        assertEquals(0, parent.childCount)
    }
}
