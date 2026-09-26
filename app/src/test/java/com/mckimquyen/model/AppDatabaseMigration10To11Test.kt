package com.mckimquyen.model

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * FISH-008 Phase 1: Unit tests for Room schema migration v10 -> v11 and Multi-Lens data isolation.
 * Verifies:
 * 1. Lossless migration from v10 to v11: all existing app persistent records are preserved under "Lens 1" (default).
 * 2. LENS_WORKSPACE table is created with default "Lens 1" workspace.
 * 3. Multi-lens layout isolation: modifying layout/order in one workspace does not alter another.
 * 4. Open count synchronization across workspaces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppDatabaseMigration10To11Test {

    private lateinit var db: AppDatabase
    private lateinit var appDao: AppPersistentDao
    private lateinit var workspaceDao: LensWorkspaceDao

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
    fun `v10 database migrates losslessly to v11 with default workspace`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("app_persistent.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // Create an exact v10 SQLite database with pre-existing user data
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
            // Insert sample pre-existing apps
            sqlite.execSQL(
                """
                INSERT INTO APP_PERSISTENT (
                    PACKAGE_NAME, NAME, IDENTIFIER, OPEN_COUNT, ORDER_NUMBER,
                    APP_VISIBLE, APP_OPENED, PALETTE_COLOR, IS_FAVORITE, FOLDER_NAME, PINNED_ZONE
                ) VALUES (
                    'com.example.app1', 'Activity1', 'com.example.app1-Activity1',
                    42, 0, 1, 1, 12345, 1, 'Productivity', 'START'
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO APP_PERSISTENT (
                    PACKAGE_NAME, NAME, IDENTIFIER, OPEN_COUNT, ORDER_NUMBER,
                    APP_VISIBLE, APP_OPENED, PALETTE_COLOR, IS_FAVORITE, FOLDER_NAME, PINNED_ZONE
                ) VALUES (
                    'com.example.app2', 'Activity2', 'com.example.app2-Activity2',
                    10, 1, 0, 1, 0, 0, NULL, 'NONE'
                )
                """.trimIndent()
            )
            sqlite.version = 10
        }

        // Initialize AppDatabase to trigger MIGRATION_10_11
        AppDatabase.init(context)
        db = AppDatabase.getInstance()
        appDao = db.appPersistentDao()
        workspaceDao = db.lensWorkspaceDao()

        // 1. Verify LENS_WORKSPACE exists and contains default "Lens 1"
        val workspaces = workspaceDao.getAll()
        assertEquals(1, workspaces.size)
        val defaultWorkspace = workspaces.first()
        assertEquals(LensWorkspace.DEFAULT_LENS_ID, defaultWorkspace.id)
        assertEquals(LensWorkspace.DEFAULT_LENS_NAME, defaultWorkspace.name)

        // 2. Verify pre-existing rows were migrated losslessly under default lens
        val apps = appDao.getAllForLens(LensWorkspace.DEFAULT_LENS_ID)
        assertEquals(2, apps.size)

        val app1 = apps.find { it.identifier == "com.example.app1-Activity1" }
        assertNotNull(app1)
        assertEquals(42L, app1?.openCount)
        assertEquals(0, app1?.orderNumber)
        assertTrue(app1?.appVisible == true)
        assertTrue(app1?.isFavorite == true)
        assertEquals("Productivity", app1?.folderName)
        assertEquals(PinnedZone.START.name, app1?.pinnedZone)
        assertEquals(LensWorkspace.DEFAULT_LENS_ID, app1?.lensId)

        val app2 = apps.find { it.identifier == "com.example.app2-Activity2" }
        assertNotNull(app2)
        assertEquals(10L, app2?.openCount)
        assertFalse(app2?.appVisible == true)
        assertFalse(app2?.isFavorite == true)
        assertNull(app2?.folderName)
        assertEquals(PinnedZone.NONE.name, app2?.pinnedZone)
        assertEquals(LensWorkspace.DEFAULT_LENS_ID, app2?.lensId)
    }

    @Test
    fun `multi-lens workspace data isolation`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        AppDatabase.init(context)
        db = AppDatabase.getInstance()
        appDao = db.appPersistentDao()
        workspaceDao = db.lensWorkspaceDao()

        // Create a secondary workspace "Work"
        val workWorkspace = LensWorkspace(id = "work", name = "Work", orderIndex = 1)
        workspaceDao.insertOrUpdate(workWorkspace)

        val defaultApps = listOf(
            AppPersistent(
                packageName = "com.chat",
                name = "ChatActivity",
                identifier = "com.chat-ChatActivity",
                openCount = 5L,
                pinnedZone = PinnedZone.START.name,
                isFavorite = true,
                folderName = "Social",
                lensId = LensWorkspace.DEFAULT_LENS_ID
            )
        )
        appDao.insert(defaultApps.first())

        // Duplicate layout to "work" lens
        appDao.duplicateLensLayout(LensWorkspace.DEFAULT_LENS_ID, "work")

        val workAppsBefore = appDao.getAllForLens("work")
        assertEquals(1, workAppsBefore.size)
        assertEquals(PinnedZone.START.name, workAppsBefore.first().pinnedZone)
        assertEquals("Social", workAppsBefore.first().folderName)

        // Modify every per-lens layout field in "work" only.
        appDao.updateOrganization("work", "com.chat-ChatActivity", favorite = false, folderName = "WorkSpace", pinnedZone = PinnedZone.NONE.name)
        appDao.updateOrder("work", "com.chat-ChatActivity", 9)
        appDao.updateVisibility("work", "com.chat-ChatActivity", false)
        appDao.updateOpened("work", "com.chat-ChatActivity", false)

        val workAppsAfter = appDao.getAllForLens("work")
        assertFalse(workAppsAfter.first().isFavorite)
        assertEquals("WorkSpace", workAppsAfter.first().folderName)
        assertEquals(PinnedZone.NONE.name, workAppsAfter.first().pinnedZone)
        assertEquals(9, workAppsAfter.first().orderNumber)
        assertFalse(workAppsAfter.first().appVisible)
        assertFalse(workAppsAfter.first().appOpened)

        val defaultAppsAfter = appDao.getAllForLens(LensWorkspace.DEFAULT_LENS_ID)
        assertTrue(defaultAppsAfter.first().isFavorite)
        assertEquals("Social", defaultAppsAfter.first().folderName)
        assertEquals(PinnedZone.START.name, defaultAppsAfter.first().pinnedZone)
        assertEquals(-1, defaultAppsAfter.first().orderNumber)
        assertTrue(defaultAppsAfter.first().appVisible)
        assertTrue(defaultAppsAfter.first().appOpened)
    }

    @Test
    fun `incrementExisting updates open count across all workspaces`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        AppDatabase.init(context)
        db = AppDatabase.getInstance()
        appDao = db.appPersistentDao()

        val appInDefault = AppPersistent(
            packageName = "com.app",
            name = "Act",
            identifier = "com.app-Act",
            openCount = 10L,
            lensId = LensWorkspace.DEFAULT_LENS_ID
        )
        val appInWork = AppPersistent(
            packageName = "com.app",
            name = "Act",
            identifier = "com.app-Act",
            openCount = 10L,
            lensId = "work"
        )
        appDao.insert(appInDefault)
        appDao.insert(appInWork)

        // Increment existing
        val updatedRows = appDao.incrementExisting("com.app-Act")
        assertEquals(2, updatedRows)

        // Both workspaces must reflect open count = 11
        val inDefault = appDao.findByIdentifier(LensWorkspace.DEFAULT_LENS_ID, "com.app-Act")
        val inWork = appDao.findByIdentifier("work", "com.app-Act")
        assertEquals(11L, inDefault?.openCount)
        assertEquals(11L, inWork?.openCount)
    }
}
