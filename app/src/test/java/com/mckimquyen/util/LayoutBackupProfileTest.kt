package com.mckimquyen.util

import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupParseResult
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FEAT-012: a work-profile row must never reach a backup file. A backup restores onto the same device
 * where the work profile's id may differ, and an entry without a user would collide with the personal copy
 * of the same app (rejecting the whole export as "duplicate app identity").
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LayoutBackupProfileTest {

    private fun row(pkg: String, name: String, userId: Int = 0) =
        AppPersistent.defaults(pkg, name, userId = userId).copy(orderNumber = 1, isFavorite = true)

    @Test
    fun `a work profile row produces no backup entry`() {
        assertNull(LayoutBackupEntry.fromPersistent(row("com.chat", "Main", userId = 10)))
    }

    @Test
    fun `a primary user row still produces its entry`() {
        val entry = LayoutBackupEntry.fromPersistent(row("com.chat", "Main"))

        assertEquals("com.chat", entry?.packageName)
        assertEquals("Main", entry?.name)
    }

    @Test
    fun `exporting the same app from both profiles keeps only the personal copy and does not fail`() {
        val lenses = listOf(LensWorkspace.createDefault())
        val snapshot = LayoutBackupSnapshotFactory.create(
            lenses,
            listOf(row("com.chat", "Main"), row("com.chat", "Main", userId = 10)),
            listOf(LensAppSettingsBackup(LensWorkspace.DEFAULT_LENS_ID, LensAppScope.ALL, emptySet(), false)),
            exportedAt = 1L
        )

        val entries = snapshot.lenses.single().entries
        assertEquals(1, entries.size)
        assertEquals("com.chat", entries.single().packageName)
    }

    @Test
    fun `the exported file round trips and never contains a user suffix`() {
        val json = LayoutBackupSnapshotFactory.create(
            listOf(LensWorkspace.createDefault()),
            listOf(row("com.a", "A"), row("com.w", "W", userId = 10)),
            listOf(LensAppSettingsBackup(LensWorkspace.DEFAULT_LENS_ID, LensAppScope.ALL, emptySet(), false)),
            exportedAt = 1L
        ).toJson()

        assertTrue("work apps must not be in the file", !json.contains("com.w"))
        assertTrue(!json.contains("@u"))
        assertTrue(LayoutBackup.parse(json) is LayoutBackupParseResult.Success)
    }
}
