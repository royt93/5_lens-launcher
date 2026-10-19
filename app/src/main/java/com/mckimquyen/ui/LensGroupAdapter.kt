package com.mckimquyen.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensGroupPolicy

/** Plays only platform haptic feedback, which respects the user's system touch-feedback setting. */
internal fun performLensManagerHaptic(view: View, feedback: Int): Boolean =
    if (LensPhysicsPolicy.shouldReduceLensMotion(view.context)) false else view.performHapticFeedback(feedback)

/** M3 card position within one group section — drives corner radii. */
enum class CardPosition { ONLY, TOP, MIDDLE, BOTTOM }

internal sealed class LensGroupRow {
    abstract val position: CardPosition
    data class Header(
        val groupName: String?,
        val expanded: Boolean,
        val lensCount: Int,
        override val position: CardPosition
    ) : LensGroupRow()
    data class Lens(
        val workspace: LensWorkspace,
        val active: Boolean,
        val editing: Boolean = false,
        val selected: Boolean = false,
        override val position: CardPosition
    ) : LensGroupRow()
}

/** Card background (M3 corners) for a row position. */
internal fun CardPosition.backgroundRes(): Int = when (this) {
    CardPosition.ONLY -> R.drawable.bg_lens_card_only
    CardPosition.TOP -> R.drawable.bg_lens_card_top
    CardPosition.MIDDLE -> R.drawable.bg_lens_card_middle
    CardPosition.BOTTOM -> R.drawable.bg_lens_card_bottom
}

/** Stable key for a row: "H:<groupName|_>" or "L:<lensId>". */
internal fun LensGroupRow.stableKey(): String = when (this) {
    is LensGroupRow.Header -> "H:${groupName ?: "_"}"
    is LensGroupRow.Lens -> "L:${workspace.id}"
}

/**
 * Build the flat list of rows for the list from state, computing card position for M3 shape.
 * Pure function so JVM tests can call it without Android.
 */
internal fun buildLensGroupRows(
    lenses: List<LensWorkspace>,
    collapsed: Set<String>,
    activeLensId: String?,
    editing: Boolean = false,
    selectedIds: Set<String> = emptySet()
): List<LensGroupRow> {
    val canonicalized = LensGroupPolicy.canonicalize(lenses)
    val canonicalCollapsed = LensGroupPolicy.canonicalizeCollapsedNames(collapsed, canonicalized)
    val result = mutableListOf<LensGroupRow>()
    LensGroupPolicy.sections(canonicalized).forEach { section ->
        val expanded = section.groupName == null || section.groupName !in canonicalCollapsed
        val sectionRows = mutableListOf<LensGroupRow>()
        sectionRows += LensGroupRow.Header(
            section.groupName, expanded, section.lenses.size,
            if (expanded) CardPosition.TOP else CardPosition.ONLY
        )
        if (expanded) section.lenses.forEachIndexed { index, lens ->
            val isLast = index == section.lenses.size - 1
            sectionRows += LensGroupRow.Lens(
                workspace = lens,
                active = lens.id == activeLensId,
                editing = editing,
                selected = lens.id in selectedIds,
                position = if (isLast) CardPosition.BOTTOM else CardPosition.MIDDLE
            )
        }
        result.addAll(sectionRows)
    }
    return result
}

/** DiffUtil callback for the complete row list. Identity by stable key; content by data equality. */
internal class LensGroupRowDiff(
    private val before: List<LensGroupRow>,
    private val after: List<LensGroupRow>
) : DiffUtil.Callback() {
    override fun getOldListSize() = before.size
    override fun getNewListSize() = after.size
    override fun areItemsTheSame(o: Int, n: Int) = before[o].stableKey() == after[n].stableKey()
    override fun areContentsTheSame(o: Int, n: Int) = before[o] == after[n]
}

