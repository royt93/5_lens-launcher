package com.mckimquyen.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.R
import com.mckimquyen.util.UIUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash Activity - Màn hình khởi động
 *
 * Fix: 4.3 - Sử dụng coroutines thay vì Handler để tránh memory leak
 * Fix: 5.1 - Sử dụng overrideActivityTransition() thay cho overridePendingTransition() deprecated
 * UI-004: logo/progress/text now fade+scale in on a short stagger instead of appearing instantly.
 */
class SplashAct : BaseActivity() {

    private val entranceViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_splash)
        UIUtils.setupEdgeToEdge2(findViewById(R.id.rootLayout))

        playEntranceAnimation(
            findViewById(R.id.logoContainer),
            findViewById(R.id.progressIndicator),
            findViewById(R.id.appNameText),
            findViewById(R.id.loadingText)
        )

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

    private fun playEntranceAnimation(logo: View, progress: View, appName: View, loading: View) {
        val staggered = listOf(logo to 0L, progress to 80L, appName to 200L, loading to 300L)
        staggered.forEach { (view, startDelay) ->
            entranceViews += view
            view.alpha = 0f
            view.scaleX = 0.85f
            view.scaleY = 0.85f
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(startDelay)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun onDestroy() {
        entranceViews.forEach { it.animate().cancel() }
        entranceViews.clear()
        super.onDestroy()
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
