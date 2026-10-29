package com.mckimquyen.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.ui.dialog.LensDialogs
import com.mckimquyen.adt.LensAppChoiceAdapter
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.LensAppScopeEditor
import com.mckimquyen.util.LensFreezeEditor
import com.mckimquyen.util.SmartFocusArranger
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilCalculator
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cài đặt luôn thuộc lens trong Intent, không đổi lens đang active. */
class LensSettingsActivity : ActBase() {
    companion object {
        const val EXTRA_LENS_ID = "com.mckimquyen.lenslauncher.EXTRA_LENS_SETTINGS_ID"
        private const val STATE_DIALOG = "lensSettingsDialog"
        private const val STATE_CHECKED = "lensSettingsChecked"
        private const val DIALOG_APPS = "apps"
        private const val DIALOG_HIDDEN = "hidden"
        private const val DIALOG_UNFREEZE = "unfreeze"
        private const val DISTORTION_MAX = 5f
        private const val ICON_STEP = 1f
        private const val LIGHT_SURFACE_LUMINANCE = 0.5f
        private const val DISTORTION_STEP = 0.1f

        /** Slider từ chối giá trị lệch bước, trong khi prefs có thể giữ float tùy ý. */
        internal fun snap(value: Float, min: Float, max: Float, step: Float): Float {
            val safe = if (value.isFinite()) value.coerceIn(min, max) else min
            return (min + Math.round((safe - min) / step) * step).coerceIn(min, max)
        }
    }

    private var targetId: String? = null
    private var ready = false
    private var busy = false
    private var dialog: AlertDialog? = null
    private var dialogKind: String? = null
    private var pendingDialog: String? = null
    private var pendingChecked: Set<String>? = null
    private var smartFocusListener: android.widget.CompoundButton.OnCheckedChangeListener? = null
    private var restoring = false
    private var targetApps: List<App> = emptyList()

