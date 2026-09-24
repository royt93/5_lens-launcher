package com.mckimquyen.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mckimquyen.R
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.adt.FragmentPagerAdapter
import com.mckimquyen.app.RAppsSingleton.Companion.instance
import com.mckimquyen.itf.AppsInterface
import com.mckimquyen.model.App
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.services.BroadcastReceivers.AppsEditedReceiver
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilSettings

class FrmApps : Fragment(), AppsInterface, AppAdapter.SelectionListener, ActionMode.Callback {

    companion object {
        fun newInstance() = FrmApps()
    }

    private var rvApps: RecyclerView? = null
    private var progressBarApps: CircularProgressIndicator? = null
    private var utilSettings: UtilSettings? = null
    private var appAdapter: AppAdapter? = null
    private var indexScrolledItem = 0
    private var actionMode: ActionMode? = null

    /**
     * FEAT-007: `ActSettings` sets `viewpager.setOffscreenPageLimit(2)`, and with only 3
     * tabs total that keeps every page's Fragment fully RESUMED even off-screen — ViewPager2
     * does not pause non-current pages the way the old ViewPager did (confirmed live:
     * `onPause()` never fired on tab switch). Watching page selection directly is the
     * mechanism that actually fires when this tab loses focus.
     */
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (position != FragmentPagerAdapter.TAB_APPS) {
                actionMode?.finish()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.frm_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        utilSettings = UtilSettings(requireContext())
        setupViews(view)
        instance.apps?.let { setupRecycler(it) }
        requireActivity().findViewById<ViewPager2>(R.id.viewpager)?.registerOnPageChangeCallback(pageChangeCallback)
    }

