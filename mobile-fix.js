/**
 * RP-Hub Android 修复脚本（最小化版本）
 * 只保留最安全的修复，避免任何可能导致崩溃的操作
 */
(function() {

    // Fullscreen API polyfill
    // Android WebView 不支持 JS Fullscreen API
    // RP-Hub 的 CSS 已有 .app-native-fullscreen 类
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
                    }, 0);
                    resolve();
                });
            };
            if (!document.webkitExitFullscreen) {
                document.webkitExitFullscreen = document.exitFullscreen;
            }
        }
    } catch(e) {}

    // 禁止双击缩放
    try {
        var lt = 0;
        document.addEventListener('touchend', function(e) {
            var n = Date.now();
            if (n - lt < 300) e.preventDefault();
            lt = n;
        }, { passive: false });
    } catch(e) {}
})();
