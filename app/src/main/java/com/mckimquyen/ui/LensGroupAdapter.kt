package com.mckimquyen.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensGroupPolicy

internal sealed class LensGroupRow {
    data class Header(val groupName: String?, val expanded: Boolean) : LensGroupRow()
    data class Lens(val workspace: LensWorkspace) : LensGroupRow()
}

internal class LensGroupAdapter(
    private val onLensSelected: (String) -> Unit,
    private val onCollapsedGroupsChanged: (Set<String>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val lenses = mutableListOf<LensWorkspace>()
    private val collapsedGroups = mutableSetOf<String>()
    private val rows = mutableListOf<LensGroupRow>()

    fun submit(lenses: List<LensWorkspace>, collapsedNames: Set<String>) {
        this.lenses.clear()
        this.lenses.addAll(LensGroupPolicy.canonicalize(lenses))
        collapsedGroups.clear()
        collapsedGroups.addAll(LensGroupPolicy.canonicalizeCollapsedNames(collapsedNames, this.lenses))
        rebuildRows()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is LensGroupRow.Header -> TYPE_HEADER
        is LensGroupRow.Lens -> TYPE_LENS
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == TYPE_HEADER) R.layout.item_lens_group_header else R.layout.item_lens_group_lens
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return if (viewType == TYPE_HEADER) HeaderHolder(view) else LensHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is LensGroupRow.Header -> (holder as HeaderHolder).bind(row)
            is LensGroupRow.Lens -> (holder as LensHolder).bind(row.workspace)
        }
    }

    private fun rebuildRows() {
        rows.clear()
        LensGroupPolicy.sections(lenses).forEach { section ->
            val expanded = section.groupName == null || section.groupName !in collapsedGroups
            rows += LensGroupRow.Header(section.groupName, expanded)
            if (expanded) section.lenses.forEach { rows += LensGroupRow.Lens(it) }
        }
        notifyDataSetChanged()
    }

    private fun toggle(groupName: String?) {
        if (groupName == null) return
        if (!collapsedGroups.add(groupName)) collapsedGroups.remove(groupName)
        onCollapsedGroupsChanged(collapsedGroups.toSet())
        rebuildRows()
    }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvLensGroupHeader)
        private val state: TextView = view.findViewById(R.id.tvLensGroupExpanded)

        fun bind(row: LensGroupRow.Header) {
            val displayName = row.groupName ?: itemView.context.getString(R.string.lens_group_ungrouped)
            title.text = displayName
            val action = itemView.context.getString(
                if (row.expanded) R.string.lens_group_collapse else R.string.lens_group_expand
            )
            itemView.contentDescription = "$displayName, $action"
            state.text = if (row.expanded) "⌄" else "›"
            state.contentDescription = action
            itemView.setOnClickListener { toggle(row.groupName) }
        }
    }

    private inner class LensHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvLensGroupRow)

        fun bind(lens: LensWorkspace) {
            title.text = lens.name
            itemView.contentDescription = lens.name
            itemView.setOnClickListener { onLensSelected(lens.id) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_LENS = 1
    }
}
