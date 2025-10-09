package com.mckimquyen.util

import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap

object UtilBitmap {

    /**
     * Convert Drawable to Bitmap
     * Source: http://stackoverflow.com/questions/3035692/how-to-convert-a-drawable-to-a-bitmap
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.setAntiAlias(true)
            drawable.setDither(true)
            drawable.setTargetDensity(Int.MAX_VALUE)
            drawable.bitmap?.let { return it }
        }

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Get bitmap from resource id with quality options
     */
    private fun resIdToBitmap(res: Resources?, resId: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            BitmapFactory.decodeResource(res, resId, options)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get bitmap from app package name
     */
    fun packageNameToBitmap(packageManager: PackageManager, packageName: String): Bitmap? {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val resources = packageManager.getResourcesForApplication(applicationInfo)
            resIdToBitmap(resources, applicationInfo.icon)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get bitmap from app package name with specific resource id
     * Falls back to default app icon if resource not found
     */
    fun packageNameToBitmap(packageManager: PackageManager, packageName: String, resId: Int): Bitmap? {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val resources = packageManager.getResourcesForApplication(applicationInfo)

            resIdToBitmap(resources, resId) ?: run {
                val drawable = packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        } catch (e: NotFoundException) {
            e.printStackTrace()
            null
        }
    }
}
