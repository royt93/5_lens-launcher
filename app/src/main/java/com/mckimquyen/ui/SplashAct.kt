package com.mckimquyen.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.R
import com.mckimquyen.sdkadbmob.UIUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash Activity - Màn hình khởi động
 *
 * Fix: 4.3 - Sử dụng coroutines thay vì Handler để tránh memory leak
 * Fix: 5.1 - Sử dụng overrideActivityTransition() thay cho overridePendingTransition() deprecated
 */
class SplashAct : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_splash)
        UIUtils.setupEdgeToEdge2(findViewById(R.id.rootLayout))

        // Sử dụng lifecycleScope để tự động cancel khi Activity destroy
        lifecycleScope.launch {
            delay(1000)
            if (!isFinishing && !isDestroyed) {
                val intent = Intent(this@SplashAct, ActSettings::class.java)
                startActivity(intent)
                overrideTransition(0, 0)
                finish()
            }
        }
    }

    /**
     * Helper function để handle deprecated overridePendingTransition
     */
    private fun Activity.overrideTransition(enterAnim: Int, exitAnim: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, enterAnim, exitAnim)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
    }
}
