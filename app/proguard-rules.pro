# RP-Hub ProGuard 规则
# 保留 NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# 保留 WebView JS 接口（如有）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留 Kotlin Metadata
-keepattributes *Annotation*
-dontwarn kotlin.Metadata
