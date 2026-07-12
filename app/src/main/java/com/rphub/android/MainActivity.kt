package com.rphub.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : Activity() {

    companion object {
        private const val TAG = "RP-Hub"
        private const val SERVER_PORT = 8080
        private const val RP_HUB_ASSET_DIR = "rp-hub-web"
        private const val PREF_NAME = "rphub_prefs"
        private const val KEY_ASSET_VERSION = "asset_version"
        private const val CURRENT_ASSET_VERSION = 5
    }

    private lateinit var webView: WebView
    private lateinit var webRoot: File
    private var localServer: LocalWebServer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // ★ 关键修复：用 FLAG_FULLSCREEN 让窗口真正全屏
        // 这样 WebView 的 100vh 就等于整个屏幕高度，不会留黑边
        // 不用 setDecorFitsSystemWindows(false)，因为那个只让内容画到系统栏后面
        // 但不改变 viewport 高度，导致 100vh < 实际屏幕高度
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // 背景设为 RP-Hub 的底色，即使有间隙也是同色
        window.decorView.setBackgroundColor(Color.parseColor("#F9FAFB"))

        // 创建 WebView 全屏
        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#F9FAFB"))
        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.parseColor("#F9FAFB"))
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        layout.addView(webView, params)
        setContentView(layout)

        applyImmersiveMode()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        // 关闭 wideViewPort 防止桌面布局
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.displayZoomControls = false

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true

        // Mobile UA
        settings.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 RP-Hub-App/1.0"

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) return false
                if (url.startsWith("http://") || url.startsWith("https://")) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "无法打开: $url", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                view.setBackgroundColor(Color.parseColor("#F9FAFB"))
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.setBackgroundColor(Color.TRANSPARENT)
                // ★ 注入 JS 覆盖 CSS 变量，强制全屏高度
                view.evaluateJavascript(
                    """
                    (function() {
                        var root = document.documentElement;
                        root.style.setProperty('--app-visual-height', '100vh', 'important');
                        root.style.setProperty('--chat-bg-height', '100vh', 'important');
                        document.body.style.margin = '0';
                        document.body.style.padding = '0';
                        document.body.style.height = '100vh';
                        document.body.style.overflow = 'hidden';
                        var app = document.getElementById('app');
                        if (app) {
                            app.style.height = '100vh';
                        }
                    })();
                    """,
                    null
                )
                applyImmersiveMode()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (request?.isForMainFrame == true) {
                        Log.e(TAG, "页面加载失败: ${error?.description}")
                    }
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
        prepareAndLoad()
    }

    private fun applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+：隐藏状态栏和导航栏
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // API 29 以下
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
        // FLAG_FULLSCREEN 确保窗口真正全屏
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun prepareAndLoad() {
        webRoot = File(filesDir, "rp-hub-web")
        if (!webRoot.exists() || needUpdateAssets()) {
            copyAssetsTo(RP_HUB_ASSET_DIR, webRoot)
            markAssetsUpdated()
        }
        startLocalServer()
        webView.loadUrl("http://localhost:$SERVER_PORT/index.html")
    }

    private fun copyAssetsTo(assetDir: String, targetDir: File) {
        targetDir.mkdirs()
        try {
            val files = assets.list(assetDir) ?: return
            for (file in files) {
                val srcPath = "$assetDir/$file"
                val destFile = File(targetDir, file)
                if (assets.list(srcPath)?.isNotEmpty() == true) {
                    copyAssetsTo(srcPath, destFile)
                } else {
                    assets.open(srcPath).use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "复制 assets 失败", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun needUpdateAssets(): Boolean {
        return getSharedPreferences(PREF_NAME, MODE_PRIVATE).getInt(KEY_ASSET_VERSION, 0) < CURRENT_ASSET_VERSION
    }

    private fun markAssetsUpdated() {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putInt(KEY_ASSET_VERSION, CURRENT_ASSET_VERSION).apply()
    }

    private fun startLocalServer() {
        localServer = LocalWebServer(SERVER_PORT, webRoot)
        try {
            localServer?.start()
        } catch (e: IOException) {
            Log.e(TAG, "本地服务器启动失败", e)
            webView.loadUrl("file://${webRoot.absolutePath}/index.html")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        applyImmersiveMode()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        localServer?.stop()
        webView.destroy()
        super.onDestroy()
    }
}
