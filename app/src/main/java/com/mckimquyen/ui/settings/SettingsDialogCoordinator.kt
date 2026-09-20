package com.mckimquyen.ui.settings

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mckimquyen.R
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.SortType
import com.mckimquyen.ext.showDialog2
import com.mckimquyen.util.UtilIconPackManager
import com.mckimquyen.util.UtilNightModeUtil
import com.mckimquyen.util.UtilSettings
import java.util.EnumSet

/**
 * ARCH-001: Coordinates all Material You settings dialogs and ensures safe dismissal.
 */
class SettingsDialogCoordinator {

    var dlgSortType: AlertDialog? = null
        internal set
    var dlgIconPack: AlertDialog? = null
        internal set
    var dlgNightMode: AlertDialog? = null
        internal set
    var dlgBackground: AlertDialog? = null
        internal set
    var dlgHighlightColor: AlertDialog? = null
        internal set
    var dlgTerms: AlertDialog? = null
        internal set

    companion object {
        @JvmField
        val COLOR_HEXES = arrayOf(
            "#FFF50057",
            "#FFF44336",
            "#FFFF5722",
            "#FFFF9800",
            "#FFFFC107",
            "#FF4CAF50",
            "#FF009688",
            "#FF00BCD4",
            "#FF2196F3",
            "#FF3F51B5",
            "#FF673AB7",
            "#FF9C27B0",
            "#FF607D8B",
            "#FF212121",
            "#FFFFFFFF"
        )

        @JvmField
        val COLOR_NAMES = arrayOf(
            "Rose",
            "Red",
            "Deep Orange",
            "Orange",
            "Amber",
            "Green",
            "Teal",
            "Cyan",
            "Blue",
            "Indigo",
            "Deep Purple",
            "Purple",
            "Blue Grey",
            "Dark Neutral",
            "Light Neutral"
        )

        @JvmStatic
        fun findColorIndex(hex: String?): Int {
            if (hex == null) return 0
            val idx = COLOR_HEXES.indexOfFirst { it.equals(hex, ignoreCase = true) }
            return if (idx >= 0) idx else 0
        }

        @JvmStatic
        fun createColorAdapter(context: Context): ArrayAdapter<String> {
            return object : ArrayAdapter<String>(
                context,
                android.R.layout.select_dialog_singlechoice,
                android.R.id.text1,
                COLOR_NAMES
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val textView = view.findViewById<TextView>(android.R.id.text1)
                    if (textView != null) {
                        val dot = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(COLOR_HEXES[position].toColorInt())
                            val size = (20 * context.resources.displayMetrics.density).toInt()
                            setSize(size, size)
                            setBounds(0, 0, size, size)
                        }
                        textView.setCompoundDrawablesRelative(dot, null, null, null)
                        textView.compoundDrawablePadding = (16 * context.resources.displayMetrics.density).toInt()
                    }
                    return view
                }
            }
        }
    }

    fun showSortTypeDialog(
        activity: Activity,
        utilSettings: UtilSettings,
        onSortTypeSelected: (SortType) -> Unit
    ) {
        dismissSortTypeDialog()
        val allSortTypes = ArrayList(EnumSet.allOf(SortType::class.java))
        val sortTypeStrings = allSortTypes.map { activity.getString(it.displayNameResId) }
        val selectedIndex = allSortTypes.indexOf(utilSettings.sortType)

        dlgSortType = MaterialAlertDialogBuilder(activity, R.style.MaterialYouDialogTheme)
            .setTitle(R.string.setting_sort_apps)
            .setSingleChoiceItems(sortTypeStrings.toTypedArray(), selectedIndex) { dialog, which ->
                val selected = allSortTypes[which]
                utilSettings.save(selected)
                onSortTypeSelected(selected)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showIconPackDialog(
        activity: Activity,
        utilSettings: UtilSettings,
        onIconPackSelected: (String) -> Unit
    ) {
        dismissIconPackDialog()
        val availableIconPacks = UtilIconPackManager().getAvailableIconPacksWithIcons(true, activity.application)
        val displayNames = ArrayList<String>()
        val values = ArrayList<String>()

        displayNames.add(activity.getString(R.string.setting_default_icon_pack))
        values.add(UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME)

        for (pack in availableIconPacks) {
            val name = pack.mName
            if (!values.contains(name)) {
                displayNames.add(name)
                values.add(name)
            }
        }

        val selectedValue = utilSettings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME)
        val selectedIndex = values.indexOf(selectedValue)

        dlgIconPack = MaterialAlertDialogBuilder(activity, R.style.MaterialYouDialogTheme)
            .setTitle(R.string.setting_icon_pack)
            .setSingleChoiceItems(displayNames.toTypedArray(), selectedIndex) { dialog, which ->
                val chosenValue = values[which]
                utilSettings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, chosenValue)
                onIconPackSelected(chosenValue)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showNightModeChooser(
        activity: Activity,
        utilSettings: UtilSettings,
        onNightModeSelected: (Int) -> Unit,
        onRecreateRequested: () -> Unit
    ) {
        dismissNightModeDialog()
        val arrAvailableNightMode = activity.resources.getStringArray(R.array.night_modes)
        val nightModes = ArrayList<String>().apply { addAll(arrAvailableNightMode) }
        val selectedNightMode = UtilNightModeUtil.getNightModeDisplayName(utilSettings.nightMode)
        val selectedIndex = nightModes.indexOf(selectedNightMode)

        dlgNightMode = MaterialAlertDialogBuilder(activity, R.style.MaterialYouDialogTheme)
            .setTitle(R.string.setting_night_mode)
            .setSingleChoiceItems(arrAvailableNightMode, selectedIndex) { dialog, which ->
                val selection = nightModes[which]
                val mode = UtilNightModeUtil.getNightModeFromDisplayName(selection)
                utilSettings.save(UtilSettings.KEY_NIGHT_MODE, mode)
                onNightModeSelected(mode)
                dialog.dismiss()

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!activity.isDestroyed && !activity.isFinishing) {
                        onRecreateRequested()
                    }
                }, 200)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showBackgroundDialog(
        activity: Activity,
        utilSettings: UtilSettings,
        onWallpaperSelected: () -> Unit,
        onColorSelected: () -> Unit
    ) {
        dismissBackgroundDialog()
        val backgroundModes = BackgroundMode.values()
        val bgNames = activity.resources.getStringArray(R.array.backgrounds)
        val selectedIndex = utilSettings.backgroundMode.ordinal

        dlgBackground = MaterialAlertDialogBuilder(activity, R.style.MaterialYouDialogTheme)
            .setTitle(R.string.setting_background)
            .setSingleChoiceItems(bgNames, selectedIndex) { dialog, which ->
                dialog.dismiss()
                val selection = backgroundModes[which]
                if (selection == BackgroundMode.WALLPAPER) {
                    utilSettings.save(BackgroundMode.WALLPAPER)
                    onWallpaperSelected()
                } else if (selection == BackgroundMode.COLOR) {
                    onColorSelected()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showBackgroundColorDialog(
        activity: Activity,
        utilSettings: UtilSettings,
        onColorSelected: (String) -> Unit
    ) {
        val currentHex = utilSettings.getString(UtilSettings.KEY_BACKGROUND_COLOR)
        val selectedIndex = findColorIndex(currentHex)
        val adapter = createColorAdapter(activity)

        MaterialAlertDialogBuilder(activity, R.style.MaterialYouDialogTheme)
            .setTitle(R.string.setting_background)
            .setSingleChoiceItems(adapter, selectedIndex) { dialog, which ->
                val selectedHex = COLOR_HEXES[which]
                utilSettings.save(BackgroundMode.COLOR)
                utilSettings.save(UtilSettings.KEY_BACKGROUND_COLOR, selectedHex)
                onColorSelected(selectedHex)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showHighlightColorDialog(
        activity: Activity,
        utilSettings: UtilSettings,
        onHighlightColorSelected: (String) -> Unit
    ) {
        dismissHighlightColorDialog()
        val currentHex = utilSettings.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)
        val selectedIndex = findColorIndex(currentHex)
        val adapter = createColorAdapter(activity)

        dlgHighlightColor = MaterialAlertDialogBuilder(activity, R.style.MaterialYouDialogTheme)
            .setTitle(R.string.setting_highlight_color)
            .setSingleChoiceItems(adapter, selectedIndex) { dialog, which ->
                val selectedHex = COLOR_HEXES[which]
                utilSettings.save(UtilSettings.KEY_HIGHLIGHT_COLOR, selectedHex)
                onHighlightColorSelected(selectedHex)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showTermsDialog(
        activity: Activity,
        utilSettings: UtilSettings,
        onAgree: () -> Unit,
        onCancel: () -> Unit
    ) {
        dlgTerms = activity.showDialog2(
            title = activity.getString(R.string.terms_and_privacy_policy),
            msg = activity.getString(R.string.read_policy),
            button1 = activity.getString(R.string.agree_and_continue),
            button2 = activity.getString(R.string.cancel),
            onClickButton1 = Runnable {
                utilSettings.save(UtilSettings.KEY_READ_POLICY, true)
                onAgree()
            },
            onClickButton2 = Runnable {
                utilSettings.save(UtilSettings.KEY_READ_POLICY, true)
                onCancel()
            },
            isCancelable = false,
            onDismiss = Runnable {
                utilSettings.save(UtilSettings.KEY_READ_POLICY, true)
                onCancel()
            }
        )
    }

    fun dismissSortTypeDialog() {
        if (dlgSortType?.isShowing == true) {
            dlgSortType?.dismiss()
        }
    }

    fun dismissIconPackDialog() {
        if (dlgIconPack?.isShowing == true) {
            dlgIconPack?.dismiss()
        }
    }

    fun dismissNightModeDialog() {
        if (dlgNightMode?.isShowing == true) {
            dlgNightMode?.dismiss()
        }
    }

    fun dismissBackgroundDialog() {
        if (dlgBackground?.isShowing == true) {
            dlgBackground?.dismiss()
        }
    }

    fun dismissHighlightColorDialog() {
        if (dlgHighlightColor?.isShowing == true) {
            dlgHighlightColor?.dismiss()
        }
    }

    fun dismissAllDialogs() {
        dismissSortTypeDialog()
        dismissIconPackDialog()
        dismissNightModeDialog()
        dismissBackgroundDialog()
        dismissHighlightColorDialog()
        if (dlgTerms?.isShowing == true) {
            dlgTerms?.dismiss()
        }
    }
}
