package com.mckimquyen.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.mckimquyen.R
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.ext.searchIconPack
import com.mckimquyen.itf.SettingsInterface
import com.mckimquyen.util.UtilLauncher
import com.mckimquyen.util.UtilNightModeUtil
import com.mckimquyen.util.UtilSettings

class FrmSettings : Fragment(), SettingsInterface {
    companion object {
        fun newInstance() = FrmSettings()
    }

    private var tvSelectedHomeLauncher: TextView? = null
    private var tvSelectedIconPack: TextView? = null
    private var tvSelectedNightMode: TextView? = null
    private var tvSelectedBackground: TextView? = null
    private var ivSelectedBackgroundColor: ImageView? = null
    private var tvSelectedHighlightColor: TextView? = null
    private var ivSelectedHighlightColor: ImageView? = null
    private var swVibrateAppHover: SwitchCompat? = null
    private var swVibrateAppLaunch: SwitchCompat? = null
    private var swShowNameAppHover: SwitchCompat? = null
    private var swShowNewAppTag: SwitchCompat? = null
    private var swShowTouchSelection: SwitchCompat? = null
    private var swShowSearchBar: SwitchCompat? = null
    private var tvSelectedSearchHint: TextView? = null
    private var swQuickActionCalculator: SwitchCompat? = null
    private var swQuickActionUnit: SwitchCompat? = null
    private var swQuickActionTimer: SwitchCompat? = null
    private var swQuickActionBattery: SwitchCompat? = null
    private var swQuickActionSettings: SwitchCompat? = null
    private var utilSettings: UtilSettings? = null
    private var tvVipStatusSummary: TextView? = null
    private var tvSelectedLanguage: TextView? = null
    private var tvSelectedLauncherMode: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.frm_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        utilSettings = UtilSettings(requireContext())
        setupViews(view)
        assignValues()
    }

    override fun onResume() {
        super.onResume()
        assignValues()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context as? ActSettings)?.setSettingsInterface(this)
    }

    private fun setupViews(view: View) {
        tvSelectedHomeLauncher = view.findViewById(R.id.tvSelectedHomeLauncher)
        tvSelectedIconPack = view.findViewById(R.id.tvSelectedIconPack)
        tvSelectedNightMode = view.findViewById(R.id.tvSelectedNightMode)
        tvSelectedBackground = view.findViewById(R.id.tvSelectedBackground)
        ivSelectedBackgroundColor = view.findViewById(R.id.ivSelectedBackgroundColor)
        tvSelectedHighlightColor = view.findViewById(R.id.tvSelectedHighlightColor)
        ivSelectedHighlightColor = view.findViewById(R.id.ivSelectedHighlightColor)
        swVibrateAppHover = view.findViewById(R.id.swVibrateAppHover)
        swVibrateAppLaunch = view.findViewById(R.id.swVibrateAppLaunch)
        swShowNameAppHover = view.findViewById(R.id.swShowNameAppHover)
        swShowNewAppTag = view.findViewById(R.id.swShowNewAppTag)
        swShowTouchSelection = view.findViewById(R.id.swShowTouchSelection)
        swShowSearchBar = view.findViewById(R.id.swShowSearchBar)
        tvSelectedSearchHint = view.findViewById(R.id.tvSelectedSearchHint)
        swQuickActionCalculator = view.findViewById(R.id.swQuickActionCalculator)
        swQuickActionUnit = view.findViewById(R.id.swQuickActionUnit)
        swQuickActionTimer = view.findViewById(R.id.swQuickActionTimer)
        swQuickActionBattery = view.findViewById(R.id.swQuickActionBattery)
        swQuickActionSettings = view.findViewById(R.id.swQuickActionSettings)
        tvVipStatusSummary = view.findViewById(R.id.tvVipStatusSummary)
        tvSelectedLanguage = view.findViewById(R.id.tvSelectedLanguage)

        view.findViewById<View>(R.id.llLanguage).setOnClickListener {
            showLanguagePicker()
        }

        view.findViewById<View>(R.id.llVipPremium).setOnClickListener {
            (activity as? ActSettings)?.navigateToVipTab()
        }

        view.findViewById<View>(R.id.llHomeLauncher).setOnClickListener {
            showHomeLauncherChooser()
        }
        view.findViewById<View>(R.id.llIconPack).setOnClickListener {
            showIconPackDialog()
        }
        view.findViewById<View>(R.id.btGetMoreIconPacks).setOnClickListener {
            activity?.searchIconPack()
        }
        tvSelectedLauncherMode = view.findViewById(R.id.tvSelectedLauncherMode)
        view.findViewById<View>(R.id.llLauncherMode).setOnClickListener {
            showLauncherModeDialog()
        }
        view.findViewById<View>(R.id.llNightMode).setOnClickListener {
            showNightModeChooser()
        }
        view.findViewById<View>(R.id.llBackground).setOnClickListener {
            showBackgroundDialog()
        }
        view.findViewById<View>(R.id.llHighlightColor).setOnClickListener {
            showHighlightColorDialog()
        }
        // Empty click listeners to prevent parent click events
        view.findViewById<View>(R.id.rlSwitchVibrateAppHoverParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchVibrateAppLaunchParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchShowNameAppHoverParent).setOnClickListener(null)
        view.findViewById<View>(R.id.swShowNewAppTagParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchShowTouchSelectionParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchShowSearchBarParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchQuickActionCalculatorParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchQuickActionUnitParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchQuickActionTimerParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchQuickActionBatteryParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSwitchQuickActionSettingsParent).setOnClickListener(null)

        view.findViewById<View>(R.id.llSearchHintText).setOnClickListener {
            showSearchHintDialog()
        }

        swVibrateAppHover?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_VIBRATE_APP_HOVER, isChecked)
        }
        swVibrateAppLaunch?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, isChecked)
        }
        swShowNameAppHover?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_SHOW_NAME_APP_HOVER, isChecked)
        }
        swShowNewAppTag?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_SHOW_NEW_APP_TAG, isChecked)
        }
        swShowTouchSelection?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_SHOW_TOUCH_SELECTION, isChecked)
        }
        swShowSearchBar?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_SHOW_SEARCH_BAR, isChecked)
        }
        swQuickActionCalculator?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_QUICK_ACTION_CALCULATOR, isChecked)
        }
        swQuickActionUnit?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_QUICK_ACTION_UNIT, isChecked)
        }
        swQuickActionTimer?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_QUICK_ACTION_TIMER, isChecked)
        }
        swQuickActionBattery?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_QUICK_ACTION_BATTERY, isChecked)
        }
        swQuickActionSettings?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_QUICK_ACTION_SETTINGS, isChecked)
        }
    }

    private fun showSearchHintDialog() {
        val context = requireContext()
        val current = utilSettings?.getString(UtilSettings.KEY_SEARCH_HINT_TEXT).orEmpty()
        val input = android.widget.EditText(context).apply {
            setText(current)
            hint = getString(R.string.search_apps_hint)
            setSelection(text.length)
        }
        val paddingPx = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(context).apply {
            setPadding(paddingPx, paddingPx / 2, paddingPx, 0)
            addView(input)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.setting_search_hint_title)
            .setView(container)
            .setPositiveButton(R.string.done) { _, _ ->
                utilSettings?.save(UtilSettings.KEY_SEARCH_HINT_TEXT, input.text.toString().trim())
                assignValues()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun assignValues() {
        utilSettings?.let { us ->
            val isVip = com.roy.sdkadbmob.AdManager.isVIPMember()
            tvVipStatusSummary?.text = if (isVip) getString(R.string.vip_active) else getString(R.string.vip_free_user)

            val storedIconPack = us.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME)
            tvSelectedIconPack?.text = if (storedIconPack == UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME) {
                getString(R.string.setting_default_icon_pack)
            } else {
                storedIconPack
            }

            val highlightColorFull = us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR) ?: ""
            val highlightColor = "#${highlightColorFull.substring(3)}"
            tvSelectedHighlightColor?.text = highlightColor

            val homeLauncher = activity?.let { UtilLauncher.getNameHomeLauncher(it.application) } ?: ""
            tvSelectedHomeLauncher?.text = homeLauncher

            tvSelectedNightMode?.text = UtilNightModeUtil.getNightModeDisplayName(us.nightMode)

            // Background setup
            if (us.backgroundMode == BackgroundMode.COLOR) {
                val backgroundColorFull = us.getString(UtilSettings.KEY_BACKGROUND_COLOR) ?: ""
                val backgroundColor = "#${backgroundColorFull.substring(3)}"
                tvSelectedBackground?.text = backgroundColor
                ivSelectedBackgroundColor?.isVisible = true

                val backgroundColorDrawable = GradientDrawable().apply {
                    setColor(backgroundColorFull.toColorInt())
                    cornerRadius = resources.getDimension(R.dimen.radius_highlight_color_switch)
                }
                ivSelectedBackgroundColor?.setImageDrawable(backgroundColorDrawable)
            } else {
                tvSelectedBackground?.text = resources.getStringArray(R.array.backgrounds)
                    .getOrNull(BackgroundMode.WALLPAPER.ordinal)
                ivSelectedBackgroundColor?.isVisible = false
            }

            // Highlight color drawable
            val highlightColorDrawable = GradientDrawable().apply {
                setColor(highlightColorFull.toColorInt())
                cornerRadius = resources.getDimension(R.dimen.radius_highlight_color_switch)
            }
            ivSelectedHighlightColor?.setImageDrawable(highlightColorDrawable)

            // Switches
            swVibrateAppHover?.isChecked = us.getBoolean(UtilSettings.KEY_VIBRATE_APP_HOVER)
            swVibrateAppLaunch?.isChecked = us.getBoolean(UtilSettings.KEY_VIBRATE_APP_LAUNCH)
            swShowNameAppHover?.isChecked = us.getBoolean(UtilSettings.KEY_SHOW_NAME_APP_HOVER)
            swShowNewAppTag?.isChecked = us.getBoolean(UtilSettings.KEY_SHOW_NEW_APP_TAG)
            swShowTouchSelection?.isChecked = us.getBoolean(UtilSettings.KEY_SHOW_TOUCH_SELECTION)
            swShowSearchBar?.isChecked = us.getBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR)
            swQuickActionCalculator?.isChecked = us.getBoolean(UtilSettings.KEY_QUICK_ACTION_CALCULATOR)
            swQuickActionUnit?.isChecked = us.getBoolean(UtilSettings.KEY_QUICK_ACTION_UNIT)
            swQuickActionTimer?.isChecked = us.getBoolean(UtilSettings.KEY_QUICK_ACTION_TIMER)
            swQuickActionBattery?.isChecked = us.getBoolean(UtilSettings.KEY_QUICK_ACTION_BATTERY)
            swQuickActionSettings?.isChecked = us.getBoolean(UtilSettings.KEY_QUICK_ACTION_SETTINGS)
            val customHint = us.getString(UtilSettings.KEY_SEARCH_HINT_TEXT)
            tvSelectedSearchHint?.text = if (customHint.isNullOrEmpty()) {
                getString(R.string.setting_search_hint_default)
            } else {
                customHint
            }

            // Launcher Mode (FEAT-003)
            val currentMode = us.getLauncherMode()
            tvSelectedLauncherMode?.setText(currentMode.displayNameResId)

            // Language
            val currentLang = com.mckimquyen.util.LocaleHelper.getLanguage(requireContext())
            val matchingLang = com.mckimquyen.util.LocaleHelper.supportedLanguages.firstOrNull { it.code == currentLang }
            if (matchingLang != null) {
                tvSelectedLanguage?.text = getString(R.string.language_display_format, matchingLang.flag, matchingLang.nativeName)
            } else {
                tvSelectedLanguage?.text = currentLang
            }
        }
    }

    private fun showLauncherModeDialog() {
        val modes = LauncherMode.entries.toTypedArray()
        val currentMode = utilSettings?.getLauncherMode() ?: LauncherMode.FISHEYE
        val currentIndex = modes.indexOf(currentMode).coerceAtLeast(0)
        val itemLabels = modes.map { getString(it.displayNameResId) }.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.MaterialYouDialogTheme)
            .setTitle(R.string.setting_launcher_mode)
            .setSingleChoiceItems(itemLabels, currentIndex) { dialog, which ->
                val selectedMode = modes[which]
                utilSettings?.setLauncherMode(selectedMode)
                tvSelectedLauncherMode?.setText(selectedMode.displayNameResId)
                com.mckimquyen.services.AppEventManager.notifyAppsEdited()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showIconPackDialog() {
        (activity as? ActSettings)?.showIconPackDialog()
    }

    private fun showHomeLauncherChooser() {
        (activity as? ActSettings)?.showHomeLauncherChooser()
    }

    private fun showNightModeChooser() {
        (activity as? ActSettings)?.showNightModeChooser()
    }

    private fun showBackgroundDialog() {
        (activity as? ActSettings)?.showBackgroundDialog()
    }

    private fun showHighlightColorDialog() {
        (activity as? ActSettings)?.showHighlightColorDialog()
    }

    override fun onDefaultsReset() {
        resetToDefault()
        assignValues()
    }

    override fun onValuesUpdated() {
        assignValues()
    }

    private fun resetToDefault() {
        utilSettings?.let { us ->
            us.save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
            us.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, true)
            us.save(UtilSettings.KEY_SHOW_NAME_APP_HOVER, true)
            us.save(UtilSettings.KEY_SHOW_TOUCH_SELECTION, false)
            us.save(UtilSettings.KEY_SHOW_SEARCH_BAR, UtilSettings.DEFAULT_SHOW_SEARCH_BAR)
            us.save(UtilSettings.KEY_QUICK_ACTION_CALCULATOR, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED)
            us.save(UtilSettings.KEY_QUICK_ACTION_UNIT, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED)
            us.save(UtilSettings.KEY_QUICK_ACTION_TIMER, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED)
            us.save(UtilSettings.KEY_QUICK_ACTION_BATTERY, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED)
            us.save(UtilSettings.KEY_QUICK_ACTION_SETTINGS, UtilSettings.DEFAULT_QUICK_ACTION_ENABLED)
            us.save(UtilSettings.KEY_SEARCH_HINT_TEXT, "")
            us.save(UtilSettings.KEY_SHOW_NEW_APP_TAG, true)
            us.save(UtilSettings.DEFAULT_BACKGROUND_MODE)
            us.save(UtilSettings.KEY_BACKGROUND_COLOR, UtilSettings.DEFAULT_BACKGROUND_COLOR)
            us.save(UtilSettings.KEY_HIGHLIGHT_COLOR, UtilSettings.DEFAULT_HIGHLIGHT_COLOR)
            us.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME)
            us.save(UtilSettings.KEY_NIGHT_MODE, UtilSettings.DEFAULT_NIGHT_MODE)
        }
        (activity as? ActSettings)?.sendNightModeBroadcast()
    }

    private fun showLanguagePicker() {
        val dialog = LanguageBottomSheetDialogFragment()
        dialog.setOnLanguageSelectedListener(object : LanguageBottomSheetDialogFragment.OnLanguageSelectedListener {
            override fun onLanguageSelected(languageCode: String) {
                activity?.recreate()
            }
        })
        dialog.show(parentFragmentManager, "LanguageBottomSheet")
    }
}
