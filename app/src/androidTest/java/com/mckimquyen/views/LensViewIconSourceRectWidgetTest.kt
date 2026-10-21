package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.BitmapCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewIconSourceRectWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test
    fun differentBitmapSizesReuseSourceRectWithoutClippingPixelsOrChangingDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cacheField = BitmapCache::class.java.getDeclaredField("cache").apply { isAccessible = true }
        val cache = cacheField.get(BitmapCache) as LruCache<*, *>
        val oldKeys = cache.snapshot().keys.filterIsInstance<String>().toSet()
        val first = Bitmap.createBitmap(FIRST_WIDTH, FIRST_HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val second = Bitmap.createBitmap(SECOND_WIDTH, SECOND_HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        try {
            RAppsSingleton.instance.setAppIcon(FIRST_KEY, first)
            RAppsSingleton.instance.setAppIcon(SECOND_KEY, second)
            instrumentation.runOnMainSync {
                val view = LensView(ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme))
                view.setApps(arrayListOf(App(iconCacheKey = FIRST_KEY), App(iconCacheKey = SECOND_KEY)))
                val sources = mutableListOf<Rect?>()
                val bounds = mutableListOf<Rect>()
                val canvas = object : Canvas(output) {
                    override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
                        sources.add(src)
                        bounds.add(Rect(requireNotNull(src)))
                        super.drawBitmap(bitmap, src, dst, paint)
                    }
                }
                val draw = LensView::class.java.getDeclaredMethod(
                    "drawAppIcon", Canvas::class.java, RectF::class.java, Int::class.javaPrimitiveType
                ).apply { isAccessible = true }
                val target = RectF(0f, 0f, TARGET_SIZE.toFloat(), TARGET_SIZE.toFloat())
                draw.invoke(view, canvas, target, 0)
                assertEquals(Color.RED, output.getPixel(TARGET_SIZE / 2, TARGET_SIZE / 2))
                assertEquals(Rect(0, 0, FIRST_WIDTH, FIRST_HEIGHT), bounds[0])
                draw.invoke(view, canvas, target, 1)
                assertEquals(Color.BLUE, output.getPixel(TARGET_SIZE / 2, TARGET_SIZE / 2))
                assertEquals(Color.TRANSPARENT, output.getPixel(OUTPUT_SIZE - 1, OUTPUT_SIZE - 1))
                assertEquals(Rect(0, 0, SECOND_WIDTH, SECOND_HEIGHT), bounds[1])
                assertSame("mỗi icon phải tái dùng cùng Rect nguồn", sources[0], sources[1])
            }
        } finally {
            BitmapCache.retainKeys(oldKeys)
            first.recycle()
            second.recycle()
            output.recycle()
        }
    }

    private companion object {
        const val FIRST_KEY = "perf005-test-first"
        const val SECOND_KEY = "perf005-test-second"
        const val FIRST_WIDTH = 16
        const val FIRST_HEIGHT = 24
        const val SECOND_WIDTH = 31
        const val SECOND_HEIGHT = 17
        const val TARGET_SIZE = 40
        const val OUTPUT_SIZE = 80
    }
}
