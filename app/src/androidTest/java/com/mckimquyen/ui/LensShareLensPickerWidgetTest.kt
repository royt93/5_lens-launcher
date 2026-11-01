package com.mckimquyen.ui

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LensShareLensPickerWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() = runBlocking {
        cleanDb()
    }

    @After
    fun tearDown() = runBlocking {
        cleanDb()
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        UtilSettings(context).clearPendingAutoExportLens()
    }

    private suspend fun cleanDb() {
        AppDatabase.init(context)
        val dao = AppDatabase.getInstance().lensWorkspaceDao()
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
    }

    private fun idle() = instrumentation.waitForIdleSync()

    @Test
    fun aSingleLensExportsDirectlyWithoutShowingThePicker() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            var captured: Intent? = null
            val latch = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.lensShareLauncher = ActHome.LensShareLauncher { intent ->
                    captured = intent
                    latch.countDown()
                }
                activity.exportActiveLensImage()
            }
            assertTrue("export completes", latch.await(10, TimeUnit.SECONDS))
            assertNotNull(captured)
            scenario.onActivity { activity ->
                assertNull("no picker dialog should be shown", activity.lensDialog)
            }
        }
    }

    @Test
    fun twoLensesOpenThePickerWithActiveLensCheckedAndDisabledWhenEmpty() {
        runBlocking {
            AppDatabase.getInstance().lensWorkspaceDao()
                .insertOrUpdate(LensWorkspace(id = "second", name = "Second", orderIndex = 1))
        }
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity -> activity.exportActiveLensImage() }
            idle()
            var dialog: AlertDialog? = null
            scenario.onActivity { activity -> dialog = activity.lensDialog }
            assertNotNull("picker dialog must be showing", dialog)
            assertTrue(dialog!!.isShowing)

            instrumentation.runOnMainSync {
                val list = dialog!!.listView
                assertEquals(2, list.count)
                // First lens (default, active) must be checked; second must be unchecked
                assertTrue("active lens is checked", list.isItemChecked(0))
                assertFalse("inactive lens is unchecked", list.isItemChecked(1))
                val positive = dialog!!.getButton(DialogInterface.BUTTON_POSITIVE)
                assertTrue("confirm is enabled with one checked", positive.isEnabled)

                // Untick the active lens -> positive disabled
                list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                assertFalse("confirm is disabled with none checked", positive.isEnabled)

                // Tick both -> positive enabled
                list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1))
                assertTrue("confirm is enabled with both checked", positive.isEnabled)

                dialog!!.dismiss()
            }
            idle()
        }
    }

    @Test
    fun confirmingTwoLensesLaunchesChooserWithLensesSheetStream() {
        runBlocking {
            AppDatabase.getInstance().lensWorkspaceDao()
                .insertOrUpdate(LensWorkspace(id = "second", name = "Second", orderIndex = 1))
        }
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            var captured: Intent? = null
            val latch = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.lensShareLauncher = ActHome.LensShareLauncher { intent ->
                    captured = intent
                    latch.countDown()
                }
                activity.exportActiveLensImage()
            }
            idle()
            scenario.onActivity { activity ->
                val dialog = activity.lensDialog
                assertNotNull(dialog)
                val list = dialog!!.listView
                // Tick the second lens as well
                list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1))
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            assertTrue("contact sheet export completes", latch.await(15, TimeUnit.SECONDS))
            assertNotNull(captured)
            val inner = captured!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(inner)
            val stream = inner!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            assertNotNull(stream)
            assertTrue("stream must point to lenses.png", stream.toString().contains("lenses.png"))
        }
    }

    @Test
    fun confirmingThePickerKeepsTheDurablePendingFlagUntilTheExportFinishes() {
        runBlocking {
            AppDatabase.getInstance().lensWorkspaceDao()
                .insertOrUpdate(LensWorkspace(id = "second", name = "Second", orderIndex = 1))
        }
        val settings = UtilSettings(context)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            settings.setPendingAutoExportLens(true)
            val latch = CountDownLatch(1)
            var flagAtLaunch: Boolean? = null
            scenario.onActivity { activity ->
                activity.lensShareLauncher = ActHome.LensShareLauncher {
                    flagAtLaunch = settings.hasPendingAutoExportLens()
                    latch.countDown()
                }
                activity.exportActiveLensImage()
            }
            idle()
            val flagAfterDismissDelivered = CountDownLatch(1)
            var flagAfterDismiss: Boolean? = null
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.lensDialog)
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                // OnDismissListener is delivered through the main handler, queued by dismiss() above.
                // This runnable is queued after it and before the export's own main-thread completion
                // (that one needs the IO write first), so it sees the flag once the dismiss listener ran.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    flagAfterDismiss = settings.hasPendingAutoExportLens()
                    flagAfterDismissDelivered.countDown()
                }
            }
            assertTrue(flagAfterDismissDelivered.await(10, TimeUnit.SECONDS))
            assertTrue("the flag survives the picker's dismiss listener on confirm", flagAfterDismiss == true)
            assertTrue("export completes", latch.await(15, TimeUnit.SECONDS))
            assertFalse("the flag is cleared once the export reaches its terminal outcome", settings.hasPendingAutoExportLens())
        }
    }

    @Test
    fun cancellingThePickerClearsTheDurablePendingFlagAndLaunchesNothing() {
        runBlocking {
            AppDatabase.getInstance().lensWorkspaceDao()
                .insertOrUpdate(LensWorkspace(id = "second", name = "Second", orderIndex = 1))
        }
        val settings = UtilSettings(context)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            // Written only now: a flag on disk at launch would make ActHome resurrect the export itself.
            settings.setPendingAutoExportLens(true)
            assertTrue(settings.hasPendingAutoExportLens())
            var launched = false
            scenario.onActivity { activity ->
                activity.lensShareLauncher = ActHome.LensShareLauncher { launched = true }
                activity.exportActiveLensImage()
            }
            idle()
            scenario.onActivity { activity ->
                val dialog = activity.lensDialog
                assertNotNull(dialog)
                dialog!!.cancel()
            }
            idle()
            assertFalse("cancel must not launch share", launched)
            assertFalse("cancel must clear the durable pending flag", settings.hasPendingAutoExportLens())
        }
    }
}
