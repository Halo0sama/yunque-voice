package com.halo.yunquevoice.voice

/**
 * 记录一次被打断的播报：
 * fullText 是完整回答，spokenText 是已经播出去的，missedText 是没听到的部分。
 */
data class InterruptionRecord(
    val time: Long,
    val fullText: String,
    val spokenText: String,
    val missedText: String
)
