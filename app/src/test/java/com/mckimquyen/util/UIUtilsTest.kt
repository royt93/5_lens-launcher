package com.mckimquyen.util

import android.app.Activity
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage gap found by whole-codebase audit sweep: UIUtils backs edge-to-edge inset handling -
 * exactly the problem area that caused multiple real, live-verified bugs this session
 * (UI-013 through UI-020: nav-bar harmony, status-bar content extension, LensView double
 * insets, top-margin compounding). It had zero test at any tier before this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UIUtilsTest {

    private fun activity() = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun dispatchSystemBarInsets(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(left, top, right, bottom))
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
    }

    @Test
    fun `setupEdgeToEdge1 makes the window not fit system windows`() {
        val window = activity().window

        UIUtils.setupEdgeToEdge1(window)

        // Pre-R (this project's Robolectric SDK), WindowCompat implements "don't fit system
        // windows" via legacy systemUiVisibility flags - assert the actual flags it must set.
        val flags = window.decorView.systemUiVisibility
        val expected = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        assertEquals(expected, flags and expected)
    }

    @Test
    fun `setupEdgeToEdge2 applies system bar insets as padding on all sides by default`() {
        val view = View(activity())
        UIUtils.setupEdgeToEdge2(view)

        dispatchSystemBarInsets(view, left = 10, top = 20, right = 30, bottom = 40)

        assertEquals(10, view.paddingLeft)
        assertEquals(20, view.paddingTop)
        assertEquals(30, view.paddingRight)
        assertEquals(40, view.paddingBottom)
    }

    @Test
    fun `setupEdgeToEdge2 zeroes top padding when paddingTop is false`() {
        val view = View(activity())
        UIUtils.setupEdgeToEdge2(view, paddingTop = false)

        dispatchSystemBarInsets(view, left = 10, top = 20, right = 30, bottom = 40)

        assertEquals(0, view.paddingTop)
        assertEquals(10, view.paddingLeft)
        assertEquals(40, view.paddingBottom)
    }

    @Test
    fun `setupEdgeToEdge2 zeroes bottom padding when paddingBottom is false`() {
        val view = View(activity())
        UIUtils.setupEdgeToEdge2(view, paddingBottom = false)

        dispatchSystemBarInsets(view, left = 10, top = 20, right = 30, bottom = 40)

        assertEquals(0, view.paddingBottom)
        assertEquals(20, view.paddingTop)
    }
}
