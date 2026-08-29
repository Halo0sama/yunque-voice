package com.halo.yunquevoice.memory

import com.halo.yunquevoice.voice.VoiceMvpClient
import com.halo.yunquevoice.voice.VoiceMvpLog

/**
 * 从最近对话中提炼长期记忆，写入 memories 表。
 * 通过 DeepSeek 读取最近对话，返回 JSON 数组格式的记忆条目。
 */
object MemoryExtractor {

    suspend fun extract(db: MemoryDb, deepSeekKey: String): Int {
        val recent = db.recentConversations(40)
        if (recent.isEmpty()) return 0
        val batch = recent.joinToString("\n") { "[${it.speakerName ?: "未知"}] ${it.text}" }
        VoiceMvpLog.i("MEMORY", "开始自动提炼记忆，对话 ${recent.size} 条")
        val facts = runCatching { VoiceMvpClient.extractMemories(deepSeekKey, batch) }
            .getOrElse {
                VoiceMvpLog.e("MEMORY", "自动提炼失败: ${it.message}", it)
                return 0
            }
        val existing = db.listMemories().map { it.content }.toSet()
        var added = 0
        for (fact in facts) {
            val clean = fact.trim()
            if (clean.isNotEmpty() && clean !in existing) {
                db.addMemory(clean)
                added++
            }
        }
        VoiceMvpLog.i("MEMORY", "自动提炼完成，新增 $added 条记忆")
        return added
    }
}
