package com.mckimquyen.adt

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.views.LensView

/**
 * FISH-008 Phase 2: pages a [ViewPager2][androidx.viewpager2.widget.ViewPager2] across lens
 * workspaces. A plain [RecyclerView.Adapter], not a `FragmentStateAdapter` - a lens page is one
 * custom [LensView], no child-fragment lifecycle is needed, so this avoids FragmentManager
 * transaction overhead entirely.
 *
 * Stable ids (from [LensWorkspace.id]) are the whole contract a dynamic item count needs here -
 * unlike `FragmentStateAdapter`, a plain adapter has no saved Fragment state to prune via
 * getItemId/containsItem, so [setHasStableIds] is sufficient for RecyclerView's own recycling to
 * follow lens create/delete/reorder correctly.
 */
class LensPagerAdapter(
    private val onBindPage: (LensView, LensWorkspace) -> Unit
) : RecyclerView.Adapter<LensPagerAdapter.PageHolder>() {

    private var lenses: List<LensWorkspace> = emptyList()
    private var lockedLensIds: Set<String> = emptySet()

    fun setLockedLensIds(ids: Set<String>) {
        if (lockedLensIds == ids) return
        lockedLensIds = ids.toSet()
        notifyDataSetChanged()
    }

    @androidx.annotation.VisibleForTesting
    fun lockedLensIdsForTest(): Set<String> = lockedLensIds

    init {
        setHasStableIds(true)
    }

    class PageHolder(pageRoot: android.view.View, val lensView: LensView) : RecyclerView.ViewHolder(pageRoot)

    override fun getItemId(position: Int): Long = lenses[position].id.hashCode().toLong()

    override fun getItemCount(): Int = lenses.size

    fun lensAt(position: Int): LensWorkspace? = lenses.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val root = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lens_page, parent, false)
        return PageHolder(root, root.findViewById(R.id.lensViews))
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val lens = lenses[position]
        val locked = lens.id in lockedLensIds
        val placeholder = holder.itemView.findViewById<android.widget.TextView>(R.id.tvLensLockedPlaceholder)
        if (locked) {
            holder.lensView.setApps(arrayListOf())
            holder.lensView.visibility = android.view.View.INVISIBLE
            placeholder.text = holder.itemView.context.getString(R.string.lens_locked_message)
            placeholder.visibility = android.view.View.VISIBLE
            holder.itemView.contentDescription = placeholder.text
            return
        }
        placeholder.visibility = android.view.View.GONE
        holder.lensView.visibility = android.view.View.VISIBLE
        holder.itemView.contentDescription = null
        onBindPage(holder.lensView, lens)
    }

    /** Diffs by id so an unrelated create/rename/delete doesn't reset scroll/page position. */
    fun submitLenses(newLenses: List<LensWorkspace>) {
        val old = lenses
        lenses = newLenses
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newLenses.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].id == newLenses[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == newLenses[newPos]
        }).dispatchUpdatesTo(this)
    }
}
