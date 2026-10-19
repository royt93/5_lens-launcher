package com.mckimquyen.ui

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.util.LensAppSettingsBackup
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.util.WorkspacePreferencesSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DB-002: the real UI path of a v1 backup import. A v1 file only ever targets the default lens,
 * so importing it while another lens is active must leave that lens's layout untouched.
 */
@RunWith(AndroidJUnit4::class)
class ActSettingsLegacyImportWidgetTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val database get() = AppDatabase.getInstance()
    private val settings get() = UtilSettings(context)

    private var originalApps: ArrayList<App>? = null
    private var originalLenses: List<LensWorkspace> = emptyList()
    private var originalRows: List<AppPersistent> = emptyList()
    private var originalPrefs: WorkspacePreferencesSnapshot? = null

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        // A real PackageManager scan would overwrite the fixture list mid-test.
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        originalLenses = database.lensWorkspaceDao().getAll()
        originalRows = database.appPersistentDao().getAllRows()
        originalPrefs = settings.snapshotWorkspacePreferences()
    }

    @After
    fun tearDown() {
        runBlocking { database.lensWorkspaceDao().replaceWorkspace(originalLenses, originalRows) }
        originalPrefs?.let { settings.restoreWorkspacePreferences(it) }
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun v1ImportWhileAnotherLensIsActiveOnlyChangesTheDefaultLens() {
        runBlocking {
            database.lensWorkspaceDao().replaceWorkspace(
                listOf(LensWorkspace.createDefault(), LensWorkspace(WORK_LENS, "Work", 1, 2L)),
                listOf(
                    AppPersistent(packageName = PKG, name = ACT, identifier = IDENTIFIER, folderName = "DefaultOld", lensId = LensWorkspace.DEFAULT_LENS_ID),
                    AppPersistent(packageName = PKG, name = ACT, identifier = IDENTIFIER, folderName = "WorkKeep", lensId = WORK_LENS)
                )
            )
        }
        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup(LensWorkspace.DEFAULT_LENS_ID, LensAppScope.ALL, emptySet(), false),
                LensAppSettingsBackup(WORK_LENS, LensAppScope.ALL, emptySet(), false)
            ),
            WORK_LENS
        )
        // The in-memory list always follows the ACTIVE lens, so its apps carry lensId = work.
        RAppsSingleton.instance.apps = arrayListOf(App(packageName = PKG, name = ACT, lensId = WORK_LENS))
        val legacy = LayoutBackup(
            LayoutBackup.SCHEMA_VERSION_V1, 0L,
            entries = listOf(LayoutBackupEntry(PKG, ACT, 0, true, true, false, "Imported", PinnedZone.NONE.name))
        )

        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.showLayoutImportPreview(legacy)
                org.junit.Assert.assertNotNull("v1 preview must be tracked like v2", activity.layoutImportDialog)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = activity.layoutImportDialog
                assertTrue("preview dialog must be showing", dialog?.isShowing == true)
                dialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.performClick()
            }
            val deadline = SystemClock.uptimeMillis() + IMPORT_TIMEOUT_MS
            var defaultFolder: String? = null
            while (SystemClock.uptimeMillis() < deadline && defaultFolder != "Imported") {
                SystemClock.sleep(POLL_MS)
                defaultFolder = runBlocking { database.appPersistentDao().findByIdentifier(LensWorkspace.DEFAULT_LENS_ID, IDENTIFIER)?.folderName }
            }
            instrumentation.waitForIdleSync()

            val diag = runBlocking {
                database.appPersistentDao().getAllRows().joinToString { "${it.lensId}:${it.identifier}=${it.folderName}" } +
                    " | singleton=" + RAppsSingleton.instance.apps?.joinToString { "${it.packageName}@${it.lensId}" } +
                    " | active=" + settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
            }
            assertEquals("v1 layout must land on the default lens [$diag]", "Imported", defaultFolder)
            assertEquals(
                "the active (work) lens must not be touched by a v1 import",
                "WorkKeep",
                runBlocking { database.appPersistentDao().findByIdentifier(WORK_LENS, IDENTIFIER)?.folderName }
            )
        }
    }

    private companion object {
        const val WORK_LENS = "work"
        const val PKG = "com.example.home"
        const val ACT = "com.example.home.Main"
        const val IDENTIFIER = "com.example.home-com.example.home.Main"
        const val IMPORT_TIMEOUT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
