package com.mckimquyen.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.util.BitmapCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-002 integration proof: exercises the *real*, already-registered ComponentCallbacks2 on
 * the live RApplication instance (via Application#onTrimMemory's built-in dispatch to
 * registered callbacks - not a reimplementation), proving the tiered response actually wired
 * up in production: mild pressure trims a fraction, only severe/background pressure clears
 * everything. Before this story, any level >= RUNNING_CRITICAL (including the very common
 * UI_HIDDEN, fired every time the app is merely backgrounded) cleared the cache completely.
 */
@RunWith(AndroidJUnit4::class)
class RApplicationMemoryTrimIntegrationTest {

    private val application: Application
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Before
    fun seedCache() {
        BitmapCache.clear()
        repeat(20) { i ->
            BitmapCache.put("trim-test-$i", Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888))
        }
        assertTrue("fixture must actually populate the cache", BitmapCache.sizeKb() > 0)
    }

    @Test
    fun runningLow_trimsPartially_doesNotClearEverything() {
        val before = BitmapCache.sizeKb()

        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertTrue(
            "RUNNING_LOW must trim to roughly 75%, not clear",
            BitmapCache.sizeKb() in 1..((before * 0.75f).toInt() + 1)
        )
    }

    @Test
    fun runningCritical_trimsHarderButStillNotToZero() {
        val before = BitmapCache.sizeKb()

        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        assertTrue(
            "RUNNING_CRITICAL must trim to roughly 50%, not clear (still potentially visible)",
            BitmapCache.sizeKb() in 1..((before * 0.5f).toInt() + 1)
        )
    }

    @Test
    fun uiHidden_trimsHarderButDoesNotClear() {
        // Regression guard: UI_HIDDEN (20) is numerically >= RUNNING_CRITICAL (15) and fires on
        // every ordinary backgrounding (e.g. pressing Home) - it must not be treated as the
        // same severity as a genuine deep-background reclaim.
        val before = BitmapCache.sizeKb()

        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        assertTrue(
            "UI_HIDDEN must not clear the cache outright",
            BitmapCache.sizeKb() > 0
        )
        assertTrue(BitmapCache.sizeKb() <= before)
    }

    @Test
    fun background_clearsEverything() {
        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

        assertEquals(0, BitmapCache.sizeKb())
    }

    @Test
    fun onLowMemory_clearsEverything() {
        application.onLowMemory()

        assertEquals(0, BitmapCache.sizeKb())
    }

    @Test
    fun runningModerate_belowAnyTier_leavesCacheUntouched() {
        val before = BitmapCache.sizeKb()

        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        assertEquals("below the lowest configured tier, nothing should be evicted", before, BitmapCache.sizeKb())
    }
}
