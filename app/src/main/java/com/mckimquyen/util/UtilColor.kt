package com.mckimquyen.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.palette.graphics.Palette
import com.mckimquyen.model.App

/**
 * Utility for color extraction and manipulation
 */
object UtilColor {

    @ColorInt
    fun getPaletteColorFromApp(app: App): Int = getPaletteColorFromBitmap(app.icon)

    @JvmStatic
    @ColorInt
    fun getPaletteColorFromBitmap(bitmap: Bitmap?): Int {
        if (bitmap == null) return Color.BLACK

        return try {
            val palette = Palette.from(bitmap).generate()
            palette.swatches.maxByOrNull { it.population }?.rgb ?: Color.BLACK
        } catch (e: Exception) {
            e.printStackTrace()
            Color.BLACK
        }
    }

    @JvmStatic
    fun getHueColorFromApp(app: App): Float = getHueColorFromColor(app.paletteColor)

    @JvmStatic
    fun getHueColorFromColor(@ColorInt color: Int): Float {
        val hsvValues = FloatArray(3)
        Color.colorToHSV(color, hsvValues)
        return hsvValues[0]
    }
}
