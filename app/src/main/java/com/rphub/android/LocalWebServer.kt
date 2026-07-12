package com.rphub.android

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * 基于 NanoHTTPD 的本地文件服务器
 * 为 RP-Hub 提供标准 HTTP 访问，解决 file:// 和 content:// 协议的兼容性问题
 */
class LocalWebServer(port: Int, private val webRoot: File) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "RP-Hub-Server"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        try {
            // 默认页面
            val path = if (uri == "/" || uri == "") "/index.html" else uri

            // 安全检查：防止目录遍历
            val file = File(webRoot, path).canonicalFile
            if (!file.path.startsWith(webRoot.canonicalPath)) {
                Log.w(TAG, "拒绝访问: $path (目录遍历)")
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "403 Forbidden")
            }

            if (!file.exists() || !file.isFile) {
                // SPA 路由 fallback：找不到文件时返回 index.html
                val indexFile = File(webRoot, "index.html")
                if (indexFile.exists()) {
                    return serveFile(indexFile, "text/html; charset=utf-8")
                }
                Log.w(TAG, "文件未找到: $path")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found")
            }

            // 根据扩展名设置 Content-Type
            val contentType = guessContentType(file.name)
            return serveFile(file, contentType)

        } catch (e: Exception) {
            Log.e(TAG, "服务请求失败: $uri", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "500 Internal Server Error"
            )
        }
    }

    private fun serveFile(file: File, contentType: String): Response {
        return try {
            val fis = FileInputStream(file)
            newChunkedResponse(Response.Status.OK, contentType, fis)
        } catch (e: IOException) {
            Log.e(TAG, "读取文件失败: ${file.name}", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "500 Internal Server Error"
            )
        }
    }

    private fun guessContentType(filename: String): String {
        return when {
            filename.endsWith(".html") || filename.endsWith(".htm") -> "text/html; charset=utf-8"
            filename.endsWith(".css") -> "text/css; charset=utf-8"
            filename.endsWith(".js") -> "application/javascript; charset=utf-8"
            filename.endsWith(".json") -> "application/json; charset=utf-8"
            filename.endsWith(".png") -> "image/png"
            filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
            filename.endsWith(".gif") -> "image/gif"
            filename.endsWith(".webp") -> "image/webp"
            filename.endsWith(".svg") -> "image/svg+xml"
            filename.endsWith(".ico") -> "image/x-icon"
            filename.endsWith(".woff") -> "font/woff"
            filename.endsWith(".woff2") -> "font/woff2"
            filename.endsWith(".ttf") -> "font/ttf"
            filename.endsWith(".otf") -> "font/otf"
            filename.endsWith(".mp3") -> "audio/mpeg"
            filename.endsWith(".mp4") -> "video/mp4"
            filename.endsWith(".webm") -> "video/webm"
            filename.endsWith(".pdf") -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
