/**
 * RP-Hub Android 修复脚本
 * 在 RP-Hub 的 app.js 加载之前注入
 *
 * 根因：RP-Hub 的 syncMobileVisualViewport() 会计算
 *   keyboardInset = innerHeight - visualViewport.height
 * 在 Android WebView 全屏模式下，这个差值 = 状态栏高度（不是键盘）
 * 当状态栏高度 > 40px 时，被误判为键盘弹出，导致：
 *   1. --app-visual-height 被设为含状态栏的 innerHeight
 *   2. --keyboard-inset 被设为状态栏高度
 *   3. 顶部内容被状态栏覆盖 → 黑额头
 *   4. 输入框上移 → 底部空白
 *
 * 修复：用 Proxy 拦截 visualViewport，让 height 始终 = innerHeight
 */
(function() {
    var realViewport = window.visualViewport;
    if (!realViewport) return;

    // 创建代理对象，覆盖 height 和 offsetTop
    var fakeViewport = new Proxy(realViewport, {
        get: function(target, prop) {
            if (prop === 'height') return window.innerHeight;
            if (prop === 'offsetTop') return 0;
            if (prop === 'offsetLeft') return target.offsetLeft;
            if (prop === 'width') return target.width;
            if (prop === 'scale') return target.scale;
            if (prop === 'pageLeft') return target.pageLeft;
            if (prop === 'pageTop') return target.pageTop;
            var val = target[prop];
            if (typeof val === 'function') return val.bind(target);
            return val;
        }
    });

    // 替换 window.visualViewport
    Object.defineProperty(window, 'visualViewport', {
        get: function() { return fakeViewport; },
        configurable: true
    });

    // ===== 安卓优化 =====

    // 禁止双击缩放
    var lastTouch = 0;
    document.addEventListener('touchend', function(e) {
        var now = Date.now();
        if (now - lastTouch < 300) { e.preventDefault(); }
        lastTouch = now;
    }, { passive: false });

    // 按钮触摸震动反馈
    document.addEventListener('click', function(e) {
        var target = e.target.closest('button');
        if (target && navigator.vibrate) { navigator.vibrate(8); }
    }, true);

    // 屏幕常亮（生成中不熄屏）
    var wakeLock = null;
    function requestWakeLock() {
        if (navigator.wakeLock) {
            navigator.wakeLock.request('screen').then(function(wl) {
                wakeLock = wl;
            }).catch(function() {});
        }
    }
    function releaseWakeLock() {
        if (wakeLock) {
            try { wakeLock.release(); } catch(e) {}
            wakeLock = null;
        }
    }
    document.addEventListener('visibilitychange', function() {
        if (document.hidden) { releaseWakeLock(); } else { requestWakeLock(); }
    });
    requestWakeLock();
})();
