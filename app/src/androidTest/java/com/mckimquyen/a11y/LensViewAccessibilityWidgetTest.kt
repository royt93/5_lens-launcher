package com.mckimquyen.a11y

import android.content.Context
import android.graphics.Rect
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.views.LensView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A11Y-001: Widget tests proving [LensView] exposes an accessible semantics tree
 * for TalkBack and D-pad/keyboard navigation on real devices.
 */
@RunWith(AndroidJUnit4::class)
class LensViewAccessibilityWidgetTest {

    private lateinit var targetContext: Context
    private lateinit var lensView: LensView

    @Before
    fun setup() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(targetContext)
        }
    }

    @Test
    fun testLensView_accessibilityHelperIsAttachedAndFocusable() {
        assertNotNull("Accessibility helper must be attached", lensView.getAccessibilityHelper())
        assertTrue("LensView must be focusable for accessibility/D-pad", lensView.isFocusable)
    }

    @Test
    fun testLensView_semanticsTreePopulatesVirtualAppNodesWithCorrectProperties() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test Phone", packageName = "com.android.dialer", name = "DialtactsActivity"),
            App(id = 2, label = "Test Browser", packageName = "com.android.chrome", name = "Main")
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            lensView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, 1080, 1920)
        }

        val helper = lensView.getAccessibilityHelper()
        assertNotNull("Helper must be present", helper)

        val visibleIds = mutableListOf<Int>()
        helper!!.testGetVisibleVirtualViews(visibleIds)
        assertEquals("Must expose exactly 2 virtual nodes", 2, visibleIds.size)
        assertEquals(listOf(0, 1), visibleIds)

        // Test app 0 node
        val node0 = AccessibilityNodeInfoCompat.obtain()
        helper.testPopulateNodeForVirtualView(0, node0)
        assertEquals("Test Phone", node0.text)
        assertEquals("Test Phone", node0.contentDescription)
        assertEquals("android.widget.Button", node0.className)
        assertTrue("Node must be clickable", node0.isClickable)
        assertTrue("Node must be focusable", node0.isFocusable)

        // Test app 1 node
        val node1 = AccessibilityNodeInfoCompat.obtain()
        helper.testPopulateNodeForVirtualView(1, node1)
        assertEquals("Test Browser", node1.text)
        assertEquals("Test Browser", node1.contentDescription)
        assertEquals("android.widget.Button", node1.className)
        assertTrue("Node must be clickable", node1.isClickable)
    }

    @Test
    fun testLensView_getAppBounds_returnsValidCoordinates() {
        val testApps = arrayListOf(
            App(id = 1, label = "App 1", packageName = "com.test1", name = "Act1"),
            App(id = 2, label = "App 2", packageName = "com.test2", name = "Act2")
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            lensView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, 1080, 1920)
            // Trigger draw to compute grid cache
            val canvas = android.graphics.Canvas()
            lensView.draw(canvas)
        }

        val bounds = Rect()
        val hasBounds0 = lensView.getAppBounds(0, bounds)
        assertTrue("App 0 bounds must be computable", hasBounds0)
        assertFalse("App 0 bounds must not be empty", bounds.isEmpty)
    }
}
