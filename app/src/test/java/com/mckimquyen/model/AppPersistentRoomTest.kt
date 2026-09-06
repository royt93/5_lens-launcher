package com.mckimquyen.model

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppPersistentRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: AppPersistentDao

    @Before
    fun createDb() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.appPersistentDao()
        setDatabaseInstance(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        if (::db.isInitialized && db.isOpen) db.close()
        resetAppDatabaseInstance()
    }

    @Test
    fun `room insert and lookup use stable component identity`() = runBlocking {
        val app = persistent("com.example.test", "TestActivity", openCount = 5)
        val id = dao.insert(app)
        val retrieved = dao.findByIdentifier(app.identifier)

        assertNotNull(retrieved)
        assertEquals(id, retrieved?.id)
        assertEquals(5L, retrieved?.openCount)
    }

    @Test
    fun `concurrent increments are atomic`() = runBlocking {
        val defaults = persistent("com.example.atomic", "MainActivity", openCount = 0)

        (1..100).map { async { dao.incrementAtomic(defaults) } }.awaitAll()

        assertEquals(100L, dao.getOpenCount(defaults.identifier))
    }

    @Test
    fun `targeted upserts preserve independent state`() = runBlocking {
        val defaults = persistent("com.example.state", "MainActivity", openCount = 0)
        dao.setVisibility(defaults, false)
        dao.setOpened(defaults, false)
        dao.setPaletteColor(defaults, 0xFF00FF00.toInt())
        dao.setOrganization(defaults, true, "Work", PinnedZone.START.name)
        dao.incrementAtomic(defaults)

        val stored = dao.findByIdentifier(defaults.identifier)!!
        assertFalse(stored.appVisible)
        assertFalse(stored.appOpened)
        assertEquals(0xFF00FF00.toInt(), stored.paletteColor)
        assertEquals(1L, stored.openCount)
        assertTrue(stored.isFavorite)
        assertEquals("Work", stored.folderName)
        assertEquals(PinnedZone.START.name, stored.pinnedZone)
    }

    @Test
    fun `batch reorder commits every position without replacing app state`() = runBlocking {
        val first = persistent("com.example.first", "MainActivity", openCount = 4).copy(
            isFavorite = true,
            folderName = "Work",
            orderNumber = 1
        )
        val second = persistent("com.example.second", "MainActivity", openCount = 7).copy(
            orderNumber = 0
        )
        dao.insert(first)
        dao.insert(second)

        dao.setOrders(listOf(first.copy(orderNumber = 0), second.copy(orderNumber = 1)))

        val storedFirst = dao.findByIdentifier(first.identifier)!!
        val storedSecond = dao.findByIdentifier(second.identifier)!!
        assertEquals(0, storedFirst.orderNumber)
        assertEquals(1, storedSecond.orderNumber)
        assertEquals(4L, storedFirst.openCount)
        assertTrue(storedFirst.isFavorite)
        assertEquals("Work", storedFirst.folderName)
        assertEquals(7L, storedSecond.openCount)
    }

    @Test
    fun `component metadata survives uninstall and reinstall rediscovery`() = runBlocking {
        val defaults = persistent("com.example.returning", "MainActivity", openCount = 0)
        dao.setOrganization(defaults, true, "Games", PinnedZone.END.name)

        // Package discovery is ephemeral; its stable component row remains while absent.
        dao.insertIfAbsent(defaults)

        val restored = dao.findByIdentifier(defaults.identifier)!!
        assertTrue(restored.isFavorite)
        assertEquals("Games", restored.folderName)
        assertEquals(PinnedZone.END.name, restored.pinnedZone)
    }

    @Test
    fun `version 8 duplicate component rows migrate by keeping newest state`() = runBlocking {
        db.close()
        resetAppDatabaseInstance()
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("app_persistent.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE APP_PERSISTENT (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    PACKAGE_NAME TEXT,
                    NAME TEXT,
                    IDENTIFIER TEXT NOT NULL,
                    OPEN_COUNT INTEGER NOT NULL,
                    ORDER_NUMBER INTEGER NOT NULL,
                    APP_VISIBLE INTEGER NOT NULL,
                    APP_OPENED INTEGER NOT NULL,
                    PALETTE_COLOR INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                "INSERT INTO APP_PERSISTENT VALUES " +
                    "(1, 'com.example.dupe', 'MainActivity', 'dupe', 2, 8, 1, 1, 0)"
            )
            sqlite.execSQL(
                "INSERT INTO APP_PERSISTENT VALUES " +
                    "(2, 'com.example.dupe', 'MainActivity', 'dupe', 9, 3, 0, 1, 123)"
            )
            sqlite.version = 8
        }

        AppDatabase.init(context)
        db = AppDatabase.getInstance()
        dao = db.appPersistentDao()

        val rows = dao.getAll().filter { it.identifier == "dupe" }
        assertEquals(1, rows.size)
        assertEquals(2L, rows.single().id)
        assertEquals(9L, rows.single().openCount)
        assertFalse(rows.single().appVisible)
        assertEquals(123, rows.single().paletteColor)
        assertFalse(rows.single().isFavorite)
        assertEquals(PinnedZone.NONE.name, rows.single().pinnedZone)
        assertEquals(-1L, dao.insertIfAbsent(persistent("com.other", "Other", 0).copy(identifier = "dupe")))
    }

    @Test
    fun `Sugar version 7 migrates through version 10`() = runBlocking {
        db.close()
        resetAppDatabaseInstance()
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("app_persistent.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE APP_PERSISTENT (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    M_PACKAGE_NAME TEXT,
                    M_NAME TEXT,
                    M_IDENTIFIER TEXT,
                    M_OPEN_COUNT INTEGER,
                    M_ORDER_NUMBER INTEGER,
                    M_APP_VISIBLE INTEGER,
                    M_APP_OPENED INTEGER
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO APP_PERSISTENT (
                    ID, M_PACKAGE_NAME, M_NAME, M_IDENTIFIER, M_OPEN_COUNT,
                    M_ORDER_NUMBER, M_APP_VISIBLE, M_APP_OPENED
                ) VALUES (1, 'com.example.old', 'OldActivity',
                    'com.example.old-OldActivity', 7, 3, 0, 1)
                """.trimIndent()
            )
            sqlite.version = 7
        }

        AppDatabase.init(context)
        db = AppDatabase.getInstance()
        dao = db.appPersistentDao()
        val migrated = dao.findByIdentifier("com.example.old-OldActivity")

        assertNotNull(migrated)
        assertEquals(7L, migrated?.openCount)
        assertEquals(3, migrated?.orderNumber)
        assertFalse(migrated?.appVisible ?: true)
        assertTrue(migrated?.appOpened ?: false)
        assertEquals(0, migrated?.paletteColor)
        assertFalse(migrated?.isFavorite ?: true)
        assertEquals(null, migrated?.folderName)
        assertEquals(PinnedZone.NONE.name, migrated?.pinnedZone)
    }

    private fun persistent(packageName: String, name: String, openCount: Long) = AppPersistent(
        packageName = packageName,
        name = name,
        identifier = AppPersistent.generateIdentifier(packageName, name),
        openCount = openCount
    )

    private fun setDatabaseInstance(database: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    private fun resetAppDatabaseInstance() = setDatabaseInstance(null)
}
