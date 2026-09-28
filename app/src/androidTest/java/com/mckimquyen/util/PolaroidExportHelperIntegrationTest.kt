package com.mckimquyen.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
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
class PolaroidExportHelperIntegrationTest {

    private companion object {
        const val SIZE_PX = 300
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
    private lateinit var lensView: LensView

    @Before
    fun setUp() {
        val apps = ArrayList(
            listOf(App(id = 1, packageName = "com.polaroid.a", name = "A", label = "A", iconCacheKey = "polaroid-int#a"))
        )
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
        RAppsSingleton.instance.clearAllData()
        File(context.cacheDir, "polaroid").listFiles()?.forEach { it.delete() }
    }

    @Test
    fun exportAsync_producesAContentUriReadableAcrossTheFileProviderBoundary() {
        val latch = CountDownLatch(1)
        var uri: Uri? = null
        instrumentation.runOnMainSync {
            PolaroidExportHelper.exportAsync(lensView, "Integration", context) { result ->
                uri = result
                latch.countDown()
            }
        }
        assertTrue("export must complete within 5s", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("FileProvider must resolve a real content:// uri", uri)
        assertEquals("content", uri!!.scheme)

        context.contentResolver.openInputStream(uri!!)?.use { stream ->
            assertTrue("the shared file must contain real PNG bytes", stream.readBytes().isNotEmpty())
        } ?: throw AssertionError("contentResolver could not open the FileProvider uri - check file_paths.xml")
    }
}
