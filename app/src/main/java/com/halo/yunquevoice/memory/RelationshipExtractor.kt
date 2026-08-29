package com.halo.yunquevoice.memory

import com.halo.yunquevoice.voice.VoiceMvpClient
import com.halo.yunquevoice.voice.VoiceMvpLog
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 人物关系自动梳理：
 * - AI 从对话中推断说话人真实姓名（证据充分时）
 * - AI 推断人物关系
 * - 结果写回 speakers / relationships，供关系图谱使用
 */
object RelationshipExtractor {

    suspend fun extract(db: MemoryDb, deepSeekKey: String): Int {
        val convs = db.recentConversations(80)
        if (convs.size < 3) {
            VoiceMvpLog.i("RELATION", "对话不足，跳过关系梳理")
            return 0
        }
        val batch = convs.joinToString("\n") {
            "[${it.speakerName ?: "未知"}] ${it.text}"
        }
        VoiceMvpLog.i("RELATION", "开始关系梳理，对话 ${convs.size} 条")
        val result = runCatching { VoiceMvpClient.extractRelations(deepSeekKey, batch) }
            .getOrElse {
                VoiceMvpLog.e("RELATION", "关系梳理失败: ${it.message}", it)
                return 0
            }
        var changed = 0

        // 1) 名字推断
        val names = result.optJSONArray("names") ?: JSONArray()
        for (i in 0 until names.length()) {
            val item = names.getJSONObject(i)
            val speakerTag = item.optString("speaker")
            val realName = item.optString("name").trim()
            val evidence = item.optString("evidence")
            if (realName.isBlank() || realName.length > 20) continue
            val speaker = db.getSpeakers().firstOrNull { it.name == speakerTag } ?: continue
            if (speaker.name == realName) continue
            db.renameSpeaker(speaker.id, realName)
            VoiceMvpLog.i("RELATION", "自动命名：$speakerTag -> $realName（$evidence）")
            changed++
        }

        // 2) 关系写入
        val rels = result.optJSONArray("relations") ?: JSONArray()
        for (i in 0 until rels.length()) {
            val item = rels.getJSONObject(i)
            val from = item.optString("from").trim()
            val to = item.optString("to").trim()
            val relation = item.optString("relation").trim()
            val evidence = item.optString("evidence")
            if (from.isBlank() || to.isBlank() || relation.isBlank() || from == "云雀" || to == "云雀") continue
            val source = ensureSpeaker(db, from)
            val target = ensureSpeaker(db, to)
            db.addRelationship(source.id, source.name, target.id, target.name, relation, evidence)
            VoiceMvpLog.i("RELATION", "关系：${source.name} --$relation--> ${target.name}（$evidence）")
            changed++
        }
        VoiceMvpLog.i("RELATION", "关系梳理完成，变动 $changed 项")
        return changed
    }

    private fun ensureSpeaker(db: MemoryDb, name: String): SpeakerProfile {
        db.getSpeakers().firstOrNull { it.name == name }?.let { return it }
        val created = SpeakerProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            feature = "[]",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            sampleCount = 0
        )
        db.upsertSpeaker(created)
        return created
    }
}
