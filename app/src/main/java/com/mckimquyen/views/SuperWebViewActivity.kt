package com.mckimquyen.views

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mckimquyen.R
import com.mckimquyen.sdkadbmob.UIUtils
import com.mckimquyen.ui.ActBase

class SuperWebViewActivity : ActBase() {
    companion object {
        const val KEY_TITLE = "KEY_TITLE"
        const val KEY_URL = "KEY_URL"
    }

    private var currentTitle: String = ""
    private var currentWebsite: String = "https://www.facebook.com/loitp93/"
    private lateinit var webView: WebView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var errorLayout: ConstraintLayout
    private lateinit var retryButton: Button
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.act_super_wv)
        UIUtils.setupEdgeToEdge2(findViewById(R.id.rootLayout))

        // Set status bar icons to dark/black
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        currentTitle = intent?.getStringExtra(KEY_TITLE) ?: ""
        currentWebsite = intent?.getStringExtra(KEY_URL) ?: ""

        setupViews()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupViews() {
        webView = findViewById(R.id.webView)
        progressIndicator = findViewById(R.id.progressIndicator)
        errorLayout = findViewById(R.id.errorLayout)
        retryButton = findViewById(R.id.retryButton)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBack()
        }
        supportActionBar?.title = currentTitle

        webView.webViewClient = MyWebViewClient()
        webView.webChromeClient = MyWebChromeClient()
        with(webView.settings) {
            // Tell the WebView to enable JavaScript execution
            javaScriptEnabled = true
            // Enable DOM storage API
            domStorageEnabled = false
            // Disable support for zooming using webView's on-screen zoom controls and gestures
            setSupportZoom(false)
        }
        // If dark theme is turned on, automatically render all web contents using a dark theme
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
                }

                Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
                }
            }
        }

        webView.loadUrl(currentWebsite)

        /**
         * Theme Swipe-to-refresh layout
         */
        val spinnerTypedValue = TypedValue()
        theme.resolveAttribute(
            androidx.appcompat.R.attr.colorPrimary, spinnerTypedValue, true
        )

        val backgroundTypedValue = TypedValue()
        theme.resolveAttribute(
            androidx.appcompat.R.attr.colorPrimary, backgroundTypedValue, true
        )
//        val backgroundColor = backgroundTypedValue.resourceId
//        binding.srl.setProgressBackgroundColorSchemeResource(backgroundColor)

//        webView.viewTreeObserver.addOnScrollChangedListener {
//            root.isEnabled = webView.scrollY == 0
//        }

        /**
         * If there's no web page history, close the application
         */
        val mCallback = onBackPressedDispatcher.addCallback(this) {
            onBack()
        }
        mCallback.isEnabled = true
    }

    private fun onBack() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    private inner class MyWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView?, request: WebResourceRequest?,
        ): Boolean {
            if (request?.url.toString().contains(currentWebsite)) {
                return false
            }
            Intent(Intent.ACTION_VIEW, request?.url).apply {
                startActivity(this)
            }
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            webView.visibility = View.VISIBLE
            errorLayout.visibility = View.GONE
            progressIndicator.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            progressIndicator.visibility = View.INVISIBLE
//            toolbar.title = view?.title
        }

        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?, error: WebResourceError?,
        ) {
            super.onReceivedError(view, request, error)
            webView.visibility = View.GONE
            errorLayout.visibility = View.VISIBLE
            retryButton.setOnClickListener {
                if (view?.url.isNullOrEmpty()) {
                    view?.loadUrl(currentWebsite)
                } else {
                    view.reload()
                }
            }
        }

    }

    /**
     * Update the progress bar when loading a webpage
     */
    private inner class MyWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            progressIndicator.progress = newProgress
        }
    }
}
