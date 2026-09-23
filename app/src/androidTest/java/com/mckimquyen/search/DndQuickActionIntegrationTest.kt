package com.mckimquyen.search

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.preference.PreferenceManager
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * SEARCH-007 real-device proof: DND special access (unlike CAMERA/ACCESS_FINE_LOCATION) is not a
 * runtime permission - GrantPermissionRule can't touch it. This grants/revokes the real OS special
 * access the same way LensPhysicsPolicyIntegrationTest toggles a real Settings.Global value, via a
 * shell command, then proves resolveDnd() and the actual NotificationManager.setInterruptionFilter
 * side effect both reflect real system state - not a mock.
 */
@RunWith(AndroidJUnit4::class)
class DndQuickActionIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageName get() = context.packageName
    private fun notificationManager() = context.getSystemService(NotificationManager::class.java)

    private fun runShell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)))
            .use { it.readText() }
    }

    private fun allowDnd() = runShell("cmd notification allow_dnd $packageName")
    private fun disallowDnd() = runShell("cmd notification disallow_dnd $packageName")

    @Before
    fun clearPrefsAndRevoke() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        disallowDnd()
    }

    @After
    fun restoreRevoked() {
        disallowDnd()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun accessNotGranted_resolvesToAccessRequest_onRealDevice() {
        assertTrue(
            "test precondition: access must actually be revoked on this device for this assertion to mean anything",
            !notificationManager().isNotificationPolicyAccessGranted
        )

        val result = QuickActionEngine.resolveDnd(context, "dnd")

        assertEquals(QuickAction.DndAccessRequest("dnd", previouslyRequested = false), result)
    }

    @Test
    fun grantingRealAccess_resolvesToToggle_reflectingRealInterruptionFilter() {
        allowDnd()
        try {
            assertTrue(
                "shell grant must actually take effect for this test to prove anything real",
                notificationManager().isNotificationPolicyAccessGranted
            )

            notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            assertEquals(
                QuickAction.DndToggle("dnd", isOn = false),
                QuickActionEngine.resolveDnd(context, "dnd")
            )

            notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            assertEquals(
                QuickAction.DndToggle("dnd", isOn = true),
                QuickActionEngine.resolveDnd(context, "dnd")
            )
        } finally {
            notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    @Test
    fun toggling_actuallyChangesTheRealSystemInterruptionFilter() {
        allowDnd()
        try {
            notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

            // Same side effect ActHome.performDndToggle() performs on tap.
            notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            assertEquals(
                "real system DND state must actually flip, not just this app's cached view of it",
                NotificationManager.INTERRUPTION_FILTER_NONE,
                notificationManager().currentInterruptionFilter
            )

            notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            assertEquals(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                notificationManager().currentInterruptionFilter
            )
        } finally {
            notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}
