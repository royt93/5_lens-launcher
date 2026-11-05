package com.mckimquyen.ui

import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensShortcuts
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

@RunWith(AndroidJUnit4::class)
class ActHomeQuickLensSwitchWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val secondId = "quick-switch-second"
    private val managerEntryId = ActHome.MENU_ID_LENS_APPS

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        seed()
    }

    @After
    fun tearDown() = clean()

    private fun clean() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().forEach { dao.delete(it) }
        dao.insertOrUpdate(LensWorkspace.createDefault())
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        Unit
    }

    private fun seed() {
        clean()
        runBlocking { dao.insertOrUpdate(LensWorkspace(id = secondId, name = "Second", orderIndex = 1)) }
    }

    private fun waitForPage(scenario: ActivityScenario<ActHome>, expected: Int): Int {
        var page = -1
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && page != expected) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { page = it.findViewById<ViewPager2>(R.id.lensPager).currentItem }
            if (page != expected) Thread.sleep(POLL_MS)
        }
        return page
    }

    private fun waitForLensCount(scenario: ActivityScenario<ActHome>, expected: Int) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var count = -1
        while (System.currentTimeMillis() < deadline && count != expected) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { count = it.findViewById<ViewPager2>(R.id.lensPager).adapter?.itemCount ?: -1 }
            if (count != expected) Thread.sleep(POLL_MS)
        }
        assertEquals("the pager never held $expected lenses", expected, count)
    }

    private fun targetIntent(lensId: String?) =
        Intent(context, ActHome::class.java).apply {
            if (lensId != null) putExtra(ActHome.EXTRA_TARGET_LENS_ID, lensId)
        }

    @Test
    fun lockedStartupLensFallsBackBeforeSelectedPageGetsApps() {
        runBlocking { dao.insertOrUpdate(LensWorkspace(id = "private", name = "Private", orderIndex = 1)) }
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, "private")
        com.mckimquyen.util.LensLockStore(context).saveLocked("private", true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals("startup must resolve active locked lens to first unlocked", 0, waitForPage(scenario, 0))
            scenario.onActivity { activity ->
                assertEquals(LensWorkspace.DEFAULT_LENS_ID,
                    UtilSettings(context).getString(UtilSettings.KEY_ACTIVE_LENS_ID))
                val adapter = activity.findViewById<ViewPager2>(R.id.lensPager).adapter as com.mckimquyen.adt.LensPagerAdapter
                assertTrue("locked lens adapter position must not hold app list",
                    adapter.lockedLensIdsForTest().contains("private"))
            }
        }
    }

    @Test
    fun lockedShortcutIntentDoesNotPersistOrReportLockedTarget() {
        runBlocking { dao.insertOrUpdate(LensWorkspace(id = "private", name = "Private", orderIndex = 1)) }
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        com.mckimquyen.util.LensLockStore(context).saveLocked("private", true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 3)
            scenario.onActivity { activity -> activity.handleTargetLensIntent(targetIntent("private")) }
            assertEquals(0, waitForPage(scenario, 0))
            assertEquals(LensWorkspace.DEFAULT_LENS_ID,
                UtilSettings(context).getString(UtilSettings.KEY_ACTIVE_LENS_ID))
        }
    }

    @Test
    fun userCannotSelectLockedPageAndPrefetchedPageGetsNoApps() {
        runBlocking { dao.insertOrUpdate(LensWorkspace(id = "private", name = "Private", orderIndex = 2)) }
        com.mckimquyen.util.LensLockStore(context).saveLocked("private", true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 3)
            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.lensPager)
                pager.setCurrentItem(2, false)
            }
            assertEquals(0, waitForPage(scenario, 0))
            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.lensPager)
                val recycler = pager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView
                val holder = recycler.findViewHolderForAdapterPosition(2) as? com.mckimquyen.adt.LensPagerAdapter.PageHolder
                if (holder != null) assertTrue(holder.lensView.displayedApps.isEmpty())
                assertEquals(LensWorkspace.DEFAULT_LENS_ID,
                    UtilSettings(context).getString(UtilSettings.KEY_ACTIVE_LENS_ID))
            }
        }
    }

    @Test
    fun lockingVisibleLensImmediatelyFallsBackToFirstUnlockedLens() {
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, secondId)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 2)
            assertEquals(1, waitForPage(scenario, 1))
            com.mckimquyen.util.LensLockStore(context).saveLocked(secondId, true)
            scenario.onActivity { it.onLensAccessChanged() }
            assertEquals(0, waitForPage(scenario, 0))
            assertEquals(LensWorkspace.DEFAULT_LENS_ID,
                UtilSettings(context).getString(UtilSettings.KEY_ACTIVE_LENS_ID))
        }
    }

    @Test
    fun coldStartWithATargetLensOpensThatLens() {
        ActivityScenario.launch<ActHome>(targetIntent(secondId)).use { scenario ->
            assertEquals(1, waitForPage(scenario, expected = 1))
        }
    }

    @Test
    fun anUnknownTargetLensIsIgnored() {
        ActivityScenario.launch<ActHome>(targetIntent("does-not-exist")).use { scenario ->
            assertEquals(0, waitForPage(scenario, expected = 0))
        }
    }

    @Test
    fun anEmptyTargetLensIdIsIgnored() {
        ActivityScenario.launch<ActHome>(targetIntent("")).use { scenario ->
            assertEquals(0, waitForPage(scenario, expected = 0))
        }
    }

    @Test
    fun aLaunchWithoutAnyTargetKeepsTheSavedActiveLens() {
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, secondId)
        ActivityScenario.launch<ActHome>(targetIntent(null)).use { scenario ->
            assertEquals(1, waitForPage(scenario, expected = 1))
        }
    }

    @Test
    fun aNewIntentWhileRunningSwitchesLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 2)
            assertEquals(0, waitForPage(scenario, expected = 0))
            scenario.onActivity { it.handleTargetLensIntent(targetIntent(secondId)) }
            assertEquals(1, waitForPage(scenario, expected = 1))
        }
    }

    @Test
    fun theTargetExtraIsConsumedSoARecreateDoesNotReplayIt() {
        val intent = targetIntent(secondId)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.handleTargetLensIntent(intent) }
            assertFalse(intent.hasExtra(ActHome.EXTRA_TARGET_LENS_ID))
        }
    }

    @Test
    fun menuGroupActionUsesCurrentLensAndSingleLensNameOpensManager() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 2)
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.tvLensName).performClick()
                assertTrue(activity.onLensMenuItemSelected(ActHome.MENU_ID_MANAGE_CURRENT_LENS_GROUP, 0))
            }
        }

        runBlocking { dao.delete(LensWorkspace(id = secondId, name = "Second", orderIndex = 1)) }
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 1)
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.tvLensName).performClick()
                assertTrue(activity.onLensMenuItemSelected(ActHome.MENU_ID_MANAGE_CURRENT_LENS_GROUP, 0))
            }
        }
    }

    @Test
    fun lensShortcutsArePublishedForEveryLensThatFits() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 2)
            val deadline = System.currentTimeMillis() + WAIT_MS
            var ids = emptySet<String>()
            while (System.currentTimeMillis() < deadline && ids.size < 2) {
                ids = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.toSet()
                if (ids.size < 2) Thread.sleep(POLL_MS)
            }
            assertTrue(LensShortcuts.shortcutIdFor(secondId) in ids)
            assertTrue(LensShortcuts.shortcutIdFor(LensWorkspace.DEFAULT_LENS_ID) in ids)
        }
    }

    @Test
    fun aPublishedShortcutCarriesTheTargetLensIdToActHome() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 2)
            val deadline = System.currentTimeMillis() + WAIT_MS
            var intent: Intent? = null
            while (System.currentTimeMillis() < deadline && intent == null) {
                intent = ShortcutManagerCompat.getDynamicShortcuts(context)
                    .firstOrNull { it.id == LensShortcuts.shortcutIdFor(secondId) }?.intent
                if (intent == null) Thread.sleep(POLL_MS)
            }
            assertNotNull("the shortcut for the second lens was never published", intent)
            assertEquals(secondId, intent!!.getStringExtra(ActHome.EXTRA_TARGET_LENS_ID))
            assertEquals(ActHome::class.java.name, intent.component?.className)
        }
    }

    @Test
    fun shortcutsFollowTheLensList() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLensCount(scenario, 2)
            runBlocking { dao.delete(LensWorkspace(id = secondId, name = "Second", orderIndex = 1)) }
            scenario.onActivity { it.refreshLensList() }
            waitForLensCount(scenario, 1)
            val deadline = System.currentTimeMillis() + WAIT_MS
            var ids = setOf(LensShortcuts.shortcutIdFor(secondId))
            while (System.currentTimeMillis() < deadline && LensShortcuts.shortcutIdFor(secondId) in ids) {
                ids = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.toSet()
                if (LensShortcuts.shortcutIdFor(secondId) in ids) Thread.sleep(POLL_MS)
            }
            assertFalse("a deleted lens must lose its shortcut", LensShortcuts.shortcutIdFor(secondId) in ids)
        }
    }

    @Test
    fun reportingUsageNeverThrowsForPublishedOrUnknownLenses() {
        // The shortcut manager may ignore an id it does not know; the app must still not crash.
        LensShortcuts.reportUsed(context, secondId)
        LensShortcuts.reportUsed(context, "no-such-lens")
        LensShortcuts.reportUsed(context, "")
    }

    @Test
    fun aShortcutTargetIsReportedAsUsedWithoutBreakingNavigation() {
        ActivityScenario.launch<ActHome>(targetIntent(secondId)).use { scenario ->
            assertEquals(1, waitForPage(scenario, expected = 1))
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
