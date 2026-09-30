package com.mckimquyen.search

import android.content.Intent
import android.app.Application
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.R
import com.mckimquyen.adt.AppDiffCallback
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.services.BroadcastReceivers.AppsEditedReceiver
import com.mckimquyen.util.UtilApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface SearchResultClickListener {
    fun onAppClick(app: App, source: View)
}

class SearchResultAdapter @JvmOverloads constructor(
    private val onAppClick: SearchResultClickListener,
    private val onRemoveFromRecent: ((App) -> Unit)? = null
) : RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder>() {
    private val apps = mutableListOf<App>()

    // Repo audit: this scope is cancelled on detach, and a cancelled CoroutineScope is dead for
    // good - every later launch is dropped silently, so a re-attached adapter would stop loading
    // icons with no crash and nothing in the log. Recreate it on attach instead of holding one
    // instance for the adapter's whole lifetime.
    private var adapterScope = newAdapterScope()

    private fun newAdapterScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Whether the scope can still run work; see [onAttachedToRecyclerView]. */
    @androidx.annotation.VisibleForTesting
    fun isScopeActiveForTest(): Boolean = adapterScope.isActive
    private val shortcutsByPackage = mutableMapOf<String, List<QuickShortcut>>()
    private val loadingIconKeys = mutableSetOf<String>()
    private val loadingShortcutPackages = mutableSetOf<String>()

    fun submitList(newApps: List<App>) {
        val oldApps = apps.toList()
        apps.clear()
        apps.addAll(newApps)
        val diffResult = DiffUtil.calculateDiff(AppDiffCallback(oldApps, apps))
        diffResult.dispatchUpdatesTo(this)
    }

    fun firstOrNull(): App? = apps.firstOrNull()

    override fun getItemCount(): Int = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_search_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun onViewRecycled(holder: ResultViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (!adapterScope.isActive) {
            adapterScope = newAdapterScope()
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        // Cancel in-flight icon/shortcut loads for a list nobody is showing; onAttached revives
        // the scope, so this stays a pause rather than a one-way shutdown.
        adapterScope.cancel()
        loadingIconKeys.clear()
        loadingShortcutPackages.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mainRow: View = itemView.findViewById(R.id.llSearchResultMainRow)
        private val icon: ImageView = itemView.findViewById(R.id.ivSearchResultIcon)
        private val label: TextView = itemView.findViewById(R.id.tvSearchResultLabel)
        private val packageName: TextView = itemView.findViewById(R.id.tvSearchResultPackage)
        private val shortcutsRow: ViewGroup = itemView.findViewById(R.id.llSearchResultShortcuts)

        fun bind(app: App) {
            val appLabel = app.label.toString()
            label.text = appLabel
            packageName.text = app.packageName
            // CORE-002: keyed by iconCacheKey, not packageName (see BitmapCache.buildKey)
            bindIcon(app)
            mainRow.contentDescription = mainRow.context.getString(R.string.search_open_app, appLabel)
            mainRow.setOnClickListener { onAppClick.onAppClick(app, mainRow) }
            mainRow.setOnLongClickListener {
                showActionMenu(app, mainRow)
                true
            }

            bindShortcuts(app)
        }

        fun clear() {
            icon.setImageResource(R.mipmap.ic_launcher)
            shortcutsRow.removeAllViews()
            shortcutsRow.visibility = View.GONE
        }

        private fun bindIcon(app: App) {
            val iconKey = app.iconCacheKey
            RAppsSingleton.instance.getAppIcon(iconKey)?.let {
                icon.setImageBitmap(it)
                return
            }
            app.icon?.let {
                icon.setImageBitmap(it)
                return
            }

            icon.setImageResource(R.mipmap.ic_launcher)
            val application = itemView.context.applicationContext as? Application ?: return
            val packageName = app.packageName.toString()
            val iconResId = app.iconResId
            if (iconKey.isBlank() || packageName.isBlank() || !loadingIconKeys.add(iconKey)) return
            adapterScope.launch {
                val loadedIcon = withContext(Dispatchers.IO) {
                    UtilApp.loadSingleAppIcon(application, packageName, iconResId)
                }
                loadingIconKeys.remove(iconKey)
                if (loadedIcon == null) return@launch
                RAppsSingleton.instance.setAppIcon(iconKey, loadedIcon)
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION
                    && apps.getOrNull(position)?.iconCacheKey == iconKey) {
                    icon.setImageBitmap(loadedIcon)
                }
            }
        }

        // ==================================================================== SEARCH-003: shortcuts

        private fun bindShortcuts(app: App) {
            val packageName = app.packageName.toString()
            val cachedShortcuts = shortcutsByPackage[packageName]
            if (cachedShortcuts != null) {
                renderShortcuts(cachedShortcuts)
                return
            }

            shortcutsRow.removeAllViews()
            shortcutsRow.visibility = View.GONE
            if (packageName.isBlank() || !loadingShortcutPackages.add(packageName)) return

            val appContext = shortcutsRow.context.applicationContext
            adapterScope.launch {
                val shortcuts = withContext(Dispatchers.IO) {
                    AppShortcutsProvider.shortcutsFor(appContext, packageName)
                }
                shortcutsByPackage[packageName] = shortcuts
                loadingShortcutPackages.remove(packageName)
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION
                    && apps.getOrNull(position)?.packageName?.toString() == packageName) {
                    renderShortcuts(shortcuts)
                }
            }
        }

        private fun renderShortcuts(shortcuts: List<QuickShortcut>) {
            shortcutsRow.removeAllViews()
            if (shortcuts.isEmpty()) {
                shortcutsRow.visibility = View.GONE
                return
            }
            val inflater = LayoutInflater.from(shortcutsRow.context)
            shortcuts.forEach { shortcut ->
                val chip = inflater.inflate(R.layout.item_search_shortcut_chip, shortcutsRow, false)
                chip.findViewById<ImageView>(R.id.ivShortcutIcon).setImageDrawable(shortcut.icon)
                chip.findViewById<TextView>(R.id.tvShortcutLabel).text = shortcut.label
                chip.contentDescription = shortcut.label
                chip.setOnClickListener {
                    val bounds = Rect(0, 0, chip.measuredWidth, chip.measuredHeight)
                    val started = AppShortcutsProvider.startShortcut(chip.context, shortcut, bounds)
                    if (!started) {
                        Toast.makeText(chip.context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
                    }
                }
                shortcutsRow.addView(chip)
            }
            shortcutsRow.visibility = View.VISIBLE
        }

        // ==================================================================== SEARCH-003: row actions

        /** Exposed for tests only - the exact popup the last long-press built, unshown state
         *  included, mirroring ActHome.lensDialog's existing test-inspection convention. */
        @androidx.annotation.VisibleForTesting
        var lastActionMenu: PopupMenu? = null
            private set

        private fun showActionMenu(app: App, anchor: View) {
            val wrapper = ContextThemeWrapper(anchor.context, R.style.PopupMenuTheme)
            val popupMenu = PopupMenu(wrapper, anchor, Gravity.END)
            popupMenu.inflate(R.menu.menu_search_result)
            // FEAT-009: the recent-apps panel (onRemoveFromRecent != null) offers exactly App
            // info + Remove from recent - the organization actions below only make sense for the
            // search overlay's stable-grid use case, never for this transient MRU list.
            val isRecentPanel = onRemoveFromRecent != null
            popupMenu.menu.findItem(R.id.menuItemPinStart).isVisible = !isRecentPanel
            popupMenu.menu.findItem(R.id.menuItemPinEnd).isVisible = !isRecentPanel
            popupMenu.menu.findItem(R.id.menuItemUnpin).isVisible =
                !isRecentPanel && app.pinnedZone != PinnedZone.NONE
            popupMenu.menu.findItem(R.id.menuItemElementUninstall).isVisible = !isRecentPanel
            popupMenu.menu.findItem(R.id.menuItemRemoveFromRecent).isVisible = isRecentPanel
            popupMenu.setForceShowIcon(true)
            popupMenu.setOnMenuItemClickListener { item -> handleMenuAction(item.itemId, app, anchor) }
            lastActionMenu = popupMenu
            popupMenu.show()
        }

        /** Split out from the popup's click listener so tests can invoke it directly instead of
         *  simulating a real popup-item click (this project's test device cannot build Espresso's
         *  event injector) - same convention as ActHome.onLensMenuItemSelected. */
        @androidx.annotation.VisibleForTesting
        fun handleMenuAction(itemId: Int, app: App, anchor: View): Boolean = when (itemId) {
            R.id.menuItemElementAppInfo -> {
                startActionIntent(anchor, UtilApp.appInfoIntent(app.packageName.toString()))
                true
            }
            R.id.menuItemElementUninstall -> {
                startActionIntent(anchor, UtilApp.uninstallIntent(app.packageName.toString()))
                true
            }
            R.id.menuItemPinStart -> {
                pin(app, anchor, PinnedZone.START)
                true
            }
            R.id.menuItemPinEnd -> {
                pin(app, anchor, PinnedZone.END)
                true
            }
            R.id.menuItemUnpin -> {
                pin(app, anchor, PinnedZone.NONE)
                true
            }
            R.id.menuItemRemoveFromRecent -> {
                onRemoveFromRecent?.invoke(app)
                true
            }
            else -> false
        }

        private fun startActionIntent(anchor: View, intent: Intent) {
            try {
                anchor.context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(anchor.context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
            }
        }

        /** Same persistence call AppAdapter's applyOrganization uses - no duplicated logic. */
        private fun pin(app: App, anchor: View, zone: PinnedZone) {
            AppPersistent.setOrganization(
                app.packageName.toString(),
                app.name.toString(),
                app.isFavorite,
                app.folderName,
                zone,
                app.lensId
            )
            anchor.context.sendBroadcast(Intent(anchor.context, AppsEditedReceiver::class.java))
            Toast.makeText(anchor.context, R.string.organization_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
