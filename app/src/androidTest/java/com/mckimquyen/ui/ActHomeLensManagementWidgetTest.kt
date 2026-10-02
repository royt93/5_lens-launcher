package com.mckimquyen.ui

import android.content.DialogInterface
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
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

/**
 * FISH-008 Phase 2 + Phase 3 widget tests: the long-press-on-indicator menu's create / rename /
 * delete / Smart Focus flows must actually persist (LensWorkspaceDao rows and per-lens prefs),
 * not just close a dialog.
 *
 * No Espresso here. Its event injector calls `InputManager.getInstance`, removed on API 37, so on
 * this project's current test device even a non-clicking `onView()` throws NoSuchMethodException
 * while building the injector - not only `perform(click())`. These tests drive the real
 * production entry points directly instead: the long-press listener, the Activity's own menu
 * callback, and the live dialog's own buttons.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeLensManagementWidgetTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()

    private val settings: UtilSettings
        get() = UtilSettings(InstrumentationRegistry.getInstrumentation().targetContext)

    // Menu item ids as built by ActHome.showLensManagementMenu.
    private val itemAdd = 1
    private val itemRename = 2
    private val itemDelete = 3
    private val itemSmartFocus = 4
    private val itemShare = 5
    private val itemRecentAppsPanel = 6
    private val itemSearch = 7
    private val itemToggleCleanLens = 8

    @Before
    fun setup() {
        cleanDb()
        clearLensPrefs()
    }

    @After
    fun tearDown() {
        cleanDb()
        clearLensPrefs()
    }

    /** Two lenses so the indicator is visible and long-pressable; second is the active page. */
    private fun cleanDb(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    /** FISH-008 Phase 3: per-lens prefs outlive the DB rows, so they need their own reset. */
    private fun clearLensPrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val stale = prefs.all.keys.filter {
            it.startsWith("${UtilSettings.KEY_DISTORTION_FACTOR}_") ||
                it.startsWith("${UtilSettings.KEY_SMART_FOCUS_BIAS}_") ||
                it.startsWith("${UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR}_")
        }
        prefs.edit().apply {
            stale.forEach { remove(it) }
            remove(UtilSettings.KEY_SMART_FOCUS_BIAS)
            remove(UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR)
            remove(UtilSettings.KEY_CUSTOM_SCALE_FACTOR)
            remove(UtilSettings.KEY_CUSTOM_ANIMATION_TIME)
            remove(UtilSettings.KEY_ACTIVE_LENS_ID)
            remove(UtilSettings.KEY_CLEAN_LENS_MODE)
            remove(UtilSettings.KEY_SHOW_SEARCH_BAR)
        }.commit()
    }

    private fun seedSecondLens() = runBlocking {
        dao.insertOrUpdate(LensWorkspace(id = "second", name = "Second Lens", orderIndex = 1))
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(250)
    }

    /**
     * Opens the real menu (proving the long-press entry point still builds and shows it), then
     * runs the exact callback its item would run.
     */
    private fun openMenuAndSelect(scenario: ActivityScenario<ActHome>, itemId: Int, position: Int) {
        scenario.onActivity { activity ->
            activity.findViewById<View>(R.id.lensPageIndicator).performLongClick()
            activity.onLensMenuItemSelected(itemId, position)
        }
        idle()
    }

    /** Types into the live name dialog and presses its real OK button. */
    private fun confirmNameDialog(scenario: ActivityScenario<ActHome>, name: String) {
        scenario.onActivity { activity ->
            val dialog = activity.lensDialog
            assertNotNull("The name dialog must be showing", dialog)
            dialog!!.findViewById<EditText>(android.R.id.edit)!!.setText(name)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        // FISH-010: confirming now plays a circular-unreveal (ApertureRevealHelper.
        // DEFAULT_DURATION_MS) before the dialog actually dismisses and its business logic runs -
        // idle()'s own 250ms margin is the same order of magnitude as that animation on a real
        // device with animations enabled, so wait past it explicitly rather than race it.
        idle()
        SystemClock.sleep(300)
    }

    /** Presses the live confirm dialog's real positive button. */
    private fun confirmDialog(scenario: ActivityScenario<ActHome>) {
        scenario.onActivity { activity ->
            val dialog = activity.lensDialog
            assertNotNull("The confirm dialog must be showing", dialog)
            dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        // FISH-010: same reveal-then-dismiss timing note as confirmNameDialog above.
        idle()
        SystemClock.sleep(300)
    }

    /** Pages to the second lens so the menu acts on a non-default lens (its own suffixed keys). */
    private fun pageToSecondLens(scenario: ActivityScenario<ActHome>) {
        scenario.onActivity { activity ->
            activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager)
                .setCurrentItem(1, false)
        }
        idle()
    }

    // ---- FISH-008 Phase 3: per-lens Smart Focus + settings lifecycle ----

    @Test
    fun smartFocusToggle_appliesToThePagedLensOnly() {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            pageToSecondLens(scenario)

            scenario.onActivity { activity ->
                assertEquals(
                    "A lens that is off must offer the 'turn on' label",
                    R.string.lens_smart_focus_enable,
                    activity.lensSmartFocusMenuLabelRes(1)
                )
            }
            openMenuAndSelect(scenario, itemSmartFocus, position = 1)

            assertTrue("The paged lens must have Smart Focus on", settings.isSmartFocusBias("second"))
            assertFalse(
                "The other lens must be untouched",
                settings.isSmartFocusBias(LensWorkspace.DEFAULT_LENS_ID)
            )
        }
    }

    @Test
    fun smartFocusMenuLabel_reflectsThePagedLensState() {
        seedSecondLens()
        settings.saveSmartFocusBias("second", true)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            pageToSecondLens(scenario)

            scenario.onActivity { activity ->
                assertEquals(
                    "A lens already on must offer the 'turn off' label",
                    R.string.lens_smart_focus_disable,
                    activity.lensSmartFocusMenuLabelRes(1)
                )
            }
            openMenuAndSelect(scenario, itemSmartFocus, position = 1)

            assertFalse("Selecting it again must turn it back off", settings.isSmartFocusBias("second"))
        }
    }

    @Test
    fun addLens_copiesTheSourceLensSettingsNotJustItsLayout() {
        seedSecondLens()
        settings.saveDistortionFactor("second", 4.5f)
        settings.saveSmartFocusBias("second", true)
        // Real saves always write distortion + scale together (FrmLens.saveCurrentAsCustomPreset)
        // - saveCustomScaleFactor is what flips hasCustomPreset() true, so include it here too.
        settings.saveCustomDistortionFactor("second", 3.3f)
        settings.saveCustomScaleFactor(1.3f)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            pageToSecondLens(scenario)

            openMenuAndSelect(scenario, itemAdd, position = 1)
            confirmNameDialog(scenario, "Copy")

            val created = runBlocking { dao.getAll() }.find { it.name == "Copy" }
            assertNotNull("The new lens must exist", created)
            assertEquals(
                "The new lens must inherit its source's curvature",
                4.5f,
                settings.getDistortionFactor(created!!.id),
                0.001f
            )
            assertTrue(
                "The new lens must inherit its source's Smart Focus state",
                settings.isSmartFocusBias(created.id)
            )
            assertEquals(
                "FISH-015: the new lens must also inherit its source's Custom distortion override",
                3.3f,
                settings.getCustomDistortionFactor(created.id),
                0.001f
            )
        }
    }

    @Test
    fun deleteLens_alsoClearsThatLensOwnSettings() {
        seedSecondLens()
        settings.saveDistortionFactor("second", 4.5f)
        settings.saveSmartFocusBias("second", true)
        settings.saveCustomDistortionFactor("second", 3.3f)
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            pageToSecondLens(scenario)

            openMenuAndSelect(scenario, itemDelete, position = 1)
            confirmDialog(scenario)

            assertNull(
                "The lens row must be gone",
                runBlocking { dao.getAll() }.find { it.id == "second" }
            )
            // Its prefs must not linger and re-attach to a future lens reusing the id.
            assertEquals(
                "A deleted lens's curvature override must be gone, falling back to the shared value",
                2.0f,
                settings.getDistortionFactor("second"),
                0.001f
            )
            assertFalse(settings.isSmartFocusBias("second"))
            assertEquals(
                "FISH-015: a deleted lens's Custom override must be gone too",
                settings.getDistortionFactor("second"),
                settings.getCustomDistortionFactor("second"),
                0.001f
            )
        }
    }

    /**
     * The single-lens case is the one that matters: Phase 2 hides the page-dots indicator until a
     * second lens exists, so before this listener there was no way to open the menu at all - no
     * user could create their second lens. Found in real smoke on Pixel 7 Pro, not in review.
     */
    @Test
    fun emptySpaceLongPress_opensTheLensMenuEvenWithASingleLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            scenario.onActivity { activity ->
                assertEquals(
                    "This test is only meaningful while the indicator is hidden",
                    View.GONE,
                    activity.findViewById<View>(R.id.lensPageIndicator).visibility
                )

                activity.lensManagementMenu = null
                val lensView = activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                assertNotNull(
                    "ActHome must wire the empty-space long-press on every lens page",
                    lensView.onEmptySpaceLongPressListener
                )
                lensView.onEmptySpaceLongPressListener!!.onEmptySpaceLongPress()

                val menu = activity.lensManagementMenu
                assertNotNull("The long-press must build and show the lens menu", menu)
                val titles = (0 until menu!!.menu.size())
                    .map { menu.menu.getItem(it).title.toString() }
                assertTrue(
                    "The menu must offer adding a lens - the whole reason this entry point exists. Got: $titles",
                    titles.contains(activity.getString(R.string.lens_add))
                )
                assertTrue(
                    "The menu must offer the per-lens Smart Focus toggle. Got: $titles",
                    titles.contains(activity.getString(R.string.lens_smart_focus_enable)) ||
                        titles.contains(activity.getString(R.string.lens_smart_focus_disable))
                )
            }
        }
    }

    /**
     * A PopupMenu lays itself out against its anchor. Anchoring to the full-screen LensView pushed
     * the menu off the top edge of a real TECNO KJ7 and clipped it to one visible row, so only
     * "Add lens" of the four items could be reached - `uiautomator` showed the whole popup window
     * as `[0,108][638,277]`. An item-count assertion would have passed right through that (the
     * items existed, they just had nowhere to render), so this pins the actual cause: the anchor
     * must be a small view, not one that fills the screen.
     */
    @Test
    fun lensMenu_isAnchoredToASmallViewSoEveryItemCanRender() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            scenario.onActivity { activity ->
                activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                    .onEmptySpaceLongPressListener!!.onEmptySpaceLongPress()

                assertEquals(
                    "The menu must offer all eight actions (add/rename/delete/Smart Focus/share/Recent apps/Search/Clean)",
                    8,
                    activity.lensManagementMenu!!.menu.size()
                )
                val menuTitles = (0 until activity.lensManagementMenu!!.menu.size())
                    .map { activity.lensManagementMenu!!.menu.getItem(it).title.toString() }
                assertTrue(menuTitles.contains(activity.getString(R.string.search_apps_hint)))
                assertTrue(menuTitles.contains(activity.getString(R.string.setting_clean_lens_mode)))

                val anchor = activity.lensMenuAnchor()
                val lensView = activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                assertFalse(
                    "The full-screen lens grid must not be the anchor - that clips the menu",
                    anchor === lensView
                )
                assertEquals(
                    "The page-dots indicator is the intended anchor (same menu from both entry points)",
                    activity.findViewById<View>(R.id.lensPageIndicator),
                    anchor
                )
                assertTrue(
                    "The anchor must leave room below it for a four-row menu",
                    anchor.height * 4 < activity.window.decorView.height
                )
            }
        }
    }

    @Test
    fun searchMenuItem_opensTheSameFullSearchSurface() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            openMenuAndSelect(scenario, itemSearch, position = 0)
            scenario.onActivity { activity ->
                assertTrue(
                    activity.findViewById<com.google.android.material.search.SearchView>(R.id.searchView)
                        .isShowing
                )
            }
        }
    }

    @Test
    fun cleanMenuItem_togglesPreferenceAndHomeChromeImmediately() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            openMenuAndSelect(scenario, itemToggleCleanLens, position = 0)
            assertTrue(settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.searchBar).visibility)
            }

            openMenuAndSelect(scenario, itemToggleCleanLens, position = 0)
            assertFalse(settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.searchBar).visibility)
            }
        }
    }

    /**
     * UI-025: the lens menu must be built from the same rounded popup theme as every other popup.
     * PopupMenu has no theme getter, so read the theme off its MenuBuilder's context and resolve
     * the real `popupMenuStyle` attribute - the style a square menu would NOT resolve to.
     */
    @Test
    fun lensMenu_resolvesTheRoundedPopupStyle() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            scenario.onActivity { activity ->
                activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                    .onEmptySpaceLongPressListener!!.onEmptySpaceLongPress()

                val builder = activity.lensManagementMenu!!.menu as androidx.appcompat.view.menu.MenuBuilder
                val resolved = android.util.TypedValue()
                assertTrue(
                    "popupMenuStyle must resolve in the menu's context",
                    builder.context.theme.resolveAttribute(androidx.appcompat.R.attr.popupMenuStyle, resolved, true)
                )
                assertEquals(
                    "the lens menu must use the rounded popup style, not the default square one",
                    R.style.RoundedPopupMenuStyle,
                    resolved.resourceId
                )
            }
        }
    }

    @Test
    fun emptySpaceLongPress_createsASecondLensEndToEnd() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            scenario.onActivity { activity ->
                activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                    .onEmptySpaceLongPressListener!!.onEmptySpaceLongPress()
                activity.onLensMenuItemSelected(itemAdd, 0)
            }
            idle()
            confirmNameDialog(scenario, "From empty space")

            val lenses = runBlocking { dao.getAll() }
            assertEquals("A second lens must now exist", 2, lenses.size)
            assertNotNull(lenses.find { it.name == "From empty space" })
        }
    }

    /**
     * A position the lens list does not contain must be refused outright. The menu captures the
     * page index when it opens, and a lens can be deleted from under it before an item is picked,
     * so this is a reachable state, not a theoretical one - and every branch indexes the list.
     */
    @Test
    fun menuSelection_withAStalePosition_isRefusedInsteadOfCrashing() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            scenario.onActivity { activity ->
                listOf(itemAdd, itemRename, itemDelete, itemSmartFocus, itemSearch, itemToggleCleanLens).forEach { item ->
                    assertFalse(
                        "Item $item must be refused for a negative position",
                        activity.onLensMenuItemSelected(item, -1)
                    )
                    assertFalse(
                        "Item $item must be refused for a position past the end",
                        activity.onLensMenuItemSelected(item, 99)
                    )
                }
                assertFalse(
                    "An unknown item id must be refused",
                    activity.onLensMenuItemSelected(999, 0)
                )
            }
        }
    }

    /** Same guard on the label lookup, which runs while the menu is being built. */
    @Test
    fun smartFocusMenuLabel_withAStalePosition_fallsBackToTheEnableLabel() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            scenario.onActivity { activity ->
                assertEquals(
                    R.string.lens_smart_focus_enable,
                    activity.lensSmartFocusMenuLabelRes(-1)
                )
                assertEquals(
                    R.string.lens_smart_focus_enable,
                    activity.lensSmartFocusMenuLabelRes(99)
                )
            }
        }
    }

    /**
     * Both handles hold a View belonging to this Activity (a dialog's decor window, a popup's
     * anchor). A dialog left showing through a rotation leaks its window, so onDestroy has to
     * dismiss and drop them.
     */
    @Test
    fun destroy_dismissesAndReleasesTheLensDialogAndMenu() {
        var dialog: androidx.appcompat.app.AlertDialog? = null

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            scenario.onActivity { activity ->
                activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                    .onEmptySpaceLongPressListener!!.onEmptySpaceLongPress()
                activity.onLensMenuItemSelected(itemAdd, 0)
            }
            idle()

            scenario.onActivity { activity ->
                dialog = activity.lensDialog
                assertNotNull("The add-lens dialog must be showing before we destroy", dialog)
                assertTrue(dialog!!.isShowing)
                assertNotNull(activity.lensManagementMenu)
            }

            scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED)
        }
        idle()

        assertFalse(
            "A dialog still showing at onDestroy leaks its window",
            dialog!!.isShowing
        )
    }

    // ---- FISH-008 Phase 2 regression coverage ----

    @Test
    fun addLens_createsNewWorkspaceWithCopiedLayout() {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            openMenuAndSelect(scenario, itemAdd, position = 0)
            confirmNameDialog(scenario, "Focus")

            val lenses = runBlocking { dao.getAll() }
            assertEquals("A third lens must have been created", 3, lenses.size)
            assertNotNull("The new lens must carry the typed name", lenses.find { it.name == "Focus" })
        }
    }

    @Test
    fun renameLens_updatesTheActiveLensNameOnly() {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            openMenuAndSelect(scenario, itemRename, position = 0)
            confirmNameDialog(scenario, "Renamed")

            val lenses = runBlocking { dao.getAll() }
            assertEquals("Rename must not add or remove a lens", 2, lenses.size)
            assertNotNull("The active lens must carry the new name", lenses.find { it.name == "Renamed" })
            assertNotNull(
                "The other lens must keep its name",
                lenses.find { it.name == "Second Lens" || it.name == LensWorkspace.DEFAULT_LENS_NAME }
            )
        }
    }

    @Test
    fun deleteLens_removesTheWorkspaceAndItsLayoutRows() {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            openMenuAndSelect(scenario, itemDelete, position = 0)
            confirmDialog(scenario)

            val lenses = runBlocking { dao.getAll() }
            assertEquals("Exactly one lens must remain", 1, lenses.size)
            // Page 0 is the default lens, so that is the one deleted here.
            assertNull(
                "The active (default) lens must be gone",
                lenses.find { it.id == LensWorkspace.DEFAULT_LENS_ID }
            )
            assertNotNull("The non-active lens must survive", lenses.find { it.id == "second" })
        }
    }

    // ---- FISH-010: reduced motion must skip the reveal but still apply the business logic ----

    private fun runShell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return java.io.BufferedReader(
            java.io.InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd))
        ).use { it.readText() }
    }

    /** Forces `LensPhysicsPolicy.shouldReduceLensMotion` true for the duration of [block]. */
    private fun withReducedMotionForced(block: () -> Unit) {
        val original = runShell("settings get global animator_duration_scale").trim()
        runShell("settings put global animator_duration_scale 0")
        try {
            block()
        } finally {
            runShell("settings put global animator_duration_scale ${if (original.isBlank() || original == "null") "1" else original}")
        }
    }

    @Test
    fun renameLens_underReducedMotion_stillAppliesInstantly() = withReducedMotionForced {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            openMenuAndSelect(scenario, itemRename, position = 0)
            scenario.onActivity { activity ->
                val dialog = activity.lensDialog
                assertNotNull("The name dialog must be showing", dialog)
                dialog!!.findViewById<EditText>(android.R.id.edit)!!.setText("Instant Rename")
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                // No reveal plays under reduced motion, so the dialog itself must already be
                // gone by the time performClick() returns - only the underlying DB write
                // (LensWorkspace.renameLens's own IO-dispatcher coroutine) is still async.
                assertFalse(dialog.isShowing)
            }
            idle()

            val lenses = runBlocking { dao.getAll() }
            assertNotNull(
                "The rename must be applied even with no reveal animation",
                lenses.find { it.name == "Instant Rename" }
            )
        }
    }

    @Test
    fun deleteLens_underReducedMotion_stillAppliesInstantly() = withReducedMotionForced {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            openMenuAndSelect(scenario, itemDelete, position = 0)
            scenario.onActivity { activity ->
                val dialog = activity.lensDialog
                assertNotNull("The confirm dialog must be showing", dialog)
                dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                // Same reduced-motion synchronous-dismiss note as the rename test above.
                assertFalse(dialog.isShowing)
            }
            idle()

            val lenses = runBlocking { dao.getAll() }
            assertEquals("The delete must be applied even with no reveal animation", 1, lenses.size)
            assertNull(
                "The active (default) lens must be gone",
                lenses.find { it.id == LensWorkspace.DEFAULT_LENS_ID }
            )
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun shareMenuItem_exportsAndLaunchesAChooserForTheActiveLens() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        var captured: android.content.Intent? = null
        val latch = java.util.concurrent.CountDownLatch(1)

        scenario.onActivity { activity ->
            activity.lensShareLauncher = ActHome.LensShareLauncher { intent ->
                captured = intent
                latch.countDown()
            }
            activity.onLensMenuItemSelected(itemShare, 0)
        }

        assertTrue("share export must complete", latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertNotNull("selecting Share must build a chooser Intent", captured)

        val inner = captured!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertNotNull("the chooser must wrap a real ACTION_SEND intent", inner)
        assertEquals(android.content.Intent.ACTION_SEND, inner!!.action)
        assertEquals("image/png", inner.type)
        assertNotNull(
            "the send intent must carry the exported image's uri",
            inner.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        )

        scenario.close()
    }

    /** FEAT-009: the lens menu's Recent apps entry is a second, untoggleable entry point into
     *  the same panel the SearchBar icon opens. */
    @Test
    fun selectingRecentAppsPanelItem_showsThePanel() {
        seedSecondLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            openMenuAndSelect(scenario, itemRecentAppsPanel, 0)

            scenario.onActivity { activity ->
                assertNotNull(
                    "The lens menu's Recent apps entry must open the panel",
                    activity.supportFragmentManager.findFragmentByTag(RecentAppsPanelFragment.TAG)
                )
            }
        }
    }
}
