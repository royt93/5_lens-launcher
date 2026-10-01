package com.mckimquyen.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.views.LensGestureState
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PolaroidExportHelperWidgetTest {

    private companion object {
        const val SIZE_PX = 400
        const val APP_COUNT = 12
        const val ICON_PX = 48
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(Color.RED) }
    private lateinit var lensView: LensView

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.polaroid.app$i", name = "App$i", label = "App $i", iconCacheKey = "polaroid#$i")
        })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, iconBitmap) }
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
            }
        }
    }

    @After
    fun tearDown() {
        com.mckimquyen.util.BitmapCache.clear()
        File(context.cacheDir, "polaroid").listFiles()?.forEach { it.delete() }
    }

    private fun exportAndAwait(lensName: String): android.net.Uri? {
        val latch = CountDownLatch(1)
        var result: android.net.Uri? = null
        instrumentation.runOnMainSync {
            PolaroidExportHelper.exportAsync(lensView, lensName, context) { uri ->
                result = uri
                latch.countDown()
            }
        }
        assertTrue("export must complete within 5s", latch.await(5, TimeUnit.SECONDS))
        return result
    }

    @Test
    fun exportAsync_producesAFramedPngLargerThanTheRawContent() {
        val uri = exportAndAwait("Work")
        assertNotNull("export must succeed for a laid-out view", uri)

        val file = File(File(context.cacheDir, "polaroid"), "Work.png")
        assertTrue("exported PNG must exist on disk at the expected path", file.exists())

        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(decoded)
        assertTrue("framed image must be wider than raw content (border added)", decoded!!.width > SIZE_PX)
        assertTrue("framed image must be taller than raw content (border + caption)", decoded.height > SIZE_PX)
    }

    // ==================================================================== B4 (test-audit)

    @Test
    fun exportAsync_onAnUnlaidOutView_deliversNullSynchronouslyAndWritesNoFile() {
        lateinit var unlaidView: LensView
        instrumentation.runOnMainSync {
            unlaidView = LensView(context) // never layout()'d - width/height stay 0
        }

        var called = false
        var result: android.net.Uri? = null
        instrumentation.runOnMainSync {
            PolaroidExportHelper.exportAsync(unlaidView, "NeverLaidOut", context) { uri ->
                called = true
                result = uri
            }
            // Must already be true here, inside the same runOnMainSync block that made the call -
            // proving the width<=0 branch returns before ever touching Dispatchers.IO, not via a
            // posted/async callback.
            assertTrue("the unlaid-out branch must call onDone synchronously on the calling thread", called)
        }
        assertEquals(null, result)
        assertTrue(
            "no file must be written for a view that was never laid out",
            !File(File(context.cacheDir, "polaroid"), "NeverLaidOut.png").exists()
        )
    }

    // ==================================================================== B6 (test-audit)

    @Test
    fun exportAsync_rendersAWhiteBorderAndVisibleCaptionText() {
        val uri = exportAndAwait("PixelCheck")
        assertNotNull(uri)
        val file = File(File(context.cacheDir, "polaroid"), "PixelCheck.png")
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(decoded)

        // Reuse the real (public, pure) layout math instead of hardcoding the border/caption
        // pixel offsets here - BORDER_PX/CAPTION_* stay private implementation details.
        val layout = PolaroidExportHelper.calculatePolaroidLayout(SIZE_PX, SIZE_PX)
        val borderPixel = decoded!!.getPixel(layout.contentLeft / 2, layout.contentTop / 2)
        assertEquals("the polaroid frame border must be plain white", Color.WHITE, borderPixel)

        val captionRowY = layout.captionLine1BaselineY - 10
        val captionRowHasInk = (0 until decoded.width).any { x -> decoded.getPixel(x, captionRowY) != Color.WHITE }
        assertTrue("the caption row must contain non-white pixels (rendered text)", captionRowHasInk)
    }

    @Test
    fun exportAsync_resetsLiveTouchAndGestureStateBeforeCapture() {
        instrumentation.runOnMainSync {
            lensView.setLensStateForTest(50f, 50f, 1f, reduceMotion = false)
            lensView.gestureState = LensGestureState.PINCHING
        }

        exportAndAwait("Pinching")

        instrumentation.runOnMainSync {
            assertEquals(
                "resetToIdleForExport() must run before draw() so a live pinch never leaks into the snapshot",
                LensGestureState.IDLE,
                lensView.gestureState
            )
        }
    }
}
