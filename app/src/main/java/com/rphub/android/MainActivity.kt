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
        private const val CURRENT_ASSET_VERSION = 9
        private const val FILE_CHOOSER_REQUEST = 1001
    }

    private lateinit var webView: WebView
    private lateinit var webRoot: File
    private var localServer: LocalWebServer? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // ★ 沉浸式全屏：隐藏状态栏 + 导航栏 + 内容填满全屏
        // setDecorFitsSystemWindows(false) = 内容延伸到系统栏后面
        // controller.hide() = 真正隐藏系统栏（不是透明，是彻底消失）
        // 两者缺一不可：
        //   - 只 hide 不 setDecorFitsSystemWindows(false) → 系统栏区域变黑
        //   - 只 setDecorFitsSystemWindows(false) 不 hide → 系统栏还看得见、吃触摸事件
        applyImmersiveMode()

        // 背景设为 RP-Hub 底色
        window.decorView.setBackgroundColor(Color.parseColor("#F9FAFB"))

        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#F9FAFB"))
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        settings.useWideViewPort = false
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.displayZoomControls = false
        settings.textZoom = 100

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true

        settings.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) return false
                if (url.startsWith("http://") || url.startsWith("https://")) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                view.setBackgroundColor(Color.parseColor("#F9FAFB"))
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.setBackgroundColor(Color.TRANSPARENT)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request?.isForMainFrame == true) {
                    Log.e(TAG, "页面加载失败: ${error?.description}")
                }
            }
        }

        // ★ WebChromeClient：处理文件选择、alert/confirm 等
        webView.webChromeClient = object : WebChromeClient() {

            // 文件选择（<input type="file">）
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST)
                } catch (e: Exception) {
                    filePathCallback = null
                    return false
                }
                return true
            }

            // JS alert
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                result?.confirm()
                return true
            }

            // JS confirm
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                result?.confirm()
                return true
            }

            // JS prompt
            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult?
            ): Boolean {
                result?.confirm(defaultValue)
                return true
            }

            // 控制台日志
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "JS: ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                return true
            }
        }

        // ★ 下载支持
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法下载文件", Toast.LENGTH_SHORT).show()
            }
        }

        // ★ 允许 content:// URI 访问
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            webView.settings.allowFileAccess = true
            webView.settings.allowContentAccess = true
        }

        prepareAndLoad()
    }

    /**
     * 沉浸式全屏
     * setDecorFitsSystemWindows(false) + controller.hide() 两者缺一不可
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
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val callback = filePathCallback
            filePathCallback = null
            if (callback == null) return
            val result = if (resultCode == RESULT_OK) {
                val uri = data?.data
                if (uri != null) arrayOf(uri) else null
            } else null
            callback.onReceiveValue(result)
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
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "复制 assets 失败", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun needUpdateAssets() = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getInt(KEY_ASSET_VERSION, 0) < CURRENT_ASSET_VERSION

    private fun markAssetsUpdated() = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putInt(KEY_ASSET_VERSION, CURRENT_ASSET_VERSION).apply()

    private fun startLocalServer() {
        localServer = LocalWebServer(SERVER_PORT, webRoot)
        try { localServer?.start() } catch (e: IOException) {
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

    override fun onPause() { webView.onPause(); super.onPause() }

    override fun onDestroy() { localServer?.stop(); webView.destroy(); super.onDestroy() }
}
