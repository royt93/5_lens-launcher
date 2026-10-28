package com.mckimquyen.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.LensWorkspaceDao
import com.mckimquyen.util.LensGroupPolicy
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LensManagementActivity : ActBase() {
    companion object {
        const val EXTRA_MANAGE_LENS_ID = "com.mckimquyen.lenslauncher.EXTRA_MANAGE_LENS_ID"
        private const val STATE_EDITING = "state_editing_groups"
        private const val STATE_SELECTED = "state_selected_lens_ids"
        // UI-032: preserve draft dialog state across process death / recreation
        private const val STATE_DIALOG_KIND = "state_dialog_kind"
        private const val STATE_DIALOG_TEXT = "state_dialog_text"
        private const val STATE_DIALOG_TARGET_LENS_ID = "state_dialog_target_lens_id"
        private const val DIALOG_KIND_RENAME = "rename"
        private const val DIALOG_KIND_CREATE_GROUP = "create_group"
        private const val DIALOG_KIND_ASSIGN_GROUP = "assign_group"
        private const val DIALOG_KIND_DELETE = "delete"
        private const val ACTION_BAR_DURATION_MS = 220L
        private const val LIST_ADD_DURATION_MS = 180L
        private const val LIST_REMOVE_DURATION_MS = 160L
        private const val LIST_MOVE_DURATION_MS = 180L
        private const val LIST_CHANGE_DURATION_MS = 120L
        private const val ACTION_BAR_START_OFFSET_DP = 48f
    }

    private lateinit var list: RecyclerView
    private lateinit var adapter: LensGroupAdapter
    private lateinit var settings: UtilSettings
    private val dao: LensWorkspaceDao by lazy { AppDatabase.getInstance().lensWorkspaceDao() }
    private val selectedLensIds = mutableSetOf<String>()
    private var editingGroups = false
    private var lensGroupDialog: android.app.Dialog? = null
    // UI-032: track open dialog kind and target so onSaveInstanceState can preserve drafts
    private var activeDialogKind: String? = null
    private var activeDialogLensId: String? = null
    // UI-032: cache collapsed groups for the Activity's lifetime instead of re-reading prefs on main
    private var cachedCollapsedGroups: Set<String>? = null
    private var groupOperationInProgress = false
    private var groupLoadVersion = 0
    private var lastGroupOperationError: Throwable? = null
    private lateinit var managementToolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var groupActions: android.view.View
    private lateinit var collapsingToolbar: com.google.android.material.appbar.CollapsingToolbarLayout
    private var originalStatusBarColor = android.graphics.Color.TRANSPARENT
    private var originalNavigationBarColor = android.graphics.Color.TRANSPARENT
    private var originalLightStatusBars = false
    private var originalLightNavigationBars = false
    private var activeLensId: String? = null

    @androidx.annotation.VisibleForTesting
    var groupPickerFailureForTest: Throwable? = null
        private set

    @androidx.annotation.VisibleForTesting
    val groupOperationErrorForTest: Throwable?
        get() = lastGroupOperationError

    @androidx.annotation.VisibleForTesting
    val lensGroupDialogForTest: android.app.Dialog?
        get() = lensGroupDialog

    @androidx.annotation.VisibleForTesting
    fun toggleEditModeForTest() = toggleEditMode()

    @androidx.annotation.VisibleForTesting
    fun selectLensForGroupEdit(lensId: String, selected: Boolean) {
        if (selected) selectedLensIds.add(lensId) else selectedLensIds.remove(lensId)
        adapter.setGroupEditSelection(selectedLensIds.toSet())
        onGroupSelectionChanged(selectedLensIds)
    }

    @androidx.annotation.VisibleForTesting
    fun selectLensForTest(lensId: String) = selectLens(lensId)
    @androidx.annotation.VisibleForTesting
    fun showCreateGroupDialogForTest(selectedIds: Set<String>, draftText: String? = null) =
        showCreateGroupDialog(selectedIds, draftText)

    @androidx.annotation.VisibleForTesting
    fun showRenameLensDialogForTest(lens: LensWorkspace, draftText: String? = null) =
        showRenameLensDialog(lens, draftText)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalStatusBarColor = window.statusBarColor
        originalNavigationBarColor = window.navigationBarColor
        val originalBars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        originalLightStatusBars = originalBars.isAppearanceLightStatusBars
        originalLightNavigationBars = originalBars.isAppearanceLightNavigationBars
        setContentView(R.layout.act_lens_management)
        settings = UtilSettings(this)
        cachedCollapsedGroups = settings.getCollapsedLensGroups()
        activeLensId = settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
        list = findViewById(R.id.rvLensGroups)
        managementToolbar = findViewById(R.id.tbLensManagement)
        groupActions = findViewById(R.id.lensGroupActions)
        groupActions.visibility = android.view.View.GONE
        val onSurface = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK
        )
        val surface = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE
        )
        // The collapsing layout paints the large title itself; the toolbar stays transparent and
        // keeps its own title text invisible-but-present so TalkBack and tests still read it.
        managementToolbar.setTitleTextColor(android.graphics.Color.TRANSPARENT)
        collapsingToolbar = findViewById(R.id.lensManagerCollapsingToolbar)
        collapsingToolbar.setCollapsedTitleTextColor(onSurface)
        collapsingToolbar.setExpandedTitleColor(onSurface)
        setScreenTitle(getString(R.string.lens_management_title))
        managementToolbar.setNavigationIconTint(onSurface)
        managementToolbar.setNavigationOnClickListener { finish() }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = android.graphics.Color.luminance(surface) > 0.5f
            isAppearanceLightNavigationBars = android.graphics.Color.luminance(surface) > 0.5f
        }
        managementToolbar.inflateMenu(R.menu.menu_lens_group_edit)
        managementToolbar.menu.findItem(R.id.menuLensGroupEditToggle).icon?.setTint(onSurface)
        managementToolbar.menu.findItem(R.id.menuLensGroupEditToggle).isVisible = true
        managementToolbar.menu.findItem(R.id.menuLensGroupEditDone).isVisible = false
        applyEdgeToEdgeInsets()
        managementToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menuLensGroupEditToggle || item.itemId == R.id.menuLensGroupEditDone) {
                toggleEditMode()
                true
            } else false
        }
        val manageLensId = intent.getStringExtra(EXTRA_MANAGE_LENS_ID)
        val restoredEditing = savedInstanceState?.getBoolean(STATE_EDITING) == true
        val restoredSelection = savedInstanceState?.getStringArrayList(STATE_SELECTED).orEmpty()
        adapter = LensGroupAdapter(
            onLensSelected = ::selectLens,
            onCollapsedGroupsChanged = { groups ->
                cachedCollapsedGroups = groups
                settings.saveCollapsedLensGroups(groups)
            },
            onGroupEditSelectionChanged = { ids ->
                selectedLensIds.clear()
                selectedLensIds.addAll(ids)
                onGroupSelectionChanged(selectedLensIds)
            },
            isEditingGroups = { editingGroups },
            selectedLensIds = selectedLensIds,
            onLensOverflow = ::showLensRowMenu
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        groupActions.findViewById<com.google.android.material.button.MaterialButton>(R.id.btLensGroupAssign)
            .setOnClickListener { showAssignGroupDialog() }
        groupActions.findViewById<com.google.android.material.button.MaterialButton>(R.id.btLensGroupUngroup)
            .setOnClickListener { ungroupSelected() }
        updateItemAnimator()
        LensWorkspace.loadAll { lenses ->
            if (!isFinishing && !isDestroyed) {
                adapter.submit(lenses, effectiveCollapsedGroups(), activeLensId)
                findViewById<android.view.View>(R.id.lensManagerSingleLensHint).visibility =
                    if (lenses.size == 1) android.view.View.VISIBLE else android.view.View.GONE
                // Selection is only trusted once checked against live Room rows: a stale or
                // unknown ID (deleted lens, bad intent extra) must never enter edit mode.
                val knownIds = lenses.mapTo(mutableSetOf()) { it.id }
                if (restoredEditing) {
                    editingGroups = true
                    selectedLensIds.addAll(restoredSelection.filter { it in knownIds })
                    adapter.setGroupEditSelection(selectedLensIds.toSet())
                    onGroupSelectionChanged(selectedLensIds)
                } else if (!manageLensId.isNullOrBlank() && manageLensId in knownIds) {
                    beginManagingLens(manageLensId)
                }
                // UI-032: restore draft dialog after Activity recreation
                val dialogKind = savedInstanceState?.getString(STATE_DIALOG_KIND)
                if (dialogKind != null) {
                    val draftText = savedInstanceState.getString(STATE_DIALOG_TEXT)
                    val targetId = savedInstanceState.getString(STATE_DIALOG_TARGET_LENS_ID)
                    when (dialogKind) {
                        DIALOG_KIND_RENAME -> {
                            val lens = lenses.firstOrNull { it.id == targetId }
                            if (lens != null) showRenameLensDialog(lens, draftText)
                        }
                        DIALOG_KIND_CREATE_GROUP -> {
                            showCreateGroupDialog(selectedLensIds.toSet(), draftText)
                        }
                        DIALOG_KIND_ASSIGN_GROUP -> {
                            showAssignGroupDialog(selectedLensIds.toSet())
                        }
                        DIALOG_KIND_DELETE -> {
                            val lens = lenses.firstOrNull { it.id == targetId }
                            if (lens != null) showDeleteLensDialog(lens)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reduced-motion / battery-saver can change while the screen is backgrounded.
        updateItemAnimator()
    }

    private fun updateItemAnimator() {
        list.itemAnimator = if (LensPhysicsPolicy.shouldReduceLensMotion(this)) {
            null
        } else {
            androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = LIST_ADD_DURATION_MS
                removeDuration = LIST_REMOVE_DURATION_MS
                moveDuration = LIST_MOVE_DURATION_MS
                changeDuration = LIST_CHANGE_DURATION_MS
            }
        }
    }

    private fun confirmFeedbackConstant(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) android.view.HapticFeedbackConstants.CONFIRM
        else android.view.HapticFeedbackConstants.VIRTUAL_KEY

    private fun rejectFeedbackConstant(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) android.view.HapticFeedbackConstants.REJECT
        else android.view.HapticFeedbackConstants.VIRTUAL_KEY

    private fun confirmHaptic(feedback: Int) {
        performLensManagerHaptic(list, feedback)
    }

    private fun lensOperationFeedback() =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.HapticFeedbackConstants.CONFIRM
        } else android.view.HapticFeedbackConstants.VIRTUAL_KEY

    @androidx.annotation.VisibleForTesting
    var lensRowMenuForTest: androidx.appcompat.widget.PopupMenu? = null
        private set

    private fun showLensRowMenu(anchor: android.view.View, lens: LensWorkspace) {
        lensRowMenuForTest?.dismiss()
        val themed = androidx.appcompat.view.ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val menu = androidx.appcompat.widget.PopupMenu(themed, anchor)
        menu.menuInflater.inflate(R.menu.menu_lens_row, menu.menu)
        menu.menu.findItem(R.id.menuLensDelete).isEnabled = LensWorkspace.canDelete(lens.id, adapter.lensCount())
        val onSurface = com.google.android.material.color.MaterialColors.getColor(
            themed, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK
        )
        menu.menu.forEach { it.icon?.mutate()?.setTint(onSurface) }
        menu.setForceShowIcon(true)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuLensRename -> { showRenameLensDialog(lens); true }
                R.id.menuLensDelete -> { showDeleteLensDialog(lens); true }
                else -> false
            }
        }
        menu.setOnDismissListener { if (lensRowMenuForTest === menu) lensRowMenuForTest = null }
        lensRowMenuForTest = menu
        menu.show()
    }

    private fun showRenameLensDialog(lens: LensWorkspace, draftText: String? = null) {
        activeDialogKind = DIALOG_KIND_RENAME
        activeDialogLensId = lens.id
        val dialog = com.mckimquyen.ui.dialog.LensDialogs.textInput(
            this, R.string.lens_rename_title, R.string.lens_name_hint, draftText ?: lens.name,
            R.string.lens_group_error_blank,
            { name ->
                LensWorkspace.renameLens(lens, name) { lenses ->
                    if (!isFinishing && !isDestroyed) {
                        refreshAfterLensChange(lenses)
                        confirmHaptic(lensOperationFeedback())
                    }
                }
            },
            {
                lensGroupDialog = null
                activeDialogKind = null
                activeDialogLensId = null
            }
        )
        lensGroupDialog = dialog
        dialog.show()
    }

    private fun showDeleteLensDialog(lens: LensWorkspace) {
        activeDialogKind = DIALOG_KIND_DELETE
        activeDialogLensId = lens.id
        val dialog = com.mckimquyen.ui.dialog.LensDialogs.confirm(
            this, R.string.lens_delete, getString(R.string.lens_delete_confirm_message, lens.name),
            R.string.lens_delete, true,
            {
                LensWorkspace.deleteLens(lens.id) { lenses ->
                    if (!isFinishing && !isDestroyed) {
                        finishLensDeletion(lens.id, lenses)
                    }
                }
            },
            {
                lensGroupDialog = null
                activeDialogKind = null
                activeDialogLensId = null
            }
        )
        lensGroupDialog = dialog
        dialog.show()
    }

    private fun finishLensDeletion(deletedId: String, lenses: List<LensWorkspace>) {
        selectedLensIds.remove(deletedId)
        settings.deleteLensSettings(deletedId)
        com.mckimquyen.util.LensShortcuts.refresh(applicationContext, lenses)
        if (activeLensId == deletedId && lenses.isNotEmpty()) {
            val fallback = lenses.first()
            activeLensId = fallback.id
            settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, fallback.id)
            (application as? com.mckimquyen.app.RApplication)?.appRefreshPipeline?.switchLens(fallback.id)
        }
        refreshAfterLensChange(lenses)
        confirmHaptic(lensOperationFeedback())
    }

    private fun refreshAfterLensChange(lenses: List<LensWorkspace>) {
        adapter.submit(lenses, effectiveCollapsedGroups(), activeLensId)
        findViewById<android.view.View>(R.id.lensManagerSingleLensHint).visibility =
            if (lenses.size == 1) android.view.View.VISIBLE else android.view.View.GONE
        onGroupSelectionChanged(selectedLensIds)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_EDITING, editingGroups)
        outState.putStringArrayList(STATE_SELECTED, ArrayList(selectedLensIds))
        // UI-032: save open dialog state and uncommitted draft text across recreation
        if (lensGroupDialog?.isShowing == true && activeDialogKind != null) {
            outState.putString(STATE_DIALOG_KIND, activeDialogKind)
            activeDialogLensId?.let { outState.putString(STATE_DIALOG_TARGET_LENS_ID, it) }
            val input = lensGroupDialog?.findViewById<EditText>(android.R.id.edit)
            input?.text?.toString()?.let { outState.putString(STATE_DIALOG_TEXT, it) }
        }
    }

    override fun onDestroy() {
        lensRowMenuForTest?.dismiss()
        lensRowMenuForTest = null
        lensGroupDialog?.dismiss()
        lensGroupDialog = null
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lensManagementRoot), null)
        window.statusBarColor = originalStatusBarColor
        window.navigationBarColor = originalNavigationBarColor
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = originalLightStatusBars
            isAppearanceLightNavigationBars = originalLightNavigationBars
        }
        super.onDestroy()
    }

    @androidx.annotation.VisibleForTesting
    fun reloadGroupsForTest() {
        LensWorkspace.loadAll { lenses ->
            if (!isFinishing && !isDestroyed) adapter.submit(lenses, effectiveCollapsedGroups(), activeLensId)
        }
    }

    /**
     * Targets SDK 37, where the system draws edge-to-edge: without consuming insets the toolbar sat
     * under the status bar / cutout and the list's last rows and the group action bar under the
     * gesture bar. Each pass sets "padding from layout + inset" from values captured once, so
     * repeated dispatches (rotation, IME, requestApplyInsets) never accumulate.
     */
    private fun applyEdgeToEdgeInsets() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<android.view.View>(R.id.lensManagementRoot)
        val appBar = findViewById<android.view.View>(R.id.lensManagerAppBar)
        val basePaddingTop = appBar.paddingTop
        val baseListBottom = list.paddingBottom
        val baseActionsBottom = groupActions.paddingBottom
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            root.setPadding(bars.left, root.paddingTop, bars.right, root.paddingBottom)
            appBar.setPadding(appBar.paddingLeft, basePaddingTop + bars.top, appBar.paddingRight, appBar.paddingBottom)
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, baseListBottom + bars.bottom)
            groupActions.setPadding(
                groupActions.paddingLeft, groupActions.paddingTop, groupActions.paddingRight,
                baseActionsBottom + bars.bottom
            )
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    private fun effectiveCollapsedGroups(): Set<String> =
        cachedCollapsedGroups ?: settings.getCollapsedLensGroups().also { cachedCollapsedGroups = it }

    private fun reloadGroups() = reloadGroupsForTest()

    /** One title, two views: the collapsing layout draws it, the toolbar keeps the text for a11y. */
    private fun setScreenTitle(title: String) {
        managementToolbar.title = title
        collapsingToolbar.title = title
    }

    private fun beginManagingLens(lensId: String) {
        if (lensId.isBlank()) return
        editingGroups = true
        selectedLensIds.add(lensId)
        adapter.setGroupEditSelection(selectedLensIds.toSet())
        onGroupSelectionChanged(selectedLensIds)
    }

    private fun onGroupSelectionChanged(ids: Set<String>) {
        setScreenTitle(
            if (editingGroups) getString(R.string.lens_group_selected_count, ids.size)
            else getString(R.string.lens_management_title)
        )
        updateGroupActionsVisibility(editingGroups)
        val canEditSelection = editingGroups && ids.isNotEmpty() && !groupOperationInProgress
        groupActions.findViewById<com.google.android.material.button.MaterialButton>(R.id.btLensGroupAssign).apply {
            isEnabled = canEditSelection
        }
        groupActions.findViewById<com.google.android.material.button.MaterialButton>(R.id.btLensGroupUngroup).apply {
            isEnabled = canEditSelection
        }
        managementToolbar.menu.findItem(R.id.menuLensGroupEditToggle).isVisible = !editingGroups
        managementToolbar.menu.findItem(R.id.menuLensGroupEditDone)?.isVisible = editingGroups
    }

    private fun updateGroupActionsVisibility(show: Boolean) {
        val targetVisibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        if (show && groupActions.visibility == targetVisibility) return
        if (!show && groupActions.isGone) return
        groupActions.animate().cancel()
        if (LensPhysicsPolicy.shouldReduceLensMotion(this)) {
            groupActions.visibility = targetVisibility
            groupActions.translationY = 0f
            groupActions.alpha = 1f
            return
        }
        if (show) {
            groupActions.visibility = android.view.View.VISIBLE
            // Before the first layout pass the bar has no height, so start translated by a
            // minimum offset; the settle animation then runs to rest.
            groupActions.translationY = groupActions.height.toFloat().coerceAtLeast(
                ACTION_BAR_START_OFFSET_DP * resources.displayMetrics.density
            )
            groupActions.alpha = 0f
            groupActions.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(ACTION_BAR_DURATION_MS)
                .start()
        } else {
            groupActions.animate().cancel()
            groupActions.visibility = android.view.View.GONE
            groupActions.translationY = 0f
            groupActions.alpha = 1f
        }
    }

    private fun toggleEditMode() {
        if (groupOperationInProgress) return
        editingGroups = !editingGroups
        if (!editingGroups) {
            selectedLensIds.clear()
            adapter.setGroupEditSelection(emptySet())
        }
        managementToolbar.menu.findItem(R.id.menuLensGroupEditToggle).isVisible = !editingGroups
        managementToolbar.menu.findItem(R.id.menuLensGroupEditDone).isVisible = editingGroups
        onGroupSelectionChanged(selectedLensIds)
        androidx.core.view.ViewCompat.requestApplyInsets(groupActions)
    }

    private fun showAssignGroupDialog(restoredSelectedIds: Set<String>? = null) {
        restoredSelectedIds?.let { ids ->
            selectedLensIds.clear()
            selectedLensIds.addAll(ids)
            adapter.setGroupEditSelection(selectedLensIds.toSet())
            editingGroups = true
            onGroupSelectionChanged(selectedLensIds)
        }
        val selectedIds = selectedLensIds.toSet()
        if (!editingGroups || selectedIds.isEmpty() || groupOperationInProgress) return
        groupOperationInProgress = true
        activeDialogKind = DIALOG_KIND_ASSIGN_GROUP
        onGroupSelectionChanged(selectedLensIds)
        val version = ++groupLoadVersion
        LensWorkspace.loadAll { lenses ->
            if (isFinishing || isDestroyed || version != groupLoadVersion) return@loadAll
            try {
                val groupNames = LensGroupPolicy.sections(lenses).mapNotNull { it.groupName }.distinct()
                val dialog = com.mckimquyen.ui.dialog.LensDialogs.choiceSheet(
                    this, R.string.lens_group_edit_title, groupNames, R.string.lens_group_create,
                    { index ->
                        if (index in groupNames.indices) {
                            groupOperationInProgress = false
                            activeDialogKind = null
                            assignGroup(selectedIds, groupNames[index])
                        }
                    },
                    {
                        groupOperationInProgress = false
                        activeDialogKind = null
                        showCreateGroupDialog(selectedIds)
                    },
                    {
                        groupOperationInProgress = false
                        activeDialogKind = null
                        onGroupSelectionChanged(selectedLensIds)
                    }
                )
                lensGroupDialog = dialog
                dialog.setOnDismissListener {
                    if (lensGroupDialog === dialog) lensGroupDialog = null
                    if (activeDialogKind == DIALOG_KIND_ASSIGN_GROUP) activeDialogKind = null
                }
                dialog.show()
            } catch (error: Exception) {
                groupPickerFailureForTest = error
                if (version == groupLoadVersion) setGroupOperationIdle()
                com.mckimquyen.util.Logger.e("LensManagementActivity: failed to render group picker", error)
                Toast.makeText(this, R.string.lens_group_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateGroupDialog(selectedIds: Set<String>, draftText: String? = null) {
        groupOperationInProgress = false
        lensGroupDialog = null
        activeDialogKind = DIALOG_KIND_CREATE_GROUP
        onGroupSelectionChanged(selectedLensIds)
        val dialogHolder = arrayOfNulls<android.app.Dialog>(1)
        val dialog = com.mckimquyen.ui.dialog.LensDialogs.textInput(
            this, R.string.lens_group_create, R.string.lens_group_name_hint, draftText,
            R.string.lens_group_error_blank,
            { name -> assignGroup(selectedIds, name) },
            {
                if (lensGroupDialog === dialogHolder[0]) {
                    lensGroupDialog = null
                    activeDialogKind = null
                }
            }
        )
        dialogHolder[0] = dialog
        lensGroupDialog = dialog
        dialog.show()
    }

    private fun setGroupOperationIdle() {
        groupOperationInProgress = false
        if (!isFinishing && !isDestroyed) onGroupSelectionChanged(adapter.selectedIds())
    }

    private fun assignGroup(selectedIds: Set<String>, name: String) {
        val canonicalInput = LensGroupPolicy.normalizeName(name) ?: run {
            Toast.makeText(this, R.string.lens_group_error_blank, Toast.LENGTH_SHORT).show()
            return
        }
        if (groupOperationInProgress) return
        groupOperationInProgress = true
        onGroupSelectionChanged(selectedLensIds)
        lifecycleScope.launch {
            try {
                val canonical = withContext(Dispatchers.IO) {
                    val currentGroups = LensGroupPolicy.canonicalize(dao.getAll())
                    currentGroups.firstOrNull {
                        it.groupName?.let(LensGroupPolicy::canonicalKey) == LensGroupPolicy.canonicalKey(canonicalInput)
                    }?.groupName ?: canonicalInput
                }
                withContext(Dispatchers.IO) { dao.assignGroup(selectedIds, canonical) }
                reloadGroups()
                confirmHaptic(confirmFeedbackConstant())
                setGroupOperationIdle()
                toggleEditMode()
            } catch (error: Exception) {
                lastGroupOperationError = error
                setGroupOperationIdle()
                Toast.makeText(this@LensManagementActivity, R.string.lens_group_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ungroupSelected() {
        val selectedIds = selectedLensIds.toSet()
        if (!editingGroups || selectedIds.isEmpty() || groupOperationInProgress) return
        groupOperationInProgress = true
        onGroupSelectionChanged(selectedLensIds)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { dao.assignGroup(selectedIds, null) }
                reloadGroups()
                confirmHaptic(confirmFeedbackConstant())
                setGroupOperationIdle()
                toggleEditMode()
            } catch (error: Exception) {
                setGroupOperationIdle()
                Toast.makeText(this@LensManagementActivity, R.string.lens_group_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    val editingGroupsForTest: Boolean
        get() = editingGroups

    @androidx.annotation.VisibleForTesting
    val selectedLensIdsForTest: Set<String>
        get() = selectedLensIds.toSet()

    private fun selectLens(lensId: String) {
        val intent = Intent(this, ActHome::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ActHome.EXTRA_TARGET_LENS_ID, lensId)
        startActivity(intent)
        finish()
    }
}
