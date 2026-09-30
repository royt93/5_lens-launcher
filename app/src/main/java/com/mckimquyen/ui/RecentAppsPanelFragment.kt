package com.mckimquyen.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.search.AppSearchEngine
import com.mckimquyen.search.RecentAppsPanelResolver
import com.mckimquyen.search.SearchHistoryStore
import com.mckimquyen.search.SearchResultAdapter
import com.mckimquyen.util.UtilApp

/**
 * FEAT-009: quick access to recently-launched apps, reachable from a SearchBar icon and from the
 * lens-management menu. Reuses SearchResultAdapter as-is (icon loading, shortcuts, DiffUtil, the
 * long-press action menu) - the only new wiring is the Remove-from-recent callback.
 */
class RecentAppsPanelFragment : BottomSheetDialogFragment() {

    private var adapter: SearchResultAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var emptyState: TextView? = null
    private var historyStore: SearchHistoryStore? = null

    override fun getTheme(): Int = R.style.TransBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_recent_apps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        val store = SearchHistoryStore(context)
        historyStore = store

        val rv = view.findViewById<RecyclerView>(R.id.rvRecentApps)
        val empty = view.findViewById<TextView>(R.id.tvRecentAppsEmpty)
        recyclerView = rv
        emptyState = empty

        val newAdapter = SearchResultAdapter(
            onAppClick = { app, source -> launchAndDismiss(app, source, store) },
            onRemoveFromRecent = { app ->
                store.removeKey(AppSearchEngine.componentKey(app))
                refreshList()
            }
        )
        adapter = newAdapter
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = newAdapter

        refreshList()
    }

    private fun launchAndDismiss(app: App, source: View, store: SearchHistoryStore) {
        store.recordLaunch(AppSearchEngine.componentKey(app))
        UtilApp.launchComponent(
            requireContext(),
            app.packageName.toString(),
            app.label.toString(),
            app.name.toString(),
            source,
            Rect(0, 0, source.width, source.height)
        )
        dismiss()
    }

    private fun refreshList() {
        val store = historyStore ?: return
        val resolved = RecentAppsPanelResolver.resolve(store.recentKeys(), RAppsSingleton.instance.apps.orEmpty())
        adapter?.submitList(resolved)
        recyclerView?.visibility = if (resolved.isEmpty()) View.GONE else View.VISIBLE
        emptyState?.visibility = if (resolved.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        adapter = null
        recyclerView = null
        emptyState = null
        historyStore = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "RecentAppsPanelFragment"
    }
}
