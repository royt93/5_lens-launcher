package com.mckimquyen.model

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.app.RAppsSingleton
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-008 Phase 1: Real-device migration proof against an actual v10 SQLite database.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationIntegrationTest {

    companion object {
        private const val TEST_DB = "fish-008-migration-test.db"
        private const val TEST_WRITE_TIMEOUT_MILLIS = 5_000L
        private const val TEST_WRITE_POLL_MILLIS = 10L
    }

    @Test
    fun migrate10To11_preservesLayoutAndCreatesDefaultLens() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dbFile = context.getDatabasePath(TEST_DB)
            dbFile.parentFile?.mkdirs()
            dbFile.delete()

            // Create an exact v10 SQLite database on real device filesystem
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
                        PINNED_ZONE TEXT NOT NULL DEFAULT 'NONE'
                    )
                    """.trimIndent()
                )
                sqlite.execSQL(
                    "CREATE UNIQUE INDEX index_APP_PERSISTENT_IDENTIFIER ON APP_PERSISTENT (IDENTIFIER)"
                )
                sqlite.execSQL(
                    """
                    INSERT INTO APP_PERSISTENT (
                        PACKAGE_NAME, NAME, IDENTIFIER, OPEN_COUNT, ORDER_NUMBER,
                        APP_VISIBLE, APP_OPENED, PALETTE_COLOR, IS_FAVORITE, FOLDER_NAME, PINNED_ZONE
                    ) VALUES (
                        'com.real.device', 'Main', 'com.real.device-Main',
                        99, 7, 0, 1, 123, 1, 'Work', 'END'
                    )
                    """.trimIndent()
                )
                sqlite.version = 10
            }

            // Open with Room and apply MIGRATION_10_11
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
                .addMigrations(migration10To11())
                .build()
            try {
                val workspaces = migrated.lensWorkspaceDao().getAll()
                assertEquals(listOf(LensWorkspace.DEFAULT_LENS_ID), workspaces.map { it.id })
                assertEquals(LensWorkspace.DEFAULT_LENS_NAME, workspaces.single().name)

                val rows = migrated.appPersistentDao().getAllForLens(LensWorkspace.DEFAULT_LENS_ID)
                assertEquals(1, rows.size)
                val row = rows.single()
                assertEquals(99L, row.openCount)
                assertEquals(7, row.orderNumber)
                assertFalse(row.appVisible)
                assertTrue(row.isFavorite)
                assertEquals("Work", row.folderName)
                assertEquals(PinnedZone.END.name, row.pinnedZone)
                assertEquals(LensWorkspace.DEFAULT_LENS_ID, row.lensId)
            } finally {
                migrated.close()
                context.deleteDatabase(TEST_DB)
            }
        }
    }

    @Test
    fun multiLensRoundTrip_keepsLayoutsIsolatedOnDevice() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dbFile = context.getDatabasePath(TEST_DB)
            dbFile.parentFile?.mkdirs()
            context.deleteDatabase(TEST_DB)

            val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
            try {
                db.lensWorkspaceDao().insertOrUpdate(LensWorkspace.createDefault())
                db.lensWorkspaceDao().insertOrUpdate(
                    LensWorkspace(id = "work", name = "Work", orderIndex = 1)
                )
                val defaultRow = AppPersistent(
                    packageName = "com.example",
                    name = "Main",
                    identifier = "com.example-Main",
                    isFavorite = true,
                    folderName = "Social",
                    pinnedZone = PinnedZone.START.name,
                    lensId = LensWorkspace.DEFAULT_LENS_ID
                )
                db.appPersistentDao().insert(defaultRow)
                db.appPersistentDao().duplicateLensLayout(LensWorkspace.DEFAULT_LENS_ID, "work")
                db.appPersistentDao().updateOrganization(
                    "work", defaultRow.identifier, false, "WorkSpace", PinnedZone.END.name
                )
                db.appPersistentDao().updateOrder("work", defaultRow.identifier, 9)
                db.appPersistentDao().updateVisibility("work", defaultRow.identifier, false)
                db.appPersistentDao().updateOpened("work", defaultRow.identifier, false)

                val defaultLayout = db.appPersistentDao()
                    .findByIdentifier(LensWorkspace.DEFAULT_LENS_ID, defaultRow.identifier)
                val workLayout = db.appPersistentDao().findByIdentifier("work", defaultRow.identifier)

                assertTrue(defaultLayout?.isFavorite == true)
                assertEquals("Social", defaultLayout?.folderName)
                assertEquals(PinnedZone.START.name, defaultLayout?.pinnedZone)
                assertEquals(-1, defaultLayout?.orderNumber)
                assertTrue(defaultLayout?.appVisible == true)
                assertTrue(defaultLayout?.appOpened == true)
                assertFalse(workLayout?.isFavorite == true)
                assertEquals("WorkSpace", workLayout?.folderName)
                assertEquals(PinnedZone.END.name, workLayout?.pinnedZone)
                assertEquals(9, workLayout?.orderNumber)
                assertFalse(workLayout?.appVisible == true)
                assertFalse(workLayout?.appOpened == true)
            } finally {
                db.close()
                context.deleteDatabase(TEST_DB)
            }
        }
    }

    @Test
    fun concurrentLensOrderWrites_doNotCancelEachOther() {
        runBlocking {
            val dao = AppDatabase.getInstance().appPersistentDao()
            val packageName = "com.example.concurrent"
            val componentName = "Main"
            val identifier = AppPersistent.generateIdentifier(packageName, componentName)
            val app = App(label = "Concurrent", packageName = packageName, name = componentName)
            RAppsSingleton.instance.apps = arrayListOf(app)
            try {
                AppPersistent.setAppOrderBatch(listOf(app), LensWorkspace.DEFAULT_LENS_ID)
                AppPersistent.setAppOrderBatch(listOf(app), "work")

                withTimeout(TEST_WRITE_TIMEOUT_MILLIS) {
                    while (
                        dao.findByIdentifier(LensWorkspace.DEFAULT_LENS_ID, identifier)?.orderNumber != 0 ||
                        dao.findByIdentifier("work", identifier)?.orderNumber != 0
                    ) {
                        delay(TEST_WRITE_POLL_MILLIS)
                    }
                }
            } finally {
                dao.findByIdentifier(LensWorkspace.DEFAULT_LENS_ID, identifier)?.let { dao.delete(it) }
                dao.findByIdentifier("work", identifier)?.let { dao.delete(it) }
                RAppsSingleton.instance.clearAllData()
            }
        }
    }

    private fun migration10To11(): androidx.room.migration.Migration {
        return AppDatabase.MIGRATION_10_11
    }
}
