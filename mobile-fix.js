/**
 * RP-Hub Android 修复脚本
 * 在 RP-Hub 的 app.js 加载之前注入
 *
 * 修复内容：
 * 1. 黑额头：Proxy 拦截 visualViewport，防止状态栏高度被误判为键盘
 * 2. 全屏失败：Polyfill requestFullscreen/exitFullscreen，用 CSS class 替代
 *    RP-Hub 的 CSS 已支持 .app-native-fullscreen 类（隐藏侧边栏、全宽主内容）
 *    所以只需要让 requestFullscreen 添加这个 class 并触发 fullscreenchange 事件
 */
(function() {

    // ===== 1. 修复 visualViewport =====
    var realViewport = window.visualViewport;
    if (realViewport) {
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
        Object.defineProperty(window, 'visualViewport', {
            get: function() { return fakeViewport; },
            configurable: true
        });
    }

    // ===== 2. Polyfill Fullscreen API =====
    // Android WebView 不支持 JS Fullscreen API
    // RP-Hub 的 CSS 已有 .app-native-fullscreen 类的样式
    // 我们让 requestFullscreen 添加这个类并触发事件

    var _fullscreenElement = null;

    // 覆盖 document.fullscreenElement
    Object.defineProperty(document, 'fullscreenElement', {
        get: function() { return _fullscreenElement; },
        configurable: true
    });
    Object.defineProperty(document, 'webkitFullscreenElement', {
        get: function() { return _fullscreenElement; },
        configurable: true
    });

    // 覆盖 element.requestFullscreen()
    Element.prototype.requestFullscreen = function() {
        var self = this;
        return new Promise(function(resolve, reject) {
            try {
                // 移除之前的全屏
                if (_fullscreenElement) {
                    _fullscreenElement.classList.remove('app-native-fullscreen');
                }
                // 添加 CSS class（RP-Hub 的 CSS 会隐藏侧边栏、全宽主内容）
                self.classList.add('app-native-fullscreen');
                _fullscreenElement = self;

                // 触发 fullscreenchange 事件
                setTimeout(function() {
                    document.dispatchEvent(new Event('fullscreenchange'));
                    document.dispatchEvent(new Event('webkitfullscreenchange'));
                }, 0);

                resolve();
            } catch(e) {
                reject(e);
            }
        });
    };
    Element.prototype.webkitRequestFullscreen = Element.prototype.requestFullscreen;

    // 覆盖 document.exitFullscreen()
    document.exitFullscreen = function() {
        return new Promise(function(resolve, reject) {
            try {
                if (_fullscreenElement) {
                    _fullscreenElement.classList.remove('app-native-fullscreen');
                    _fullscreenElement = null;
                }
                setTimeout(function() {
                    document.dispatchEvent(new Event('fullscreenchange'));
                    document.dispatchEvent(new Event('webkitfullscreenchange'));
                }, 0);
                resolve();
            } catch(e) {
                reject(e);
            }
        });
    };
    document.webkitExitFullscreen = document.exitFullscreen;

    // ===== 3. 安卓优化 =====

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

    // 屏幕常亮
    var wakeLock = null;
    function requestWakeLock() {
        if (navigator.wakeLock) {
            navigator.wakeLock.request('screen').then(function(wl) {
                wakeLock = wl;
            }).catch(function() {});
        }
    }
    function releaseWakeLock() {
        if (wakeLock) { try { wakeLock.release(); } catch(e) {} wakeLock = null; }
    }
    document.addEventListener('visibilitychange', function() {
        if (document.hidden) { releaseWakeLock(); } else { requestWakeLock(); }
    });
    requestWakeLock();
})();
