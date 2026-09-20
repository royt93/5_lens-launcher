package com.mckimquyen.ui.settings

import com.mckimquyen.R
import com.mckimquyen.util.URL_POLICY_NOTION

/**
 * ARCH-001: Decomposed menu actions from ActSettings.
 * Pure model and resolver decoupled from Android UI lifecycle.
 */
sealed interface SettingsMenuAction {
    object ShowApps : SettingsMenuAction
    object About : SettingsMenuAction
    data class ResetDefaults(val currentTab: Int) : SettingsMenuAction
    object RateApp : SettingsMenuAction
    object MoreApps : SettingsMenuAction
    object ShareApp : SettingsMenuAction
    object FacebookFanPage : SettingsMenuAction
    data class OpenWeb(val url: String, val titleResId: Int, val isExternal: Boolean) : SettingsMenuAction
    object Feedback : SettingsMenuAction
    object Unhandled : SettingsMenuAction
}

/**
 * Pure resolver mapping Android menu item IDs to deterministic SettingsMenuAction.
 */
object SettingsMenuResolver {

    @JvmStatic
    fun resolve(itemId: Int, currentTab: Int): SettingsMenuAction {
        return when (itemId) {
            R.id.menuItemShowApps -> SettingsMenuAction.ShowApps
            R.id.menuItemAbout -> SettingsMenuAction.About
            R.id.menuItemResetDefaultSettings -> SettingsMenuAction.ResetDefaults(currentTab)
            R.id.menuRateApp -> SettingsMenuAction.RateApp
            R.id.menuMoreApp -> SettingsMenuAction.MoreApps
            R.id.menuShareApp -> SettingsMenuAction.ShareApp
            R.id.menuFacebookFanPage -> SettingsMenuAction.FacebookFanPage
            R.id.menuPolicy -> SettingsMenuAction.OpenWeb(URL_POLICY_NOTION, R.string.terms_and_privacy_policy, false)
            R.id.menuGithubOriginal -> SettingsMenuAction.OpenWeb("https://github.com/ricknout/lens-launcher", R.string.github_original, true)
            R.id.menuGithubFork -> SettingsMenuAction.OpenWeb("https://github.com/gj-loitp/lens-launcher", R.string.github_fork, true)
            R.id.menuLicense -> SettingsMenuAction.OpenWeb("https://raw.githubusercontent.com/ricknout/lens-launcher/master/LICENSE.md", R.string.license, true)
            R.id.menuChangelog -> SettingsMenuAction.OpenWeb("https://raw.githubusercontent.com/gj-loitp/lens-launcher/dev/CHANGE_LOG.md", R.string.changelog, true)
            R.id.menuFeedback -> SettingsMenuAction.Feedback
            else -> SettingsMenuAction.Unhandled
        }
    }
}

/**
 * Host contract for performing UI side effects triggered by menu actions.
 */
interface SettingsMenuHost {
    fun launchApps()
    fun openAbout()
    fun resetTabDefaults(currentTab: Int)
    fun rateApp()
    fun openMoreApps()
    fun shareApp()
    fun openFacebookFanPage()
    fun openWebUrl(url: String, titleResId: Int, isExternal: Boolean)
    fun sendFeedback()
}

/**
 * Dispatches a resolved menu action to the host.
 * Returns true if action was handled, false if unhandled.
 */
class SettingsMenuDispatcher(private val host: SettingsMenuHost) {

    fun dispatch(action: SettingsMenuAction): Boolean {
        return when (action) {
            is SettingsMenuAction.ShowApps -> {
                host.launchApps()
                true
            }
            is SettingsMenuAction.About -> {
                host.openAbout()
                true
            }
            is SettingsMenuAction.ResetDefaults -> {
                host.resetTabDefaults(action.currentTab)
                true
            }
            is SettingsMenuAction.RateApp -> {
                host.rateApp()
                true
            }
            is SettingsMenuAction.MoreApps -> {
                host.openMoreApps()
                true
            }
            is SettingsMenuAction.ShareApp -> {
                host.shareApp()
                true
            }
            is SettingsMenuAction.FacebookFanPage -> {
                host.openFacebookFanPage()
                true
            }
            is SettingsMenuAction.OpenWeb -> {
                host.openWebUrl(action.url, action.titleResId, action.isExternal)
                true
            }
            is SettingsMenuAction.Feedback -> {
                host.sendFeedback()
                true
            }
            is SettingsMenuAction.Unhandled -> false
        }
    }
}
