package com.mckimquyen.adt

import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * UI-006: pure unit tests for the lock-icon-state logic extracted out of [AppAdapter] view
 * binding, so this doesn't need a device/Robolectric to verify. Convention:
 * isAppOpened == true means unlocked (tapping the control LOCKS the app);
 * isAppOpened == false means locked (tapping the control UNLOCKS the app).
 */
class AppAdapterLockStateTest {

    @Test
    fun `unlocked state shows the open lock icon`() {
        assertEquals(R.drawable.ic_lock_open_24dp, AppAdapter.lockIconResFor(true))
    }

    @Test
    fun `locked state shows the closed lock icon`() {
        assertEquals(R.drawable.ic_lock_24dp, AppAdapter.lockIconResFor(false))
    }

    @Test
    fun `the two states never share the same icon`() {
        assertNotEquals(AppAdapter.lockIconResFor(true), AppAdapter.lockIconResFor(false))
    }

    @Test
    fun `unlocked state describes the lock action available`() {
        assertEquals(R.string.lock, AppAdapter.lockContentDescriptionResFor(true))
    }

    @Test
    fun `locked state describes the unlock action available`() {
        assertEquals(R.string.unlock, AppAdapter.lockContentDescriptionResFor(false))
    }

    @Test
    fun `the two states never share the same content description`() {
        assertNotEquals(
            AppAdapter.lockContentDescriptionResFor(true),
            AppAdapter.lockContentDescriptionResFor(false)
        )
    }
}
