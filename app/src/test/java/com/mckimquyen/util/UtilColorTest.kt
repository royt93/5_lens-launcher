package com.mckimquyen.util

import android.graphics.Bitmap
import android.graphics.Color
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage gap found by whole-codebase audit sweep: UtilColor backs the icon-color sort feature
 * (App.paletteColor / SortType) but had zero test at any tier before this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilColorTest {

    @Test
    fun `getPaletteColorFromBitmap returns black for a null bitmap`() {
        assertEquals(Color.BLACK, UtilColor.getPaletteColorFromBitmap(null))
    }

    @Test
    fun `getPaletteColorFromBitmap returns a real color for a solid-fill bitmap`() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)

        val result = UtilColor.getPaletteColorFromBitmap(bitmap)

        assertNotEquals("a real bitmap must not silently fall back to black", Color.BLACK, result)
    }

    @Test
    fun `getPaletteColorFromApp delegates to the app's own icon bitmap`() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val app = App(id = 1, icon = bitmap)

        assertEquals(
            UtilColor.getPaletteColorFromBitmap(bitmap),
            UtilColor.getPaletteColorFromApp(app)
        )
    }

    @Test
    fun `getHueColorFromColor matches known HSV hue angles`() {
        assertEquals(0f, UtilColor.getHueColorFromColor(Color.RED), 0.01f)
        assertEquals(120f, UtilColor.getHueColorFromColor(Color.GREEN), 0.01f)
        assertEquals(240f, UtilColor.getHueColorFromColor(Color.BLUE), 0.01f)
    }

    @Test
    fun `getHueColorFromApp reads the app's own stored paletteColor`() {
        val app = App(id = 1, paletteColor = Color.GREEN)
        assertEquals(120f, UtilColor.getHueColorFromApp(app), 0.01f)
    }
}
