package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.appcompat.app.AlertDialog
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LocaleHelper
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensManagementActivityWidgetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        LocaleHelper.setLocale(context, "en")
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .resources.configuration.setLocale(java.util.Locale.ENGLISH)
        dao.deleteAll()
        dao.insertAll(
            listOf(
                LensWorkspace.createDefault().copy(groupName = "Personal"),
                LensWorkspace("work", "Work", 1, 20L, "Work"),
                LensWorkspace("travel", "Travel", 2, 30L, "work"),
                LensWorkspace("ungrouped", "Ungrouped", 3, 40L)
            )
        )
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    @After
    fun tearDown() = runBlocking {
        dao.deleteAll()
        dao.insertIfAbsent(LensWorkspace.createDefault())
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        UtilSettings(context).saveCollapsedLensGroups(emptySet())
    }

    @Test
    fun disabledDeleteIconKeepsSameTintAsEnabledMenuIcons() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                    .onEmptySpaceLongPressListener!!.onEmptySpaceLongPress(0f, 0f)
                val menu = requireNotNull(activity.lensManagementMenu).menu
                val addIcon = requireNotNull(menu.findItem(1).icon)
                val deleteIcon = requireNotNull(menu.findItem(3).icon)
                assertEquals(addIcon.alpha, deleteIcon.alpha)
            }
        }
    }

    @Test
    fun managerSetsContrastingStatusAndNavigationSystemBars() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val surface = com.google.android.material.color.MaterialColors.getColor(
                    activity, com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE
                )
                val expectedLightIcons = android.graphics.Color.luminance(surface) > 0.5f
                val root = activity.findViewById<View>(R.id.lensManagementRoot)
                assertNotNull("Root surface must draw behind edge-to-edge bars", root)
                val bars = androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                assertEquals(expectedLightIcons, bars.isAppearanceLightStatusBars)
                assertEquals(expectedLightIcons, bars.isAppearanceLightNavigationBars)
            }
        }
    }

    @Test
    fun headerChevronIsAVectorThatRotatesWithExpandState() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val header = requireNotNull(list.findViewHolderForAdapterPosition(4)).itemView
                val chevron = header.findViewById<View>(R.id.tvLensGroupExpanded)
                assertTrue("Chevron must be an ImageView, not a text glyph", chevron is android.widget.ImageView)
                assertEquals("Expanded chevron points down (no rotation)", 0f, chevron.rotation, 0.01f)
                assertTrue("Group header needs a leading folder icon", header.findViewById<View>(R.id.ivLensGroupIcon) is android.widget.ImageView)
                header.performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val chevron = requireNotNull(list.findViewHolderForAdapterPosition(4)).itemView
                    .findViewById<View>(R.id.tvLensGroupExpanded)
                assertEquals("Collapsed chevron is rotated", -90f, chevron.rotation, 0.01f)
            }
        }
    }

    @Test
    fun lensRowsShowALensIconAndActiveRowShowsCheckInsideItsChip() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val row = requireNotNull(list.findViewHolderForAdapterPosition(3)).itemView
                assertEquals("Lens 1", row.findViewById<TextView>(R.id.tvLensGroupRow).text.toString())
                assertTrue("Lens row needs a leading lens icon", row.findViewById<View>(R.id.ivLensGroupRowIcon) is android.widget.ImageView)
                val chip = row.findViewById<TextView>(R.id.tvLensGroupActive)
                assertEquals(View.VISIBLE, chip.visibility)
                assertTrue("Active chip must carry a check icon", chip.compoundDrawablesRelative[0] != null)
                val other = requireNotNull(list.findViewHolderForAdapterPosition(1)).itemView
                assertEquals(View.GONE, other.findViewById<View>(R.id.tvLensGroupActive).visibility)
            }
        }
    }

    @Test
    fun managerBarsFollowSurfaceLuminanceInNightMode() {
        // ActBase applies UtilSettings.nightMode in onCreate, so drive the app's own preference.
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val original = if (prefs.contains(UtilSettings.KEY_NIGHT_MODE)) prefs.getInt(UtilSettings.KEY_NIGHT_MODE, 0) else null
        try {
            prefs.edit().putInt(
                UtilSettings.KEY_NIGHT_MODE, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            ).commit()
            ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val surface = com.google.android.material.color.MaterialColors.getColor(
                        activity, com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE
                    )
                    assertTrue("Night surface must be dark", android.graphics.Color.luminance(surface) < 0.5f)
                    val bars = androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    assertEquals("Dark surface needs light-on-dark status icons", false, bars.isAppearanceLightStatusBars)
                    assertEquals(false, bars.isAppearanceLightNavigationBars)
                }
            }
        } finally {
            prefs.edit().apply {
                if (original == null) remove(UtilSettings.KEY_NIGHT_MODE) else putInt(UtilSettings.KEY_NIGHT_MODE, original)
            }.commit()
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }
    }

    @Test
    fun assigningToAnExistingGroupReusesItsDisplaySpellingCaseInsensitively() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit("ungrouped", true)
                activity.findViewById<View>(R.id.btLensGroupAssign).performClick()
            }
            awaitLensGroupDialog(scenario)
            scenario.onActivity { activity -> clickDialogChoice(activity.lensGroupDialogForTest!!, activity.getString(R.string.lens_group_create)) }
            awaitLensGroupDialog(scenario)
            scenario.onActivity { activity ->
                val dialog = activity.lensGroupDialogForTest!!
                dialog.findViewById<android.widget.EditText>(android.R.id.edit)!!.setText("  WORK  ")
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
            }
            awaitGroupName(scenario, "ungrouped", "Work")
        }
    }

    @Test
    fun blankGroupNameIsRejectedWithoutWritingAndKeepsDialogOpen() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit("ungrouped", true)
                activity.findViewById<View>(R.id.btLensGroupAssign).performClick()
            }
            awaitLensGroupDialog(scenario)
            scenario.onActivity { activity -> clickDialogChoice(activity.lensGroupDialogForTest!!, activity.getString(R.string.lens_group_create)) }
            awaitLensGroupDialog(scenario)
            scenario.onActivity { activity ->
                val dialog = activity.lensGroupDialogForTest!!
                dialog.findViewById<android.widget.EditText>(android.R.id.edit)!!.setText("   ")
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                assertTrue("Dialog must stay open for correction", dialog.isShowing)
                assertEquals(
                    activity.getString(R.string.lens_group_error_blank),
                    dialog.findViewById<android.widget.EditText>(android.R.id.edit)!!.error.toString()
                )
            }
            awaitGroupName(scenario, "ungrouped", null)
        }
    }

    @Test
    fun browseToolbarHasCompactTitleAndSingleEditAction() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.tbLensManagement)
                assertEquals(activity.getString(R.string.lens_management_title), toolbar.title.toString())
                assertTrue(toolbar.navigationIcon != null)
                assertTrue(toolbar.menu.findItem(R.id.menuLensGroupEditToggle).isVisible)
                assertTrue(!toolbar.menu.findItem(R.id.menuLensGroupEditDone).isVisible)
                assertEquals(
                    activity.getString(R.string.lens_group_edit_action),
                    toolbar.menu.getItem(0).title.toString()
                )
            }
        }
    }

    @Test
    fun groupActionsAppearOnlyDuringEditModeAndAfterSelection() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val actionBar = activity.findViewById<View>(R.id.lensGroupActions)
                val browseToolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.tbLensManagement)
                assertEquals(activity.getString(R.string.lens_management_title), browseToolbar.title.toString())
                assertEquals(View.GONE, actionBar.visibility)

                activity.toggleEditModeForTest()
                assertEquals(View.VISIBLE, actionBar.visibility)
                assertEquals(activity.getString(R.string.lens_group_selected_count, 0), browseToolbar.title.toString())
                assertEquals(activity.getString(R.string.done), browseToolbar.menu.findItem(R.id.menuLensGroupEditDone).title.toString())
                assertEquals(2, browseToolbar.menu.size())
                val assign = activity.findViewById<View>(R.id.btLensGroupAssign)
                val ungroup = activity.findViewById<View>(R.id.btLensGroupUngroup)
                assertTrue("Assign starts disabled without selection", !assign.isEnabled)

                activity.selectLensForGroupEdit(LensWorkspace.DEFAULT_LENS_ID, true)
                assertEquals(activity.getString(R.string.lens_group_selected_count, 1), browseToolbar.title.toString())
                assertTrue(assign.isEnabled)
                assertTrue(ungroup.isEnabled)
                activity.toggleEditModeForTest()
                assertEquals(View.GONE, actionBar.visibility)
                assertEquals(activity.getString(R.string.lens_management_title), browseToolbar.title.toString())
                assertEquals(activity.getString(R.string.lens_group_edit_action), browseToolbar.menu.findItem(R.id.menuLensGroupEditToggle).title.toString())
                assertEquals(2, browseToolbar.menu.size())
                assertTrue(browseToolbar.menu.findItem(R.id.menuLensGroupEditToggle).isVisible)
                assertTrue(!browseToolbar.menu.findItem(R.id.menuLensGroupEditDone).isVisible)
            }
        }
    }

    @Test
    fun quickGroupEntryStartsEditingWithCurrentLensSelected() {
        val intent = android.content.Intent(context, LensManagementActivity::class.java)
            .putExtra(LensManagementActivity.EXTRA_MANAGE_LENS_ID, "work")
        ActivityScenario.launch<LensManagementActivity>(intent).use { scenario ->
            awaitActivity(scenario, "valid manage id must start editing with it selected") {
                it.editingGroupsForTest && it.selectedLensIdsForTest == setOf("work")
            }
            scenario.onActivity { activity ->
                assertTrue(activity.editingGroupsForTest)
                assertEquals(setOf("work"), activity.selectedLensIdsForTest)
                assertTrue(activity.findViewById<View>(R.id.btLensGroupAssign).isEnabled)
                assertTrue(activity.findViewById<View>(R.id.btLensGroupUngroup).isEnabled)
            }
        }
    }

    @Test
    fun unknownOrBlankManageLensIdFallsBackToBrowseWithoutSelection() {
        listOf("does-not-exist", "").forEach { badId ->
            val intent = android.content.Intent(context, LensManagementActivity::class.java)
                .putExtra(LensManagementActivity.EXTRA_MANAGE_LENS_ID, badId)
            ActivityScenario.launch<LensManagementActivity>(intent).use { scenario ->
                awaitActivity(scenario, "bad id '$badId' must fall back to browse") {
                    !it.editingGroupsForTest && it.selectedLensIdsForTest.isEmpty() &&
                        it.findViewById<View>(R.id.rvLensGroups).let { list ->
                            (list as RecyclerView).adapter?.itemCount == 7
                        }
                }
                scenario.onActivity { activity ->
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.lensGroupActions).visibility)
                    assertEquals(
                        activity.getString(R.string.lens_management_title),
                        activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.tbLensManagement)
                            .title.toString()
                    )
                }
            }
        }
    }

    @Test
    fun editModeAndSelectionSurviveActivityRecreate() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit("work", true)
            }
            scenario.recreate()
            awaitActivity(scenario, "edit mode and selection must be restored") {
                it.editingGroupsForTest && it.selectedLensIdsForTest == setOf("work")
            }
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.lensGroupActions).visibility)
                assertEquals(
                    activity.getString(R.string.lens_group_selected_count, 1),
                    activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.tbLensManagement)
                        .title.toString()
                )
            }
        }
    }

    @Test
    fun selectionStaysIntactWhileItsGroupIsCollapsedAndExpanded() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit("work", true)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                requireNotNull(list.findViewHolderForAdapterPosition(4)).itemView.performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(5, requireNotNull(activity.findViewById<RecyclerView>(R.id.rvLensGroups).adapter).itemCount)
                assertEquals("Selection must survive collapse", setOf("work"), activity.selectedLensIdsForTest)
                requireNotNull(activity.findViewById<RecyclerView>(R.id.rvLensGroups).findViewHolderForAdapterPosition(4))
                    .itemView.performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val checkbox = requireNotNull(list.findViewHolderForAdapterPosition(5))
                    .itemView.findViewById<android.widget.CheckBox>(R.id.cbLensGroupRow)
                assertTrue("Re-expanded row must still be checked", checkbox.isChecked)
                assertEquals(setOf("work"), activity.selectedLensIdsForTest)
            }
        }
    }

    @Test
    fun managerShowsCanonicalGroupsAndUngroupedLensInOrder() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = requireNotNull(activity.findViewById<RecyclerView>(R.id.rvLensGroups))
                val adapter = requireNotNull(list.adapter)
                assertEquals(7, adapter.itemCount)
                assertEquals(activity.getString(R.string.lens_group_ungrouped), rowText(list, 0))
                assertEquals("Ungrouped", rowText(list, 1))
                assertEquals("Personal", rowText(list, 2))
                assertEquals("Lens 1", rowText(list, 3))
                assertEquals("Work", rowText(list, 4))
                assertEquals("Work", rowText(list, 5))
                assertEquals("Travel", rowText(list, 6))
                val workHeader = (0 until adapter.itemCount)
                    .asSequence()
                    .mapNotNull { position -> list.findViewHolderForAdapterPosition(position)?.itemView }
                    .first { row -> row.findViewById<TextView>(R.id.tvLensGroupHeader)?.text == "Work" }
                assertEquals(
                    activity.resources.getQuantityString(R.plurals.lens_group_lens_count, 2, 2),
                    workHeader.findViewById<TextView>(R.id.tvLensGroupCount).text.toString()
                )
                val activeLens = (0 until adapter.itemCount)
                    .asSequence()
                    .mapNotNull { position -> list.findViewHolderForAdapterPosition(position)?.itemView }
                    .first { row -> row.findViewById<TextView>(R.id.tvLensGroupRow)?.text == "Lens 1" }
                assertEquals(View.VISIBLE, activeLens.findViewById<View>(R.id.tvLensGroupActive).visibility)
            }
        }
    }

    @Test
    fun savedCollapsedGroupsHideSectionsWhenManagerReopens() {
        UtilSettings(context).saveCollapsedLensGroups(setOf("Work"))
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                assertEquals(5, requireNotNull(list.adapter).itemCount)
            }
        }
    }

    @Test
    fun collapsingGroupHidesItsLensRowsAndPersistsCanonicalState() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val header = requireNotNull(list.findViewHolderForAdapterPosition(4)?.itemView)
                header.performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(setOf("Work"), UtilSettings(context).getCollapsedLensGroups())
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                assertEquals(5, requireNotNull(list.adapter).itemCount)
            }
        }
    }

    @Test
    fun editModeBulkAssignsSelectedLensesToGroupThroughRoom() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.lensGroupActions).visibility)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(
                    "Assign actions group bar above bottom surface",
                    activity.findViewById<View>(R.id.rvLensGroups).bottom,
                    activity.findViewById<View>(R.id.lensGroupActions).top
                )
                activity.selectLensForGroupEdit(LensWorkspace.DEFAULT_LENS_ID, true)
                activity.selectLensForGroupEdit("work", true)
                assertEquals(
                    activity.getString(R.string.lens_group_selected_count, 2),
                    activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.tbLensManagement).title.toString()
                )
                val assignButton = activity.findViewById<View>(R.id.btLensGroupAssign)
                assertTrue("Assign should enable after selecting lenses", assignButton.isEnabled)
                assignButton.performClick()
            }
            awaitLensGroupDialog(scenario)
            scenario.onActivity { activity -> clickDialogChoice(activity.lensGroupDialogForTest!!, "Personal") }
            awaitGroupName(scenario, LensWorkspace.DEFAULT_LENS_ID, "Personal")
            awaitGroupName(scenario, "work", "Personal")
        }
    }

    @Test
    fun editModeUngroupsSelectedLens() {
        runBlocking { dao.update(LensWorkspace.createDefault().copy(groupName = "Existing")) }
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit(LensWorkspace.DEFAULT_LENS_ID, true)
                activity.findViewById<View>(R.id.btLensGroupUngroup).performClick()
            }
            awaitGroupName(scenario, LensWorkspace.DEFAULT_LENS_ID, null)
        }
    }

    @Test
    fun editModeBulkCreateAndUngroupUseStableSelectedLensIds() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit(LensWorkspace.DEFAULT_LENS_ID, true)
                activity.selectLensForGroupEdit("travel", true)
                activity.findViewById<View>(R.id.btLensGroupAssign).performClick()
            }
            awaitLensGroupDialog(scenario)
            scenario.onActivity { activity ->
                clickDialogChoice(activity.lensGroupDialogForTest!!, activity.getString(R.string.lens_group_create))
            }
            awaitLensGroupDialog(scenario)
            scenario.onActivity { activity ->
                val dialog = activity.lensGroupDialogForTest!!
                val input = dialog.findViewById<android.widget.EditText>(android.R.id.edit)
                    ?: (dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).rootView
                        .findViewById(android.R.id.edit))
                requireNotNull(input).setText("Shared")
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
            }
            awaitGroupName(scenario, LensWorkspace.DEFAULT_LENS_ID, "Shared")
            awaitGroupName(scenario, "travel", "Shared")
            awaitGroupName(scenario, "work", "Work")

            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit(LensWorkspace.DEFAULT_LENS_ID, true)
                activity.findViewById<View>(R.id.btLensGroupUngroup).performClick()
            }
            awaitGroupName(scenario, LensWorkspace.DEFAULT_LENS_ID, null)
        }
    }

    @Test
    fun editMode_ungroupsOnlySelectedLensAndCancelDoesNotWrite() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit("work", true)
                activity.findViewById<View>(R.id.btLensGroupUngroup).performClick()
            }
            awaitGroupName(scenario, "work", null)

            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit("travel", true)
                activity.findViewById<View>(R.id.btLensGroupAssign).performClick()
            }
            awaitLensGroupDialog(scenario)
            scenario.onActivity { activity ->
                activity.lensGroupDialogForTest!!.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                    .performClick()
            }
            awaitGroupName(scenario, "travel", "work")
        }

        runBlocking { assertEquals("work", dao.findById("travel")?.groupName) }
    }

    @Test
    fun tappingLensRowReturnsExactLensIdToHome() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val lensRow = (0 until requireNotNull(list.adapter).itemCount)
                    .asSequence()
                    .mapNotNull { position -> list.findViewHolderForAdapterPosition(position)?.itemView }
                    .first { row -> row.findViewById<TextView>(R.id.tvLensGroupRow)?.text == "Work" }
                lensRow.performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)
            assertEquals("work", UtilSettings(context).getString(UtilSettings.KEY_ACTIVE_LENS_ID))
        }
    }

    private fun clickDialogChoice(dialog: androidx.appcompat.app.AlertDialog, label: String) {
        val list = requireNotNull(dialog.listView)
        val labels = (0 until list.count).map { list.getItemAtPosition(it).toString() }
        val index = labels.indexOf(label)
        assertTrue("Expected '$label' in dialog options: $labels", index >= 0)
        list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
    }

    private fun awaitGroupName(scenario: ActivityScenario<LensManagementActivity>, lensId: String, expected: String?) {
        val deadline = System.currentTimeMillis() + 5_000L
        var actual: String? = null
        while (System.currentTimeMillis() < deadline && actual != expected) {
            runBlocking { actual = dao.findById(lensId)?.groupName }
            if (actual != expected) Thread.sleep(50)
        }
        assertEquals(expected, actual)
    }

    /** Polls on the test thread; the manager loads Room asynchronously so state settles after idle. */
    private fun awaitActivity(
        scenario: ActivityScenario<LensManagementActivity>,
        message: String,
        condition: (LensManagementActivity) -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + 5_000L
        var ok = false
        while (!ok && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { ok = condition(it) }
            if (!ok) Thread.sleep(50)
        }
        assertTrue(message, ok)
    }

    private fun awaitLensGroupDialog(scenario: ActivityScenario<LensManagementActivity>) {
        val deadline = System.currentTimeMillis() + 5_000L
        var shown = false
        var pickerFailure: Throwable? = null
        var operationError: Throwable? = null
        while (!shown && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                shown = it.lensGroupDialogForTest?.isShowing == true
                pickerFailure = it.groupPickerFailureForTest
                operationError = it.groupOperationErrorForTest
            }
            if (!shown) Thread.sleep(50)
        }
        assertTrue("group dialog should show; picker error=$pickerFailure operation error=$operationError", shown)
    }

    private fun rowText(list: RecyclerView, position: Int): String? =
        list.findViewHolderForAdapterPosition(position)?.itemView?.let { row ->
            row.findViewById<TextView>(R.id.tvLensGroupRow)?.text?.toString()
                ?: row.findViewById<TextView>(R.id.tvLensGroupHeader)?.text?.toString()
        }
}
