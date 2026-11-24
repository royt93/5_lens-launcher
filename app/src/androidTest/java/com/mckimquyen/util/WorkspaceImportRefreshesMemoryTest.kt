package com.mckimquyen.util

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
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

/**
 * Audit S1: after a v2 workspace import Room holds the imported state, but RAppsSingleton (the in-memory snapshot every screen draws and every drag writes back)
 * is only re-sorted by the apps-edited event, never re-read from Room. A drag right after the import then writes the OLD order/flags over the imported ones.
 * This test asks the snapshot what it holds after an import that changes a favourite flag.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceImportRefreshesMemoryTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val database get() = AppDatabase.getInstance()
    private val settings get() = UtilSettings(context)
    private var originalApps: ArrayList<App>? = null
    private var scenario: androidx.test.core.app.ActivityScenario<com.mckimquyen.feature.organization.OrganizationHarnessActivity>? = null

    private companion object {
        const val PKG = "com.example.s1"
        const val NAME = "com.example.s1.Main"
        const val IDENTIFIER = "$PKG-$NAME"
    }

    @Before fun setup() {
        scenario = androidx.test.core.app.ActivityScenario.launch(com.mckimquyen.feature.organization.OrganizationHarnessActivity::class.java)
        AppDatabase.init(context)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        originalApps = RAppsSingleton.instance.apps
        runBlocking {
            database.appPersistentDao().deleteAllRows()
            database.lensWorkspaceDao().replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList())
        }
    }

    @After fun tearDown() {
        scenario?.close()
        scenario = null
        runBlocking {
            database.appPersistentDao().deleteAllRows()
            database.lensWorkspaceDao().replaceWorkspace(listOf(LensWorkspace.createDefault()), emptyList())
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        RAppsSingleton.instance.apps = originalApps
    }

    @Test fun theInMemorySnapshotFollowsAnImportThatChangesAFavouriteFlag() = runBlocking {
        val workspaceDao = database.lensWorkspaceDao()
        val appDao = database.appPersistentDao()
        // The backup says: the app IS a favourite.
        workspaceDao.replaceWorkspace(
            listOf(LensWorkspace.createDefault()),
            listOf(AppPersistent(packageName = PKG, name = NAME, identifier = IDENTIFIER, isFavorite = true))
        )
        val backup = LayoutBackupSnapshotFactory.create(
            workspaceDao.getAll(), appDao.getAllRows(),
            listOf(LensAppSettingsBackup(LensWorkspace.DEFAULT_LENS_ID, LensAppScope.ALL, emptySet(), false)),
            10L
        )
        // Now the phone state differs: Room says NOT a favourite, and so does the in-memory snapshot the screens draw.
        workspaceDao.replaceWorkspace(
            listOf(LensWorkspace.createDefault()),
            listOf(AppPersistent(packageName = PKG, name = NAME, identifier = IDENTIFIER, isFavorite = false))
        )
        RAppsSingleton.instance.apps = arrayListOf(App(label = "S1", packageName = PKG, name = NAME, isFavorite = false))
        assertFalse("positive control: the snapshot starts as not favourite", RAppsSingleton.instance.findApp(PKG, NAME)?.isFavorite ?: true)

        val result = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context)).apply(backup)

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertTrue(
            "positive control: Room really holds the imported favourite",
            appDao.findByIdentifier(LensWorkspace.DEFAULT_LENS_ID, IDENTIFIER)?.isFavorite == true
        )
        // The re-sort that the apps-edited event starts runs on a background coroutine; give it a bounded time to land before judging.
        val deadline = System.currentTimeMillis() + SETTLE_MS
        while (System.currentTimeMillis() < deadline && RAppsSingleton.instance.findApp(PKG, NAME)?.isFavorite != true) Thread.sleep(POLL_MS)
        assertTrue(
            "the in-memory snapshot must show the imported favourite flag, otherwise the next drag writes the old state back over it",
            RAppsSingleton.instance.findApp(PKG, NAME)?.isFavorite == true
        )
    }

    private val SETTLE_MS = 5_000L
    private val POLL_MS = 100L
}
