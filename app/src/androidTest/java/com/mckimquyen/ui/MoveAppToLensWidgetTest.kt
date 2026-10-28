package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-038 on a real device: move an app from the lens grid's own menu to another lens, then undo. */
@RunWith(AndroidJUnit4::class)
class MoveAppToLensWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val WAIT_MS = 6_000L
        const val POLL_MS = 50L
        const val WORK_LENS = "fish038-work"
        const val WORK_NAME = "Work038"
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private val homeLens = LensWorkspace.DEFAULT_LENS_ID
    private val mail = App(label = "Move Mail", packageName = "com.test.move.mail", name = "Main", isVisible = true)
    private val chat = App(label = "Move Chat", packageName = "com.test.move.chat", name = "Main", isVisible = true)
    private var originalApps: ArrayList<App>? = null
    private var originalPreferences: Map<String, *> = emptyMap<String, Any>()
    private var originalDisableScan = false

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalDisableScan = RApplication.sDisableAutoAppRefresh
        RApplication.sDisableAutoAppRefresh = true
        originalPreferences = HashMap(prefs().all)
        originalApps = RAppsSingleton.instance.apps
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, homeLens)
        settings.setLauncherMode(LauncherMode.FISHEYE)
        settings.saveLensAppScope(homeLens, LensAppScope.SELECTED)
        settings.saveLensAppSelection(homeLens, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        settings.saveLensHiddenAppIds(homeLens, emptySet())
        settings.saveLensAppScope(WORK_LENS, LensAppScope.SELECTED)
        settings.saveLensAppSelection(WORK_LENS, setOf(LensAppScope.identifierOf(chat)))
        RAppsSingleton.instance.apps = arrayListOf(mail, chat)
    }

    @After
    fun cleanup() {
        runBlocking { AppDatabase.getInstance().lensWorkspaceDao().findById(WORK_LENS)?.let { AppDatabase.getInstance().lensWorkspaceDao().delete(it) } }
        val editor = prefs().edit().clear()
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
        RApplication.sDisableAutoAppRefresh = originalDisableScan
        RAppsSingleton.instance.apps = originalApps
    }

    private fun addWorkLens() = runBlocking {
        val dao = AppDatabase.getInstance().lensWorkspaceDao()
        val order = (dao.getAll().maxOfOrNull { it.orderIndex } ?: -1) + 1
        dao.insertOrUpdate(LensWorkspace(id = WORK_LENS, name = WORK_NAME, orderIndex = order))
    }

    private fun awaitGrid(scenario: ActivityScenario<ActHome>, expectedLens: String) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity { ready = it.lensViews?.lensId == expectedLens && it.lensViews?.appsForTest?.isNotEmpty() == true }
            if (ready) return
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("grid for $expectedLens never became ready")
    }

    private fun clickUndo(scenario: ActivityScenario<ActHome>) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var clicked = false
        while (!clicked && System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val action = activity.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_action)
                if (action != null && action.isShown && action.text == activity.getString(R.string.reset_undo)) {
                    action.performClick()
                    clicked = true
                }
            }
            if (!clicked) Thread.sleep(POLL_MS)
        }
        assertTrue("the Undo action must appear in the Snackbar", clicked)
    }

    @Test
    fun movingFromTheGridMenuMovesTheAppAndUndoRestoresBothLenses() {
        addWorkLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                val grid = requireNotNull(activity.lensViews)
                grid.moveAppToLensForTest(grid.appsForTest.orEmpty().first { it.packageName == mail.packageName }, WORK_LENS, WORK_NAME)
            }
            instrumentation.waitForIdleSync()

            assertEquals(setOf(LensAppScope.identifierOf(chat)), settings.getLensAppSelection(homeLens))
            assertEquals(
                setOf(LensAppScope.identifierOf(chat), LensAppScope.identifierOf(mail)),
                settings.getLensAppSelection(WORK_LENS)
            )

            clickUndo(scenario)
            instrumentation.waitForIdleSync()

            assertEquals(setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)), settings.getLensAppSelection(homeLens))
            assertEquals(setOf(LensAppScope.identifierOf(chat)), settings.getLensAppSelection(WORK_LENS))
        }
    }

    @Test
    fun choosingMoveInTheRealMenuAndTheChooserMovesTheApp() {
        addWorkLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                val grid = requireNotNull(activity.lensViews)
                assertTrue(grid.showAppOptionsAtIndex(0))
                val menu = requireNotNull(grid.quickActionsMenuForTest).menu
                menu.performIdentifierAction(R.id.menuItemMoveToLens, 0)
                grid.quickActionsMenuForTest?.dismiss()
            }
            val deadline = System.currentTimeMillis() + WAIT_MS
            var chosen = false
            while (!chosen && System.currentTimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val list = findChooserList(activity)
                    if (list != null && list.count == 1) {
                        list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                        chosen = true
                    }
                }
                if (!chosen) Thread.sleep(POLL_MS)
            }
            assertTrue("the lens chooser must list exactly the one other lens", chosen)
            instrumentation.waitForIdleSync()

            val all = setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat))
            val home = settings.getLensAppSelection(homeLens)
            val work = settings.getLensAppSelection(WORK_LENS)
            assertEquals("exactly one app left the home lens", 1, all.minus(home).size)
            assertTrue("the moved app landed in the work lens", all.minus(home).single() in work)
            assertTrue("the work lens keeps its own app", LensAppScope.identifierOf(chat) in work)
        }
    }

    /** Starts a drag exactly as a hold-then-move would: through the view's own drag seam. */
    private fun startDragOf(grid: com.mckimquyen.views.LensView, packageName: String) {
        val app = grid.appsForTest.orEmpty().first { it.packageName == packageName }
        grid.onAppDragStartListener?.onDragStarted(com.mckimquyen.views.LensDragPayload(grid, app, grid.lensId))
    }

    private fun dropStripChips(activity: ActHome): List<android.widget.TextView> {
        val out = ArrayList<android.widget.TextView>()
        fun walk(v: android.view.View) {
            if (v is android.widget.TextView && v.tag is String) out.add(v)
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(activity.findViewById(R.id.rootLayout))
        return out
    }

    @Test
    fun draggingOntoAChipMovesTheAppAndTheSameUndoRestoresBothLenses() {
        addWorkLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                startDragOf(requireNotNull(activity.lensViews), mail.packageName.toString())
                val chips = dropStripChips(activity)
                assertEquals("only the other lens is offered", listOf(WORK_LENS), chips.map { it.tag })
                assertTrue(activity.dropStripDropForTest(chips.single(), activity.lensViews!!, mail.packageName.toString()))
            }
            instrumentation.waitForIdleSync()
            assertEquals(setOf(LensAppScope.identifierOf(chat)), settings.getLensAppSelection(homeLens))
            assertEquals(setOf(LensAppScope.identifierOf(chat), LensAppScope.identifierOf(mail)), settings.getLensAppSelection(WORK_LENS))

            clickUndo(scenario)
            instrumentation.waitForIdleSync()
            assertEquals(setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)), settings.getLensAppSelection(homeLens))
            assertEquals(setOf(LensAppScope.identifierOf(chat)), settings.getLensAppSelection(WORK_LENS))
        }
    }

    @Test
    fun aDragWithNoDropLeavesBothLensesUntouchedAndHidesTheStrip() {
        addWorkLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                startDragOf(requireNotNull(activity.lensViews), mail.packageName.toString())
                assertEquals(1, dropStripChips(activity).size)
                activity.dropStripEndForTest(dropStripChips(activity).single())
                assertTrue("the strip must be gone after the drag ends", dropStripChips(activity).isEmpty())
            }
            instrumentation.waitForIdleSync()
            assertEquals(setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)), settings.getLensAppSelection(homeLens))
            assertEquals(setOf(LensAppScope.identifierOf(chat)), settings.getLensAppSelection(WORK_LENS))
        }
    }

    @Test
    fun theViewPagerStaysOnTheSamePageWhileADragIsShowing() {
        addWorkLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager)
                val before = pager.currentItem
                startDragOf(requireNotNull(activity.lensViews), mail.packageName.toString())
                assertEquals("showing the strip must not page the pager", before, pager.currentItem)
            }
        }
    }

    private fun findChooserList(activity: ActHome): android.widget.ListView? {
        val title = activity.getString(R.string.move_to_lens_title)
        val root = activity.window.decorView.rootView
        // The dialog lives in its own window; look through the global view roots for its list.
        val field = Class.forName("android.view.WindowManagerGlobal").getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
        val views = field.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(field) as List<*>
        for (v in views) {
            val view = v as? android.view.View ?: continue
            if (view === root) continue
            val list = view.findViewById<android.widget.ListView>(androidx.appcompat.R.id.select_dialog_listview)
            val titleView = view.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)
            if (list != null && titleView?.text?.toString() == title) return list
        }
        return null
    }

    @Test
    fun theMenuOffersMoveOnlyWhenAnotherLensExists() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                val grid = requireNotNull(activity.lensViews)
                assertTrue(grid.showAppOptionsAtIndex(0))
                val item = requireNotNull(grid.quickActionsMenuForTest).menu.findItem(R.id.menuItemMoveToLens)
                assertFalse("a single lens has nowhere to move to", item.isVisible)
                grid.quickActionsMenuForTest?.dismiss()
            }
        }
    }

    @Test
    fun theMenuOffersMoveWithTwoLensesAndTheChooserSkipsTheCurrentOne() {
        addWorkLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                val grid = requireNotNull(activity.lensViews)
                assertTrue(grid.showAppOptionsAtIndex(0))
                val item = requireNotNull(grid.quickActionsMenuForTest).menu.findItem(R.id.menuItemMoveToLens)
                assertTrue(item.isVisible)
                grid.quickActionsMenuForTest?.dismiss()
                val lenses = listOf(LensWorkspace(homeLens, "Home", 0), LensWorkspace(WORK_LENS, WORK_NAME, 1))
                assertEquals(listOf(WORK_LENS), grid.moveTargetsForTest(lenses).map { it.id })
            }
        }
    }

    @Test
    fun talkBackOffersTheMoveActionOnlyWithTwoLenses() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                val helper = requireNotNull(requireNotNull(activity.lensViews).getAccessibilityHelper())
                val node = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.obtain()
                helper.testPopulateNodeForVirtualView(0, node)
                assertFalse("one lens: no move action", node.actionList.any { it.id == R.id.menuItemMoveToLens })
            }
        }
        addWorkLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitGrid(scenario, homeLens)
            scenario.onActivity { activity ->
                val helper = requireNotNull(requireNotNull(activity.lensViews).getAccessibilityHelper())
                val node = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.obtain()
                helper.testPopulateNodeForVirtualView(0, node)
                assertTrue("two lenses: TalkBack must offer Move to lens", node.actionList.any { it.id == R.id.menuItemMoveToLens })
            }
        }
    }
}
