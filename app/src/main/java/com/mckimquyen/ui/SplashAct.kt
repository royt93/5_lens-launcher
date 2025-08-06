package com.mckimquyen.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.mckimquyen.R
import com.mckimquyen.sdkadbmob.UIUtils

class SplashAct : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_splash)
        UIUtils.setupEdgeToEdge2(findViewById(R.id.rootLayout))

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, ActSettings::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }, 1000)
    }

}