    @androidx.annotation.VisibleForTesting
    internal val dialogForTest: AlertDialog? get() = dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.act_lens_settings)
        // Nền theo colorSurface; icon status/navigation bar phải tương phản với nền, không để mặc định trắng.
        val surface = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE)
        val lightSurface = android.graphics.Color.luminance(surface) > LIGHT_SURFACE_LUMINANCE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightSurface
            isAppearanceLightNavigationBars = lightSurface
        }
        targetId = intent.getStringExtra(EXTRA_LENS_ID)?.takeIf(String::isNotBlank)
        if (targetId == null) { finish(); return }
        pendingDialog = savedInstanceState?.getString(STATE_DIALOG)
        pendingChecked = savedInstanceState?.getStringArrayList(STATE_CHECKED)?.toSet()
        findViewById<MaterialToolbar>(R.id.tbLensSettings).apply {
            setNavigationContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description)
            setNavigationOnClickListener { finish() }
        }
        val root = findViewById<View>(R.id.lensSettingsRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        setupControls()
        setEnabled(false)
    }

    override fun onResume() {
        super.onResume()
        if (targetId != null) loadTarget()
    }

    private fun setupControls() {
        findViewById<Slider>(R.id.sbLensSettingsIconSize).addOnChangeListener { _, value, fromUser ->
            findViewById<TextView>(R.id.tvLensSettingsIconSize).text = getString(R.string.unit_dp_format, value.toInt())
            if (fromUser && ready) targetId?.let { utilSettings?.saveIconSize(it, value) }
        }
        findViewById<Slider>(R.id.sbLensSettingsDistortion).addOnChangeListener { _, value, fromUser ->
            findViewById<TextView>(R.id.tvLensSettingsDistortion).text = String.format(java.util.Locale.getDefault(), "%.1f", value)
            if (fromUser && ready) targetId?.let { utilSettings?.saveDistortionFactor(it, value) }
        }
        smartFocusListener = android.widget.CompoundButton.OnCheckedChangeListener { _, checked ->
            if (ready && !restoring) targetId?.let { utilSettings?.saveSmartFocusBias(it, checked) }
        }
        findViewById<MaterialSwitch>(R.id.swLensSettingsSmartFocus).setOnCheckedChangeListener(smartFocusListener)
        findViewById<View>(R.id.btLensSettingsApps).setOnClickListener { showApps(false) }
        findViewById<View>(R.id.btLensSettingsHidden).setOnClickListener { showApps(true) }
        findViewById<View>(R.id.btLensSettingsFreeze).setOnClickListener {
            val id = targetId ?: return@setOnClickListener
            val settings = utilSettings ?: return@setOnClickListener
            if (settings.isLensFrozen(id)) showUnfreeze() else freeze(true)
        }
    }

    private fun loadTarget() {
        val id = targetId ?: return
        ready = false
        setEnabled(false)
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val lens = AppDatabase.getInstance().lensWorkspaceDao().findById(id)
                    val settings = utilSettings ?: return@withContext null
                    lens?.let { it to UtilApp.mergeLensPersistence(RAppsSingleton.instance.apps.orEmpty(), id, settings.sortType) }
                }
                if (data == null) { finish(); return@launch }
                targetApps = data.second
                findViewById<MaterialToolbar>(R.id.tbLensSettings).title = data.first.name
                bind()
                ready = true
                setEnabled(!busy)
                val kind = pendingDialog
                pendingDialog = null
                if (kind != null) {
                    when (kind) {
                        DIALOG_APPS -> showApps(false, pendingChecked)
                        DIALOG_HIDDEN -> showApps(true, pendingChecked)
                        DIALOG_UNFREEZE -> showUnfreeze()
                    }
                    pendingChecked = null
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                com.mckimquyen.util.Logger.e("LensSettingsActivity: load failed for $id", error)
                showFailure(); finish()
            }
        }
    }

    private fun bind() {
        val id = targetId ?: return
        val settings = utilSettings ?: return
        restoring = true
        findViewById<Slider>(R.id.sbLensSettingsIconSize).value = snap(
            settings.getIconSize(id), UtilSettings.MIN_ICON_SIZE, UtilSettings.MIN_ICON_SIZE + UtilSettings.MAX_ICON_SIZE, ICON_STEP)
        findViewById<Slider>(R.id.sbLensSettingsDistortion).value = snap(
            settings.getDistortionFactor(id), UtilSettings.MIN_DISTORTION_FACTOR, DISTORTION_MAX, DISTORTION_STEP)
        findViewById<TextView>(R.id.tvLensSettingsIconSize).text = getString(R.string.unit_dp_format,
            findViewById<Slider>(R.id.sbLensSettingsIconSize).value.toInt())
        findViewById<TextView>(R.id.tvLensSettingsDistortion).text = String.format(java.util.Locale.getDefault(), "%.1f",
            findViewById<Slider>(R.id.sbLensSettingsDistortion).value)
        findViewById<MaterialSwitch>(R.id.swLensSettingsSmartFocus).isChecked = settings.isSmartFocusBias(id)
        restoring = false
        findViewById<MaterialButton>(R.id.btLensSettingsFreeze).setText(
            if (settings.isLensFrozen(id)) R.string.lens_unfreeze_positions else R.string.lens_freeze_positions
        )
    }

    private fun setEnabled(enabled: Boolean) {
        intArrayOf(R.id.sbLensSettingsIconSize, R.id.sbLensSettingsDistortion, R.id.swLensSettingsSmartFocus,
            R.id.btLensSettingsApps, R.id.btLensSettingsFreeze, R.id.btLensSettingsHidden)
            .forEach { findViewById<View>(it).isEnabled = enabled }
    }

    private fun showApps(hiddenOnly: Boolean, restored: Set<String>? = null) {
        val id = targetId ?: return
        val settings = utilSettings ?: return
        val apps = if (hiddenOnly) targetApps.filter { it.identifier in settings.getLensHiddenAppIds(id) }
            else targetApps.filter { it.isVisible }
        dialog?.dismiss()
        if (apps.isEmpty()) {
            showDialog(LensDialogs.confirm(this, R.string.lens_hidden_apps,
                getString(R.string.lens_hidden_apps_empty), android.R.string.cancel, false, {}, null), null)
            return
        }
        val ids = apps.map { it.identifier }
        val checked = restored ?: if (hiddenOnly) emptySet()
            else LensAppScopeEditor(settings).effectiveIds(id, ids.toSet())
        val seed = ids.indices.filter { ids[it] in checked }.toSet()
        val picker = LensDialogs.multiChoice(this,
            if (hiddenOnly) R.string.lens_hidden_apps else R.string.lens_choose_apps,
            LensAppChoiceAdapter(this, ArrayList(apps)), seed,
            if (hiddenOnly) R.string.lens_restore_apps else android.R.string.ok,
            { selected ->
                val picked = selected.mapNotNull { ids.getOrNull(it) }.toSet()
                lifecycleScope.launch {
                    val exists = withContext(Dispatchers.IO) { AppDatabase.getInstance().lensWorkspaceDao().findById(id) != null }
                    if (!exists) { finish(); return@launch }
                    if (hiddenOnly) {
                        settings.saveLensHiddenAppIds(id, settings.getLensHiddenAppIds(id) - picked)
                        AppEventManager.notifyLensScopeChanged(id)
                    } else LensAppScopeEditor(settings).apply(id, picked)
                }
            }, null)
        showDialog(picker, if (hiddenOnly) DIALOG_HIDDEN else DIALOG_APPS)
    }

    private fun showUnfreeze() {
        showDialog(LensDialogs.confirm(this, R.string.lens_unfreeze_title,
            getString(R.string.lens_unfreeze_message), R.string.lens_unfreeze_positions,
            false, { freeze(false) }, null), DIALOG_UNFREEZE)
    }

    private fun showDialog(next: AlertDialog, kind: String?) {
        dialog?.dismiss()
        dialog = next
        dialogKind = kind
        next.setOnDismissListener { if (dialog === next) { dialog = null; dialogKind = null } }
        next.show()
    }

    private fun freeze(frozen: Boolean) {
        val id = targetId ?: return
        val settings = utilSettings ?: return
        if (busy) return
        busy = true
        setEnabled(false)
        lifecycleScope.launch {
            try {
                val displayed = if (frozen) displayedTargetApps(id, settings) else emptyList()
                withContext(Dispatchers.IO) { LensFreezeEditor(AppDatabase.getInstance(), settings).setFrozen(id, frozen, displayed) }
                val active = settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID) ?: LensWorkspace.DEFAULT_LENS_ID
                if (id == active) (application as? RApplication)?.appRefreshPipeline?.switchLens(id)
                AppEventManager.notifyLensScopeChanged(id)
                ready = false
                bind()
                ready = true
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { showFailure()
            } finally { busy = false; setEnabled(ready) }
        }
    }

    private suspend fun displayedTargetApps(id: String, settings: UtilSettings): List<App> {
        val merged = withContext(Dispatchers.IO) {
            UtilApp.mergeLensPersistence(RAppsSingleton.instance.apps.orEmpty(), id, settings.sortType)
        }
        val visible = LensAppScope.filter(merged.filter { it.isVisible }, settings.getLensAppScope(id),
            settings.getLensAppSelection(id), settings.getLensHiddenAppIds(id))
        if (!settings.isSmartFocusBias(id)) return visible
        val root = findViewById<View>(R.id.lensSettingsRoot)
        val bars = ViewCompat.getRootWindowInsets(root)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val width = root.width - (bars?.left ?: 0) - (bars?.right ?: 0)
        val top = (bars?.top ?: 0) + resources.getDimensionPixelSize(R.dimen.home_search_top_margin) +
            resources.getDimensionPixelSize(R.dimen.home_search_height) + resources.getDimensionPixelSize(R.dimen.home_search_to_grid_gap)
        val height = root.height - top - (bars?.bottom ?: 0)
        val maxWidth = resources.getDimensionPixelSize(R.dimen.home_content_max_width)
        val grid = UtilCalculator.calculateGrid(this, width.coerceAtMost(maxWidth), height, visible.size, settings.getIconSize(id))
        return SmartFocusArranger.arrange(visible, grid.itemCountHorizontal, grid.itemCountVertical, true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        dialogKind?.let { kind ->
            outState.putString(STATE_DIALOG, kind)
            if (kind == DIALOG_APPS || kind == DIALOG_HIDDEN) {
                val apps = if (kind == DIALOG_HIDDEN) targetApps.filter { it.identifier in utilSettings?.getLensHiddenAppIds(targetId).orEmpty() }
                    else targetApps.filter { it.isVisible }
                val checks = dialog?.listView?.checkedItemPositions
                val ids = apps.indices.filter { checks?.get(it) == true }.map { apps[it].identifier }
                outState.putStringArrayList(STATE_CHECKED, ArrayList(ids))
            }
        }
        super.onSaveInstanceState(outState)
    }

    private fun showFailure() = Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        smartFocusListener = null
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lensSettingsRoot), null)
        super.onDestroy()
    }
}
