package com.mckimquyen.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.VisibleForTesting

/**
 * FISH-004: named lens movement presets. Each is just a fixed (distortion, scale, animation-time)
 * triple applied through the same [UtilSettings] keys the existing continuous sliders in
 * `FrmLens` already write to - switching presets never touches `LensView`'s rendering/hit-testing
 * math, so selection geometry stays exactly what it already was for that (distortion, scale)
 * pair (already audited: both draw and hit-test read the same per-frame computed rect, so there
 * is nothing here for a preset choice to destabilize).
 */
enum class LensPhysicsPreset(
    val distortionFactor: Float,
    val scaleFactor: Float,
    val animationTimeMs: Long
) {
    GENTLE(distortionFactor = 1.0f, scaleFactor = 1.0f, animationTimeMs = 350L),
    STANDARD(
        distortionFactor = UtilSettings.DEFAULT_DISTORTION_FACTOR,
        scaleFactor = UtilSettings.DEFAULT_SCALE_FACTOR,
        animationTimeMs = UtilSettings.DEFAULT_ANIMATION_TIME
    ),
    SNAPPY(distortionFactor = 4.5f, scaleFactor = 2.0f, animationTimeMs = 120L)
}

object LensPhysicsPolicy {

    /**
     * Don't animate or vibrate the lens when the user has asked the system to reduce motion
     * (Developer options "Animator duration scale" = 0 - the standard Android signal apps use
     * to detect a system-wide reduced-motion preference; there is no dedicated public API for
     * it below API 33), or under battery saver / moderate+ thermal throttling - same reasoning
     * as [BaseActivity.shouldRequestHighRefreshRate]. Pulled out as a pure function so every
     * (reducedMotionEnabled, batterySaverOn, thermalStatus) case is unit-testable without a
     * real Context/Display.
     */
    @VisibleForTesting
    internal fun shouldReduceLensMotion(
        reducedMotionEnabled: Boolean,
        batterySaverOn: Boolean,
        thermalStatus: Int
    ): Boolean {
        if (reducedMotionEnabled) return true
        if (batterySaverOn) return true
        if (thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return true
        return false
    }

    // THERMAL_STATUS_NONE is a plain compile-time int constant (value 0), safe to reference
    // below API 29 - only the getter that reports live thermal status needs that floor.
    @SuppressLint("InlinedApi")
    fun shouldReduceLensMotion(context: Context): Boolean {
        val reducedMotionEnabled = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batterySaverOn = powerManager?.isPowerSaveMode == true
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
        return shouldReduceLensMotion(reducedMotionEnabled, batterySaverOn, thermalStatus)
    }
}
