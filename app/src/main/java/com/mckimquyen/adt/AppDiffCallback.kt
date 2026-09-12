package com.mckimquyen.adt

import androidx.recyclerview.widget.DiffUtil
import com.mckimquyen.model.App

/**
 * Shared [DiffUtil.Callback] for [App] lists, used by [AppAdapter] and
 * [com.mckimquyen.search.SearchResultAdapter] so each dispatches granular
 * RecyclerView updates (insert/move/change) instead of `notifyDataSetChanged()`.
 *
 * Item identity matches the (packageName, name) pair already used as the app identity
 * everywhere else in the codebase (see [com.mckimquyen.app.RAppsSingleton.findApp]).
 * Content equality relies on [App]'s data-class `equals()` — safe here because every
 * [App] reaching these adapters already has `icon = null` (icons live only in
 * `BitmapCache`, stripped in `TaskUpdateApps` before the snapshot is stored).
 */
class AppDiffCallback(
    private val oldList: List<App>,
    private val newList: List<App>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldApp = oldList[oldItemPosition]
        val newApp = newList[newItemPosition]
        return oldApp.packageName.toString() == newApp.packageName.toString() &&
            oldApp.name.toString() == newApp.name.toString()
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldList[oldItemPosition] == newList[newItemPosition]
}
