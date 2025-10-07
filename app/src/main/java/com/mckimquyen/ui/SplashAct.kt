package com.mckimquyen.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.mckimquyen.R
import com.mckimquyen.sdkadbmob.UIUtils

/**
 * Splash Activity - Màn hình khởi động
 *
 * Fix: 4.3 - Cleanup handler để tránh memory leak khi Activity bị destroy sớm
 */
class SplashAct : BaseActivity() {

    // Lưu reference đến handler để cleanup trong onDestroy
    private var handler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_splash)
        UIUtils.setupEdgeToEdge2(findViewById(R.id.rootLayout))

        // Khởi tạo handler và lưu reference
        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed({
            // Chỉ thực hiện nếu Activity chưa bị destroy
            if (!isFinishing && !isDestroyed) {
                val intent = Intent(this, ActSettings::class.java)
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
            }
        }, 1000)
    }

    /**
     * Cleanup handler callbacks để tránh memory leak
     */
    override fun onDestroy() {
        // Remove tất cả callbacks và messages để tránh memory leak
        handler?.removeCallbacksAndMessages(null)
        handler = null
        super.onDestroy()
    }
}
