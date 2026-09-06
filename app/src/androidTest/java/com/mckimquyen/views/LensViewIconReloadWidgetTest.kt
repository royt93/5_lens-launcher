package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.util.BitmapCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-002 widget proof: when BitmapCache has evicted (or never had) an icon for a visible
 * grid cell, drawing LensView must trigger an asynchronous reload that eventually repopulates
 * the cache - not leave that cell permanently blank, which is exactly what would happen before
 * this story (drawAppIcon silently skipped drawing on a cache miss with no reload path).
 */
@RunWith(AndroidJUnit4::class)
class LensViewIconReloadWidgetTest {

    @Test
    fun drawingAMissingIcon_triggersAnAsyncReloadThatRepopulatesTheCache() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Use the real, always-installed test package so PackageManager/UtilBitmap has a real
        // icon to extract - this is the same real-device boundary the story's reload path runs
        // against in production, not a mock.
        val packageName = context.packageName
        val iconCacheKey = "$packageName/reload-test#v1#system"
        BitmapCache.clear()

        var lensView: LensView? = null
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, 800, 800)
                setApps(arrayListOf(App(id = 0, packageName = packageName, name = "reload-test", iconCacheKey = iconCacheKey)))
            }
        }

        instrumentation.runOnMainSync {
            val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            lensView!!.draw(Canvas(bitmap))
        }

        val deadline = System.currentTimeMillis() + 8_000
        var reloaded: Bitmap? = null
        while (System.currentTimeMillis() < deadline) {
            reloaded = BitmapCache.get(iconCacheKey)
            if (reloaded != null) break
            Thread.sleep(200)
        }

        assertNotNull("expected the evicted icon to be reloaded into BitmapCache", reloaded)
        assertTrue("reloaded bitmap must not be recycled", reloaded?.isRecycled == false)
    }
}
