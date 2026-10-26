package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** DB-009: rotating on-device workspace snapshots. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LocalWorkspaceBackupsTest {

    @get:Rule val temp = TemporaryFolder()

    private var now = START
    private val dir get() = File(temp.root, "layout_backups")
    private fun backups(max: Int = LocalWorkspaceBackups.MAX_BACKUPS) = LocalWorkspaceBackups(dir, { now }, max)

    private fun json(lensName: String): String = LayoutBackup(
        LayoutBackup.CURRENT_SCHEMA_VERSION,
        now,
        lenses = listOf(
            LayoutBackupLens(
                LensWorkspace.DEFAULT_LENS_ID, lensName, 0, LensAppScope.ALL, emptySet(), false,
                listOf(LayoutBackupEntry("com.a", "com.a.Main", 0, true, true, false, null, PinnedZone.NONE.name))
            )
        )
    ).toJson()

    private fun LocalWorkspaceBackups.lensNames() = list().map { requireNotNull(read(it)).lenses.single().name }

    @Test
    fun `a saved snapshot is the latest and parses back`() {
        val store = backups()
        assertNotNull(store.save(json("first")))

        assertEquals("first", store.latest()?.lenses?.single()?.name)
    }

    @Test
    fun `nothing saved means no latest and a missing directory is not an error`() {
        assertNull(backups().latest())
        assertTrue(backups().list().isEmpty())
    }

    @Test
    fun `only the newest max backups are kept, newest first`() {
        val store = backups(max = 3)
        listOf("a", "b", "c", "d", "e").forEach {
            now += 1_000
            store.save(json(it))
        }

        assertEquals(listOf("e", "d", "c"), store.lensNames())
    }

    @Test
    fun `saves in the same millisecond never overwrite each other and keep their order`() {
        val store = backups(max = 5)
        val first = store.save(json("first"))
        val second = store.save(json("second"))

        assertNotEquals(first, second)
        assertEquals(listOf("second", "first"), store.lensNames())
    }

    @Test
    fun `a clock that moves backwards still produces a strictly newer snapshot`() {
        val store = backups(max = 5)
        store.save(json("before"))
        now -= 10 * DAY
        store.save(json("after-clock-jump"))

        assertEquals("after-clock-jump", store.latest()?.lenses?.single()?.name)
    }

    @Test
    fun `latest skips a corrupt newest file and returns the next valid one`() {
        val store = backups(max = 5)
        now += 1_000
        store.save(json("good"))
        now += 1_000
        val bad = requireNotNull(store.save(json("will be corrupted")))
        bad.writeText("{ not json")

        assertEquals("good", store.latest()?.lenses?.single()?.name)
    }

    @Test
    fun `an interrupted write leaves no visible snapshot and is cleaned up on the next save`() {
        val store = backups()
        dir.mkdirs()
        val leftover = File(dir, "backup-0001700000000000.json.tmp").apply { writeText("partial") }

        assertTrue("a half-written file must never be listed", store.list().isEmpty())
        assertNull(store.latest())

        store.save(json("ok"))
        assertTrue("stale temp file must be removed", !leftover.exists())
        assertEquals(1, store.list().size)
    }

    @Test
    fun `rotation never touches files it does not own`() {
        val store = backups(max = 1)
        dir.mkdirs()
        val foreign = File(dir, "notes.txt").apply { writeText("keep me") }

        repeat(3) { now += 1_000; store.save(json("x$it")) }

        assertTrue(foreign.exists())
        assertEquals("keep me", foreign.readText())
        assertEquals(1, store.list().size)
    }

    @Test
    fun `a failing save returns null instead of throwing`() {
        val blocker = File(temp.root, "layout_backups").apply { writeText("i am a file, not a directory") }

        assertNull(LocalWorkspaceBackups(blocker, { now }).save(json("x")))
    }

    @Test
    fun `names sort chronologically across clock magnitudes`() {
        val store = backups(max = 5)
        now = 999_999_999_999L
        store.save(json("old-epoch"))
        now = 1_700_000_000_000L
        store.save(json("new-epoch"))

        assertEquals(listOf("new-epoch", "old-epoch"), store.lensNames())
    }

    @Test
    fun `the default keeps a small bounded number of snapshots`() {
        assertTrue(LocalWorkspaceBackups.MAX_BACKUPS in 2..10)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val DAY = 86_400_000L
    }
}
