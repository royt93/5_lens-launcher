package com.mckimquyen.model

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * UI-024: Room schema migration v11 -> v12 - adds NOTIFICATION_COUNT, global across lenses,
 * defaulting existing rows to 0 losslessly. Mirrors AppDatabaseMigration10To11Test's shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppDatabaseMigration11To12Test {

    private lateinit var db: AppDatabase
    private lateinit var appDao: AppPersistentDao

    @Before
    fun setup() {
        resetAppDatabaseInstance()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        resetAppDatabaseInstance()
    }

    private fun setDatabaseInstance(database: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    private fun resetAppDatabaseInstance() = setDatabaseInstance(null)

    @Test
    fun `v11 database migrates losslessly to v12 with notification count defaulted to zero`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("app_persistent.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // Create an exact v11 SQLite database with pre-existing user data (no NOTIFICATION_COUNT).
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
                    PALETTE_COLOR INTEGER NOT NULL DEFAULT 0,
                    IS_FAVORITE INTEGER NOT NULL DEFAULT 0,
                    FOLDER_NAME TEXT,
                    PINNED_ZONE TEXT NOT NULL DEFAULT 'NONE',
                    LENS_ID TEXT NOT NULL DEFAULT 'default'
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                "CREATE UNIQUE INDEX index_APP_PERSISTENT_LENS_ID_IDENTIFIER " +
                    "ON APP_PERSISTENT (LENS_ID, IDENTIFIER)"
            )
            // Full v11 schema also includes LENS_WORKSPACE (added by MIGRATION_10_11) - this
            // synthetic seed starts directly at v11, so it must already be present, matching
            // that migration's own CREATE TABLE exactly.
            sqlite.execSQL(
                """
                CREATE TABLE LENS_WORKSPACE (
                    ID TEXT PRIMARY KEY NOT NULL,
                    NAME TEXT NOT NULL,
                    ORDER_INDEX INTEGER NOT NULL DEFAULT 0,
                    CREATED_AT INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                "INSERT INTO LENS_WORKSPACE (ID, NAME, ORDER_INDEX, CREATED_AT) " +
                    "VALUES ('default', 'Lens 1', 0, 0)"
            )
            sqlite.execSQL(
                """
                INSERT INTO APP_PERSISTENT (
                    PACKAGE_NAME, NAME, IDENTIFIER, OPEN_COUNT, ORDER_NUMBER,
                    APP_VISIBLE, APP_OPENED, PALETTE_COLOR, IS_FAVORITE, FOLDER_NAME, PINNED_ZONE, LENS_ID
                ) VALUES (
                    'com.example.app1', 'Activity1', 'com.example.app1-Activity1',
                    42, 0, 1, 1, 12345, 1, 'Productivity', 'START', 'default'
                )
                """.trimIndent()
            )
            sqlite.version = 11
        }

        AppDatabase.init(context)
        db = AppDatabase.getInstance()
        appDao = db.appPersistentDao()

        val migrated = appDao.findByIdentifier("com.example.app1-Activity1")
        assertNotNull(migrated)
        assertEquals(0, migrated?.notificationCount)
        assertEquals(42L, migrated?.openCount)
        assertEquals("Productivity", migrated?.folderName)
    }
}
