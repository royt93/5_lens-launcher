package com.mckimquyen.model

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensWorkspaceDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LensWorkspaceDao
    private lateinit var apps: AppPersistentDao

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
        apps = db.appPersistentDao()
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

    @Test
    fun `replaceWorkspace restores lenses and rows while preserving non-layout data`() = runBlocking {
        dao.insertOrUpdate(LensWorkspace.createDefault())
        dao.insertOrUpdate(LensWorkspace("old-work", "Old work", 1, 11L))
        apps.insert(AppPersistent(packageName="com.keep", name="com.keep.Main", identifier="com.keep-com.keep.Main", openCount=9, paletteColor=123, notificationCount=4, orderNumber=8, lensId="default"))
        apps.insert(AppPersistent(packageName="com.keep", name="com.keep.Main", identifier="com.keep-com.keep.Main", openCount=9, paletteColor=123, notificationCount=4, orderNumber=3, lensId="old-work"))
        apps.insert(AppPersistent(packageName="com.stale", name="com.stale.Main", identifier="com.stale-com.stale.Main", openCount=5, paletteColor=77, notificationCount=2, isFavorite=true, lensId="old-work"))

        val backupLenses = listOf(
            LensWorkspace("default", "Home", 0, 101L),
            LensWorkspace("work", "Work", 1, 202L)
        )
        val backupRows = listOf(
            AppPersistent(packageName="com.keep", name="com.keep.Main", identifier="com.keep-com.keep.Main", orderNumber=2, appVisible=false, appOpened=false, isFavorite=true, folderName="Saved", pinnedZone="START", lensId="default"),
            AppPersistent(packageName="com.keep", name="com.keep.Main", identifier="com.keep-com.keep.Main", orderNumber=0, appVisible=true, isFavorite=false, lensId="work"),
            AppPersistent(packageName="com.gone", name="com.gone.Main", identifier="com.gone-com.gone.Main", orderNumber=1, lensId="work")
        )

        dao.replaceWorkspace(backupLenses, backupRows)

        assertEquals(listOf("default", "work"), dao.getAll().map { it.id })
        assertEquals(listOf("Home", "Work"), dao.getAll().map { it.name })
        val defaultRow = apps.findByIdentifier("default", "com.keep-com.keep.Main") ?: throw AssertionError("missing default row")
        val workRows = apps.getAllForLens("work").associateBy { it.identifier }
        val workKeep = workRows["com.keep-com.keep.Main"] ?: throw AssertionError("missing work row")
        assertEquals(2, defaultRow.orderNumber)
        assertEquals(false, defaultRow.appVisible)
        assertEquals(true, defaultRow.isFavorite)
        assertEquals("Saved", defaultRow.folderName)
        assertEquals(9L, defaultRow.openCount)
        assertEquals(123, defaultRow.paletteColor)
        assertEquals(4, defaultRow.notificationCount)
        assertEquals(0, workKeep.orderNumber)
        assertEquals(9L, workKeep.openCount)
        assertEquals(123, workKeep.paletteColor)
        assertEquals(4, workKeep.notificationCount)
        assertEquals(setOf("com.keep-com.keep.Main", "com.gone-com.gone.Main"), workRows.keys)
        assertNull(apps.findByIdentifier("old-work", "com.keep-com.keep.Main"))
        assertNull(apps.findByIdentifier("old-work", "com.stale-com.stale.Main"))
        val retainedStats = apps.findByIdentifier("default", "com.stale-com.stale.Main") ?: throw AssertionError("missing preserved non-layout data")
        assertEquals(5L, retainedStats.openCount)
        assertEquals(77, retainedStats.paletteColor)
        assertEquals(2, retainedStats.notificationCount)
        assertEquals(-1, retainedStats.orderNumber)
        assertEquals(false, retainedStats.appVisible)
        assertEquals(false, retainedStats.isFavorite)
        assertNull(retainedStats.folderName)
    }

    @Test
    fun `replaceWorkspace transaction rolls back when an insert fails`() = runBlocking {
        dao.insertOrUpdate(LensWorkspace.createDefault())
        apps.insert(AppPersistent(packageName="com.keep", name="com.keep.Main", identifier="com.keep-com.keep.Main", orderNumber=5, lensId="default"))
        val originalLens = dao.getAll()
        val originalRows = apps.getAllForLens("default")
        try {
            dao.replaceWorkspace(
                listOf(LensWorkspace("default", "Replacement", 0)),
                listOf(
                    AppPersistent(packageName="com.a", name="com.a.Main", identifier="duplicate", lensId="default"),
                    AppPersistent(packageName="com.b", name="com.b.Main", identifier="duplicate", lensId="default")
                )
            )
            throw AssertionError("Expected unique-key insert to fail")
        } catch (_: Exception) {
            assertEquals(originalLens, dao.getAll())
            assertEquals(originalRows, apps.getAllForLens("default"))
            return@runBlocking
        }
        fail("Expected unique-key insert to fail")
    }
}
