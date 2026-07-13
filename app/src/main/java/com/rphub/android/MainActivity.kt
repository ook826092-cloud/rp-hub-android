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
        private const val CURRENT_ASSET_VERSION = 13
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

        window.decorView.setBackgroundColor(Color.parseColor("#F9FAFB"))

        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#F9FAFB"))
        setContentView(webView)

        applyImmersiveMode()

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
        }

        // ★ WebChromeClient：文件选择 + 控制台日志（不碰 JS 对话框）
        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                try {
                    val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST)
                } catch (e: Exception) {
                    filePathCallback = null
                    return false
                }
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "JS: ${consoleMessage?.message()}")
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }

        // 下载支持
        webView.setDownloadListener { url, _, _, _, _ ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Toast.makeText(this, "无法下载文件", Toast.LENGTH_SHORT).show()
            }
        }

        prepareAndLoad()
    }

    private fun applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
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
            if (callback != null) {
                val result = if (resultCode == RESULT_OK) {
                    data?.data?.let { arrayOf(it) }
                } else null
                callback.onReceiveValue(result)
            }
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
