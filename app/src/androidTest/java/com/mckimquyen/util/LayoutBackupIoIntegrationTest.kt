package com.mckimquyen.util

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackupParseResult
import com.mckimquyen.model.PinnedZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * FEAT-006 real-device proof: a real SAF-style content read/write round trip (via a real file://
 * Uri and the real ContentResolver, not a mock stream) and a real Room round trip applying
 * imported state through LayoutImportApplier - the two boundaries this story's own acceptance
 * criteria call out as needing more than a pure unit test.
 */
@RunWith(AndroidJUnit4::class)
class LayoutBackupIoIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageName = "com.example.layoutbackup"
    private val componentName = "com.example.layoutbackup.MainActivity"
    private val identifier = AppPersistent.generateIdentifier(packageName, componentName)

    private fun tempFileUri(): Uri {
        val file = File(context.cacheDir, "layout_backup_test_${System.nanoTime()}.json")
        return Uri.fromFile(file)
    }

    private fun waitForRow(predicate: (AppPersistent?) -> Boolean): AppPersistent? = runBlocking {
        val dao = AppDatabase.getInstance().appPersistentDao()
        withTimeout(5_000) {
            var current: AppPersistent?
            do {
                current = dao.findByIdentifier(identifier)
                if (!predicate(current)) delay(10)
            } while (!predicate(current))
            current
        }
    }

    @After
    fun tearDown() = runBlocking {
        val dao = AppDatabase.getInstance().appPersistentDao()
        dao.findByIdentifier(identifier)?.let { dao.delete(it) }
        RAppsSingleton.instance.clearAllData()
    }

    @Test
    fun exportThenImport_realFileRoundTrip_preservesEveryField() {
        AppPersistent.setOrganization(packageName, componentName, true, "Work", PinnedZone.START)
        waitForRow { it?.folderName == "Work" }
        AppPersistent.setAppVisibility(packageName, componentName, false)
        waitForRow { it?.appVisible == false }

        val uri = tempFileUri()
        val exportLatch = CountDownLatch(1)
        var exportSucceeded = false
        LayoutBackupIo.exportAsync(context, uri) { success ->
            exportSucceeded = success
            exportLatch.countDown()
        }
        assertTrue("export must complete", exportLatch.await(5, TimeUnit.SECONDS))
        assertTrue("export must report success", exportSucceeded)

        val importLatch = CountDownLatch(1)
        var importResult: LayoutBackupParseResult? = null
        LayoutBackupIo.importAsync(context, uri) { result ->
            importResult = result
            importLatch.countDown()
        }
        assertTrue("import parse must complete", importLatch.await(5, TimeUnit.SECONDS))

        val success = importResult as LayoutBackupParseResult.Success
        val entry = success.backup.entries.first { it.packageName == packageName }
        assertEquals(packageName, entry.packageName)
        assertEquals(componentName, entry.name)
        assertEquals(false, entry.appVisible)
        assertEquals(true, entry.isFavorite)
        assertEquals("Work", entry.folderName)
        assertEquals(PinnedZone.START.name, entry.pinnedZone)
    }

    @Test
    fun applyingAnImportedPlan_actuallyPersistsThroughRoom_notJustInMemory() {
        // Starting state: favorite, folder "Old".
        AppPersistent.setOrganization(packageName, componentName, true, "Old", PinnedZone.NONE)
        waitForRow { it?.folderName == "Old" }

        RAppsSingleton.instance.apps = arrayListOf(App(packageName = packageName, name = componentName))
        val plan = LayoutImportApplier.plan(
            com.mckimquyen.model.LayoutBackup(
                1, 0L,
                listOf(
                    com.mckimquyen.model.LayoutBackupEntry(
                        packageName, componentName, orderNumber = 0,
                        appVisible = true, appOpened = true,
                        isFavorite = false, folderName = "New", pinnedZone = PinnedZone.END.name
                    )
                )
            ),
            RAppsSingleton.instance.apps.orEmpty()
        )

        LayoutImportApplier.apply(plan)

        val stored = waitForRow { it?.folderName == "New" }
        assertEquals(false, stored?.isFavorite)
        assertEquals(PinnedZone.END.name, stored?.pinnedZone)
    }
}
