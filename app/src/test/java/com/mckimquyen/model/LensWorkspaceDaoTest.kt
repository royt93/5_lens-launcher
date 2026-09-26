package com.mckimquyen.model

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * FISH-008 Phase 2: the LensWorkspace CRUD surface now has real production callers
 * (ActHome's add/rename/delete flows), so its contract is pinned here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensWorkspaceDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LensWorkspaceDao

    private fun setDatabaseInstance(database: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    @Before
    fun setup() {
        setDatabaseInstance(null)
        val context = RuntimeEnvironment.getApplication()
        context.getDatabasePath("app_persistent.db").delete()
        AppDatabase.init(context)
        db = AppDatabase.getInstance()
        dao = db.lensWorkspaceDao()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        setDatabaseInstance(null)
    }

    @Test
    fun `insertIfAbsent seeds the default lens once and is a no-op afterwards`() = runBlocking {
        dao.insertIfAbsent(LensWorkspace.createDefault())
        dao.insertIfAbsent(LensWorkspace(LensWorkspace.DEFAULT_LENS_ID, "Renamed by mistake"))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(LensWorkspace.DEFAULT_LENS_NAME, all.single().name)
    }

    @Test
    fun `getAll orders by orderIndex then createdAt`() = runBlocking {
        dao.insertOrUpdate(LensWorkspace(id = "c", name = "Third", orderIndex = 2, createdAt = 1L))
        dao.insertOrUpdate(LensWorkspace(id = "a", name = "First", orderIndex = 0, createdAt = 5L))
        dao.insertOrUpdate(LensWorkspace(id = "b", name = "Second", orderIndex = 1, createdAt = 3L))

        assertEquals(listOf("a", "b", "c"), dao.getAll().map { it.id })
    }

    @Test
    fun `rename updates only the targeted lens`() = runBlocking {
        val work = LensWorkspace(id = "work", name = "Work", orderIndex = 1)
        dao.insertOrUpdate(LensWorkspace.createDefault())
        dao.insertOrUpdate(work)

        dao.update(work.copy(name = "Focus"))

        assertEquals("Focus", dao.findById("work")?.name)
        assertEquals(LensWorkspace.DEFAULT_LENS_NAME, dao.findById(LensWorkspace.DEFAULT_LENS_ID)?.name)
    }

    @Test
    fun `deleteById removes only that lens`() = runBlocking {
        dao.insertOrUpdate(LensWorkspace.createDefault())
        dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))

        assertEquals(1, dao.deleteById("work"))

        assertNull(dao.findById("work"))
        assertEquals(listOf(LensWorkspace.DEFAULT_LENS_ID), dao.getAll().map { it.id })
    }
}
