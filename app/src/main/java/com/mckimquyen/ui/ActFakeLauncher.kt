package com.mckimquyen.ui

import android.os.Bundle
import com.mckimquyen.R
import com.mckimquyen.util.UIUtils

class ActFakeLauncher : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.act_fake_launcher)
        UIUtils.setupEdgeToEdge2(findViewById(R.id.rootLayout))
    }
}
