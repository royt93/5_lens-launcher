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
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

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
    fun hiddenIdsRoundTripThroughFileAndRestoreAfterPreferenceFailure() {
        val originalHidden = setOf("com.example.home-com.example.home.Main", "com.gone-com.gone.Main")
        runBlocking { seedTwoLensWorkspace() }
        settings.saveLensHiddenAppIds("default", originalHidden)
        settings.saveLensHiddenAppIds("work", setOf("work-orphan"))
        val target = File(context.cacheDir, "hidden-ids-${System.nanoTime()}.json")
        val done = CountDownLatch(1)
        var exported = false
        try {
            LayoutBackupIo.exportAsync(context, Uri.fromFile(target)) { exported = it; done.countDown() }
            assertTrue("export must finish", done.await(5, TimeUnit.SECONDS))
            assertTrue("hidden app state must export", exported)
            val parsed = LayoutBackup.parse(target.readText()) as LayoutBackupParseResult.Success
            assertEquals(originalHidden, parsed.backup.lenses.single { it.id == "default" }.temporarilyHiddenAppIds)
            assertEquals(setOf("work-orphan"), parsed.backup.lenses.single { it.id == "work" }.temporarilyHiddenAppIds)
            settings.saveLensHiddenAppIds("default", setOf("local-orphan"))
            settings.saveLensHiddenAppIds("work", emptySet())
            val real = WorkspaceBackupRoomStore(context)
            val failingPrefs = object : WorkspaceBackupImportStore by real {
                override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) {
                    real.replacePreferences(plan)
                    error("injected failure after preferences changed")
                }
            }
            val failed = runBlocking { WorkspaceBackupImportCoordinator(failingPrefs).apply(parsed.backup) }
            assertTrue(failed is WorkspaceBackupImportResult.Failed)
            assertEquals(setOf("local-orphan"), settings.getLensHiddenAppIds("default"))
            assertEquals(emptySet<String>(), settings.getLensHiddenAppIds("work"))
            val applied = runBlocking { WorkspaceBackupImportCoordinator(real).apply(parsed.backup) }
            assertEquals(WorkspaceBackupImportResult.Applied, applied)
            assertEquals(originalHidden, settings.getLensHiddenAppIds("default"))
            assertEquals(setOf("work-orphan"), settings.getLensHiddenAppIds("work"))
            assertEquals(LensAppScope.SELECTED, settings.getLensAppScope("work"))
            assertEquals(setOf("com.example.work-com.example.work.Main"), settings.getLensAppSelection("work"))
        } finally {
            target.delete()
        }
    }

    @Test
    fun legacyV2WithoutHiddenIdsReplacesStaleHiddenIdsWithEmptySet() = runBlocking {
        val root = org.json.JSONObject(v2Backup().toJson())
        root.getJSONArray("lenses").getJSONObject(0).remove("temporarilyHiddenAppIds")
        settings.saveLensHiddenAppIds("default", setOf("stale-orphan"))
        val parsed = LayoutBackup.parse(root.toString()) as LayoutBackupParseResult.Success
        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(parsed.backup)
        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(emptySet<String>(), settings.getLensHiddenAppIds("default"))
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

    /** DB-003: after any create/delete attempt (incl. the refused default delete), export still works and re-imports. */
    @Test
    fun exportAndImportStillRoundTripAfterRefusedDefaultDeleteAndLastLensDelete() = runBlocking {
        val workspaceDao = database.lensWorkspaceDao()
        workspaceDao.replaceWorkspace(
            listOf(LensWorkspace.createDefault(), LensWorkspace("temp", "Temp", 1, 2L)),
            emptyList()
        )

        assertFalse("default lens must not be deletable", workspaceDao.deleteLensIfMoreThanOne(LensWorkspace.DEFAULT_LENS_ID))
        assertTrue(workspaceDao.deleteLensIfMoreThanOne("temp"))
        assertFalse("the last lens must not be deletable", workspaceDao.deleteLensIfMoreThanOne(LensWorkspace.DEFAULT_LENS_ID))

        val lenses = workspaceDao.getAll()
        assertEquals(listOf(LensWorkspace.DEFAULT_LENS_ID), lenses.map { it.id })
        val backup = LayoutBackupSnapshotFactory.create(
            lenses, database.appPersistentDao().getAllRows(),
            listOf(LensAppSettingsBackup(LensWorkspace.DEFAULT_LENS_ID, LensAppScope.ALL, emptySet(), false)),
            10L
        )
        val reparsed = LayoutBackup.parse(backup.toJson())
        assertTrue("exported file must parse", reparsed is LayoutBackupParseResult.Success)

        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context))
            .apply((reparsed as LayoutBackupParseResult.Success).backup)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf(LensWorkspace.DEFAULT_LENS_ID), workspaceDao.getAll().map { it.id })
    }

    /** DB-004: a legacy workspace with a duplicate orderIndex is repaired, and then exports a re-importable file. */
    @Test
    fun legacyDuplicateOrderIndexIsRepairedAndThenRoundTrips() = runBlocking {
        val workspaceDao = database.lensWorkspaceDao()
        workspaceDao.replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList())
        workspaceDao.insertOrUpdate(LensWorkspace("a", "A", 2, 10L))
        workspaceDao.insertOrUpdate(LensWorkspace("b", "B", 2, 20L))

        workspaceDao.normalizeOrderIndices()

        val lenses = workspaceDao.getAll()
        assertEquals(listOf(0, 1, 2), lenses.map { it.orderIndex })
        val backup = LayoutBackupSnapshotFactory.create(
            lenses, database.appPersistentDao().getAllRows(),
            lenses.map { LensAppSettingsBackup(it.id, LensAppScope.ALL, emptySet(), false) },
            10L
        )
        val parsed = LayoutBackup.parse(backup.toJson())
        assertTrue("repaired workspace must export an importable file", parsed is LayoutBackupParseResult.Success)
        assertEquals(
            WorkspaceBackupImportResult.Applied,
            WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply((parsed as LayoutBackupParseResult.Success).backup)
        )
        assertEquals(listOf(LensWorkspace.DEFAULT_LENS_ID, "a", "b"), workspaceDao.getAll().map { it.id })
    }

    /** DB-005: opening an app while a non-default lens is active must not move the count backwards. */
    @Test
    fun openCountIsGlobalAcrossLensesAfterIncrementAppCount() = runBlocking {
        seedTwoLensWorkspace()
        val dao = database.appPersistentDao()
        val identifier = "com.example.home-com.example.home.Main"
        dao.insert(AppPersistent(packageName = "com.example.home", name = "com.example.home.Main", identifier = identifier, openCount = 50, lensId = "work"))
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.example.home", name = "com.example.home.Main", openCount = 50, lensId = "work")
        )

        val committed = dao.incrementAtomic(AppPersistent(packageName = "com.example.home", name = "com.example.home.Main", identifier = identifier, lensId = "work"))

        assertEquals(51L, committed)
        assertEquals(51L, dao.findByIdentifier("default", identifier)?.openCount)
        assertEquals(51L, dao.findByIdentifier("work", identifier)?.openCount)
        val shell = App(packageName = "com.example.home", name = "com.example.home.Main")
        val onDefault = UtilApp.mergeLensPersistence(listOf(shell), "default", com.mckimquyen.enums.SortType.LABEL_ASCENDING).single().openCount
        val onWork = UtilApp.mergeLensPersistence(listOf(shell), "work", com.mckimquyen.enums.SortType.LABEL_ASCENDING).single().openCount
        assertEquals(onWork, onDefault)
        assertEquals(51L, onDefault)
    }

    /** DB-005: the extra GROUP BY in mergeLensPersistence must stay cheap for a large app list. */
    @Test
    fun mergeLensPersistenceStaysFastForAThousandApps() = runBlocking {
        val dao = database.appPersistentDao()
        val shells = (1..1000).map { App(label = "App$it", packageName = "bulk.p$it", name = "bulk.p$it.Main") }
        dao.insertAll(shells.map {
            AppPersistent(packageName = it.packageName.toString(), name = it.name.toString(),
                identifier = AppPersistent.generateIdentifier(it.packageName.toString(), it.name.toString()), openCount = 3, lensId = "default")
        })
        UtilApp.mergeLensPersistence(shells, "default", com.mckimquyen.enums.SortType.LABEL_ASCENDING) // warm up
        val start = System.nanoTime()
        repeat(5) { UtilApp.mergeLensPersistence(shells, "default", com.mckimquyen.enums.SortType.LABEL_ASCENDING) }
        val averageMs = (System.nanoTime() - start) / 5 / 1_000_000
        dao.getGlobalOpenCounts() // warm up
        val groupStart = System.nanoTime()
        repeat(5) { dao.getGlobalOpenCounts() }
        val groupMs = (System.nanoTime() - groupStart) / 5 / 1_000_000.0
        val lensStart = System.nanoTime()
        repeat(5) { dao.getAllForLens("default") }
        val lensMs = (System.nanoTime() - lensStart) / 5 / 1_000_000.0
        android.util.Log.i("DB005PERF", "merge=${averageMs}ms groupBy=${groupMs}ms getAllForLens=${lensMs}ms")
        assertTrue("merge of 1000 apps took ${averageMs}ms", averageMs < 500)
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
    fun groupsAndCollapsedStateRoundTripThroughSerializedJson() = runBlocking {
        val workspaceDao = database.lensWorkspaceDao()
        val prefs = listOf(
            LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false),
            LensAppSettingsBackup("work", LensAppScope.ALL, emptySet(), false),
            LensAppSettingsBackup("loose", LensAppScope.ALL, emptySet(), false)
        )
        workspaceDao.replaceWorkspace(
            listOf(
                LensWorkspace.createDefault().copy(groupName = "Personal"),
                LensWorkspace("work", "Work", 1, 2L, groupName = "Work"),
                LensWorkspace("loose", "Loose", 2, 3L)
            ),
            emptyList()
        )
        val json = LayoutBackupSnapshotFactory.create(
            workspaceDao.getAll(), database.appPersistentDao().getAllRows(), prefs, 5L,
            activeLensId = "work", collapsedGroupNames = setOf("Work")
        ).toJson()
        // Wipe to a different state so a no-op import cannot pass.
        workspaceDao.replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList())
        settings.replaceWorkspacePreferences(
            listOf(LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false)), "default", emptySet()
        )

        val parsed = LayoutBackup.parse(json)
        assertTrue("exported JSON must parse", parsed is LayoutBackupParseResult.Success)
        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context))
            .apply((parsed as LayoutBackupParseResult.Success).backup)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(
            listOf("Personal" to "default", "Work" to "work", null to "loose"),
            workspaceDao.getAll().map { it.groupName to it.id }
        )
        assertEquals(setOf("Work"), settings.getCollapsedLensGroups())
    }

    @Test
    fun exportIgnoresOrphanAppRowsOfDeletedLensesInsteadOfFailing() {
        runBlocking {
            database.lensWorkspaceDao().replaceWorkspace(
                listOf(LensWorkspace.createDefault().copy(groupName = "Smoke")), emptyList()
            )
            // Orphan rows outlive a deleted lens whenever something writes after deleteLens().
            database.appPersistentDao().insert(
                AppPersistent(
                    packageName = "com.ghost", name = "com.ghost.Main",
                    identifier = "com.ghost-com.ghost.Main", lensId = "ghost-lens"
                )
            )
            settings.replaceWorkspacePreferences(
                listOf(LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false)), "default", setOf("Smoke")
            )
        }
        val target = File(context.cacheDir, "orphan-export.json").apply { delete() }
        val done = CountDownLatch(1)
        var ok = false

        LayoutBackupIo.exportAsync(context, Uri.fromFile(target)) { ok = it; done.countDown() }

        assertTrue("export must finish", done.await(5, TimeUnit.SECONDS))
        assertTrue("orphan rows must not abort the export", ok)
        val parsed = LayoutBackup.parse(target.readText())
        assertTrue(parsed is LayoutBackupParseResult.Success)
        val backup = (parsed as LayoutBackupParseResult.Success).backup
        assertEquals(listOf("Smoke"), backup.lenses.map { it.groupName })
        assertEquals(setOf("Smoke"), backup.collapsedGroupNames)
        assertTrue("ghost app must not leak into any lens", backup.lenses.flatMap { it.entries }.none { it.packageName == "com.ghost" })
        target.delete()
    }

    @Test
    fun serializedV2BackupWithoutGroupFieldsImportsUngroupedAndExpanded() = runBlocking {
        val root = org.json.JSONObject(v2Backup().toJson())
        root.remove("collapsedGroupNames")
        root.getJSONArray("lenses").getJSONObject(0).remove("groupName")
        settings.saveCollapsedLensGroups(setOf("Stale"))

        val parsed = LayoutBackup.parse(root.toString())
        assertTrue(parsed is LayoutBackupParseResult.Success)
        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context))
            .apply((parsed as LayoutBackupParseResult.Success).backup)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf<String?>(null), database.lensWorkspaceDao().getAll().map { it.groupName })
        assertEquals(emptySet<String>(), settings.getCollapsedLensGroups())
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
                val currentRows = appDao.getAllRows()
                assertEquals(
                    previousRows,
                    currentRows.filter { row -> previousRows.any { it.identifier == row.identifier && it.lensId == row.lensId } }
                )
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
        settings.saveLensHiddenAppIds("default", setOf("default-orphan"))
        settings.saveLensHiddenAppIds("work", setOf("work-orphan"))
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
        assertEquals(setOf("default-orphan"), settings.getLensHiddenAppIds("default"))
        assertEquals(setOf("work-orphan"), settings.getLensHiddenAppIds("work"))
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
