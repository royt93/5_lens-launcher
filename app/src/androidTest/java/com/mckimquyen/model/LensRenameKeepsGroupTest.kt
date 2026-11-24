package com.mckimquyen.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Audit S3: renameLens writes `lens.copy(name = ...)`, a whole-row @Update of the object the caller was holding. ActHome keeps its lens list from onCreate and does not
 * reload it after the lens-management screen, so a rename started from the home screen carries the OLD groupName/orderIndex and silently undoes a group assigned
 * (or a reorder done) in management in between. The test plays that order of events against the real DAO.
 */
@RunWith(AndroidJUnit4::class)
class LensRenameKeepsGroupTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()

    @Before fun setup() {
        AppDatabase.init(context)
        runBlocking { dao.replaceWorkspace(listOf(LensWorkspace.createDefault(), LensWorkspace("second", "Second", 1)), emptyList()) }
    }

    @After fun tearDown() {
        runBlocking { dao.replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList()) }
    }

    private fun rename(lens: LensWorkspace, name: String): List<LensWorkspace> {
        val latch = CountDownLatch(1)
        var result: List<LensWorkspace> = emptyList()
        LensWorkspace.renameLens(lens, name) { lenses -> result = lenses; latch.countDown() }
        assert(latch.await(10, TimeUnit.SECONDS)) { "rename must finish" }
        return result
    }

    @Test fun renamingWithAStaleObjectKeepsTheGroupAssignedMeanwhile() {
        // The home screen read the lens when the group was still empty and keeps this object.
        val stale = runBlocking { dao.findById("second") }!!
        assertNull("positive control: the held object has no group", stale.groupName)
        // Lens management assigns the group while the home screen still holds `stale`.
        runBlocking { dao.assignGroup(setOf("second"), "Work") }
        assertEquals("positive control: the group really is stored", "Work", runBlocking { dao.findById("second") }!!.groupName)

        val after = rename(stale, "Renamed")

        val stored = after.first { it.id == "second" }
        assertEquals("the name changes", "Renamed", stored.name)
        assertEquals("the group assigned meanwhile must survive the rename", "Work", stored.groupName)
    }

    @Test fun renamingKeepsTheOrderChosenMeanwhile() {
        val stale = runBlocking { dao.findById("second") }!!
        runBlocking { dao.update(stale.copy(orderIndex = 5)) }

        val after = rename(stale, "Renamed")

        assertEquals(5, after.first { it.id == "second" }.orderIndex)
    }

    @Test fun aPlainRenameStillChangesOnlyTheName() {
        // Positive control for the two tests above: with nothing changed in between, the rename works and touches nothing else.
        val current = runBlocking { dao.findById("second") }!!

        val after = rename(current, "Just a name")

        val stored = after.first { it.id == "second" }
        assertEquals("Just a name", stored.name)
        assertEquals(current.orderIndex, stored.orderIndex)
        assertEquals(current.groupName, stored.groupName)
    }
}
