package com.mckimquyen.ui.settings

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.mckimquyen.R
import com.roy.sdkadbmob.AdManager

/**
 * Visual styling data for the Toolbar VIP badge.
 */
data class VipBadgeVisualState(
    val isVipActive: Boolean,
    val textResId: Int,
    val textColorInt: Int,
    val backgroundColorInt: Int,
    val iconColorInt: Int
)

/**
 * ARCH-001: Encapsulates VIP badge styling/animation and Ad lifecycle delegation.
 */
class SettingsAdVipDelegate {

    private var vipBadgeAnimator: ObjectAnimator? = null
    var bannerView: View? = null
        private set
    var isConsentResolved: Boolean = false
        internal set

    companion object {
        const val TIMEOUT_CONSENT_FALLBACK_MS = 8_000L
        const val VIP_GOLD_COLOR_HEX = "#FFD60A"
        const val VIP_DARK_TEXT_HEX = "#1C1C1E"
        const val VIP_INACTIVE_NIGHT_BG_HEX = "#2C2C2E"
        const val VIP_INACTIVE_NIGHT_TEXT_HEX = "#E5E5EA"
        const val VIP_INACTIVE_DAY_BG_HEX = "#E5E5EA"
        const val VIP_INACTIVE_DAY_TEXT_HEX = "#3A3A3C"

        /**
         * Pure function to determine VIP badge visual styling.
         */
        @JvmStatic
        fun computeBadgeState(isVip: Boolean, isNightMode: Boolean): VipBadgeVisualState {
            return if (isVip) {
                VipBadgeVisualState(
                    isVipActive = true,
                    textResId = R.string.vip_badge_active,
                    textColorInt = VIP_DARK_TEXT_HEX.toColorInt(),
                    backgroundColorInt = VIP_GOLD_COLOR_HEX.toColorInt(),
                    iconColorInt = VIP_DARK_TEXT_HEX.toColorInt()
                )
            } else {
                val bg = if (isNightMode) VIP_INACTIVE_NIGHT_BG_HEX.toColorInt() else VIP_INACTIVE_DAY_BG_HEX.toColorInt()
                val text = if (isNightMode) VIP_INACTIVE_NIGHT_TEXT_HEX.toColorInt() else VIP_INACTIVE_DAY_TEXT_HEX.toColorInt()
                VipBadgeVisualState(
                    isVipActive = false,
                    textResId = R.string.vip_badge_get,
                    textColorInt = text,
                    backgroundColorInt = bg,
                    iconColorInt = text
                )
            }
        }

        @JvmStatic
        fun shouldShowBanner(isVip: Boolean, consentResolved: Boolean): Boolean {
            return !isVip && consentResolved
        }

        @JvmStatic
        fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            @Suppress("DEPRECATION")
            val activeNetwork = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            return activeNetwork != null && activeNetwork.isConnected
        }
    }

    /**
     * Binds and styles the toolbar VIP badge chip.
     */
    fun bindVipBadge(badgeView: View?, isVip: Boolean, isNightMode: Boolean, onClick: () -> Unit) {
        if (badgeView == null) return
        badgeView.setOnClickListener { onClick() }

        val state = computeBadgeState(isVip, isNightMode)
        val tv = badgeView.findViewById<TextView>(R.id.tvVipBadgeStatus)
        val iv = badgeView.findViewById<ImageView>(R.id.ivVipBadgeIcon)

        tv?.setText(state.textResId)
        tv?.setTextColor(state.textColorInt)
        badgeView.backgroundTintList = ColorStateList.valueOf(state.backgroundColorInt)
        iv?.setColorFilter(state.iconColorInt, PorterDuff.Mode.SRC_IN)

        badgeView.visibility = View.VISIBLE
        startVipBadgeAnimation(badgeView)
    }

    fun startVipBadgeAnimation(view: View?) {
        stopVipBadgeAnimation()
        if (view == null) return
        vipBadgeAnimator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.0f)
        ).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    fun stopVipBadgeAnimation() {
        vipBadgeAnimator?.cancel()
        vipBadgeAnimator = null
    }

    /**
     * Handles banner resume/reload or destruction based on VIP/Consent status.
     */
    fun onResume(
        activity: Activity,
        bannerContainer: ViewGroup?,
        tvLabelAd: TextView?,
        isVip: Boolean
    ) {
        if (isVip) {
            destroyBanner()
            bannerContainer?.visibility = View.GONE
            tvLabelAd?.visibility = View.GONE
        } else if (isConsentResolved) {
            bannerContainer?.visibility = View.VISIBLE
            tvLabelAd?.visibility = View.VISIBLE
            if (bannerView == null && bannerContainer != null && tvLabelAd != null) {
                bannerView = AdManager.loadBanner(
                    activity,
                    bannerContainer,
                    tvLabelAd,
                    AdManager.getAdaptiveBannerSize(activity),
                    true
                )
            } else if (bannerView != null) {
                AdManager.bannerResume(bannerView)
            }
        }
    }

    fun onPause() {
        stopVipBadgeAnimation()
        if (bannerView != null) {
            AdManager.bannerPause(bannerView)
        }
    }

    fun destroyBanner() {
        if (bannerView != null) {
            AdManager.bannerDestroy(bannerView)
            bannerView = null
        }
    }

    fun onDestroy() {
        destroyBanner()
        stopVipBadgeAnimation()
    }

    /**
     * Requests ad consent or falls back safely when offline or timed out.
     */
    fun checkShowAd(
        activity: Activity,
        flAdOpenApp: ViewGroup?,
        bannerContainer: ViewGroup?,
        tvLabelAd: TextView?,
        isNetworkAvailable: Boolean
    ) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed && flAdOpenApp?.visibility == View.VISIBLE) {
                flAdOpenApp.visibility = View.GONE
                isConsentResolved = true
            }
        }, TIMEOUT_CONSENT_FALLBACK_MS)

        if (!isNetworkAvailable) {
            flAdOpenApp?.visibility = View.GONE
            isConsentResolved = true
            AdManager.loadInterstitial(activity)
            return
        }

        AdManager.requestConsentInfoUpdate(activity, false) {
            AdManager.initSplashScreen(activity) {
                flAdOpenApp?.visibility = View.GONE
                isConsentResolved = true
                AdManager.loadInterstitial(activity)

                val isVip = AdManager.isVIPMember() || AdManager.isVipByKeyActive()
                if (!isVip) {
                    if (bannerView == null && bannerContainer != null && tvLabelAd != null) {
                        bannerView = AdManager.loadBanner(
                            activity,
                            bannerContainer,
                            tvLabelAd,
                            AdManager.getAdaptiveBannerSize(activity),
                            true
                        )
                    }
                } else {
                    bannerContainer?.visibility = View.GONE
                    tvLabelAd?.visibility = View.GONE
                }
                kotlin.Unit
            }
            kotlin.Unit
        }
    }
}
