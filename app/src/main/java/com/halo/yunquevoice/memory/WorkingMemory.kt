package com.halo.yunquevoice.memory

import android.content.Context
import com.halo.yunquevoice.voice.BailianMemory
import com.halo.yunquevoice.voice.VoiceMvpClient
import com.halo.yunquevoice.voice.VoiceMvpLog
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 工作记忆层（harness 式会话）：
 * - 原话永远落盘在 conversations 表，内存里不存任何"只此一份"的状态
 * - 决策上下文 = 滚动摘要(session_state.summary) + 近期原话窗口(默认48h) + 当前输入
 * - 每天第一句旁听前惰性压缩：把窗口外的旧原话折进摘要，提炼出的新事实顺手上传记忆库
 * - 保险丝：窗口估算 token 超限就提前压缩，压缩连续失败则裁剪最老原话（数据不删，只是不进 prompt）
 */
object WorkingMemory {

    private const val VERBATIM_WINDOW_MS = 48L * 3600_000
    private const val VERBATIM_MAX_TURNS = 80
    private const val VERBATIM_MAX_CHARS = 24_000        // 约 16K token，正常两天远到不了
    private const val SUMMARY_MAX_CHARS = 4_000          // 约 2.5K token
    private const val COMPACTION_INPUT_MAX_CHARS = 60_000
    const val FUSE_TOKENS = 24_000                       // 保险丝：窗口估算 token 超过就提前压缩

    data class WorkingContext(
        val summary: String,
        val verbatimLines: List<String>,
        val verbatimChars: Int,
        val fromDbRebuild: Boolean
    ) {
        fun estTokens(): Int = ((summary.length + verbatimChars) * 0.7).toInt()
    }

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = dayFmt.format(Date())

    fun stat(db: MemoryDb, key: String, delta: Double = 1.0) {
        runCatching { db.statAdd(today(), key, delta) }
    }

    /** 决策用的工作上下文：摘要 + 原话窗口（含云雀自己的回复），全部从 DB 重建。 */
    fun buildContext(db: MemoryDb): WorkingContext {
        val state = db.loadSessionState()
        val windowStart = maxOf(state.summarizedUntilTs, System.currentTimeMillis() - VERBATIM_WINDOW_MS)
        val turns = db.conversationsBetween(windowStart, System.currentTimeMillis() + 60_000, VERBATIM_MAX_TURNS)
        val lines = mutableListOf<String>()
        var chars = 0
        for (t in turns) {
            val who = when (t.origin) {
                "assistant" -> "云雀说"
                else -> "${t.speakerName ?: "某人"}说"
            }
            val missed = t.missedText?.takeIf { it.isNotBlank() }?.let { "（漏听未播：$it）" } ?: ""
            val line = "（$who）${t.text}$missed"
            if (chars + line.length > VERBATIM_MAX_CHARS && lines.isNotEmpty()) break
            lines.add(line)
            chars += line.length
        }
        return WorkingContext(state.summary, lines, chars, fromDbRebuild = true).also {
            VoiceMvpLog.i("WORKMEM", "上下文重建：原话 ${lines.size} 句/${chars} 字，摘要 ${state.summary.length} 字（自 ${windowStart}）")
        }
    }

    /** 保险丝与裁剪判定：返回 true 表示窗口超限，需要压缩或裁剪。 */
    fun overFuse(ctx: WorkingContext): Boolean = ctx.estTokens() > FUSE_TOKENS

    /**
     * 压缩：把 [summarizedUntil, now-24h] 的旧原话折进滚动摘要，新事实上传记忆库。
     * 由惰性触发（跨天第一句）或保险丝触发。并发由调用方保证。
     */
    suspend fun compact(
        context: Context,
        db: MemoryDb,
        deepSeekKey: String,
        reason: String,
        cutoffHours: Long = 24
    ): Boolean {
        val state = db.loadSessionState()
        val day = today()
        val cutoff = System.currentTimeMillis() - cutoffHours * 3600_000
        val oldTurns = db.conversationsBetween(state.summarizedUntilTs, cutoff, 400)
        if (oldTurns.isEmpty()) {
            // 没有可折叠的原话，只推进日期标记，不浪费一次 LLM 调用
            db.saveSessionState(state.copy(lastCompactionDay = day))
            return true
        }
        val transcript = StringBuilder()
        for (t in oldTurns) {
            val who = when (t.origin) {
                "assistant" -> "云雀"
                else -> t.speakerName ?: "某人"
            }
            transcript.append("（${who}说）${t.text}\n")
            if (transcript.length > COMPACTION_INPUT_MAX_CHARS) break
        }
        val oldSummary = state.summary.ifBlank { "（无）" }
        val system = "你是云雀的记忆压缩器。输入是一份旧的对话摘要和一段时间内的新对话原话。" +
            "输出严格的 JSON：{\"summary\":\"合并后的新摘要，不超过600字，保留人物、关系、事实、计划和未尽事项，按主题组织\"," +
            "\"facts\":[\"值得长期记住的独立事实，每条一句话，没有就给空数组\"]}。只输出 JSON。"
        val user = "【旧摘要】\n$oldSummary\n\n【新对话原话】\n$transcript"
        val content = runCatching {
            VoiceMvpClient.completeRaw(deepSeekKey, system, user, "COMPACTION")
        }.getOrElse {
            VoiceMvpLog.w("WORKMEM", "压缩调用失败($reason): ${it.message}")
            stat(db, "compaction_fail")
            return false
        }
        val parsed = parseCompaction(content)
            ?: run {
                VoiceMvpLog.w("WORKMEM", "压缩返回无法解析($reason): ${content.take(120)}")
                stat(db, "compaction_fail")
                return false
            }
        val newSummary = parsed.optString("summary", "").take(SUMMARY_MAX_CHARS)
        val facts = mutableListOf<String>()
        val factArr = parsed.optJSONArray("facts") ?: JSONArray()
        for (i in 0 until factArr.length()) {
            factArr.optString(i).takeIf { it.isNotBlank() }?.let { facts.add(it.trim()) }
        }
        val lastTs = oldTurns.maxOfOrNull { it.ts } ?: state.summarizedUntilTs
        db.saveSessionState(
            MemoryDb.SessionState(
                summary = newSummary,
                summarizedUntilTs = maxOf(lastTs, state.summarizedUntilTs),
                lastCompactionDay = day
            )
        )
        stat(db, "compaction_run")
        stat(db, "compaction_turns", oldTurns.size.toDouble())
        stat(db, "compaction_facts", facts.size.toDouble())
        VoiceMvpLog.i(
            "WORKMEM",
            "压缩完成($reason)：折叠 ${oldTurns.size} 句，摘要 ${newSummary.length} 字，新事实 ${facts.size} 条"
        )
        if (facts.isNotEmpty()) {
            runCatching { BailianMemory.addFacts(context, facts) }
                .onSuccess { stat(db, "compaction_facts_uploaded", facts.size.toDouble()) }
                .onFailure { VoiceMvpLog.w("WORKMEM", "压缩事实上传失败: ${it.message}") }
        }
        return true
    }

    /** 是否应该做每日压缩：本地日期跨天了。 */
    fun shouldDailyCompact(db: MemoryDb): Boolean = db.loadSessionState().lastCompactionDay != today()

    private fun parseCompaction(content: String): JSONObject? {
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    fun yesterdayStart(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
