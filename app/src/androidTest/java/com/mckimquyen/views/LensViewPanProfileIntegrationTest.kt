package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.LruCache
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.util.BitmapCache
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Cửa sổ hardware thật, fixture 300 icon cố định; CANCEL tránh mở app/tăng open count. */
@RunWith(AndroidJUnit4::class)
class LensViewPanProfileIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test
    fun profileRealWindowPanWithCancel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cacheField = BitmapCache::class.java.getDeclaredField("cache").apply { isAccessible = true }
        val cache = cacheField.get(BitmapCache) as LruCache<*, *>
        val keys = cache.snapshot().keys.filterIsInstance<String>().toSet()
        val icon = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        RAppsSingleton.instance.setAppIcon(ICON_KEY, icon)
        try {
            ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
                var lens: LensView? = null
                scenario.onActivity { activity ->
                    lens = LensView(activity).apply {
                        setApps(ArrayList((0 until APP_COUNT).map { App(id = it, label = "Profile $it", iconCacheKey = ICON_KEY) }))
                    }
                    activity.setContentView(lens)
                }
                val deadline = SystemClock.uptimeMillis() + LAYOUT_TIMEOUT_MS
                var ready = false
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { ready = requireNotNull(lens).width > 0 && requireNotNull(lens).isHardwareAccelerated }
                    if (!ready) SystemClock.sleep(FRAME_MS)
                }
                assertTrue("lens phải có kích thước và hardware acceleration", ready)
                // Hide animation sau CANCEL vẫn gọi launchApp; vô hiệu launch cho fixture.
                scenario.onActivity { requireNotNull(lens).setPackageManager(null) }
                fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(command)
                ).bufferedReader().use { it.readText() }
                shell("dumpsys gfxinfo ${instrumentation.targetContext.packageName} reset")
                repeat(GESTURE_COUNT) {
                    val downTime = SystemClock.uptimeMillis()
                    fun send(action: Int, progress: Float) {
                        scenario.onActivity {
                            val view = requireNotNull(lens)
                            val x = view.width * CENTER_FRACTION
                            val y = view.height * (START_FRACTION - progress * DRAG_FRACTION)
                            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
                        }
                    }
                    try {
                        send(MotionEvent.ACTION_DOWN, 0f)
                        repeat(MOVE_COUNT) { step ->
                            send(MotionEvent.ACTION_MOVE, (step + 1).toFloat() / MOVE_COUNT)
                            SystemClock.sleep(FRAME_MS)
                        }
                    } finally {
                        send(MotionEvent.ACTION_CANCEL, 1f)
                    }
                    SystemClock.sleep(SETTLE_MS)
                }
                File(instrumentation.targetContext.cacheDir, "perf005-gfxinfo.txt").writeText(
                    shell("dumpsys gfxinfo ${instrumentation.targetContext.packageName} framestats")
                )
            }
        } finally {
            BitmapCache.retainKeys(keys)
            icon.recycle()
        }
    }

    private companion object {
        const val APP_COUNT = 300
        const val ICON_SIZE = 32
        const val ICON_KEY = "perf005-profile-icon"
        const val LAYOUT_TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val SETTLE_MS = 400L
        const val GESTURE_COUNT = 5
        const val MOVE_COUNT = 40
        const val CENTER_FRACTION = 0.5f
        const val START_FRACTION = 0.75f
        const val DRAG_FRACTION = 0.3f
    }
}
