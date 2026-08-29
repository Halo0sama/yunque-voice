package com.halo.yunquevoice.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.WorkingMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 云端记忆上传的统一入口：聆听旁听与打字输入共用（上云规则与聆听模式划等号）。
 * - 噪声门：去空白不足 4 字且非"记住"类指令的不上传
 * - "记住/记一下/别忘了"：剥掉指令词后立即 verbatim 上云，不进簇（避免双份）
 * - 其余：进会话簇，攒批（满 24 句 / 静默 2 分钟）一次性 AddMemory，失败整批入 outbox
 *
 * 独立协程与 Handler，不挂在服务生命周期上，服务停止/切后台都能把欠账冲出去。
 */
object MemoryUploader {

    private const val BATCH_MAX = 24
    private const val IDLE_MS = 120_000L

    @Volatile private var appContext: Context? = null
    @Volatile private var db: MemoryDb? = null
    private val pending = mutableListOf<BailianMemory.UploadItem>()
    private val lock = Any()
    @Volatile private var idleScheduled = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isMemoryCommand(text: String): Boolean =
        text.contains("记住") || text.contains("记一下") ||
            text.contains("别忘了") || text.contains("你要记住")

    fun stripMemoryCommand(text: String): String =
        text.replace(Regex("^云雀[，,。！!：:]?"), "")
            .replace(Regex("^(请|麻烦)?(你)?(帮我)?(记住|记一下|别忘了|你要记住)[:：]?"), "")
            .trim()

    /** 统一入口。speaker：旁听为识别出的说话人，打字为主人称呼。 */
    fun enqueue(context: Context, db: MemoryDb, text: String, speaker: String) {
        if (appContext == null) appContext = context.applicationContext
        if (this.db == null) this.db = db
        if (!Store.cloudMemoryEnabled(context)) return
        val clean = text.filter { !it.isWhitespace() }
        if (clean.length < 4 && !isMemoryCommand(text)) {
            WorkingMemory.stat(db, "noise_filtered")
            return
        }
        if (isMemoryCommand(text)) {
            val memoryText = stripMemoryCommand(text)
            if (memoryText.isNotEmpty()) {
                val owner = speaker.ifBlank { Store.myName(context).ifBlank { "主人" } }
                scope.launch {
                    runCatching { BailianMemory.appendReliably(context, memoryText, owner, verbatim = true) }
                        .onSuccess { VoiceMvpLog.i("MEMORY", "手动记忆已上云: $memoryText") }
                        .onFailure { VoiceMvpLog.w("MEMORY", "手动记忆已入 outbox: $memoryText") }
                }
            }
            return
        }
        val flushNow = synchronized(lock) {
            pending.add(BailianMemory.UploadItem(text, speaker, System.currentTimeMillis()))
            pending.size >= BATCH_MAX
        }
        if (flushNow) flush() else scheduleIdle()
    }

    private fun scheduleIdle() {
        if (idleScheduled) return
        idleScheduled = true
        mainHandler.postDelayed({
            idleScheduled = false
            flush()
        }, IDLE_MS)
    }

    /** 取走当前整簇并异步上传；失败由 BailianMemory 落 outbox。 */
    fun flush() {
        val ctx = appContext ?: return
        val db = this.db
        val batch = synchronized(lock) {
            if (pending.isEmpty()) return
            val b = pending.toList()
            pending.clear()
            b
        }
        if (db != null) {
            WorkingMemory.stat(db, "upload_calls")
            WorkingMemory.stat(db, "upload_sentences", batch.size.toDouble())
        }
        scope.launch {
            runCatching { BailianMemory.appendBatchReliably(ctx, batch) }
                .onSuccess { VoiceMvpLog.i("BAILIAN", "簇上传成功 ${batch.size} 句") }
                .onFailure { VoiceMvpLog.w("BAILIAN", "簇上传失败（整批入 outbox）: ${it.message}") }
        }
    }

    fun pendingCount(): Int = synchronized(lock) { pending.size }
}
