package com.mckimquyen.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * FISH-004: proves the real OS integration, not just the pure decision table - toggling the
 * actual system-wide "Animator duration scale" (the standard Android reduced-motion signal)
 * via a shell command and confirming [LensPhysicsPolicy.shouldReduceLensMotion] picks it up
 * through the real [android.provider.Settings.Global] / [android.os.PowerManager] APIs.
 */
@RunWith(AndroidJUnit4::class)
class LensPhysicsPolicyIntegrationTest {

    private fun runShell(command: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)))
            .use { it.readText() }
    }

    @Test
    fun realDeviceReducedMotionSetting_isDetectedThroughSettingsGlobal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = runShell("settings get global animator_duration_scale").trim()
        try {
            runShell("settings put global animator_duration_scale 0")
            assertTrue(
                "expected reduced motion to be detected once animator_duration_scale=0",
                LensPhysicsPolicy.shouldReduceLensMotion(context)
            )

            runShell("settings put global animator_duration_scale 1")
            assertFalse(
                "expected reduced motion to be false once animator_duration_scale=1",
                LensPhysicsPolicy.shouldReduceLensMotion(context)
            )
        } finally {
            val restoreValue = if (original.isBlank() || original == "null") "1" else original
            runShell("settings put global animator_duration_scale $restoreValue")
        }
    }
}
