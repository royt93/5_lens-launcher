package com.mckimquyen.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import com.mckimquyen.util.UtilSettings

open class BaseActivity : AppCompatActivity() {

    companion object {
        const val MIN_SUPPORTED_FONT_SCALE = 0.85f
        const val MAX_SUPPORTED_FONT_SCALE = 2.0f

        /**
         * A11Y-001: Pure function to clamp fontScale between 0.85x and 2.0x (200%),
         * respecting accessibility settings without breaking canvas math.
         */
        @VisibleForTesting
        internal fun clampFontScale(fontScale: Float): Float {
            return fontScale.coerceIn(MIN_SUPPORTED_FONT_SCALE, MAX_SUPPORTED_FONT_SCALE)
        }

        /**
         * DISPLAY-001 mode-selection policy: don't request a high refresh rate under battery
         * saver or moderate+ thermal throttling — forcing it there would fight the exact
         * heat/battery problem this policy exists to avoid. Pulled out as a pure function so
         * every (batterySaverOn, thermalStatus) case is unit-testable without a real Display.
         */
        @VisibleForTesting
        internal fun shouldRequestHighRefreshRate(batterySaverOn: Boolean, thermalStatus: Int): Boolean {
            if (batterySaverOn) return false
            if (thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return false
            return true
        }
    }

    /**
     * DISPLAY-001: only screens whose interaction/animation actually benefits from a high
     * refresh rate (the live fisheye grid in [ActHome]) should override this to `true`.
     * Static/settings screens keep the default `false` and let the system choose, saving
     * battery/heat.
     */
    protected open fun wantsHighRefreshRate(): Boolean = false

    override fun attachBaseContext(context: Context) {
        val localeContext = com.mckimquyen.util.LocaleHelper.onAttach(context)
        val systemFontScale = context.resources.configuration.fontScale
        val clampedFontScale = clampFontScale(systemFontScale)
        val override = Configuration(localeContext.resources.configuration).apply {
            fontScale = clampedFontScale
        }
        applyOverrideConfiguration(override)
        super.attachBaseContext(localeContext)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wantsHighRefreshRate()) {
            requestHighRefreshRate()
        }
        updateKeepScreenOnFlag()
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wantsHighRefreshRate()) {
            releasePreferredDisplayMode()
        }
        super.onPause()
    }

    /**
     * Owner request (2026-09-25): the "Keep Screen On" setting (previously ActHome-only,
     * FEAT-005) now applies to every screen in the app, centralized here so a new Activity
     * gets it for free just by extending BaseActivity/ActBase - no per-Activity wiring.
     * Re-read on every resume (matches [wantsHighRefreshRate]'s established pattern) so
     * toggling the setting in ActSettings takes effect immediately on return. Plain
     * `FLAG_KEEP_SCREEN_ON`, not a `PowerManager.WakeLock`: it only affects screen-on state
     * while this window is the one displayed (no `WAKE_LOCK` permission, no manual
     * acquire/release lifecycle to leak) - the native, platform-idiomatic mechanism for
     * exactly this "keep the screen on while my app is in front" use case.
     */
    private fun updateKeepScreenOnFlag() {
        val keepScreenOn = UtilSettings(this).getBoolean(UtilSettings.KEY_KEEP_SCREEN_ON)
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestHighRefreshRate() {
        // This method is already gated behind API 30 (R), which is newer than the API 29 (Q)
        // floor for currentThermalStatus, so it's always available here.
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batterySaverOn = powerManager?.isPowerSaveMode == true
        val thermalStatus = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        if (!shouldRequestHighRefreshRate(batterySaverOn, thermalStatus)) return

        // minSdk is 25, so SDK_INT is always >= M (API 23)
        val highestRefreshRateMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
        if (highestRefreshRateMode != null) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = highestRefreshRateMode.modeId
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun releasePreferredDisplayMode() {
        // preferredDisplayModeId = 0 means "no preference" — let the system pick again.
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = 0
        }
    }
}