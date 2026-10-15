package com.mckimquyen.util

import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupParseResult
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WorkspaceBackupImportIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val database get() = AppDatabase.getInstance()
    private val settings get() = UtilSettings(context)
    private var originalApps: ArrayList<App>? = null

    @Before
    fun setup() {
        AppDatabase.init(context)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        originalApps = RAppsSingleton.instance.apps
        resetWorkspace()
    }

    // replaceWorkspace deliberately parks old rows to preserve their stats, so tests must delete
    // rows explicitly or openCount/paletteColor leak between tests.
    private fun resetWorkspace() = runBlocking {
        database.appPersistentDao().deleteAllRows()
        database.lensWorkspaceDao().replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList())
    }

    @After
    fun tearDown() {
        resetWorkspace()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun exportMutateImportRestoresWorkspaceAndKeepsNonLayoutStats() = runBlocking {
        val workspaceDao = database.lensWorkspaceDao()
        val appDao = database.appPersistentDao()
        val original = listOf(
            LensWorkspace.createDefault().copy(groupName = "Personal"),
            LensWorkspace("work", "Work", 1, 2L, groupName = "Work")
        )
        val originalRows = listOf(
            AppPersistent(packageName="com.example.home", name="com.example.home.Main", identifier="com.example.home-com.example.home.Main", isFavorite=true, folderName="Home", lensId="default"),
            AppPersistent(packageName="com.example.work", name="com.example.work.Main", identifier="com.example.work-com.example.work.Main", orderNumber=3, isFavorite=true, folderName="Project", lensId="work")
        )
        workspaceDao.replaceWorkspace(original, originalRows)
        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false),
                LensAppSettingsBackup("work", LensAppScope.SELECTED, setOf("com.example.work-com.example.work.Main"), true)
            ), "work", setOf("Personal")
        )
        val backup = LayoutBackupSnapshotFactory.create(
            workspaceDao.getAll(), appDao.getAllRows(),
            listOf(
                LensAppSettingsBackup("default", settings.getLensAppScope("default"), settings.getLensAppSelection("default"), settings.isLensFrozen("default")),
                LensAppSettingsBackup("work", settings.getLensAppScope("work"), settings.getLensAppSelection("work"), settings.isLensFrozen("work"))
            ), 10L, activeLensId = "work", collapsedGroupNames = setOf("Work")
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
        val shortcutIds = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }
        assertEquals(setOf("lens_default", "lens_work"), shortcutIds.toSet())
    }

    private fun seedTwoLensWorkspace() = runBlocking {
        database.lensWorkspaceDao().replaceWorkspace(
            listOf(LensWorkspace.createDefault(), LensWorkspace("work", "Work", 1, 2L)),
            listOf(
                AppPersistent(packageName="com.example.home", name="com.example.home.Main", identifier="com.example.home-com.example.home.Main", openCount=4, paletteColor=11, isFavorite=false, lensId="default"),
                AppPersistent(packageName="com.example.work", name="com.example.work.Main", identifier="com.example.work-com.example.work.Main", isFavorite=true, folderName="Keep", lensId="work")
            )
        )
        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false),
                LensAppSettingsBackup("work", LensAppScope.SELECTED, setOf("com.example.work-com.example.work.Main"), true)
            ), "work"
        )
    }

    private fun v2Backup() = LayoutBackup(
        LayoutBackup.CURRENT_SCHEMA_VERSION, 0L, activeLensId = "default",
        lenses = listOf(
            com.mckimquyen.model.LayoutBackupLens(
                "default", "Only", 0, LensAppScope.ALL, emptySet(), false,
                listOf(LayoutBackupEntry("com.example.home", "com.example.home.Main", 0, true, true, true, null, PinnedZone.NONE.name))
            )
        )
    )

    @Test
    fun groupAndCollapsedStateSurviveV2ImportAndPreferenceRollback() = runBlocking {
        val workspaceDao = database.lensWorkspaceDao()
        val appDao = database.appPersistentDao()
        val grouped = listOf(
            LensWorkspace.createDefault().copy(groupName = "Personal"),
            LensWorkspace("work", "Work", 1, 2L, groupName = "Work")
        )
        workspaceDao.replaceWorkspace(grouped, emptyList())
        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false),
                LensAppSettingsBackup("work", LensAppScope.ALL, emptySet(), false)
            ), "work", setOf("Personal")
        )
        val backup = LayoutBackupSnapshotFactory.create(
            workspaceDao.getAll(), appDao.getAllRows(),
            listOf(
                LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false),
                LensAppSettingsBackup("work", LensAppScope.ALL, emptySet(), false)
            ), 11L, activeLensId = "work", collapsedGroupNames = setOf("Work")
        )
        workspaceDao.replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList())
        settings.replaceWorkspacePreferences(
            listOf(LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false)), "default", emptySet()
        )

        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(backup)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf("Personal", "Work"), workspaceDao.getAll().map { it.groupName })
        assertEquals(setOf("Work"), settings.getCollapsedLensGroups())

        settings.saveCollapsedLensGroups(setOf("LocalState"))
        val failingPrefs = object : WorkspaceBackupImportStore by WorkspaceBackupRoomStore(context) {
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) = error("injected prefs failure")
        }
        val failed = WorkspaceBackupImportCoordinator(failingPrefs).apply(backup)

        assertTrue(failed is WorkspaceBackupImportResult.Failed)
        assertEquals(listOf("Personal", "Work"), workspaceDao.getAll().map { it.groupName })
        assertEquals(setOf("LocalState"), settings.getCollapsedLensGroups())
    }

    @Test
    fun malformedV2ImportDoesNotChangeWorkspaceOrPreferences() {
        runBlocking {
            seedTwoLensWorkspace()
            val workspaceDao = database.lensWorkspaceDao()
            val appDao = database.appPersistentDao()
            val previousLenses = workspaceDao.getAll()
            val previousRows = appDao.getAllRows()
            val previousActiveLens = settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
            val previousPreferences = previousLenses.associate { lens ->
                lens.id to LensAppSettingsBackup(
                    lens.id,
                    settings.getLensAppScope(lens.id),
                    settings.getLensAppSelection(lens.id),
                    settings.isLensFrozen(lens.id)
                )
            }
            val file = File(context.cacheDir, "malformed_selected_scope_${System.nanoTime()}.json")
            val uri = Uri.fromFile(file)
            file.writeText(
                """{"schemaVersion":2,"exportedAt":0,"activeLensId":"default","lenses":[{"id":"default","name":"Home","orderIndex":0,"scope":"SELECTED","selectedAppIds":[],"frozen":false,"entries":[]},{"id":"work","name":"Work","orderIndex":1,"scope":"SELECTED","selectedAppIds":["com.example.work-com.example.work.Main"],"frozen":true,"entries":[]}]}"""
            )
            val latch = CountDownLatch(1)
            var result: LayoutBackupParseResult? = null

            try {
                LayoutBackupIo.importAsync(context, uri) {
                    result = it
                    latch.countDown()
                }

                assertTrue("import parse must complete", latch.await(5, TimeUnit.SECONDS))
                assertEquals(LayoutBackupParseResult.Malformed, result)
                assertEquals(previousLenses, workspaceDao.getAll())
                assertEquals(previousRows, appDao.getAllRows())
                assertEquals(previousActiveLens, settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID))
                previousPreferences.forEach { (lensId, preference) ->
                    assertEquals(preference.scope, settings.getLensAppScope(lensId))
                    assertEquals(preference.selectedAppIds, settings.getLensAppSelection(lensId))
                    assertEquals(preference.frozen, settings.isLensFrozen(lensId))
                }
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun v1ImportSkipsEntriesForAppsNotInstalled() = runBlocking {
        val installed = App(packageName = "com.example.present", name = "com.example.present.Main")
        RAppsSingleton.instance.apps = arrayListOf(installed)
        val legacy = LayoutBackup(
            LayoutBackup.SCHEMA_VERSION_V1, 0L,
            entries = listOf(
                LayoutBackupEntry("com.example.present", "com.example.present.Main", 2, true, true, true, "Keep", PinnedZone.NONE.name),
                LayoutBackupEntry("com.example.gone", "com.example.gone.Main", 1, true, true, true, "Missing", PinnedZone.NONE.name)
            )
        )

        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(legacy)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals("Keep", database.appPersistentDao().findByIdentifier(
            "default", "com.example.present-com.example.present.Main"
        )?.folderName)
        assertEquals(null, database.appPersistentDao().findByIdentifier(
            "default", "com.example.gone-com.example.gone.Main"
        ))
    }

    @Test
    fun v1ImportOnlyChangesDefaultLens() = runBlocking {
        seedTwoLensWorkspace()
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.example.home", name = "com.example.home.Main")
        )
        val legacy = LayoutBackup(
            LayoutBackup.SCHEMA_VERSION_V1, 0L,
            entries = listOf(LayoutBackupEntry("com.example.home", "com.example.home.Main", 0, true, true, true, "Imported", PinnedZone.NONE.name))
        )

        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(legacy)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf("default", "work"), database.lensWorkspaceDao().getAll().map { it.id })
        val home = database.appPersistentDao().findByIdentifier("default", "com.example.home-com.example.home.Main")
        assertEquals("Imported", home?.folderName)
        assertEquals(4L, home?.openCount)
        assertEquals(11, home?.paletteColor)
        assertEquals("Keep", database.appPersistentDao().findByIdentifier("work", "com.example.work-com.example.work.Main")?.folderName)
        assertEquals("work", settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID))
        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope("work"))
        assertTrue(settings.isLensFrozen("work"))
    }

    @Test
    fun v1ImportPreservesGlobalStatsWhenIdentityOnlyExistsOnAnotherLens() = runBlocking {
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.example.work", name = "com.example.work.Main")
        )
        database.lensWorkspaceDao().replaceWorkspace(
            listOf(LensWorkspace.createDefault(), LensWorkspace("work", "Work", 1, 2L)),
            listOf(AppPersistent(
                packageName="com.example.work", name="com.example.work.Main",
                identifier="com.example.work-com.example.work.Main", openCount=9,
                paletteColor=44, notificationCount=6, lensId="work"
            ))
        )
        val legacy = LayoutBackup(
            LayoutBackup.SCHEMA_VERSION_V1, 0L,
            entries = listOf(LayoutBackupEntry("com.example.work", "com.example.work.Main", 0, true, true, true, null, PinnedZone.NONE.name))
        )

        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(legacy)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        val restored = database.appPersistentDao().findByIdentifier("default", "com.example.work-com.example.work.Main")
        assertEquals(9L, restored?.openCount)
        assertEquals(44, restored?.paletteColor)
        assertEquals(6, restored?.notificationCount)
        assertEquals(9L, database.appPersistentDao().findByIdentifier("work", "com.example.work-com.example.work.Main")?.openCount)
    }

    @Test
    fun preferenceFailureRestoresDatabaseAndPreferences() = runBlocking {
        seedTwoLensWorkspace()
        val real = WorkspaceBackupRoomStore(context)
        val failingPrefs = object : WorkspaceBackupImportStore by real {
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) = error("injected prefs failure")
        }

        val result = WorkspaceBackupImportCoordinator(failingPrefs).apply(v2Backup())

        assertTrue(result is WorkspaceBackupImportResult.Failed)
        assertEquals(listOf("default", "work"), database.lensWorkspaceDao().getAll().map { it.id })
        assertEquals("Keep", database.appPersistentDao().findByIdentifier("work", "com.example.work-com.example.work.Main")?.folderName)
        assertEquals("work", settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID))
        assertTrue(settings.isLensFrozen("work"))
    }

    @Test
    fun rollbackFailureReportsRecoveryRequired() = runBlocking {
        seedTwoLensWorkspace()
        val real = WorkspaceBackupRoomStore(context)
        val failingBoth = object : WorkspaceBackupImportStore by real {
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) = error("injected prefs failure")
            override fun restorePreferences(snapshot: Any): Boolean = false
        }

        val result = WorkspaceBackupImportCoordinator(failingBoth).apply(v2Backup())

        assertTrue(result is WorkspaceBackupImportResult.RecoveryRequired)
    }
}
