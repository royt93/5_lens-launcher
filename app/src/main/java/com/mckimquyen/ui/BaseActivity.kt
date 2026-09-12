package com.mckimquyen.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    companion object {
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
        val override = Configuration(localeContext.resources.configuration)
        override.fontScale = 1.0f
        applyOverrideConfiguration(override)
        super.attachBaseContext(localeContext)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wantsHighRefreshRate()) {
            requestHighRefreshRate()
        }
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wantsHighRefreshRate()) {
            releasePreferredDisplayMode()
        }
        super.onPause()
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