package com.halo.yunquevoice.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云雀语音 MVP 的本地日志：同时写 logcat 和 cache/voice_mvp.log。
 */
object VoiceMvpLog {

    private const val LOGCAT_TAG = "YunqueVoice"
    private const val FILE_NAME = "voice_mvp.log"
    private var file: File? = null
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (file == null) {
            file = File(context.cacheDir, FILE_NAME)
            Log.i(LOGCAT_TAG, "VoiceMvpLog initialized: ${file!!.absolutePath}")
        }
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)
    fun w(tag: String, msg: String) = write("W", tag, msg, null)
    fun e(tag: String, msg: String, tr: Throwable? = null) = write("E", tag, msg, tr)

    fun logPath(): String? = file?.absolutePath

    fun dump(): String = file?.takeIf { it.exists() }?.readText() ?: "(no log file)"

    /** 末尾 n 行，供远程排障拉取。 */
    fun tail(n: Int = 300): String {
        val f = file?.takeIf { it.exists() } ?: return "(no log file)"
        return runCatching {
            val lines = f.readLines()
            if (lines.size <= n) lines.joinToString("\n") else lines.takeLast(n).joinToString("\n")
        }.getOrElse { "(read failed: ${it.message})" }
    }

    private fun write(level: String, tag: String, msg: String, tr: Throwable?) {
        val time = fmt.format(Date())
        val line = "$time $level/$tag $msg"
        when (level) {
            "I" -> Log.i(LOGCAT_TAG, line)
            "W" -> Log.w(LOGCAT_TAG, line)
            "E" -> Log.e(LOGCAT_TAG, line, tr)
        }
        try {
            val f = file ?: return
            // 轮转：超过 4MB 归档为 .old（只留一代），保证长期运行日志有界
            if (f.length() > 4L * 1024 * 1024) {
                val old = File(f.parentFile, "$FILE_NAME.old")
                old.delete()
                f.renameTo(old)
            }
            f.parentFile?.mkdirs()
            FileWriter(f, true).use { w ->
                w.write(line)
                w.write("\n")
                if (tr != null) {
                    val sw = StringWriter()
                    tr.printStackTrace(PrintWriter(sw))
                    w.write(sw.toString())
                    w.write("\n")
                }
            }
        } catch (_: Throwable) {
        }
    }
}
