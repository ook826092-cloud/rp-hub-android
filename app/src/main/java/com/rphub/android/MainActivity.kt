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

/**
 * RP-Hub Android WebView 套壳应用
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "RP-Hub"
        private const val SERVER_PORT = 8080
        private const val RP_HUB_ASSET_DIR = "rp-hub-web"
        private const val PREF_NAME = "rphub_prefs"
        private const val KEY_ASSET_VERSION = "asset_version"
        private const val CURRENT_ASSET_VERSION = 3
    }

    private lateinit var webView: WebView
    private lateinit var webRoot: File
    private var localServer: LocalWebServer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 隐藏标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // ★ 关键：在 setContentView 之前设置沉浸式
        // 让内容延伸到状态栏和导航栏后面
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        // ★ 设置 window 背景为白色，防止黑边
        window.decorView.setBackgroundColor(Color.WHITE)

        // 创建 WebView，用 FrameLayout 确保填满整个屏幕
        webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        val layout = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        layout.addView(webView, params)
        setContentView(layout)

        // 应用沉浸式全屏（隐藏系统栏）
        applyImmersiveMode()

        // WebView 配置
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        // 视口自适应
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.displayZoomControls = false

        // 缓存
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // 允许 file:// 域的 JS 访问
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true

        // User-Agent
        settings.userAgentString = "${settings.userAgentString} RP-Hub-App/1.0"

        // 调试模式
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) {
                    return false
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "无法打开: $url", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                view.setBackgroundColor(Color.WHITE)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.setBackgroundColor(Color.TRANSPARENT)
                applyImmersiveMode()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (request?.isForMainFrame == true) {
                        Log.e(TAG, "页面加载失败: ${error?.description}")
                    }
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        // 准备文件 → 启动本地服务器 → 加载页面
        prepareAndLoad()
    }

    /**
     * 沉浸式全屏 — 彻底隐藏状态栏和导航栏
     */
    private fun applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
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
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "复制 assets 失败", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun needUpdateAssets(): Boolean {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        return prefs.getInt(KEY_ASSET_VERSION, 0) < CURRENT_ASSET_VERSION
    }

    private fun markAssetsUpdated() {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_ASSET_VERSION, CURRENT_ASSET_VERSION)
            .apply()
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
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
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
