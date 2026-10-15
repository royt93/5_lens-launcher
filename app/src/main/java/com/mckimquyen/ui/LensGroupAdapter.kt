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
    data class Header(val groupName: String?, val expanded: Boolean, val lensCount: Int) : LensGroupRow()
    data class Lens(val workspace: LensWorkspace, val active: Boolean) : LensGroupRow()
}

internal class LensGroupAdapter(
    private val onLensSelected: (String) -> Unit,
    private val onCollapsedGroupsChanged: (Set<String>) -> Unit,
    private val onGroupEditSelectionChanged: (Set<String>) -> Unit = {},
    private val isEditingGroups: () -> Boolean = { false },
    private val selectedLensIds: MutableSet<String> = mutableSetOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val lenses = mutableListOf<LensWorkspace>()
    private val collapsedGroups = mutableSetOf<String>()
    private val rows = mutableListOf<LensGroupRow>()
    private var activeLensId: String? = null

    fun setGroupEditSelection(ids: Set<String> = selectedLensIds.toSet()) {
        selectedLensIds.clear()
        selectedLensIds.addAll(ids)
        notifyDataSetChanged()
    }

    fun selectedIds(): Set<String> = selectedLensIds.toSet()

    fun submit(lenses: List<LensWorkspace>, collapsedNames: Set<String>, activeLensId: String? = this.activeLensId) {
        this.lenses.clear()
        this.lenses.addAll(LensGroupPolicy.canonicalize(lenses))
        this.activeLensId = activeLensId
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
            is LensGroupRow.Lens -> (holder as LensHolder).bind(row)
        }
    }

    private fun rebuildRows() {
        rows.clear()
        LensGroupPolicy.sections(lenses).forEach { section ->
            val expanded = section.groupName == null || section.groupName !in collapsedGroups
            rows += LensGroupRow.Header(section.groupName, expanded, section.lenses.size)
            if (expanded) section.lenses.forEach { lens ->
                rows += LensGroupRow.Lens(lens, lens.id == activeLensId)
            }
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
        private val count: TextView = view.findViewById(R.id.tvLensGroupCount)

        fun bind(row: LensGroupRow.Header) {
            val displayName = row.groupName ?: itemView.context.getString(R.string.lens_group_ungrouped)
            title.text = displayName
            count.text = itemView.resources.getQuantityString(R.plurals.lens_group_lens_count, row.lensCount, row.lensCount)
            val action = itemView.context.getString(
                if (row.expanded) R.string.lens_group_collapse else R.string.lens_group_expand
            )
            itemView.contentDescription = itemView.context.getString(
                R.string.lens_group_header_description, displayName, row.lensCount, action
            )
            state.text = if (row.expanded) "⌄" else "›"
            state.contentDescription = action
            itemView.setOnClickListener { toggle(row.groupName) }
        }
    }

    private inner class LensHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvLensGroupRow)

        private val checkBox: android.widget.CheckBox = itemView.findViewById(R.id.cbLensGroupRow)

        private val activeBadge: TextView = itemView.findViewById(R.id.tvLensGroupActive)

        fun bind(row: LensGroupRow.Lens) {
            val lens = row.workspace
            title.text = lens.name
            activeBadge.visibility = if (row.active) View.VISIBLE else View.GONE
            itemView.contentDescription = if (row.active) {
                itemView.context.getString(R.string.lens_group_lens_active_description, lens.name)
            } else {
                lens.name
            }
            val editing = isEditingGroups()
            checkBox.visibility = if (editing) View.VISIBLE else View.GONE
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = lens.id in selectedLensIds
            checkBox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedLensIds.add(lens.id) else selectedLensIds.remove(lens.id)
                onGroupEditSelectionChanged(selectedLensIds.toSet())
            }
            itemView.setOnClickListener {
                if (isEditingGroups()) {
                    checkBox.isChecked = !checkBox.isChecked
                } else {
                    onLensSelected(lens.id)
                }
            }
            itemView.setOnLongClickListener {
                if (isEditingGroups()) {
                    checkBox.isChecked = !checkBox.isChecked
                    true
                } else {
                    false
                }
            }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_LENS = 1
    }
}
