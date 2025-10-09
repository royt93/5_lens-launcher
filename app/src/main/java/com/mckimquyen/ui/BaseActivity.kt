package com.mckimquyen.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(context: Context) {
        val override = Configuration(context.resources.configuration)
        override.fontScale = 1.0f
        applyOverrideConfiguration(override)
        super.attachBaseContext(context)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enableAdaptiveRefreshRate()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun enableAdaptiveRefreshRate() {
        // This function is only called when SDK_INT >= R (API 30)
        val currentDisplay: Display? = display

        if (currentDisplay != null) {
            // minSdk is 25, so SDK_INT is always >= M (API 23)
            val supportedModes = currentDisplay.supportedModes
            val highestRefreshRateMode = supportedModes.maxByOrNull { it.refreshRate }
            if (highestRefreshRateMode != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = highestRefreshRateMode.modeId
                }
                println("Adaptive refresh rate applied: ${highestRefreshRateMode.refreshRate} Hz")
            }
        }
    }
}