internal class LensGroupAdapter(
    private val onLensSelected: (String) -> Unit,
    private val onCollapsedGroupsChanged: (Set<String>) -> Unit,
    private val onGroupEditSelectionChanged: (Set<String>) -> Unit = {},
    private val isEditingGroups: () -> Boolean = { false },
    private val selectedLensIds: MutableSet<String> = mutableSetOf(),
    private val onLensOverflow: (View, LensWorkspace) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val lenses = mutableListOf<LensWorkspace>()
    private val collapsedGroups = mutableSetOf<String>()
    private val rows = mutableListOf<LensGroupRow>()
    private var activeLensId: String? = null

    fun setGroupEditSelection(ids: Set<String> = selectedLensIds.toSet()) {
        selectedLensIds.clear()
        selectedLensIds.addAll(ids)
        dispatchRows()
    }

    fun selectedIds(): Set<String> = selectedLensIds.toSet()

    fun lensCount(): Int = lenses.size

    fun submit(lenses: List<LensWorkspace>, collapsedNames: Set<String>, activeLensId: String? = this.activeLensId) {
        this.lenses.clear()
        this.lenses.addAll(lenses)
        this.activeLensId = activeLensId
        collapsedGroups.clear()
        collapsedGroups.addAll(collapsedNames)
        dispatchRows()
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

    private fun dispatchRows() {
        val newRows = buildLensGroupRows(
            lenses = lenses,
            collapsed = collapsedGroups,
            activeLensId = activeLensId,
            editing = isEditingGroups(),
            selectedIds = selectedLensIds.toSet()
        )
        val diff = DiffUtil.calculateDiff(LensGroupRowDiff(rows, newRows))
        rows.clear()
        rows.addAll(newRows)
        diff.dispatchUpdatesTo(this)
    }

    private fun toggle(groupName: String?) {
        if (groupName == null) return
        if (!collapsedGroups.add(groupName)) collapsedGroups.remove(groupName)
        onCollapsedGroupsChanged(collapsedGroups.toSet())
        dispatchRows()
    }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvLensGroupHeader)
        private val state: android.widget.ImageView = view.findViewById(R.id.tvLensGroupExpanded)
        private val icon: android.widget.ImageView = view.findViewById(R.id.ivLensGroupIcon)
        private val count: TextView = view.findViewById(R.id.tvLensGroupCount)

        fun bind(row: LensGroupRow.Header) {
            val displayName = row.groupName ?: itemView.context.getString(R.string.lens_group_ungrouped)
            title.text = displayName
            val countText = itemView.resources.getQuantityString(R.plurals.lens_group_lens_count, row.lensCount, row.lensCount)
            count.text = countText
            val action = itemView.context.getString(
                if (row.expanded) R.string.lens_group_collapse else R.string.lens_group_expand
            )
            itemView.contentDescription = itemView.context.getString(
                R.string.lens_group_header_description, displayName, countText, action
            )
            val targetRotation = if (row.expanded) EXPANDED_ROTATION else COLLAPSED_ROTATION
            state.animate().cancel()
            if (LensPhysicsPolicy.shouldReduceLensMotion(itemView.context)) {
                state.rotation = targetRotation
            } else {
                state.animate().rotation(targetRotation).setDuration(CHEVRON_DURATION_MS).start()
            }
            state.contentDescription = action
            icon.setImageResource(if (row.groupName == null) R.drawable.ic_apps_24dp else R.drawable.ic_folder_24dp)
            itemView.setBackgroundResource(row.position.backgroundRes())
            itemView.setOnClickListener { toggle(row.groupName) }
        }
    }

    private inner class LensHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvLensGroupRow)
        private val checkBox: com.google.android.material.checkbox.MaterialCheckBox = itemView.findViewById(R.id.cbLensGroupRow)
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
            itemView.setBackgroundResource(row.position.backgroundRes())
            checkBox.visibility = if (row.editing) View.VISIBLE else View.GONE
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = row.selected
            checkBox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedLensIds.add(lens.id) else selectedLensIds.remove(lens.id)
                performLensManagerHaptic(itemView, android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                onGroupEditSelectionChanged(selectedLensIds.toSet())
            }
            itemView.findViewById<View>(R.id.btLensGroupMore)?.apply {
                visibility = if (row.editing) View.GONE else View.VISIBLE
                setOnClickListener { onLensOverflow(this, lens) }
            }
            itemView.setOnClickListener {
                if (isEditingGroups()) {
                    checkBox.isChecked = !checkBox.isChecked
                } else {
                    onLensSelected(lens.id)
                }
            }
            itemView.setOnLongClickListener {
                if (isEditingGroups()) { checkBox.isChecked = !checkBox.isChecked; true } else false
            }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_LENS = 1
        const val EXPANDED_ROTATION = 0f
        const val COLLAPSED_ROTATION = -90f
        const val CHEVRON_DURATION_MS = 180L
    }
}
