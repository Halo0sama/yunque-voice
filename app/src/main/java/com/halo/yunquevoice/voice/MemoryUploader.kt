package com.halo.yunquevoice.voice

import android.content.Context
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.WorkingMemory

/**
 * 云端记忆写入入口（v0.16.0 省钱结构）：
 * - 日常旁听/打字不再实时上云——当天时效由工作记忆（原话窗口+摘要+画像）贴身覆盖，
 *   记忆库只服务跨天长尾检索，由每晚压缩产出 facts 一次性入库（max 思考，质量高于实时小簇）
 * - enqueue 仅保留噪声门统计（<4 字计 noise_filtered，供日报观察）
 * - outbox 冲刷保留：兼容旧结构积压的 batch 欠账（充值恢复后自动补传）
 */
object MemoryUploader {

    /** 统一入口。speaker：旁听为识别出的说话人，打字为主人称呼。 */
    fun enqueue(context: Context, db: MemoryDb, text: String, speaker: String) {
        if (appContext == null) appContext = context.applicationContext
        if (this.db == null) this.db = db
        if (!Store.cloudMemoryEnabled(context)) return
        val clean = text.filter { !it.isWhitespace() }
        if (clean.length < 4) {
            WorkingMemory.stat(db, "noise_filtered")
        }
        // 记录已在 conversations 落盘；上云交给夜间压缩 facts（见 WorkingMemory.compact）
    }

    @Volatile private var appContext: Context? = null
    @Volatile private var db: MemoryDb? = null
}
