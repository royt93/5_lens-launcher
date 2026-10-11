package com.mckimquyen.util

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceBackupImportIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val database get() = AppDatabase.getInstance()
    private val settings get() = UtilSettings(context)

    @Before
    fun setup() {
        AppDatabase.init(context)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        runBlocking { database.lensWorkspaceDao().replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList()) }
    }

    @After
    fun tearDown() {
        runBlocking {
            database.lensWorkspaceDao().replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList())
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun exportMutateImportRestoresWorkspaceAndKeepsNonLayoutStats() = runBlocking {
        val workspaceDao = database.lensWorkspaceDao()
        val appDao = database.appPersistentDao()
        val original = listOf(LensWorkspace.createDefault(), LensWorkspace("work", "Work", 1, 2L))
        val originalRows = listOf(
            AppPersistent(packageName="com.example.home", name="com.example.home.Main", identifier="com.example.home-com.example.home.Main", isFavorite=true, folderName="Home", lensId="default"),
            AppPersistent(packageName="com.example.work", name="com.example.work.Main", identifier="com.example.work-com.example.work.Main", orderNumber=3, isFavorite=true, folderName="Project", lensId="work")
        )
        workspaceDao.replaceWorkspace(original, originalRows)
        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false),
                LensAppSettingsBackup("work", LensAppScope.SELECTED, setOf("com.example.work-com.example.work.Main"), true)
            ), "work"
        )
        val backup = LayoutBackupSnapshotFactory.create(
            workspaceDao.getAll(), appDao.getAllRows(),
            listOf(
                LensAppSettingsBackup("default", settings.getLensAppScope("default"), settings.getLensAppSelection("default"), settings.isLensFrozen("default")),
                LensAppSettingsBackup("work", settings.getLensAppScope("work"), settings.getLensAppSelection("work"), settings.isLensFrozen("work"))
            ), 10L, activeLensId = "work"
        )
        workspaceDao.replaceWorkspace(listOf(LensWorkspace.createDefault()), listOf(
            AppPersistent(packageName="com.local", name="com.local.Main", identifier="com.local-com.local.Main", openCount=8, paletteColor=77, notificationCount=5, isFavorite=true, lensId="default")
        ))
        settings.replaceWorkspacePreferences(listOf(LensAppSettingsBackup("default", LensAppScope.SELECTED, setOf("local"), true)), "default")

        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(backup)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf("default", "work"), workspaceDao.getAll().map { it.id })
        assertEquals("work", settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID))
        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope("work"))
        assertTrue(settings.isLensFrozen("work"))
        assertEquals("Project", appDao.findByIdentifier("work", "com.example.work-com.example.work.Main")?.folderName)
        assertTrue(appDao.findByIdentifier("default", "com.example.home-com.example.home.Main")?.isFavorite == true)
        val parked = appDao.findByIdentifier("default", "com.local-com.local.Main")
        assertEquals(8L, parked?.openCount)
        assertEquals(77, parked?.paletteColor)
        assertEquals(5, parked?.notificationCount)
        assertFalse(parked?.appVisible ?: true)
    }
}
