package com.mckimquyen.model

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppPersistentRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AppPersistentDao

    @Before
    fun createDb() {
        val context = RuntimeEnvironment.getApplication()
        // Initialize an in-memory Room database for testing
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.appPersistentDao()

        // Inject in-memory database to AppDatabase static INSTANCE for static helpers testing
        val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        runCatching { AppDatabase.getInstance().close() }
        resetAppDatabaseInstance()
    }

    @Test
    fun testRoomInsertAndFind() {
        // Given
        val app = AppPersistent(
            packageName = "com.example.test",
            name = "TestActivity",
            identifier = "com.example.test-TestActivity",
            openCount = 5,
            appVisible = true,
            appOpened = false
        )

        // When
        val id = dao.insert(app)
        val retrieved = dao.findByIdentifier(app.identifier)

        // Then
        assertNotNull(retrieved)
        assertEquals(id, retrieved?.id)
        assertEquals("com.example.test", retrieved?.packageName)
        assertEquals("TestActivity", retrieved?.name)
        assertEquals(5L, retrieved?.openCount)
        assertFalse(retrieved?.appOpened ?: true)
    }

    @Test
    fun testStaticHelpers() {
        // Test incrementAppCount helper
        AppPersistent.incrementAppCount("com.example.test", "MainActivity")
        assertEquals(1L, AppPersistent.getAppOpenCount("com.example.test", "MainActivity"))

        AppPersistent.incrementAppCount("com.example.test", "MainActivity")
        assertEquals(2L, AppPersistent.getAppOpenCount("com.example.test", "MainActivity"))

        // Test setAppVisibility helper
        assertTrue(AppPersistent.getAppVisibility("com.example.test", "MainActivity"))
        AppPersistent.setAppVisibility("com.example.test", "MainActivity", false)
        assertFalse(AppPersistent.getAppVisibility("com.example.test", "MainActivity"))

        // Test setAppOpened helper
        assertTrue(AppPersistent.getAppOpened("com.example.test", "MainActivity"))
        AppPersistent.setAppOpened("com.example.test", "MainActivity", false)
        assertFalse(AppPersistent.getAppOpened("com.example.test", "MainActivity"))

        // Test Palette Color caching helper
        assertEquals(0, AppPersistent.getAppPaletteColor("com.example.test", "MainActivity"))
        AppPersistent.setAppPaletteColor("com.example.test", "MainActivity", 0xFF00FF00.toInt())
        assertEquals(0xFF00FF00.toInt(), AppPersistent.getAppPaletteColor("com.example.test", "MainActivity"))
    }

    @Test
    fun testStateOnlyRecordsDefaultToZeroOpenCount() {
        AppPersistent.setAppPaletteColor("com.example.new", "MainActivity", 0xFFAA5500.toInt())
        assertEquals(0L, AppPersistent.getAppOpenCount("com.example.new", "MainActivity"))

        AppPersistent.incrementAppCount("com.example.new", "MainActivity")
        assertEquals(1L, AppPersistent.getAppOpenCount("com.example.new", "MainActivity"))
    }

    @Test
    fun testSugarVersion7DatabaseMigratesToRoomVersion8() {
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
                    ID,
                    M_PACKAGE_NAME,
                    M_NAME,
                    M_IDENTIFIER,
                    M_OPEN_COUNT,
                    M_ORDER_NUMBER,
                    M_APP_VISIBLE,
                    M_APP_OPENED
                ) VALUES (1, 'com.example.old', 'OldActivity', 'com.example.old-OldActivity', 7, 3, 0, 1)
                """.trimIndent()
            )
            sqlite.version = 7
        }

        AppDatabase.init(context)
        val migrated = AppDatabase.getInstance().appPersistentDao()
            .findByIdentifier("com.example.old-OldActivity")

        assertNotNull(migrated)
        assertEquals("com.example.old", migrated?.packageName)
        assertEquals("OldActivity", migrated?.name)
        assertEquals(7L, migrated?.openCount)
        assertEquals(3, migrated?.orderNumber)
        assertFalse(migrated?.appVisible ?: true)
        assertTrue(migrated?.appOpened ?: false)
        assertEquals(0, migrated?.paletteColor)
    }

    private fun resetAppDatabaseInstance() {
        val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, null)
    }
}
