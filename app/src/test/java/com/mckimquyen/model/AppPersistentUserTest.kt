package com.mckimquyen.model

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** FEAT-012: Room keeps one row per (lens, package, component, user) without any schema change. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppPersistentUserTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: AppPersistentDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        dao = db.appPersistentDao()
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        field.set(null, db)
    }

    @After
    fun tearDown() {
        if (db.isOpen) db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun `defaults for the primary user keep the legacy identifier`() {
        assertEquals("com.chat-Main", AppPersistent.defaults("com.chat", "Main").identifier)
        assertEquals("com.chat-Main", AppPersistent.defaults("com.chat", "Main", userId = 0).identifier)
    }

    @Test
    fun `defaults for a work profile carry the user suffix but keep package and component clean`() {
        val row = AppPersistent.defaults("com.chat", "Main", userId = 10)

        assertEquals("com.chat-Main@u10", row.identifier)
        assertEquals("com.chat", row.packageName)
        assertEquals("Main", row.name)
    }

    @Test
    fun `the same app in two profiles stores two independent rows`() = runBlocking {
        dao.insert(AppPersistent.defaults("com.chat", "Main").copy(openCount = 2, isFavorite = true))
        dao.insert(AppPersistent.defaults("com.chat", "Main", userId = 10).copy(openCount = 9, folderName = "Work"))

        val personal = dao.findByIdentifier("default", "com.chat-Main")
        val work = dao.findByIdentifier("default", "com.chat-Main@u10")

        assertEquals(2L, personal?.openCount)
        assertEquals(true, personal?.isFavorite)
        assertEquals(9L, work?.openCount)
        assertEquals("Work", work?.folderName)
        assertEquals(2, dao.getAllForLens("default").size)
    }

    @Test
    fun `incrementing the work copy leaves the personal copy alone`() = runBlocking {
        val work = AppPersistent.defaults("com.chat", "Main", userId = 10).copy(openCount = 0)
        val personal = AppPersistent.defaults("com.chat", "Main").copy(openCount = 5)
        dao.insert(personal)

        dao.incrementAtomic(work)
        dao.incrementAtomic(work)

        assertEquals(2L, dao.getOpenCount("com.chat-Main@u10"))
        assertEquals(5L, dao.getOpenCount("com.chat-Main"))
    }

    @Test
    fun `an unsuffixed row written before work profiles existed still resolves as the primary user`() = runBlocking {
        dao.insert(AppPersistent(packageName = "com.old", name = "Old", identifier = "com.old-Old", openCount = 4))

        assertNotNull(dao.findByIdentifier("default", AppIdentity.identifier("com.old", "Old", 0)))
        assertNull(dao.findByIdentifier("default", AppIdentity.identifier("com.old", "Old", 10)))
    }

    @Test
    fun `rows of a removed profile are deleted and the others survive`() = runBlocking {
        dao.insert(AppPersistent.defaults("com.a", "A"))
        dao.insert(AppPersistent.defaults("com.w", "W", userId = 10))
        dao.insert(AppPersistent.defaults("com.x", "X", userId = 11))

        dao.deleteByIdentifiers(listOf("com.x-X@u11"))

        assertEquals(setOf("com.a-A", "com.w-W@u10"), dao.getAllRows().map { it.identifier }.toSet())
    }

    @Test
    fun `pruning removes only rows of profiles that no longer exist and reports how many`() = runBlocking {
        dao.insert(AppPersistent.defaults("com.a", "A"))
        dao.insert(AppPersistent.defaults("com.w", "W", userId = 10))
        dao.insert(AppPersistent.defaults("com.x", "X", userId = 11))
        dao.insert(AppPersistent.defaults("com.x", "X", userId = 11).copy(lensId = "work", identifier = "com.x-X@u11"))

        val removed = dao.pruneProfileRows(liveUserIds = listOf(0, 10))

        assertEquals(2, removed)
        assertEquals(setOf("com.a-A", "com.w-W@u10"), dao.getAllRows().map { it.identifier }.toSet())
    }

    @Test
    fun `pruning with only the primary user alive keeps every unsuffixed row even with odd names`() = runBlocking {
        dao.insert(AppPersistent.defaults("com.a", "A"))
        dao.insert(AppPersistent(packageName = "p", name = "Odd@name", identifier = "p-Odd@name"))
        dao.insert(AppPersistent.defaults("com.w", "W", userId = 10))

        val removed = dao.pruneProfileRows(liveUserIds = listOf(0))

        assertEquals(1, removed)
        assertEquals(setOf("com.a-A", "p-Odd@name"), dao.getAllRows().map { it.identifier }.toSet())
    }

    @Test
    fun `pruning never runs when the live profile list is empty so a failed lookup cannot wipe data`() = runBlocking {
        dao.insert(AppPersistent.defaults("com.w", "W", userId = 10))

        val removed = dao.pruneProfileRows(liveUserIds = emptyList())

        assertEquals(0, removed)
        assertEquals(1, dao.getAllRows().size)
    }
}
