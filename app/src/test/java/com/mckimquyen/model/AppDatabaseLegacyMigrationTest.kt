package com.mckimquyen.model

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * DB-008: databases older than Room v8 (SugarORM era) must open instead of crashing, and the
 * v7->v8 / v8->v9 steps that previously had no coverage keep user data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppDatabaseLegacyMigrationTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() = setDatabaseInstance(null)

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        setDatabaseInstance(null)
    }

    private fun setDatabaseInstance(database: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    private fun freshDbFile(): java.io.File {
        val file = RuntimeEnvironment.getApplication().getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        file.delete()
        return file
    }

    private fun createSugarTable(sqlite: SQLiteDatabase) = sqlite.execSQL(
        """
        CREATE TABLE APP_PERSISTENT (
            ID INTEGER PRIMARY KEY AUTOINCREMENT,
            M_PACKAGE_NAME TEXT, M_NAME TEXT, M_IDENTIFIER TEXT,
            M_OPEN_COUNT INTEGER, M_ORDER_NUMBER INTEGER, M_APP_VISIBLE INTEGER, M_APP_OPENED INTEGER
        )
        """.trimIndent()
    )

    private fun createRoomV7Table(sqlite: SQLiteDatabase) = sqlite.execSQL(
        """
        CREATE TABLE APP_PERSISTENT (
            ID INTEGER PRIMARY KEY AUTOINCREMENT,
            PACKAGE_NAME TEXT, NAME TEXT, IDENTIFIER TEXT NOT NULL,
            OPEN_COUNT INTEGER NOT NULL, ORDER_NUMBER INTEGER NOT NULL,
            APP_VISIBLE INTEGER NOT NULL, APP_OPENED INTEGER NOT NULL
        )
        """.trimIndent()
    )

    private fun openMigrated(): AppDatabase {
        AppDatabase.init(RuntimeEnvironment.getApplication())
        db = AppDatabase.getInstance()
        return db
    }

    @Test
    fun `sugar database at a legacy version opens and keeps its rows`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            createSugarTable(sqlite)
            sqlite.execSQL(
                "INSERT INTO APP_PERSISTENT (M_PACKAGE_NAME, M_NAME, M_IDENTIFIER, M_OPEN_COUNT, " +
                    "M_ORDER_NUMBER, M_APP_VISIBLE, M_APP_OPENED) VALUES ('com.a', 'A', 'com.a-A', 7, 3, 0, 1)"
            )
            sqlite.version = LEGACY_VERSION
        }

        val rows = openMigrated().appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID)

        assertEquals(1, rows.size)
        assertEquals("com.a-A", rows.first().identifier)
        assertEquals(7L, rows.first().openCount)
        assertEquals(3, rows.first().orderNumber)
        assertEquals(false, rows.first().appVisible)
    }

    @Test
    fun `every legacy version below 7 opens instead of throwing`() {
        (FIRST_LEGACY_VERSION until FIRST_ROOM_VERSION).forEach { version ->
            tearDown()
            SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
                createSugarTable(sqlite)
                sqlite.version = version
            }
            val opened = openMigrated()
            assertTrue("legacy version $version must open", opened.openHelper.writableDatabase.isOpen)
        }
    }

    @Test
    fun `legacy database without the table opens with an empty table`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { it.version = LEGACY_VERSION }

        val opened = openMigrated()

        assertTrue(opened.appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID).isEmpty())
    }

    @Test
    fun `legacy database with an unrecognised table shape opens instead of crashing`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE APP_PERSISTENT (ID INTEGER PRIMARY KEY, SOMETHING_ELSE TEXT)")
            sqlite.version = LEGACY_VERSION
        }

        val opened = openMigrated()

        assertTrue(opened.appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID).isEmpty())
    }

    @Test
    fun `v7 sugar table migrates with null columns coalesced`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            createSugarTable(sqlite)
            sqlite.execSQL("INSERT INTO APP_PERSISTENT (M_PACKAGE_NAME, M_NAME, M_IDENTIFIER) VALUES ('com.b', 'B', 'com.b-B')")
            sqlite.version = FIRST_ROOM_VERSION
        }

        val row = openMigrated().appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID).single()

        assertEquals(0L, row.openCount)
        assertEquals(-1, row.orderNumber)
        assertEquals(true, row.appVisible)
        assertEquals(true, row.appOpened)
    }

    @Test
    fun `v7 room table without palette column gains it and keeps rows`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            createRoomV7Table(sqlite)
            sqlite.execSQL(
                "INSERT INTO APP_PERSISTENT (PACKAGE_NAME, NAME, IDENTIFIER, OPEN_COUNT, ORDER_NUMBER, APP_VISIBLE, APP_OPENED) " +
                    "VALUES ('com.c', 'C', 'com.c-C', 9, 1, 1, 1)"
            )
            sqlite.version = FIRST_ROOM_VERSION
        }

        val row = openMigrated().appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID).single()

        assertEquals(9L, row.openCount)
        assertEquals(0, row.paletteColor)
    }

    @Test
    fun `v8 duplicate identifiers keep only the newest row and leave other rows alone`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            createRoomV7Table(sqlite)
            sqlite.execSQL("ALTER TABLE APP_PERSISTENT ADD COLUMN PALETTE_COLOR INTEGER NOT NULL DEFAULT 0")
            insertV8(sqlite, "com.d-D", openCount = 1)
            insertV8(sqlite, "com.d-D", openCount = 2)
            insertV8(sqlite, "com.e-E", openCount = 5)
            sqlite.version = FIRST_ROOM_VERSION + 1
        }

        val rows = openMigrated().appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID)
            .associateBy { it.identifier }

        assertEquals(setOf("com.d-D", "com.e-E"), rows.keys)
        assertEquals("newest duplicate wins", 2L, rows.getValue("com.d-D").openCount)
        assertEquals("unrelated row untouched", 5L, rows.getValue("com.e-E").openCount)
    }

    @Test
    fun `v8 rows with empty identifier but known package and name keep their data under a rebuilt identifier`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            createRoomV7Table(sqlite)
            sqlite.execSQL("ALTER TABLE APP_PERSISTENT ADD COLUMN PALETTE_COLOR INTEGER NOT NULL DEFAULT 0")
            insertV8(sqlite, "", openCount = 4, packageName = "com.g", name = "G1")
            insertV8(sqlite, "", openCount = 6, packageName = "com.h", name = "H1")
            insertV8(sqlite, "com.f-F", openCount = 8)
            sqlite.version = FIRST_ROOM_VERSION + 1
        }

        val rows = openMigrated().appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID)
            .associateBy { it.identifier }

        assertEquals(setOf("com.g-G1", "com.h-H1", "com.f-F"), rows.keys)
        assertEquals(4L, rows.getValue("com.g-G1").openCount)
        assertEquals(6L, rows.getValue("com.h-H1").openCount)
        assertEquals(8L, rows.getValue("com.f-F").openCount)
    }

    @Test
    fun `v8 rows with empty identifier and no package or name collapse to one and never touch real apps`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            createRoomV7Table(sqlite)
            sqlite.execSQL("ALTER TABLE APP_PERSISTENT ADD COLUMN PALETTE_COLOR INTEGER NOT NULL DEFAULT 0")
            insertV8(sqlite, "", openCount = 1, packageName = null, name = null)
            insertV8(sqlite, "", openCount = 2, packageName = null, name = null)
            insertV8(sqlite, "com.f-F", openCount = 8)
            sqlite.version = FIRST_ROOM_VERSION + 1
        }

        val rows = openMigrated().appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID)

        assertEquals("unrecoverable empty identifiers collapse to a single row", 1, rows.count { it.identifier.isEmpty() })
        assertEquals(8L, rows.single { it.identifier == "com.f-F" }.openCount)
    }

    @Test
    fun `sugar table from an older release missing some columns still migrates`() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            sqlite.execSQL(
                "CREATE TABLE APP_PERSISTENT (ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "M_PACKAGE_NAME TEXT, M_NAME TEXT, M_IDENTIFIER TEXT, M_OPEN_COUNT INTEGER)"
            )
            sqlite.execSQL("INSERT INTO APP_PERSISTENT (M_PACKAGE_NAME, M_NAME, M_IDENTIFIER, M_OPEN_COUNT) VALUES ('com.i', 'I', 'com.i-I', 11)")
            sqlite.version = LEGACY_VERSION
        }

        val row = openMigrated().appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID).single()

        assertEquals("com.i-I", row.identifier)
        assertEquals(11L, row.openCount)
        assertEquals(true, row.appVisible)
    }

    @Test
    fun `unrecognised legacy table is preserved as a backup table instead of being dropped`() {
        SQLiteDatabase.openOrCreateDatabase(freshDbFile(), null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE APP_PERSISTENT (ID INTEGER PRIMARY KEY, SOMETHING_ELSE TEXT)")
            sqlite.execSQL("INSERT INTO APP_PERSISTENT (SOMETHING_ELSE) VALUES ('keep me')")
            sqlite.version = LEGACY_VERSION
        }

        val sqlite = openMigrated().openHelper.writableDatabase
        val kept = sqlite.query("SELECT SOMETHING_ELSE FROM APP_PERSISTENT_UNRECOGNISED_BACKUP").use {
            it.moveToFirst() && it.getString(0) == "keep me"
        }

        assertTrue("unreadable legacy rows must be kept for manual recovery", kept)
    }

    private fun insertV8(
        sqlite: SQLiteDatabase,
        identifier: String,
        openCount: Long,
        packageName: String? = "p",
        name: String? = "n"
    ) = sqlite.execSQL(
        "INSERT INTO APP_PERSISTENT (PACKAGE_NAME, NAME, IDENTIFIER, OPEN_COUNT, ORDER_NUMBER, APP_VISIBLE, APP_OPENED, PALETTE_COLOR) " +
            "VALUES (?, ?, ?, ?, 0, 1, 1, 0)",
        arrayOf(packageName, name, identifier, openCount)
    )

    private companion object {
        const val DB_NAME = "app_persistent.db"
        const val FIRST_LEGACY_VERSION = 1
        const val FIRST_ROOM_VERSION = 7
        const val LEGACY_VERSION = 3
    }
}
