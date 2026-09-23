package com.mckimquyen.search

import android.app.NotificationManager
import androidx.preference.PreferenceManager
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SEARCH-007: resolveDnd's branch logic (keyword match, granted-vs-denied, on/off state) is pure
 * given a known NotificationManager state, which Robolectric's ShadowNotificationManager can
 * simulate - only the real system grant/deny UI flow itself needs a real device (see the
 * androidTest-side DndQuickActionIntegrationTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class QuickActionEngineDndTest {

    private fun context() = RuntimeEnvironment.getApplication()

    private fun freshPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context()).edit().clear().commit()
    }

    private fun notificationManager(): NotificationManager =
        context().getSystemService(NotificationManager::class.java)

    @Test
    fun `non-matching query returns null`() {
        freshPrefs()
        assertNull(QuickActionEngine.resolveDnd(context(), "facebook"))
    }

    @Test
    fun `access not granted, never asked before, returns first-time request`() {
        freshPrefs()
        shadowOf(notificationManager()).setNotificationPolicyAccessGranted(false)

        val result = QuickActionEngine.resolveDnd(context(), "dnd")

        assertEquals(QuickAction.DndAccessRequest("dnd", previouslyRequested = false), result)
    }

    @Test
    fun `access not granted, previously requested, returns denied-state request`() {
        freshPrefs()
        shadowOf(notificationManager()).setNotificationPolicyAccessGranted(false)
        UtilSettings(context()).save(UtilSettings.KEY_DND_PERMISSION_REQUESTED, true)

        val result = QuickActionEngine.resolveDnd(context(), "focus")

        assertEquals(QuickAction.DndAccessRequest("focus", previouslyRequested = true), result)
    }

    @Test
    fun `access granted, dnd currently off, returns toggle in off state`() {
        freshPrefs()
        shadowOf(notificationManager()).setNotificationPolicyAccessGranted(true)
        notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

        val result = QuickActionEngine.resolveDnd(context(), "dnd")

        assertEquals(QuickAction.DndToggle("dnd", isOn = false), result)
    }

    @Test
    fun `access granted, dnd currently on, returns toggle in on state`() {
        freshPrefs()
        shadowOf(notificationManager()).setNotificationPolicyAccessGranted(true)
        notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)

        val result = QuickActionEngine.resolveDnd(context(), "dnd")

        assertEquals(QuickAction.DndToggle("dnd", isOn = true), result)
    }

    @Test
    fun `vietnamese keyword without diacritics matches`() {
        freshPrefs()
        shadowOf(notificationManager()).setNotificationPolicyAccessGranted(false)

        val result = QuickActionEngine.resolveDnd(context(), "Không Làm Phiền")

        assertEquals(QuickAction.DndAccessRequest("Không Làm Phiền", previouslyRequested = false), result)
    }

    @Test
    fun `disabled via settings toggle is gated at the resolve() level`() {
        freshPrefs()
        UtilSettings(context()).save(UtilSettings.KEY_QUICK_ACTION_DND, false)
        shadowOf(notificationManager()).setNotificationPolicyAccessGranted(false)

        assertNull(QuickActionEngine.resolve(context(), "dnd"))
    }
}
