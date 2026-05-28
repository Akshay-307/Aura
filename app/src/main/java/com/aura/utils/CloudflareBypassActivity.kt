package com.aura.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

class CloudflareBypassActivity : Activity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var isFinished = false
    private lateinit var url: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        url = intent.getStringExtra("url") ?: return finish()
        
        Toast.makeText(this, "Solving Cloudflare Check...", Toast.LENGTH_LONG).show()

        val layout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        
        layout.addView(webView)
        setContentView(layout)
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                handler.postDelayed(checkCookieTask, 1000)
            }
        }
        
        webView.loadUrl(url)
    }
    
    private val checkCookieTask = object : Runnable {
        override fun run() {
            if (isFinished) return
            val cookies = CookieManager.getInstance().getCookie(url)
            if (cookies != null && cookies.contains("cf_clearance")) {
                isFinished = true
                CloudflareBypasser._currentState = CloudflareState(webView.settings.userAgentString, cookies)
                CloudflareBypasser.bypassSuccess = true
                CloudflareBypasser.isBypassing = false
                finish()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isFinished) {
            isFinished = true
            CloudflareBypasser.isBypassing = false
        }
        webView.destroy()
    }
}

