package com.mckimquyen.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.mckimquyen.R
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.app.RAppsSingleton.Companion.instance
import com.mckimquyen.itf.AppsInterface
import com.mckimquyen.model.App
import com.mckimquyen.services.BroadcastReceivers.AppsEditedReceiver
import com.mckimquyen.util.UtilSettings
import me.zhanghai.android.materialprogressbar.MaterialProgressBar

class FrmApps : Fragment(), AppsInterface {

    companion object {
        fun newInstance() = FrmApps()
    }

    private var rvApps: RecyclerView? = null
    private var progressBarApps: MaterialProgressBar? = null
    private var utilSettings: UtilSettings? = null
    private var appAdapter: AppAdapter? = null
    private var indexScrolledItem = 0

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

                override fun isLongPressDragEnabled(): Boolean = true
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
        appAdapter = null
        super.onDestroyView()
    }
}