    private fun setupViews(view: View) {
        rvApps = view.findViewById(R.id.rvApps)
        progressBarApps = view.findViewById(R.id.progressBarApps)

        // Initialize RecyclerView once
        rvApps?.apply {
            layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.columns_apps))
            itemAnimator = DefaultItemAnimator()
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    source: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = appAdapter?.moveItem(
                    source.bindingAdapterPosition,
                    target.bindingAdapterPosition,
                    false
                ) ?: false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    appAdapter?.persistOrder()
                }

                // FEAT-007: long-press now enters multi-select (native ActionMode pattern).
                // ItemTouchHelper's own long-press-drag recognizer runs inside
                // RecyclerView.onInterceptTouchEvent — ahead of the row's OnLongClickListener —
                // so with this left `true` a real long-press was silently swallowed as a
                // (no-op, since it was released without moving) drag instead of ever reaching
                // the row's listener; confirmed live on-device, not just in adapter-level tests
                // that call toggleSelection() directly and never exercise touch dispatch at all.
                // Reordering still works without drag via the existing "Move earlier"/"Move
                // later" row-menu actions.
                override fun isLongPressDragEnabled(): Boolean = false
            }).attachToRecyclerView(this)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context as? ActSettings)?.setAppsInterface(this)
    }

    private fun sendEditAppsBroadcast() {
        val editAppsIntent = Intent(requireContext(), AppsEditedReceiver::class.java)
        requireContext().sendBroadcast(editAppsIntent)
    }

    private fun setupRecycler(apps: ArrayList<App>?) {
        if (apps.isNullOrEmpty()) {
            progressBarApps?.isVisible = false
            return
        }

        // Save scroll position before updating
        (rvApps?.layoutManager as? LinearLayoutManager)?.let { lm ->
            indexScrolledItem = lm.findFirstCompletelyVisibleItemPosition()
        }

        progressBarApps?.isVisible = false
        rvApps?.isVisible = true

        // Reuse adapter if possible, otherwise create new one
        if (appAdapter == null) {
            appAdapter = AppAdapter(requireActivity(), apps)
            appAdapter?.setSelectionListener(this)
            rvApps?.adapter = appAdapter
        } else {
            appAdapter?.updateApps(apps)
        }

        rvApps?.scrollToPosition(indexScrolledItem)
        indexScrolledItem = 0
    }

    override fun onDefaultsReset() {
        utilSettings?.let { us ->
            if (us.sortType != UtilSettings.DEFAULT_SORT_TYPE_ENUM) {
                us.save(UtilSettings.DEFAULT_SORT_TYPE_ENUM)
                sendEditAppsBroadcast()
            }
        }
    }

    override fun onAppsUpdated(apps: ArrayList<App>?) {
        setupRecycler(apps)
    }

    override fun onDestroyView() {
        // A live ActionMode outlives the view it was started for (it belongs to the host
        // Activity) — finish it here so it never survives pointing at a torn-down adapter.
        actionMode?.finish()
        activity?.findViewById<ViewPager2>(R.id.viewpager)?.unregisterOnPageChangeCallback(pageChangeCallback)
        rvApps?.adapter = null
        appAdapter = null
        rvApps = null
        progressBarApps = null
        utilSettings = null
        super.onDestroyView()
    }

    // ========================================================================
    // FEAT-007: MULTI-SELECT
    // ========================================================================

    override fun onSelectionChanged(selectedCount: Int) {
        if (selectedCount == 0) {
            actionMode?.finish()
            return
        }
        if (actionMode == null) {
            actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(this)
        }
        actionMode?.title = resources.getQuantityString(R.plurals.apps_selected_count, selectedCount, selectedCount)
        actionMode?.invalidate()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.menu_apps_selection, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val selected = appAdapter?.selectedApps.orEmpty()
        menu.findItem(R.id.menuItemBulkHide)?.isVisible = selected.any { it.isVisible }
        menu.findItem(R.id.menuItemBulkUnhide)?.isVisible = selected.any { !it.isVisible }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuItemBulkSelectAll -> appAdapter?.selectAll()
            R.id.menuItemBulkHide -> {
                appAdapter?.bulkSetVisibility(false)
                mode.finish()
            }
            R.id.menuItemBulkUnhide -> {
                appAdapter?.bulkSetVisibility(true)
                mode.finish()
            }
            R.id.menuItemBulkPin -> {
                appAdapter?.bulkPin(PinnedZone.START)
                mode.finish()
            }
            R.id.menuItemBulkUninstall -> {
                confirmAndUninstallSelected()
                mode.finish()
            }
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        appAdapter?.clearSelection()
        actionMode = null
    }

    /** Test-only visibility: [FrmAppsSelectionIntegrationTest] reads this to assert the
     * confirmation dialog's real title/message/button state without Espresso (this project
     * has no working `onView()`/`check()` dependency chain yet — see
     * `SuperWebViewActivityWidgetTest`'s own disclosed note on the same gap; matches the
     * existing field-reflection pattern `ActSettingsDialogsIntegrationTest` already uses for
     * `ActSettings`'s own dialogs). */
    private var pendingUninstallDialog: androidx.appcompat.app.AlertDialog? = null

    /**
     * FEAT-007: `ACTION_UNINSTALL_PACKAGE` has no multi-package form — Android shows one
     * system confirmation per app. That's set as an explicit expectation in this dialog so
     * it isn't reported as a bug later. Packages are deduped since one package can own
     * several launcher components (Room stores per-component identity, uninstall is per-package).
     */
    private fun confirmAndUninstallSelected() {
        val packages = appAdapter?.selectedApps.orEmpty().mapNotNull { it.packageName?.toString() }.distinct()
        if (packages.isEmpty()) return
        val context = context ?: return
        pendingUninstallDialog = MaterialAlertDialogBuilder(context, R.style.MaterialYouDialogTheme)
            .setTitle(R.string.bulk_uninstall_confirm_title)
            .setMessage(resources.getQuantityString(R.plurals.bulk_uninstall_confirm_message, packages.size, packages.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                packages.forEach { pkg ->
                    try {
                        startActivity(UtilApp.uninstallIntent(pkg))
                    } catch (_: Exception) {
                        // A single missing/invalid package must not stop the rest of the batch.
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
