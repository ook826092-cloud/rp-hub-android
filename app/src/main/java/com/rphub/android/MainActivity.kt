package com.rphub.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * RP-Hub Android WebView 套壳应用
 * 
 * 架构说明：
 * 1. 首次启动时，将打包在 APK assets 中的 RP-Hub 文件复制到 app 私有目录
 * 2. 使用 Android 内置的 NanoHTTPD 风格的 LocalServer 提供本地 HTTP 服务
 * 3. WebView 通过 http://localhost:8080 访问 RP-Hub
 * 
 * 这样彻底解决了 content:// 和 file:// 协议下的各种限制问题。
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "RP-Hub"
        private const val SERVER_PORT = 8080
        private const val RP_HUB_ASSET_DIR = "rp-hub-web"
        private const val PREF_NAME = "rphub_prefs"
        private const val KEY_ASSET_VERSION = "asset_version"
        private const val CURRENT_ASSET_VERSION = 1
    }

    private lateinit var webView: WebView
    private lateinit var webRoot: File
    private var localServer: LocalWebServer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 隐藏标题栏 + 沉浸式全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 创建 WebView（全屏，无布局文件）
        webView = WebView(this)
        setContentView(webView)

        // WebView 配置
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true           // RP-Hub 使用 localStorage
        settings.databaseEnabled = true                // IndexedDB 支持
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        // 视口自适应
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.displayZoomControls = false

        // 缓存 — 支持离线
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // 允许 file:// 域的 JS 访问
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true

        // User-Agent
        settings.userAgentString = "${settings.userAgentString} RP-Hub-App/1.0"

        // 调试模式（仅 Debug 构建）
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
                    return false // 本地资源，WebView 自己处理
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false // 外部链接也在 WebView 中打开
                }
                // 非链接（如 tel:, mailto:）交给系统
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "无法打开: $url", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                view.setBackgroundColor(0xFFF9FAFB.toInt())
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.setBackgroundColor(0x00000000)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (request?.isForMainFrame == true) {
                        android.util.Log.e(TAG, "页面加载失败: ${error?.description}", error?.description as? Throwable)
                    }
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        // 准备文件 → 启动本地服务器 → 加载页面
        prepareAndLoad()
    }

    /**
     * 准备 RP-Hub 文件并启动本地服务器
     */
    private fun prepareAndLoad() {
        webRoot = File(filesDir, "rp-hub-web")

        // 首次启动：从 assets 复制文件到 app 私有目录
        if (!webRoot.exists() || needUpdateAssets()) {
            copyAssetsTo(RP_HUB_ASSET_DIR, webRoot)
            markAssetsUpdated()
        }

        // 启动本地 HTTP 服务器
        startLocalServer()

        // 加载 RP-Hub
        webView.loadUrl("http://localhost:$SERVER_PORT/index.html")
    }

    /**
     * 将 APK assets 目录中的文件递归复制到目标目录
     */
    private fun copyAssetsTo(assetDir: String, targetDir: File) {
        targetDir.mkdirs()
        try {
            val files = assets.list(assetDir) ?: return
            for (file in files) {
                val srcPath = "$assetDir/$file"
                val destFile = File(targetDir, file)

                if (assets.list(srcPath)?.isNotEmpty() == true) {
                    // 子目录，递归
                    copyAssetsTo(srcPath, destFile)
                } else {
                    // 文件，复制
                    assets.open(srcPath).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            android.util.Log.e(TAG, "复制 assets 失败", e)
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

    /**
     * 启动本地 HTTP 服务器
     * 使用 NanoHTTPD 库来提供文件服务
     */
    private fun startLocalServer() {
        localServer = LocalWebServer(SERVER_PORT, webRoot)
        try {
            localServer?.start()
        } catch (e: IOException) {
            android.util.Log.e(TAG, "本地服务器启动失败", e)
            // 如果启动失败，降级为直接用 file:// 加载
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
