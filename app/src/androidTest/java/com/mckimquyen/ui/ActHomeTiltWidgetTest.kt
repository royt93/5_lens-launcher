package com.mckimquyen.ui

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.util.TiltSensorSource
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-033: ActHome sở hữu đúng một đăng ký sensor, theo vòng đời. Dùng SensorManager giả để đếm
 * register/unregister mà không phụ thuộc vào việc máy có sensor thật hay đang nghiêng.
 * Không dùng Room; prefs do DeviceStateGuard khôi phục.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeTiltWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var settings: UtilSettings
    private lateinit var fake: CountingSource

    @Before fun setup() {
        settings = UtilSettings(context)
        settings.sortType // Khởi tạo pref mặc định trước khi bật công tắc.
        fake = CountingSource(hasSensor = true)
        ActHome.tiltSensorSourceFactoryForTest = { fake }
    }

    @After fun cleanup() {
        ActHome.tiltSensorSourceFactoryForTest = null
    }

    private fun launch(): ActivityScenario<ActHome> = ActivityScenario.launch(ActHome::class.java)

    private fun settle() {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(SETTLE_MS)
    }

    private fun runningCount(): Int = fake.registered - fake.unregistered

    @Test fun offByDefaultNeverRegistersAnything() {
        launch().use {
            settle()
            assertEquals(0, fake.registered)
        }
    }

    @Test fun enablingRegistersExactlyOnceAndDestroyingReleasesIt() {
        settings.saveTiltParallax(true)
        launch().use { scenario ->
            settle()
            assertEquals("one listener while resumed", 1, runningCount())
            scenario.moveToState(Lifecycle.State.DESTROYED)
            settle()
        }
        assertEquals("no listener survives the Activity", 0, runningCount())
    }

    @Test fun pauseUnregistersAndResumeRegistersAgain() {
        settings.saveTiltParallax(true)
        launch().use { scenario ->
            settle()
            assertEquals(1, runningCount())
            scenario.moveToState(Lifecycle.State.STARTED)
            settle()
            assertEquals("paused Activity must not hold the sensor", 0, runningCount())
            scenario.moveToState(Lifecycle.State.RESUMED)
            settle()
            assertEquals(1, runningCount())
        }
    }

    @Test fun recreateNeverLeavesAnExtraListener() {
        settings.saveTiltParallax(true)
        launch().use { scenario ->
            settle()
            repeat(RECREATES) {
                scenario.recreate()
                settle()
                assertTrue("at most one listener after recreate, was ${runningCount()}", runningCount() <= 1)
            }
            assertEquals(1, runningCount())
        }
    }

    @Test fun turningTheSettingOffStopsItWithoutRestartingTheActivity() {
        settings.saveTiltParallax(true)
        launch().use { scenario ->
            settle()
            assertEquals(1, runningCount())
            settings.saveTiltParallax(false)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            settle()
            assertEquals("a disabled setting registers nothing on the next resume", 0, runningCount())
        }
    }

    @Test fun listModeNeverRegisters() {
        settings.saveTiltParallax(true)
        settings.setLauncherMode(LauncherMode.LIST)
        try {
            launch().use {
                settle()
                assertEquals(0, fake.registered)
            }
        } finally {
            settings.setLauncherMode(LauncherMode.FISHEYE)
        }
    }

    @Test fun reducedMotionNeverRegisters() {
        settings.saveTiltParallax(true)
        val original = runShell("settings get global animator_duration_scale").trim()
        runShell("settings put global animator_duration_scale 0")
        try {
            launch().use {
                settle()
                assertEquals("reduced motion must keep the sensor off", 0, fake.registered)
            }
        } finally {
            runShell("settings put global animator_duration_scale ${if (original.isBlank() || original == "null") "1" else original}")
        }
    }

    @Test fun openingSearchStopsTheSensorAndClosingItRestartsIt() {
        settings.saveTiltParallax(true)
        launch().use { scenario ->
            settle()
            assertEquals(1, runningCount())
            scenario.onActivity { it.findViewById<com.google.android.material.search.SearchView>(com.mckimquyen.R.id.searchView).show() }
            waitUntil { runningCount() == 0 }
            scenario.onActivity { it.findViewById<com.google.android.material.search.SearchView>(com.mckimquyen.R.id.searchView).hide() }
            waitUntil { runningCount() == 1 }
            assertFalse("it registered at least once", fake.registered == 0)
        }
    }

    @Test fun aDeviceWithoutTheSensorIsAQuietNoOp() {
        val absent = CountingSource(hasSensor = false)
        ActHome.tiltSensorSourceFactoryForTest = { absent }
        settings.saveTiltParallax(true)
        launch().use { scenario ->
            settle()
            assertEquals("no sensor means nothing is ever registered", 0, absent.registered)
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
        assertEquals(0, absent.registered)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!condition() && System.currentTimeMillis() < deadline) SystemClock.sleep(POLL_MS)
        assertTrue("điều kiện chưa đạt sau $WAIT_MS ms (đang chạy: ${runningCount()})", condition())
    }

    private fun runShell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return java.io.BufferedReader(java.io.InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)))
            .use { it.readText() }
    }

    /** Nguồn sensor đếm lời gọi, không đăng ký sensor thật và không phát mẫu. */
    class CountingSource(private val hasSensor: Boolean) : TiltSensorSource(null) {
        @Volatile var registered = 0
        @Volatile var unregistered = 0
        @Volatile private var running = false
        override val available: Boolean get() = hasSensor
        override val isRunning: Boolean get() = running
        override fun start(target: Listener): Boolean {
            if (!hasSensor) return false
            if (!running) { running = true; registered++ }
            return true
        }
        override fun stop() {
            if (running) { running = false; unregistered++ }
        }
    }

    private companion object {
        const val SETTLE_MS = 600L
        const val WAIT_MS = 8_000L
        const val POLL_MS = 50L
        const val RECREATES = 3
    }
}
