package com.mckimquyen.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Root cause of the flaky icon-based widget tests: RApplication.sDisableAutoAppRefresh was a static field initialised from
 * isTestEnvironment() when the CLASS loaded, before the instrumentation registered itself, so it was false under every
 * instrumented run. The unsolicited PackageManager scan then committed the REAL app list a moment after a test seeded
 * fake icons, and BitmapCache.retainKeys() evicted them. The flag must be true whenever an instrumentation is running.
 */
@RunWith(AndroidJUnit4::class)
class RApplicationAutoRefreshUnderTestTest {
    private val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    @Test fun theAutoRefreshSwitchIsOnWhileAnInstrumentationIsRunning() {
        assertTrue("an instrumentation is registered", InstrumentationRegistry.getInstrumentation() != null)
        assertTrue("the background scan must be disabled under instrumentation", RApplication.sDisableAutoAppRefresh)
    }

    @Test fun aSeededFakeIconSurvivesTheTimeTheStartupScanWouldHaveCommitted() {
        val fake = App(id = 0, packageName = "com.fake.survivor", name = "Fake", label = "Fake", iconCacheKey = "fake#survivor")
        RAppsSingleton.instance.setAppIcon(fake.iconCacheKey, icon)
        // the scan is debounced and takes a few hundred ms; wait well past it
        Thread.sleep(SCAN_WINDOW_MS)
        assertNotNull("the seeded icon is still cached", RAppsSingleton.instance.getAppIcon(fake.iconCacheKey))
        com.mckimquyen.util.BitmapCache.clear()
    }

    private companion object { const val SCAN_WINDOW_MS = 3_000L }
}
