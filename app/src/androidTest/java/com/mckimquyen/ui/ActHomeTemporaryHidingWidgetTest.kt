package com.mckimquyen.ui

import android.content.DialogInterface
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeTemporaryHidingWidgetTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private val mail = App(label = "Hidden Mail", packageName = "com.test.hidden.mail", name = "Main", isVisible = true)
    private val chat = App(label = "Hidden Chat", packageName = "com.test.hidden.chat", name = "Main", isVisible = true)
    private var originalApps: ArrayList<App>? = null
    private var originalPreferences: Map<String, *> = emptyMap<String, Any>()

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        originalPreferences = HashMap(prefs.all)
        originalApps = RAppsSingleton.instance.apps
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        settings.setLauncherMode(LauncherMode.FISHEYE)
        settings.saveLensAppScope(lensId, LensAppScope.ALL)
        settings.saveLensAppSelection(lensId, emptySet())
        settings.saveLensHiddenAppIds(lensId, emptySet())
        RAppsSingleton.instance.apps = arrayListOf(mail, chat)
    }

    @After
    fun cleanup() {
        val editor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
        originalPreferences.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun resourceId(name: String): Int = context.resources.getIdentifier(name, "id", context.packageName)

    /**
     * Đổi lens chạy switchLens(), merge snapshot PackageManager thật và có thể ghi đè fixture giả
     * (54 app thật trên BG6). Reseed theo mẫu ActHomeLensScopeWidgetTest để test chỉ kiểm lọc ẩn.
     */
    private fun awaitGrid(scenario: ActivityScenario<ActHome>, count: Int, fixture: List<App> = listOf(mail, chat)) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var actual = -1
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val current = RAppsSingleton.instance.apps.orEmpty().map { it.packageName.toString() }.toSet()
            if (current != fixture.map { it.packageName.toString() }.toSet()) {
                RAppsSingleton.instance.apps = ArrayList(fixture)
                com.mckimquyen.services.AppEventManager.notifyAppsUpdated()
            }
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager)
                val recycler = pager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView
                val holder = recycler.findViewHolderForAdapterPosition(pager.currentItem) as? com.mckimquyen.adt.LensPagerAdapter.PageHolder
                actual = holder?.lensView?.appsForTest?.size ?: -1
            }
            if (actual == count) return
            Thread.sleep(POLL_MS)
        }
        var state = ""
        scenario.onActivity { activity ->
            val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager)
            state = "page=${pager.currentItem}, lens=${activity.lensViews?.lensId}, hidden=${settings.getLensHiddenAppIds(lensId)}, active=${settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)}"
        }
        assertEquals("Số app trên grid không cập nhật: $state", count, actual)
    }

    @Test
    fun gridMenuHidesOnlyCurrentLensAndRestoreCancelKeepsDraft() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, 2)
            scenario.onActivity { activity ->
                val grid = requireNotNull(activity.lensViews)
                assertTrue(grid.showAppOptionsAtIndex(0))
                val menu = requireNotNull(grid.quickActionsMenuForTest).menu
                val hideId = resourceId("menuItemHideFromLens")
                assertNotNull("Menu grid phải có action tạm ẩn", menu.findItem(hideId))
                menu.performIdentifierAction(hideId, 0)
                grid.quickActionsMenuForTest?.dismiss()
            }
            awaitGrid(scenario, 1)
            val hidden = settings.getLensHiddenAppIds(lensId)
            assertEquals(1, hidden.size)
            assertEquals(LensAppScope.ALL, settings.getLensAppScope(lensId))
            assertEquals(2, RAppsSingleton.instance.apps?.size)
            assertTrue(RAppsSingleton.instance.apps.orEmpty().all { it.isVisible })
            scenario.onActivity { activity ->
                assertTrue(activity.onLensMenuItemSelected(ActHome.MENU_ID_HIDDEN_LENS_APPS, 0))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.lensDialog)
                val list = dialog.listView
                assertEquals(1, list.count)
                list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            }
            assertEquals(hidden, settings.getLensHiddenAppIds(lensId))
        }
    }

    @Test
    fun recoveryNeverOffersOnlyOrphanOrGloballyHiddenApps() {
        settings.saveLensAppScope(lensId, LensAppScope.SELECTED)
        settings.saveLensAppSelection(lensId, setOf(ORPHAN))
        settings.saveLensHiddenAppIds(lensId, setOf(ORPHAN))
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, 0)
            scenario.onActivity { activity ->
                assertEquals("Orphan không tạo CTA không thể khôi phục", View.GONE,
                    activity.findViewById<View>(resourceId("restoreHiddenLensApps")).visibility)
            }
        }
    }

    @Test
    fun talkBackHideActionUsesSamePerLensState() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, 2)
            scenario.onActivity { activity ->
                val grid = requireNotNull(activity.lensViews)
                val helper = requireNotNull(grid.getAccessibilityHelper())
                val node = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.obtain()
                helper.testPopulateNodeForVirtualView(0, node)
                val actionId = resourceId("menuItemHideFromLens")
                assertTrue("TalkBack phải có action ẩn riêng theo lens", node.actionList.any { it.id == actionId })
                assertTrue(helper.testPerformActionForVirtualView(0, actionId, null))
            }
            awaitGrid(scenario, 1)
            assertEquals(1, settings.getLensHiddenAppIds(lensId).size)
        }
    }

    @Test
    fun hiddenLastAppHasRecoveryInCleanModeAndSurvivesRecreate() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        settings.saveLensHiddenAppIds(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat), ORPHAN))
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, 0)
            scenario.recreate()
            awaitGrid(scenario, 0)
            scenario.onActivity { activity ->
                val restoreId = resourceId("restoreHiddenLensApps")
                val restore = activity.findViewById<View>(restoreId)
                assertNotNull("Ẩn hết app vẫn phải có đường khôi phục", restore)
                assertEquals(View.VISIBLE, restore.visibility)
                assertTrue(restore.performClick())
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.lensDialog)
                val list = dialog.listView
                assertEquals(2, list.count)
                for (index in 0 until list.count) {
                    list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
                }
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            awaitGrid(scenario, 2)
            assertEquals(setOf(ORPHAN), settings.getLensHiddenAppIds(lensId))
        }
    }

    @Test
    fun restoringScrolledRowsKeepsUntouchedHiddenIds() {
        val apps = (0 until LONG_LIST_SIZE).map { index ->
            App(label = "Hidden $index", packageName = "com.test.hidden.list$index", name = "Main", isVisible = true)
        }
        RAppsSingleton.instance.apps = ArrayList(apps)
        val hidden = apps.map(LensAppScope::identifierOf).toSet()
        settings.saveLensHiddenAppIds(lensId, hidden)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, 0, apps)
            scenario.onActivity { it.onLensMenuItemSelected(ActHome.MENU_ID_HIDDEN_LENS_APPS, 0) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = requireNotNull(activity.lensDialog).listView
                assertEquals(LONG_LIST_SIZE, list.count)
                list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                list.setSelection(LONG_LIST_SIZE - 1)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.lensDialog)
                val list = dialog.listView
                val index = LONG_LIST_SIZE - 1
                val row = list.getChildAt(index - list.firstVisiblePosition)
                assertNotNull("Dòng cuối phải được bind sau cuộn", row)
                list.performItemClick(row, index, list.adapter.getItemId(index))
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            awaitGrid(scenario, 2, apps)
            assertEquals(hidden - setOf(LensAppScope.identifierOf(apps.first()), LensAppScope.identifierOf(apps.last())),
                settings.getLensHiddenAppIds(lensId))
        }
    }

    @Test
    fun pagingKeepsHiddenIdsAndRecoveryOnTheirOwnLens() {
        val dao = com.mckimquyen.model.AppDatabase.getInstance().lensWorkspaceDao()
        val lensesBefore = kotlinx.coroutines.runBlocking { dao.getAll() }
        val targetId = "temporary-hidden-test-lens"
        try {
            kotlinx.coroutines.runBlocking {
                dao.insertIfAbsent(LensWorkspace(targetId, "Other lens", lensesBefore.size))
            }
            settings.saveLensHiddenAppIds(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
            settings.saveLensHiddenAppIds(targetId, emptySet())
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                awaitGrid(scenario, 0)
                scenario.onActivity { activity ->
                    val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager)
                    pager.setCurrentItem(lensesBefore.size, false)
                }
                awaitGrid(scenario, 2)
                scenario.onActivity { activity ->
                    assertEquals(View.GONE, activity.findViewById<View>(resourceId("restoreHiddenLensApps")).visibility)
                    activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager).setCurrentItem(0, false)
                }
                awaitGrid(scenario, 0)
                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, activity.findViewById<View>(resourceId("restoreHiddenLensApps")).visibility)
                }
            }
        } finally {
            kotlinx.coroutines.runBlocking {
                dao.findById(targetId)?.let { dao.delete(it) }
                com.mckimquyen.model.AppDatabase.getInstance().appPersistentDao().deleteForLens(targetId)
            }
            settings.deleteLensSettings(targetId)
        }
    }

    @Test
    fun listModeNeverUsesTemporaryGridHiding() {
        settings.saveLensHiddenAppIds(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        settings.setLauncherMode(LauncherMode.LIST)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val deadline = System.currentTimeMillis() + WAIT_MS
            var count = -1
            while (System.currentTimeMillis() < deadline && count != 2) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    count = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvHomeAppList).adapter?.itemCount ?: -1
                }
                if (count != 2) Thread.sleep(POLL_MS)
            }
            assertEquals(2, count)
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(resourceId("restoreHiddenLensApps")).visibility)
            }
        }
    }

    @Test
    fun hiddenIdsSurviveMembershipChangesAndRestoreDoesNotAddMembership() {
        val mailId = LensAppScope.identifierOf(mail)
        val chatId = LensAppScope.identifierOf(chat)
        settings.saveLensAppScope(lensId, LensAppScope.SELECTED)
        settings.saveLensAppSelection(lensId, setOf(chatId))
        settings.saveLensHiddenAppIds(lensId, setOf(mailId))
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, 1)
            settings.saveLensAppSelection(lensId, setOf(mailId, chatId))
            scenario.onActivity { it.refreshLensScope() }
            awaitGrid(scenario, 1)
            settings.saveLensAppSelection(lensId, setOf(chatId))
            scenario.onActivity { activity ->
                activity.refreshLensScope()
                activity.onLensMenuItemSelected(ActHome.MENU_ID_HIDDEN_LENS_APPS, 0)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.lensDialog)
                val list = dialog.listView
                assertEquals(1, list.count)
                list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            awaitGrid(scenario, 1)
            assertEquals(setOf(chatId), settings.getLensAppSelection(lensId))
            assertTrue(settings.getLensHiddenAppIds(lensId).isEmpty())
        }
    }

    @Test
    fun hiddenStateDoesNotChangeSelectedMembershipAndNeverFiltersSearch() {
        settings.saveLensAppScope(lensId, LensAppScope.SELECTED)
        val selection = setOf(LensAppScope.identifierOf(mail))
        settings.saveLensAppSelection(lensId, selection)
        settings.saveLensHiddenAppIds(lensId, selection)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, 0)
            scenario.onActivity { activity ->
                activity.openSearchFromHome()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.search.SearchView>(R.id.searchView).editText.setText("Hidden Mail")
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val results = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSearchResults)
                assertEquals(1, results.adapter?.itemCount)
                assertEquals(View.GONE, activity.findViewById<View>(resourceId("restoreHiddenLensApps")).visibility)
            }
            assertEquals(selection, settings.getLensAppSelection(lensId))
        }
    }

    private companion object {
        const val LONG_LIST_SIZE = 60
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
        const val ORPHAN = "com.missing-Main"
    }
}
