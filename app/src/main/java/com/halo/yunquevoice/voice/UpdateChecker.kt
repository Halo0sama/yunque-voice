package com.halo.yunquevoice.voice

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：检查 GitHub Releases 最新版本，下载 APK 到 cacheDir。
 * 直连 github.com / objects.githubusercontent.com（APK 资产直链）。
 */
object UpdateChecker {

    private const val REPO = "Halo0sama/yunque-voice"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    data class UpdateInfo(
        val tag: String,          // 如 v0.18.0
        val versionName: String,  // 0.18.0
        val apkUrl: String,
        val notes: String,
        val apkSize: Long
    )

    /** 后台线程调用。有更新返回 UpdateInfo，无更新/失败返回 null（失败静默）。 */
    fun check(currentVersionName: String): UpdateInfo? {
        return runCatching {
            val conn = URL(API_LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val body = conn.inputStream.use { it.bufferedReader().readText() }
            conn.disconnect()
            val o = JSONObject(body)
            val tag = o.optString("tag_name", "")
            if (tag.isBlank()) return null
            val latest = tag.removePrefix("v")
            android.util.Log.i("YunqueVoice", "更新检查: latest=$latest current=$currentVersionName newer=${isNewer(latest, currentVersionName)}")
            if (!isNewer(latest, currentVersionName)) return null
            var apkUrl = ""
            var size = 0L
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            if (apkUrl.isBlank()) return null
            UpdateInfo(tag, latest, apkUrl, o.optString("body", ""), size)
        }.getOrNull()
    }

    /** 语义化版本比较：a > b 为真。 */
    private fun isNewer(a: String, b: String): Boolean {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 下载 APK 到 cacheDir（后台线程）。返回文件或 null。 */
    fun download(context: Context, url: String): File? {
        return runCatching {
            val out = File(context.cacheDir, "yunque_update.apk")
            out.delete()
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.inputStream.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            conn.disconnect()
            if (out.length() > 1_000_000) out else null
        }.getOrNull()
    }
}
