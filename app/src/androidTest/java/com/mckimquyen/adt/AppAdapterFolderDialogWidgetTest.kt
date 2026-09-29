package com.mckimquyen.adt

import android.content.DialogInterface
import android.os.SystemClock
import android.widget.EditText
import android.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-010: widget coverage for the "Set folder" `AlertDialog` (`AppAdapter.showFolderDialog`),
 * one of the three dialogs `ApertureRevealHelper` now wires a circular reveal onto. Drives the
 * real production `onMenuItemClick(R.id.menuItemFolder)` callback directly, the same
 * direct-callback pattern `ActHomeLensManagementWidgetTest` already established for
 * `PopupMenu`-adjacent flows this project's test devices cannot drive through Espresso.
 */
@RunWith(AndroidJUnit4::class)
class AppAdapterFolderDialogWidgetTest {

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // FISH-010: confirming/clearing plays ApertureRevealHelper's circular-unreveal
        // (DEFAULT_DURATION_MS) before the dialog dismisses and applyOrganization actually runs.
        SystemClock.sleep(400)
    }

    private fun app(pkg: String, folderName: String? = null) = App(
        packageName = pkg,
        name = "${pkg}Activity",
        label = pkg,
        isVisible = true,
        isOpened = true,
        folderName = folderName
    )

    private fun findEditText(dialog: androidx.appcompat.app.AlertDialog): EditText {
        var found: EditText? = null
        fun walk(view: android.view.View) {
            if (view is EditText) {
                found = view
                return
            }
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(dialog.window!!.decorView)
        return requireNotNull(found) { "Folder EditText not found in dialog" }
    }

    /** Builds a one-row adapter, opens the Set-folder dialog for it, and returns both. */
    private fun openFolderDialog(
        activity: ActSettings,
        initialFolder: String? = null
    ): Pair<AppAdapter, AppAdapter.AppViewHolder> {
        val recyclerView = RecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
        val adapter = AppAdapter(activity, mutableListOf(app("pkg.folder.test", initialFolder)))
        recyclerView.adapter = adapter
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1080, 400)

        val holder = recyclerView.findViewHolderForAdapterPosition(0) as AppAdapter.AppViewHolder
        val menu = PopupMenu(activity, recyclerView).menu
        activity.menuInflater.inflate(R.menu.menu_app, menu)
        holder.onMenuItemClick(menu.findItem(R.id.menuItemFolder))
        return adapter to holder
    }

    @Test
    fun folderDialog_opensShowingTheCurrentFolderName() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            val (_, holder) = openFolderDialog(activity, initialFolder = "Work")
            val dialog = holder.folderDialog
            assertNotNull("Set folder dialog must be showing", dialog)
            assertTrue(dialog!!.isShowing)
            assertEquals("Work", findEditText(dialog).text.toString())
        }
        scenario.close()
    }

    @Test
    fun folderDialog_confirmApplies_theTypedFolderName() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        lateinit var adapter: AppAdapter

        scenario.onActivity { activity ->
            val (adapterRef, holder) = openFolderDialog(activity)
            adapter = adapterRef
            val dialog = holder.folderDialog!!
            findEditText(dialog).setText("Games")
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        idle()

        scenario.onActivity {
            assertEquals("Games", adapter.getItemForPosition(0).folderName)
        }
        scenario.close()
    }

    @Test
    fun folderDialog_neutralButton_clearsTheFolder() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        lateinit var adapter: AppAdapter

        scenario.onActivity { activity ->
            val (adapterRef, holder) = openFolderDialog(activity, initialFolder = "Old")
            adapter = adapterRef
            holder.folderDialog!!.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        }
        idle()

        scenario.onActivity {
            assertNull(adapter.getItemForPosition(0).folderName)
        }
        scenario.close()
    }

    @Test
    fun folderDialog_negativeButton_dismissesWithoutChangingTheFolder() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        lateinit var adapter: AppAdapter
        lateinit var holder: AppAdapter.AppViewHolder

        scenario.onActivity { activity ->
            val (adapterRef, holderRef) = openFolderDialog(activity, initialFolder = "Keep")
            adapter = adapterRef
            holder = holderRef
            holder.folderDialog!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        }
        idle()

        scenario.onActivity {
            assertEquals("Keep", adapter.getItemForPosition(0).folderName)
            assertFalse("Dialog must be dismissed", holder.folderDialog?.isShowing ?: true)
        }
        scenario.close()
    }

    @Test
    fun folderDialog_underReducedMotion_stillAppliesInstantly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun runShell(command: String): String {
            val pfd = instrumentation.uiAutomation.executeShellCommand(command)
            return java.io.BufferedReader(
                java.io.InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd))
            ).use { it.readText() }
        }

        val original = runShell("settings get global animator_duration_scale").trim()
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        try {
            runShell("settings put global animator_duration_scale 0")
            lateinit var adapter: AppAdapter

            scenario.onActivity { activity ->
                val (adapterRef, holder) = openFolderDialog(activity)
                adapter = adapterRef
                val dialog = holder.folderDialog!!
                findEditText(dialog).setText("Instant")
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                // No reveal plays under reduced motion, so the callback must have already run
                // synchronously by the time performClick() returns - no idle()/sleep needed.
                assertEquals("Instant", adapter.getItemForPosition(0).folderName)
                assertFalse(dialog.isShowing)
            }
        } finally {
            val restore = if (original.isBlank() || original == "null") "1" else original
            runShell("settings put global animator_duration_scale $restore")
            scenario.close()
        }
    }

    /**
     * UI-023: a `View` with no id is skipped entirely by `View.dispatchSaveInstanceState` /
     * `dispatchRestoreInstanceState` (documented AOSP contract) - a config change (rotation,
     * fold, locale switch) that drives a real hierarchy-state save/restore cycle would silently
     * discard whatever was typed. Exercising the real `saveHierarchyState`/`restoreHierarchyState`
     * pair directly on the dialog's own decor view proves the exact mechanism, independent of
     * which OS event actually triggers it on a given device/config.
     */
    @Test
    fun folderDialogInput_survivesAHierarchyStateRoundTrip() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            val (_, holder) = openFolderDialog(activity)
            val dialog = holder.folderDialog!!
            val input = findEditText(dialog)
            input.setText("SurviveMe")

            val states = android.util.SparseArray<android.os.Parcelable>()
            val root = dialog.window!!.decorView
            root.saveHierarchyState(states)
            input.setText("")
            root.restoreHierarchyState(states)

            assertEquals(
                "typed folder name must survive a hierarchy-state save/restore round trip - the exact mechanism a config change drives",
                "SurviveMe",
                input.text.toString()
            )
        }
        scenario.close()
    }
}
