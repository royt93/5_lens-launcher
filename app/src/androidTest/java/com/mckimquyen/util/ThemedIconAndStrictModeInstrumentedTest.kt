package com.mckimquyen.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.os.StrictMode
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.BuildConfig
import com.mckimquyen.R
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget: the monochrome vector actually inflates and draws visible pixels on-device.
 * Integration: what the OS launcher sees (PackageManager app icon) carries the themed layer, and
 * RApplication really installed StrictMode in this debug process.
 */
@RunWith(AndroidJUnit4::class)
class ThemedIconAndStrictModeInstrumentedTest {

    private companion object {
        const val RENDER_SIZE_PX = 108
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun monochromeDrawable_rendersNonTransparentPixels() {
        val drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome))
        val bitmap = Bitmap.createBitmap(RENDER_SIZE_PX, RENDER_SIZE_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, RENDER_SIZE_PX, RENDER_SIZE_PX)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(RENDER_SIZE_PX * RENDER_SIZE_PX)
        bitmap.getPixels(pixels, 0, RENDER_SIZE_PX, 0, 0, RENDER_SIZE_PX, RENDER_SIZE_PX)
        bitmap.recycle()
        assertTrue("Silhouette must draw something", pixels.any { Color.alpha(it) > 0 })
        assertTrue("Silhouette must not fill the whole canvas", pixels.any { Color.alpha(it) == 0 })
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    fun installedAppIcon_exposesMonochromeLayer() {
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue("App icon must be adaptive", icon is AdaptiveIconDrawable)
        assertNotNull("Themed icon needs a monochrome layer", (icon as AdaptiveIconDrawable).monochrome)
    }

    @Test
    fun debugProcess_hasStrictModeInstalledByApplication() {
        assertTrue("This check only makes sense on debug builds", BuildConfig.DEBUG)
        assertNotEquals(StrictMode.VmPolicy.LAX.toString(), StrictMode.getVmPolicy().toString())
        var mainThreadPolicy = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            mainThreadPolicy = StrictMode.getThreadPolicy().toString()
        }
        assertNotEquals(StrictMode.ThreadPolicy.LAX.toString(), mainThreadPolicy)
    }
}
