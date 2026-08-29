package com.halo.yunquevoice.memory

import com.halo.yunquevoice.voice.VoiceMvpClient
import com.halo.yunquevoice.voice.VoiceMvpLog

/**
 * 自动维护“关于我”：
 * 从最近对话中由 AI 自主提取关于用户的兴趣、性格、习惯等信息，
 * 不预设任何板块，全部自由文本，可后续手动编辑。
 */
object AboutMeExtractor {

    suspend fun extract(db: MemoryDb, deepSeekKey: String): Int {
        val convs = db.recentConversations(80)
        if (convs.size < 3) {
            VoiceMvpLog.i("ABOUTME", "对话不足，跳过关于我提炼")
            return 0
        }
        val batch = convs.joinToString("\n") { "[${it.speakerName ?: "未知"}] ${it.text}" }
        VoiceMvpLog.i("ABOUTME", "开始提炼关于我，对话 ${convs.size} 条")
        val facts = runCatching { VoiceMvpClient.extractAboutMe(deepSeekKey, batch) }
            .getOrElse {
                VoiceMvpLog.e("ABOUTME", "提炼失败: ${it.message}", it)
                return 0
            }
        val existing = db.listAboutMe().map { it.content }.toSet()
        var added = 0
        for (f in facts) {
            val clean = f.trim()
            if (clean.isNotEmpty() && clean !in existing) {
                db.addAboutMe(clean, source = "ai")
                added++
            }
        }
        VoiceMvpLog.i("ABOUTME", "关于我自动添加 $added 条")
        return added
    }
}
