package com.mckimquyen.util

import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupParseResult
import com.mckimquyen.model.LensPhysicsBackup
import com.mckimquyen.model.LensWorkspace
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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * DB-007 on-device round-trip: exports a multi-lens workspace with per-lens physics overrides,
 * clears/resets preferences, imports the backup back, and confirms:
 * 1. Default and secondary lens physics restore correctly.
 * 2. Un-overridden fields on secondary lenses are cleared on restore so inheritance works.
 * 3. Legacy backup (no physics) leaves device physics intact.
 * 4. Mid-restore preference failure rolls back all physics keys.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceBackupPhysicsIntegrationTest {
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

    private fun resetWorkspace() = runBlocking {
        database.appPersistentDao().deleteAllRows()
        database.lensWorkspaceDao().replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList())
    }

    private suspend fun seedTwoLenses() {
        val lenses = listOf(
            LensWorkspace.createDefault(),
            LensWorkspace(id = "work", name = "Work", orderIndex = 1, createdAt = System.currentTimeMillis())
        )
        database.lensWorkspaceDao().replaceWorkspace(lenses, emptyList())
    }

    @After
    fun tearDown() {
        resetWorkspace()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun physicsRoundTripsThroughRealExportImportAndInheritsCorrectly() {
        runBlocking { seedTwoLenses() }

        // Setup: default has custom distortion; work has its own distortion + smartFocus; neither overrides customDistortionFactor
        settings.saveDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 2.2f)
        settings.saveDistortionFactor("work", 3.8f)
        settings.saveSmartFocusBias("work", true)

        val target = File(context.cacheDir, "physics-roundtrip-${System.nanoTime()}.json")
        val done = CountDownLatch(1)
        var exported = false
        try {
            LayoutBackupIo.exportAsync(context, Uri.fromFile(target)) { exported = it; done.countDown() }
            assertTrue("export must finish within timeout", done.await(5, TimeUnit.SECONDS))
            assertTrue("exportAsync must report success", exported)

            val parsed = LayoutBackup.parse(target.readText()) as LayoutBackupParseResult.Success
            val defaultLens = parsed.backup.lenses.single { it.id == LensWorkspace.DEFAULT_LENS_ID }
            val workLens = parsed.backup.lenses.single { it.id == "work" }

            assertNotNull(defaultLens.physics)
            assertEquals(2.2f, defaultLens.physics?.distortionFactor ?: 0f, 0.01f)
            assertNotNull(workLens.physics)
            assertEquals(3.8f, workLens.physics?.distortionFactor ?: 0f, 0.01f)
            assertEquals(true, workLens.physics?.smartFocusBias)
            assertNull(workLens.physics?.customDistortionFactor)

            // Mutate device state before restore: work gets an unwanted customDistortion override
            settings.saveDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 1.0f)
            settings.saveDistortionFactor("work", 1.0f)
            settings.saveCustomDistortionFactor("work", 4.5f)
            settings.saveSmartFocusBias("work", false)

            val applied = runBlocking {
                WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(parsed.backup)
            }
            assertEquals(WorkspaceBackupImportResult.Applied, applied)

            // Restored: default restored; work restored; work's unwanted customDistortion was cleared
            assertEquals(2.2f, settings.getDistortionFactor(LensWorkspace.DEFAULT_LENS_ID), 0.01f)
            assertEquals(3.8f, settings.getDistortionFactor("work"), 0.01f)
            assertTrue(settings.isSmartFocusBias("work"))
            val raw = PreferenceManager.getDefaultSharedPreferences(context)
            assertFalse(
                "un-overridden field must clear secondary override so it inherits again",
                raw.contains("${UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR}_work")
            )
        } finally {
            target.delete()
        }
    }

    @Test
    fun legacyBackupWithoutPhysicsPreservesDevicePhysics() = runBlocking {
        seedTwoLenses()
        settings.saveDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 2.7f)
        settings.saveDistortionFactor("work", 4.1f)

        // Strip the physics key from an exported file to simulate a pre-DB-007 v2 backup
        val target = File(context.cacheDir, "legacy-${System.nanoTime()}.json")
        val done = CountDownLatch(1)
        LayoutBackupIo.exportAsync(context, Uri.fromFile(target)) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        val root = org.json.JSONObject(target.readText())
        target.delete()
        val lensesArray = root.getJSONArray("lenses")
        for (i in 0 until lensesArray.length()) {
            lensesArray.getJSONObject(i).remove("physics")
        }

        val parsed = LayoutBackup.parse(root.toString()) as LayoutBackupParseResult.Success
        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(parsed.backup)
        assertEquals(WorkspaceBackupImportResult.Applied, result)

        assertEquals(2.7f, settings.getDistortionFactor(LensWorkspace.DEFAULT_LENS_ID), 0.01f)
        assertEquals(4.1f, settings.getDistortionFactor("work"), 0.01f)
    }

    @Test
    fun failedImportRollsBackPhysicsCompletely() = runBlocking {
        seedTwoLenses()
        settings.saveDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 2.0f)
        settings.saveDistortionFactor("work", 3.0f)

        val target = File(context.cacheDir, "rollback-${System.nanoTime()}.json")
        val done = CountDownLatch(1)
        LayoutBackupIo.exportAsync(context, Uri.fromFile(target)) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        val parsed = LayoutBackup.parse(target.readText()) as LayoutBackupParseResult.Success
        target.delete()

        // Modify the parsed backup with new physics so the import would change state
        val modifiedLenses = parsed.backup.lenses.map {
            it.copy(physics = LensPhysicsBackup(distortionFactor = 4.9f))
        }
        val modifiedBackup = parsed.backup.copy(lenses = modifiedLenses)

        val real = WorkspaceBackupRoomStore(context)
        val failingStore = object : WorkspaceBackupImportStore by real {
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) {
                real.replacePreferences(plan)
                error("injected failure right after preferences commit")
            }
        }

        val outcome = WorkspaceBackupImportCoordinator(failingStore).apply(modifiedBackup)
        assertTrue(outcome is WorkspaceBackupImportResult.Failed)

        // Rollback restored original values
        assertEquals(2.0f, settings.getDistortionFactor(LensWorkspace.DEFAULT_LENS_ID), 0.01f)
        assertEquals(3.0f, settings.getDistortionFactor("work"), 0.01f)
    }
}
