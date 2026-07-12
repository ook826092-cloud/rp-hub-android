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
        private const val CURRENT_ASSET_VERSION = 6
    }

    private lateinit var webView: WebView
    private lateinit var webRoot: File
    private var localServer: LocalWebServer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // 全屏 flags
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // 背景白色
        window.decorView.setBackgroundColor(Color.WHITE)

        // WebView 全屏
        webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        setContentView(webView)

        applyImmersiveMode()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        // 关闭 wideViewPort = 移动端布局
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.displayZoomControls = false

        // 文字大小不自动调整
        settings.textZoom = 100

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true

        // Mobile UA
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
                view.setBackgroundColor(Color.WHITE)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.setBackgroundColor(Color.TRANSPARENT)
                // ★ 核心：注入 JS 修复高度
                // RP-Hub 自己的 JS 会用 visualViewport.height 设置 --app-visual-height
                // 但这个值在 Android WebView 全屏下可能不包含状态栏区域
                // 我们用 MutationObserver 监听并强制覆盖为 window.innerHeight
                view.evaluateJavascript(FIX_SCRIPT, null)
                applyImmersiveMode()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request?.isForMainFrame == true) {
                    Log.e(TAG, "页面加载失败: ${error?.description}")
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
        prepareAndLoad()
    }

    /**
     * 注入的 JS 脚本：
     * 1. 用 MutationObserver 监听 <html> 的 style 属性变化
     * 2. 每当 RP-Hub 的 JS 修改 --app-visual-height 时，立即覆盖为 window.innerHeight
     * 3. 同时监听 resize/orientationchange/visualViewport.resize 事件
     */
    private val FIX_SCRIPT = """
(function(){
    function fixHeight(){
        var h = window.innerHeight;
        var html = document.documentElement;
        html.style.setProperty('--app-visual-height', h + 'px');
        html.style.setProperty('--chat-bg-height', h + 'px');
        var app = document.getElementById('app');
        if(app){ app.style.height = h + 'px'; }
    }

    // 立即执行
    fixHeight();

    // 延迟执行（等 RP-Hub JS 初始化后覆盖）
    setTimeout(fixHeight, 50);
    setTimeout(fixHeight, 200);
    setTimeout(fixHeight, 500);
    setTimeout(fixHeight, 1000);
    setTimeout(fixHeight, 2000);
    setTimeout(fixHeight, 3000);

    // 监听窗口变化
    window.addEventListener('resize', fixHeight);
    window.addEventListener('orientationchange', function(){ setTimeout(fixHeight, 100); });

    // 监听 visualViewport 变化（capture=true 先于 RP-Hub 自己的监听器执行）
    if(window.visualViewport){
        window.visualViewport.addEventListener('resize', fixHeight, true);
        window.visualViewport.addEventListener('scroll', fixHeight, true);
    }

    // MutationObserver：当 RP-Hub 修改 <html> style 时重新覆盖
    var observer = new MutationObserver(function(){
        fixHeight();
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['style'] });

    // ===== 额外安卓优化 =====

    // 禁止双击缩放
    var lastTouch = 0;
    document.addEventListener('touchend', function(e){
        var now = Date.now();
        if(now - lastTouch < 300){ e.preventDefault(); }
        lastTouch = now;
    }, { passive: false });

    // 按钮触摸震动反馈
    document.addEventListener('click', function(e){
        var target = e.target.closest('button');
        if(target && navigator.vibrate){ navigator.vibrate(8); }
    }, true);

    // 屏幕常亮（生成中不熄屏）
    var wakeLock = null;
    async function requestWakeLock(){
        try { wakeLock = await navigator.wakeLock.request('screen'); } catch(e){}
    }
    async function releaseWakeLock(){
        if(wakeLock){ try { wakeLock.release(); } catch(e){} wakeLock = null; }
    }
    document.addEventListener('visibilitychange', function(){
        if(document.hidden){ releaseWakeLock(); } else { requestWakeLock(); }
    });
    requestWakeLock();
})();
""".trimIndent()

    private fun applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
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
