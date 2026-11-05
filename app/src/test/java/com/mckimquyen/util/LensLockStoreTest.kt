package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensLockStoreTest {
    private lateinit var context: Context
    private lateinit var store: LensLockStore

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = LensLockStore(context)
    }

    @Test fun defaultsToUnlockedAndIsolatesLensKeys() {
        assertFalse(store.isLocked("default"))
        store.saveLocked("default", true)
        store.saveLocked("work", false)
        assertTrue(store.isLocked("default"))
        assertFalse(store.isLocked("work"))
        assertEquals(setOf("default"), store.lockedLensIds(setOf("default", "work")))
    }

    @Test fun filtersUnknownIdsAndTreatsWrongTypeAsUnlocked() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("lens_locked_default", "true").putBoolean("lens_locked_work", true).commit()
        assertFalse(store.isLocked("default"))
        assertEquals(setOf("work"), store.lockedLensIds(setOf("default", "work")))
        assertEquals(emptySet<String>(), store.lockedLensIds(setOf("default")))
    }

    @Test fun deleteRemovesOnlyRequestedLens() {
        store.saveLocked("default", true)
        store.saveLocked("work", true)
        store.delete("default")
        assertFalse(store.isLocked("default"))
        assertTrue(store.isLocked("work"))
    }

    @Test fun deletingLensSettingsAlsoClearsOnlyThatLensLock() {
        val settings = UtilSettings(context)
        store.saveLocked("work", true)
        store.saveLocked("private", true)
        settings.deleteLensSettings("work")
        assertFalse(store.isLocked("work"))
        assertTrue(store.isLocked("private"))
    }

    @Test fun duplicateAndWorkspaceImportDoNotCopyOrChangeLockFlags() {
        val settings = UtilSettings(context)
        store.saveLocked("default", true)
        settings.duplicateLensSettings("default", "copy", 1)
        assertFalse(store.isLocked("copy"))

        assertTrue(settings.snapshotWorkspacePreferences().values.keys
            .none { it.startsWith("lens_locked_") })
        settings.replaceWorkspacePreferences(
            listOf(LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false)),
            "default"
        )
        assertTrue(store.isLocked("default"))
        assertTrue(PreferenceManager.getDefaultSharedPreferences(context).all.keys
            .none { it.startsWith("lens_locked_") })
    }

    private companion object {
        const val PREFS_NAME = "lens_access_prefs"
    }
}
