package com.halo.yunquevoice.voice

import android.content.Context
import com.halo.yunquevoice.memory.MemoryDb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日原始数据导出（数据流向：手机 → 夸克同步文件夹 → 网盘 → Mac 定时分析）。
 * - 导出内容：DB（对话/工作记忆/画像/声纹/统计）、完整日志、脱敏配置、声纹样本 wav、云端记忆快照
 * - 导出位置：Download/yunque_export/export_YYYY-MM-DD.tar（公共目录，供夸克同步）
 * - 每个自然日只导一次（检查点文件防重复）；压缩跨天触发后调用，数据含当天全部内容
 * - 保留最近 7 天，旧包自清理
 */
object DailyExporter {

    private const val EXPORT_DIR = "yunque_export"
    private const val KEEP_DAYS = 7

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** 公共 Download/yunque_export/（需用户授予"所有文件访问"权限；个人工具 App 一次性授权）。 */
    fun exportDir(): File =
        File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR)

    fun hasPermission(): Boolean =
        android.os.Environment.isExternalStorageManager()

    /** 跨天后调用一次；同日重入直接跳过。返回导出文件或 null（跳过/失败）。 */
    fun exportDaily(context: Context, db: MemoryDb): File? {
        val day = dayFmt.format(Date())
        if (!hasPermission()) {
            VoiceMvpLog.w("EXPORT", "缺少\"所有文件访问\"权限，本次导出跳过（设置里授权后次日生效）")
            return null
        }
        // 检查点存 app 私有目录（公共区需 MediaStore，不适合放小文件）
        val checkpoint = File(context.filesDir, ".last_export_day")
        if (checkpoint.exists() && checkpoint.readText().trim() == day) return null

        return runCatching { doExport(context, db, day, checkpoint) }
            .onFailure { VoiceMvpLog.w("EXPORT", "每日导出失败: ${it.message}") }
            .getOrNull()
    }

    private fun doExport(context: Context, db: MemoryDb, day: String, checkpoint: File): File? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val work = File(context.cacheDir, "export_$stamp").apply { mkdirs() }
        val dir = exportDir().apply { mkdirs() }

        // 1. 本地 DB（先 checkpoint WAL 保证完整）
        runCatching { db.walCheckpoint() }
        val dbFile = context.getDatabasePath("yunque_memory.db")
        dbFile.takeIf { it.exists() }?.copyTo(File(work, "yunque_memory.db"), overwrite = true)

        // 2. 日志（含轮转旧文件）
        File(context.cacheDir, "voice_mvp.log").takeIf { it.exists() }?.copyTo(File(work, "voice_mvp.log"), overwrite = true)
        File(context.cacheDir, "voice_mvp.log.old").takeIf { it.exists() }?.copyTo(File(work, "voice_mvp.log.old"), overwrite = true)

        // 3. 脱敏配置
        runCatching {
            val prefs = context.getSharedPreferences("yunque_voice", Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in prefs.all) {
                o.put(k, if (k.contains("key") || k.contains("token")) "***MASKED***" else v?.toString() ?: "")
            }
            File(work, "prefs_masked.json").writeText(o.toString(1))
        }

        // 4. 声纹样本（原始 wav，认人分析的关键证据）
        val samplesDir = File(context.cacheDir, "speaker_samples")
        samplesDir.listFiles()?.forEach { f ->
            runCatching { f.copyTo(File(work, "vf_${f.name}"), overwrite = true) }
        }

        // 5. 云端记忆库快照（零花费 ListMemory 分页拉取）
        runCatching {
            val key = Store.dashScopeKey(context)
            val lib = Store.memoryLibraryId(context)
            val ws = Store.workspaceId(context)
            if (key.isNotBlank() && lib.isNotBlank()) {
                val nodes = JSONArray()
                var page = 1
                while (true) {
                    val conn = URL(
                        "https://dashscope.aliyuncs.com/api/v2/apps/memory/memory_nodes" +
                            "?user_id=${Store.memoryUserId(context)}&memory_library_id=$lib&page_size=50&page_num=$page"
                    ).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20000
                    conn.readTimeout = 30000
                    conn.setRequestProperty("Authorization", "Bearer $key")
                    if (ws.isNotBlank()) conn.setRequestProperty("X-DashScope-WorkspaceId", ws)
                    val body = conn.inputStream.use { it.bufferedReader().readText() }
                    val arr = JSONObject(body).optJSONArray("memory_nodes") ?: JSONArray()
                    for (i in 0 until arr.length()) nodes.put(arr.getJSONObject(i))
                    conn.disconnect()
                    if (arr.length() == 0) break
                    page++
                    if (page > 20) break
                }
                File(work, "cloud_memory.json").writeText(nodes.toString(1))
            }
        }.onFailure { VoiceMvpLog.w("EXPORT", "云端快照失败: ${it.message}") }

        // 6. 打包 tar 直写公共 Download/yunque_export/（已授予所有文件访问，原生 File 即可）
        val dirOut = File(dir, "yunque_export_$day.tar")
        dirOut.delete()
        val p = ProcessBuilder("tar", "-cf", dirOut.absolutePath, "-C", work.absolutePath, ".")
            .redirectErrorStream(true).start()
        runCatching { p.inputStream.readBytes() }
        p.waitFor()
        work.deleteRecursively()
        if (!dirOut.exists() || dirOut.length() == 0L) return null

        // 7. 清理 7 天前旧包 + 写检查点
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L
        dir.listFiles()?.filter { it.name.startsWith("yunque_export_") && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
        checkpoint.writeText(day)
        VoiceMvpLog.i("EXPORT", "每日导出完成: ${dirOut.name} (${dirOut.length() / 1024}KB)")
        return dirOut
    }
}
