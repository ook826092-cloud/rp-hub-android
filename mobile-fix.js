/**
 * RP-Hub Android 修复脚本
 * 在 RP-Hub 的 app.js 加载之前注入
 */
(function() {
    try {

    // ===== 1. 修复 visualViewport =====
    var realViewport = window.visualViewport;
    if (realViewport) {
        try {
            var fakeViewport = new Proxy(realViewport, {
                get: function(target, prop) {
                    try {
                        if (prop === 'height') return window.innerHeight;
                        if (prop === 'offsetTop') return 0;
                    } catch(e) {}
                    var val = target[prop];
                    if (typeof val === 'function') return val.bind(target);
                    return val;
                }
            });
            Object.defineProperty(window, 'visualViewport', {
                get: function() { return fakeViewport; },
                configurable: true
            });
        } catch(e) {
            console.warn('visualViewport proxy failed:', e);
        }
    }

    // ===== 2. Polyfill Fullscreen API =====
    try {
        var _fullscreenElement = null;

        Object.defineProperty(document, 'fullscreenElement', {
            get: function() { return _fullscreenElement; },
            configurable: true
        });
        Object.defineProperty(document, 'webkitFullscreenElement', {
            get: function() { return _fullscreenElement; },
            configurable: true
        });

        Element.prototype.requestFullscreen = function() {
            var self = this;
            return new Promise(function(resolve, reject) {
                try {
                    if (_fullscreenElement) {
                        _fullscreenElement.classList.remove('app-native-fullscreen');
                    }
                    self.classList.add('app-native-fullscreen');
                    _fullscreenElement = self;
                    setTimeout(function() {
                        document.dispatchEvent(new Event('fullscreenchange'));
                        document.dispatchEvent(new Event('webkitfullscreenchange'));
                    }, 0);
                    resolve();
                } catch(e) { reject(e); }
            });
        };
        Element.prototype.webkitRequestFullscreen = Element.prototype.requestFullscreen;

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
                } catch(e) { reject(e); }
            });
        };
        document.webkitExitFullscreen = document.exitFullscreen;
    } catch(e) {
        console.warn('Fullscreen polyfill failed:', e);
    }

    // ===== 3. 安卓优化 =====

    // 禁止双击缩放
    try {
        var lastTouch = 0;
        document.addEventListener('touchend', function(e) {
            var now = Date.now();
            if (now - lastTouch < 300) { e.preventDefault(); }
            lastTouch = now;
        }, { passive: false });
    } catch(e) {}

    // 按钮触摸震动反馈
    try {
        document.addEventListener('click', function(e) {
            var target = e.target.closest('button');
            if (target && navigator.vibrate) { navigator.vibrate(8); }
        }, true);
    } catch(e) {}

    // 屏幕常亮
    try {
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
    } catch(e) {}

    } catch(e) {
        console.error('mobile-fix.js error:', e);
    }
})();
