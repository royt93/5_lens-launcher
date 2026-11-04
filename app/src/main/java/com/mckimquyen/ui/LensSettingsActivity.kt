package com.mckimquyen.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensOpticalProfile
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.OpticalProfileParseResult
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.ui.dialog.LensDialogs
import com.mckimquyen.adt.LensAppChoiceAdapter
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.LensAppScopeEditor
import com.mckimquyen.util.LensFreezeEditor
import com.mckimquyen.util.LensHood
import com.mckimquyen.util.LensOpticalProfileIo
import com.mckimquyen.util.OpticalProfileApplyResult
import com.mckimquyen.util.OpticalProfileReadResult
import com.mckimquyen.util.LensProfile
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
        private const val DIALOG_OPTICAL = "optical"
        private const val STATE_OPTICAL_PREVIEW = "lensSettingsOpticalPreview"
        private const val STATE_OPTICAL_EXPORT = "lensSettingsOpticalExport"
        private const val PERCENT = 100
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
    private var pendingOpticalPreview: String? = null
    private var pendingOpticalExport: String? = null

    // Launcher phải đăng ký trước STARTED; target luôn là EXTRA_LENS_ID, không phải active lens.
    private val exportOpticalLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(LensOpticalProfile.MIME_TYPE)
    ) { uri -> onOpticalExportPicked(uri) }
    private val importOpticalLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onOpticalImportPicked(uri) }

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
        pendingOpticalPreview = savedInstanceState?.getString(STATE_OPTICAL_PREVIEW)
        pendingOpticalExport = savedInstanceState?.getString(STATE_OPTICAL_EXPORT)
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
        findViewById<Slider>(R.id.sbLensSettingsHood).addOnChangeListener { _, value, fromUser ->
            val percent = Math.round(value * 100)
            findViewById<TextView>(R.id.tvLensSettingsHood).text = getString(R.string.lens_hood_value_format, percent)
            if (fromUser && ready) targetId?.let { utilSettings?.saveLensHood(it, value) }
        }
        findViewById<Slider>(R.id.sbLensSettingsDistortion).addOnChangeListener { _, value, fromUser ->
            findViewById<TextView>(R.id.tvLensSettingsDistortion).text = String.format(java.util.Locale.getDefault(), "%.1f", value)
            if (fromUser && ready) targetId?.let { utilSettings?.saveDistortionFactor(it, value) }
        }
        smartFocusListener = android.widget.CompoundButton.OnCheckedChangeListener { _, checked ->
            if (ready && !restoring) targetId?.let { utilSettings?.saveSmartFocusBias(it, checked) }
        }
        findViewById<MaterialSwitch>(R.id.swLensSettingsSmartFocus).setOnCheckedChangeListener(smartFocusListener)
        findViewById<MaterialButtonToggleGroup>(R.id.tgLensSettingsProfile).addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && ready && !restoring) {
                targetId?.let { utilSettings?.saveLensProfile(it, profileForButton(checkedId)) }
            }
        }
        findViewById<View>(R.id.btLensSettingsExportProfile).setOnClickListener { startOpticalExport() }
        findViewById<View>(R.id.btLensSettingsImportProfile).setOnClickListener { startOpticalImport() }
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
                        DIALOG_OPTICAL -> restoreOpticalPreview()
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
        val hood = LensHood.snap(settings.getLensHood(id))
        findViewById<Slider>(R.id.sbLensSettingsHood).value = hood
        findViewById<TextView>(R.id.tvLensSettingsHood).text = getString(
            R.string.lens_hood_value_format, Math.round(hood * 100))
        findViewById<MaterialSwitch>(R.id.swLensSettingsSmartFocus).isChecked = settings.isSmartFocusBias(id)
        findViewById<MaterialButtonToggleGroup>(R.id.tgLensSettingsProfile).check(buttonForProfile(settings.getLensProfile(id)))
        restoring = false
        findViewById<MaterialButton>(R.id.btLensSettingsFreeze).setText(
            if (settings.isLensFrozen(id)) R.string.lens_unfreeze_positions else R.string.lens_freeze_positions
        )
    }

    private fun setEnabled(enabled: Boolean) {
        intArrayOf(R.id.sbLensSettingsIconSize, R.id.sbLensSettingsDistortion, R.id.sbLensSettingsHood, R.id.swLensSettingsSmartFocus,
            R.id.tgLensSettingsProfile, R.id.btLensProfileFisheye, R.id.btLensProfileAnamorphic, R.id.btLensProfileCylindrical, R.id.btLensProfilePincushion,
            R.id.btLensSettingsApps, R.id.btLensSettingsFreeze, R.id.btLensSettingsHidden,
            R.id.btLensSettingsExportProfile, R.id.btLensSettingsImportProfile)
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

    private fun startOpticalExport() {
        val id = targetId ?: return
        val settings = utilSettings ?: return
        if (!ready || busy) return
        val snapshot = settings.captureOpticalProfile(id)
        if (snapshot == null) { toast(R.string.lens_optical_export_failed); return }
        pendingOpticalExport = snapshot.toJson()
        exportOpticalLauncher.launch(LensOpticalProfile.FILE_NAME)
    }

    private fun onOpticalExportPicked(uri: Uri?) {
        val json = pendingOpticalExport
        pendingOpticalExport = null
        if (uri == null || json == null) return
        val profile = (LensOpticalProfile.parse(json) as? OpticalProfileParseResult.Success)?.value
        if (profile == null) { toast(R.string.lens_optical_export_failed); return }
        setBusy(true)
        lifecycleScope.launch {
            try {
                val ok = LensOpticalProfileIo.write(contentResolver, uri, profile)
                toast(if (ok) R.string.lens_optical_export_success else R.string.lens_optical_export_failed)
            } finally { setBusy(false) }
        }
    }

    private fun startOpticalImport() {
        if (!ready || busy) return
        importOpticalLauncher.launch(arrayOf(LensOpticalProfile.MIME_TYPE))
    }

    private fun onOpticalImportPicked(uri: Uri?) {
        if (uri == null) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                when (val result = LensOpticalProfileIo.read(contentResolver, uri)) {
                    is OpticalProfileReadResult.Success -> {
                        if (targetExists()) showOpticalPreview(result.value) else toast(R.string.lens_optical_target_missing)
                    }
                    OpticalProfileReadResult.Malformed -> toast(R.string.lens_optical_invalid)
                    OpticalProfileReadResult.UnsupportedVersion -> toast(R.string.lens_optical_unsupported)
                    OpticalProfileReadResult.TooLarge -> toast(R.string.lens_optical_too_large)
                    OpticalProfileReadResult.IoFailure -> toast(R.string.lens_optical_read_failed)
                }
            } finally { setBusy(false) }
        }
    }

    private suspend fun targetExists(): Boolean {
        val id = targetId ?: return false
        return withContext(Dispatchers.IO) { AppDatabase.getInstance().lensWorkspaceDao().findById(id) != null }
    }

    private fun restoreOpticalPreview() {
        val saved = pendingOpticalPreview
        pendingOpticalPreview = null
        val profile = (saved?.let(LensOpticalProfile::parse) as? OpticalProfileParseResult.Success)?.value ?: return
        showOpticalPreview(profile)
    }

    private fun showOpticalPreview(profile: LensOpticalProfile) {
        val id = targetId ?: return
        val settings = utilSettings ?: return
        val current = settings.captureOpticalProfile(id)
        if (current == null) { toast(R.string.lens_optical_read_failed); return }
        showDialog(LensDialogs.confirm(this, R.string.lens_optical_preview_title,
            opticalPreviewText(id, current, profile), android.R.string.ok, false,
            { applyOptical(profile) }, null), DIALOG_OPTICAL)
        previewProfile = profile
    }

    private var previewProfile: LensOpticalProfile? = null

    private fun opticalPreviewText(id: String, current: LensOpticalProfile, incoming: LensOpticalProfile): String {
        fun change(label: Int, from: String, to: String) = getString(R.string.lens_optical_change_format, getString(label), from, to)
        fun profileName(p: com.mckimquyen.util.LensProfile) = getString(when (p) {
            com.mckimquyen.util.LensProfile.ANAMORPHIC -> R.string.lens_profile_anamorphic
            com.mckimquyen.util.LensProfile.CYLINDRICAL -> R.string.lens_profile_cylindrical
            com.mckimquyen.util.LensProfile.PINCUSHION -> R.string.lens_profile_pincushion
            com.mckimquyen.util.LensProfile.FISHEYE -> R.string.lens_profile_fisheye
        })
        fun number(v: Float) = String.format(java.util.Locale.getDefault(), "%.2f", v)
        fun percent(v: Float) = getString(R.string.lens_hood_value_format, Math.round(v * PERCENT))
        fun onOff(v: Boolean) = getString(if (v) R.string.lens_optical_smart_on else R.string.lens_optical_smart_off)
        val lines = mutableListOf<String>()
        lines += getString(R.string.lens_optical_target_group)
        lines += change(R.string.lens_profile_title, profileName(current.profile), profileName(incoming.profile))
        lines += change(R.string.setting_distortion_factor, number(current.distortionFactor), number(incoming.distortionFactor))
        lines += change(R.string.setting_min_icon_size, number(current.iconSize), number(incoming.iconSize))
        lines += change(R.string.lens_hood_title, percent(current.hood), percent(incoming.hood))
        lines += change(R.string.setting_smart_focus, onOff(current.smartFocusBias), onOff(incoming.smartFocusBias))
        lines += ""
        lines += getString(R.string.lens_optical_global_group)
        lines += change(R.string.setting_scale_factor, number(current.scaleFactor), number(incoming.scaleFactor))
        lines += change(R.string.setting_animation_time, getString(R.string.unit_ms_format, current.animationTimeMs.toInt()),
            getString(R.string.unit_ms_format, incoming.animationTimeMs.toInt()))
        lines += getString(R.string.lens_optical_global_warning)
        lines += ""
        lines += getString(R.string.lens_optical_custom_group)
        incoming.custom?.let { saved ->
            val old = current.custom
            lines += change(R.string.setting_distortion_factor, old?.distortionFactor?.let(::number) ?: "-", number(saved.distortionFactor))
            lines += change(R.string.setting_scale_factor, old?.scaleFactor?.let(::number) ?: "-", number(saved.scaleFactor))
            lines += change(R.string.setting_animation_time, old?.let { getString(R.string.unit_ms_format, it.animationTimeMs.toInt()) } ?: "-",
                getString(R.string.unit_ms_format, saved.animationTimeMs.toInt()))
        } ?: run { lines += getString(R.string.lens_optical_custom_unchanged) }
        if (id == LensWorkspace.DEFAULT_LENS_ID) { lines += ""; lines += getString(R.string.lens_optical_inheritance_warning) }
        return lines.joinToString("\n")
    }

    private fun applyOptical(profile: LensOpticalProfile) {
        val id = targetId ?: return
        val settings = utilSettings ?: return
        if (busy) return
        pendingOpticalPreview = null
        setBusy(true)
        lifecycleScope.launch {
            try {
                // Phần ghi cục bộ không bị hủy giữa chừng để không để lại batch dở dang.
                val outcome = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    val dao = AppDatabase.getInstance().lensWorkspaceDao()
                    if (dao.findById(id) == null) return@withContext null
                    val result = settings.applyOpticalProfile(id, profile)
                    if (result == OpticalProfileApplyResult.APPLIED && dao.findById(id) == null) {
                        settings.deleteLensSettings(id)
                        null
                    } else result
                }
                when (outcome) {
                    null -> toast(R.string.lens_optical_target_missing)
                    OpticalProfileApplyResult.APPLIED -> {
                        AppEventManager.notifyLensScopeChanged(id)
                        toast(R.string.lens_optical_import_success)
                        ready = false; bind(); ready = true
                    }
                    OpticalProfileApplyResult.FAILED_RESTORED -> toast(R.string.lens_optical_import_failed)
                    OpticalProfileApplyResult.RESTORE_FAILED -> toast(R.string.lens_optical_restore_failed)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } finally { setBusy(false) }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        setEnabled(ready && !busy)
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()

    override fun onSaveInstanceState(outState: Bundle) {
        pendingOpticalExport?.let { outState.putString(STATE_OPTICAL_EXPORT, it) }
        if (dialogKind == DIALOG_OPTICAL) previewProfile?.let { outState.putString(STATE_OPTICAL_PREVIEW, it.toJson()) }
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

    private fun profileForButton(id: Int): LensProfile = when (id) {
        R.id.btLensProfileAnamorphic -> LensProfile.ANAMORPHIC
        R.id.btLensProfileCylindrical -> LensProfile.CYLINDRICAL
        R.id.btLensProfilePincushion -> LensProfile.PINCUSHION
        else -> LensProfile.FISHEYE
    }

    private fun buttonForProfile(profile: LensProfile): Int = when (profile) {
        LensProfile.ANAMORPHIC -> R.id.btLensProfileAnamorphic
        LensProfile.CYLINDRICAL -> R.id.btLensProfileCylindrical
        LensProfile.PINCUSHION -> R.id.btLensProfilePincushion
        LensProfile.FISHEYE -> R.id.btLensProfileFisheye
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
