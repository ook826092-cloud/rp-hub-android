/**
 * RP-Hub Android 全屏 API 修复 + 安卓优化
 * 通过 evaluateJavascript 注入（不修改 HTML）
 */
(function() {
    // Fullscreen API polyfill
    try {
        if (!document.fullscreenEnabled && !document.webkitFullscreenEnabled) {
            var _fs = null;

            Element.prototype.requestFullscreen = function() {
                var el = this;
                return new Promise(function(resolve) {
                    if (_fs) _fs.classList.remove('app-native-fullscreen');
                    el.classList.add('app-native-fullscreen');
                    _fs = el;
                    setTimeout(function() {
                        document.dispatchEvent(new Event('fullscreenchange'));
                        document.dispatchEvent(new Event('webkitfullscreenchange'));
                    }, 0);
                    resolve();
                });
            };
            if (!Element.prototype.webkitRequestFullscreen) {
                Element.prototype.webkitRequestFullscreen = Element.prototype.requestFullscreen;
            }

            document.exitFullscreen = function() {
                return new Promise(function(resolve) {
                    if (_fs) { _fs.classList.remove('app-native-fullscreen'); _fs = null; }
                    setTimeout(function() {
                        document.dispatchEvent(new Event('fullscreenchange'));
                        document.dispatchEvent(new Event('webkitfullscreenchange'));
                    }, 0);
                    resolve();
                });
            };
            if (!document.webkitExitFullscreen) {
                document.webkitExitFullscreen = document.exitFullscreen;
            }
        }
    } catch(e) { console.warn('Fullscreen polyfill error:', e); }

    // 禁止双击缩放
    try {
        var lt = 0;
        document.addEventListener('touchend', function(e) {
            var n = Date.now();
            if (n - lt < 300) e.preventDefault();
            lt = n;
        }, { passive: false });
    } catch(e) {}

    // 按钮震动反馈
    try {
        document.addEventListener('click', function(e) {
            var t = e.target.closest('button');
            if (t && navigator.vibrate) navigator.vibrate(8);
        }, true);
    } catch(e) {}

    // 屏幕常亮
    try {
        var wl = null;
        if (navigator.wakeLock) {
            navigator.wakeLock.request('screen').then(function(w) { wl = w; }).catch(function() {});
            document.addEventListener('visibilitychange', function() {
                if (document.hidden) { if (wl) { try { wl.release(); } catch(e){} wl = null; } }
                else { if (navigator.wakeLock) navigator.wakeLock.request('screen').then(function(w) { wl = w; }).catch(function() {}); }
            });
        }
    } catch(e) {}
})();
