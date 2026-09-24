package com.mckimquyen.ui

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.adt.FragmentPagerAdapter
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.util.BitmapCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-007 integration tests: real `ActSettings` + `ViewPager2` + `FrmApps` + `AppAdapter`,
 * a real `ActionMode` via `AppCompatActivity.startSupportActionMode`, and real bulk
 * hide/pin persisted through Room and reflected back in `RAppsSingleton` — mirrors
 * FEAT-002's existing persistence integration test pattern
 * ([com.mckimquyen.model.AppPersistentMainThreadTest]).
 */
@RunWith(AndroidJUnit4::class)
class FrmAppsSelectionIntegrationTest {

    @Before
    fun setup() {
        BitmapCache.clear()
    }

    @After
    fun tearDown() {
        BitmapCache.clear()
        RAppsSingleton.instance.clearAllData()
    }

    private fun <T> privateField(target: Any, name: String): T? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as T?
    }

    private fun currentFrmApps(activity: ActSettings): FrmApps? =
        activity.supportFragmentManager.fragments.filterIsInstance<FrmApps>().firstOrNull()

    private fun openAppsTab(scenario: ActivityScenario<ActSettings>, instrumentation: android.app.Instrumentation) {
        scenario.onActivity { activity ->
            val viewPager = activity.findViewById<ViewPager2>(R.id.viewpager)
            viewPager.setCurrentItem(FragmentPagerAdapter.TAB_APPS, false)
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun testLongPress_entersSelectionMode_andStartsActionMode() {
        RAppsSingleton.instance.apps = ArrayList<App>().apply {
            repeat(3) { i -> add(App(packageName = "com.feat007.test.app$i", name = "App $i")) }
        }
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                adapter.toggleSelection(adapter.getItemForPosition(0))

                assertTrue("adapter must enter selection mode", adapter.isSelectionMode)
                assertEquals(1, adapter.selectionCount)
                assertNotNull(
                    "ActionMode must start once the first item is selected",
                    privateField<Any?>(fragment, "actionMode")
                )
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun testClearingSelection_finishesActionMode() {
        RAppsSingleton.instance.apps = ArrayList<App>().apply {
            repeat(2) { i -> add(App(packageName = "com.feat007.test.app$i", name = "App $i")) }
        }
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                adapter.toggleSelection(adapter.getItemForPosition(0))
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                assertNotNull(privateField<Any?>(fragment, "actionMode"))
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                adapter.clearSelection()
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                assertNull(
                    "ActionMode must finish once the selection is empty",
                    privateField<Any?>(fragment, "actionMode")
                )
            }
        } finally {
            scenario.close()
        }
    }

    /**
     * FEAT-007 acceptance: "Selection state is cleared on tab switch ... never silently
     * stale." `FrmApps` is torn down and rebuilt by `FragmentStateAdapter` on every tab
     * switch (see [FrmAppsLifecycleIntegrationTest]) — a fresh `AppAdapter` is created with
     * an empty selection, and any live `ActionMode` is explicitly finished in
     * `onDestroyView()` so it never survives pointing at a torn-down adapter.
     */
    @Test
    fun testTabSwitch_clearsSelectionAndFinishesActionMode() {
        RAppsSingleton.instance.apps = ArrayList<App>().apply {
            repeat(2) { i -> add(App(packageName = "com.feat007.test.app$i", name = "App $i")) }
        }
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                adapter.toggleSelection(adapter.getItemForPosition(0))
                assertTrue(adapter.isSelectionMode)
                assertNotNull(privateField<Any?>(fragment, "actionMode"))
            }
            instrumentation.waitForIdleSync()

            // Switch away, then back — FragmentStateAdapter destroys/recreates FrmApps's view.
            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<ViewPager2>(R.id.viewpager)
                viewPager.setCurrentItem(FragmentPagerAdapter.TAB_SETTINGS, false)
            }
            instrumentation.waitForIdleSync()
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                assertFalse("a fresh AppAdapter must start with no selection", adapter.isSelectionMode)
                assertEquals(0, adapter.selectionCount)
                assertNull(
                    "no ActionMode must survive a tab switch",
                    privateField<Any?>(fragment, "actionMode")
                )
            }
        } finally {
            scenario.close()
        }
    }

    /**
     * Closes a real gap: every other test above drives [AppAdapter] directly
     * (`toggleSelection`/`selectAll`/`bulkSetVisibility`) — none of them prove that tapping
     * an actual `ActionMode` menu item wires through `FrmApps.onActionItemClicked` to the
     * adapter at all. This one goes through the real, inflated menu.
     */
    @Test
    fun testSelectAllMenuItem_realMenuClick_selectsEveryCurrentApp() {
        RAppsSingleton.instance.apps = ArrayList<App>().apply {
            repeat(4) { i -> add(App(packageName = "com.feat007.test.app$i", name = "App $i")) }
        }
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                adapter.toggleSelection(adapter.getItemForPosition(0))
                val mode = privateField<ActionMode>(fragment, "actionMode")!!
                val selectAllItem = mode.menu.findItem(R.id.menuItemBulkSelectAll)!!

                fragment.onActionItemClicked(mode, selectAllItem)

                assertEquals("real 'Select all' tap must select every current app", 4, adapter.selectionCount)
            }
        } finally {
            scenario.close()
        }
    }

    /**
     * Real `onPrepareActionMode` menu-visibility check against a real, inflated menu — not
     * just the boolean expression read from the source.
     */
    @Test
    fun testHideUnhideMenuVisibility_reflectsRealSelectionContentThroughRealMenu() {
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.feat007.test.visible", name = "Visible", isVisible = true),
            App(packageName = "com.feat007.test.hidden", name = "Hidden", isVisible = false)
        )
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!

                // Select only the hidden app first: only "Unhide" should be offered.
                adapter.toggleSelection(adapter.getItemForPosition(1))
                var mode = privateField<ActionMode>(fragment, "actionMode")!!
                fragment.onPrepareActionMode(mode, mode.menu)
                assertFalse("no visible app selected -> Hide must not show", mode.menu.findItem(R.id.menuItemBulkHide).isVisible)
                assertTrue("a hidden app is selected -> Unhide must show", mode.menu.findItem(R.id.menuItemBulkUnhide).isVisible)

                // Now select the visible app too: both actions apply to a mixed selection.
                adapter.toggleSelection(adapter.getItemForPosition(0))
                mode = privateField<ActionMode>(fragment, "actionMode")!!
                fragment.onPrepareActionMode(mode, mode.menu)
                assertTrue("a visible app is now selected too -> Hide must show", mode.menu.findItem(R.id.menuItemBulkHide).isVisible)
                assertTrue("the hidden app is still selected -> Unhide must still show", mode.menu.findItem(R.id.menuItemBulkUnhide).isVisible)
            }
        } finally {
            scenario.close()
        }
    }

    /**
     * Real menu tap for Uninstall: the confirmation dialog this story's own acceptance
     * criteria requires must actually appear with the correct pluralized count, and
     * cancelling it must not touch any real package or crash. Uses only test-fixture
     * package names that are guaranteed not to correspond to a real installed app.
     *
     * No Espresso `onView()`/`check()` here: this project has no working dependency chain
     * for it yet (`SuperWebViewActivityWidgetTest` discloses the same gap and declares
     * fixing it out of scope for its own story). Reads the real, currently-showing
     * `AlertDialog` via `pendingUninstallDialog` instead — the same field-reflection
     * pattern `ActSettingsDialogsIntegrationTest` already established for `ActSettings`'s
     * own dialogs.
     */
    @Test
    fun testUninstallMenuItem_realMenuClick_showsConfirmationDialog_cancelLeavesNoTrace() {
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.feat007.test.uninstallA", name = "A"),
            App(packageName = "com.feat007.test.uninstallB", name = "B")
        )
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                adapter.toggleSelection(adapter.getItemForPosition(0))
                adapter.toggleSelection(adapter.getItemForPosition(1))
                val mode = privateField<ActionMode>(fragment, "actionMode")!!
                val uninstallItem = mode.menu.findItem(R.id.menuItemBulkUninstall)!!

                fragment.onActionItemClicked(mode, uninstallItem)

                // Tapping the menu item finishes the ActionMode/clears selection
                // synchronously (only the dialog's own buttons are async).
                assertNull(
                    "ActionMode must already be finished once the menu item was tapped",
                    privateField<Any?>(fragment, "actionMode")
                )

                val dialog = privateField<AlertDialog>(fragment, "pendingUninstallDialog")
                assertNotNull("the confirmation dialog must actually be built and shown", dialog)
                assertTrue("the dialog must be showing", dialog!!.isShowing)

                val expectedMessage = activity.resources
                    .getQuantityString(R.plurals.bulk_uninstall_confirm_message, 2, 2)
                assertEquals(
                    "the dialog message must name the real selected count (2), pluralized correctly",
                    expectedMessage,
                    dialog.findViewById<android.widget.TextView>(android.R.id.message)?.text?.toString()
                )

                // Cancel — no uninstall intent must ever fire.
                dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val dialog = privateField<AlertDialog>(fragment, "pendingUninstallDialog")!!
                assertFalse("dialog must be dismissed after Cancel", dialog.isShowing)
                assertFalse("Cancelling the dialog must not crash or finish the host Activity", activity.isFinishing)
            }
        } finally {
            scenario.close()
        }
    }

    /**
     * Closes the same real-menu-wiring gap as `testSelectAllMenuItem...` but for Hide:
     * every other Hide test drives `AppAdapter.bulkSetVisibility()` directly.
     */
    @Test
    fun testHideMenuItem_realMenuClick_hidesSelectedApps() {
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.feat007.test.hideviamenu.a", name = "A"),
            App(packageName = "com.feat007.test.hideviamenu.b", name = "B")
        )
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                adapter.toggleSelection(adapter.getItemForPosition(0))
                adapter.toggleSelection(adapter.getItemForPosition(1))
                val mode = privateField<ActionMode>(fragment, "actionMode")!!
                val hideItem = mode.menu.findItem(R.id.menuItemBulkHide)!!

                fragment.onActionItemClicked(mode, hideItem)

                assertFalse(adapter.getItemForPosition(0).isVisible)
                assertFalse(adapter.getItemForPosition(1).isVisible)
                assertNull(
                    "a real Hide tap must finish the ActionMode like every other bulk action",
                    privateField<Any?>(fragment, "actionMode")
                )
            }
        } finally {
            scenario.close()
        }
    }

    /** Same real-menu-wiring gap as Hide/Select-all, for Pin. */
    @Test
    fun testPinMenuItem_realMenuClick_pinsSelectedApps() {
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.feat007.test.pinviamenu.a", name = "A"),
            App(packageName = "com.feat007.test.pinviamenu.b", name = "B")
        )
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                adapter.toggleSelection(adapter.getItemForPosition(0))
                adapter.toggleSelection(adapter.getItemForPosition(1))
                val mode = privateField<ActionMode>(fragment, "actionMode")!!
                val pinItem = mode.menu.findItem(R.id.menuItemBulkPin)!!

                fragment.onActionItemClicked(mode, pinItem)

                assertEquals(PinnedZone.START, adapter.getItemForPosition(0).pinnedZone)
                assertEquals(PinnedZone.START, adapter.getItemForPosition(1).pinnedZone)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun testBulkHide_persistsThroughRoom_andReflectsInRAppsSingleton() {
        val packageName = "com.feat007.test.bulkhide"
        val componentName = "MainActivity"
        val identifier = AppPersistent.generateIdentifier(packageName, componentName)
        RAppsSingleton.instance.apps = arrayListOf(App(packageName = packageName, name = componentName, label = "Bulk Hide Target"))
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                val target = adapter.getItemForPosition(0)
                assertTrue(target.isVisible)
                adapter.toggleSelection(target)
                adapter.bulkSetVisibility(false)
            }

            runBlocking {
                val dao = AppDatabase.getInstance().appPersistentDao()
                val stored = withTimeout(5_000) {
                    var current: AppPersistent?
                    do {
                        current = dao.findByIdentifier(identifier)
                        if (current?.appVisible != false) delay(10)
                    } while (current?.appVisible != false)
                    current
                }!!
                assertFalse("Room must persist the bulk-hide", stored.appVisible)
                dao.delete(stored)
            }

            assertFalse(
                "RAppsSingleton must reflect the bulk-hide (AppPersistent.setAppVisibility updates it synchronously)",
                RAppsSingleton.instance.findApp(packageName, componentName)?.isVisible ?: true
            )
        } finally {
            scenario.close()
        }
    }

    @Test
    fun testBulkPin_persistsThroughRoom_andReflectsInRAppsSingleton() {
        val packageName = "com.feat007.test.bulkpin"
        val componentName = "MainActivity"
        val identifier = AppPersistent.generateIdentifier(packageName, componentName)
        RAppsSingleton.instance.apps = arrayListOf(App(packageName = packageName, name = componentName, label = "Bulk Pin Target"))
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            openAppsTab(scenario, instrumentation)

            scenario.onActivity { activity ->
                val fragment = currentFrmApps(activity)!!
                val adapter = privateField<AppAdapter>(fragment, "appAdapter")!!
                val target = adapter.getItemForPosition(0)
                adapter.toggleSelection(target)
                adapter.bulkPin(PinnedZone.START)
            }

            runBlocking {
                val dao = AppDatabase.getInstance().appPersistentDao()
                val stored = withTimeout(5_000) {
                    var current: AppPersistent?
                    do {
                        current = dao.findByIdentifier(identifier)
                        if (current?.pinnedZone != PinnedZone.START.name) delay(10)
                    } while (current?.pinnedZone != PinnedZone.START.name)
                    current
                }!!
                assertEquals(PinnedZone.START.name, stored.pinnedZone)
                dao.delete(stored)
            }

            assertEquals(
                PinnedZone.START,
                RAppsSingleton.instance.findApp(packageName, componentName)?.pinnedZone
            )
        } finally {
            scenario.close()
        }
    }
}
