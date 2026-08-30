package com.halo.yunquevoice.memory

import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 轻量本地声纹识别：
 * 从 PCM 提取简单声学特征（能量/过零率/基频统计），
 * 和已有说话人档案做余弦相似度匹配；不匹配则自动新建“未知N”。
 *
 * 这是第一版占位方案，后续可替换成云端声纹 embedding 或 FunASR diarization。
 */
object SpeakerEngine {

    // 0.82 在嘈杂多人环境会把陌生人全并进来（身份坍缩），宁滥拆不误并：错拆可手动合并，错并不可逆
    private const val MATCH_COSINE = 0.93
    private const val FEATURE_DIM = 6

    fun recognize(db: MemoryDb, pcm: ByteArray): SpeakerProfile {
        val feature = extractFeature(pcm)
        val speakers = db.getSpeakers()
        var best: SpeakerProfile? = null
        var bestScore = -1.0
        for (s in speakers) {
            val existing = decodeFeature(s.feature)
            if (existing.size != feature.size) continue
            val score = cosine(feature, existing)
            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }
        if (best != null && bestScore >= MATCH_COSINE) {
            val a = existingFeature(best!!)
            // 增量均值更新：档案越成熟，单个新样本的权重越小，防止特征漂移成"通用嗓音"
            val n = best.sampleCount.coerceAtLeast(1)
            val w = 1.0 / (n + 1)
            val merged = DoubleArray(a.size) { a[it] * (1 - w) + feature[it] * w }
            val updated = best!!.copy(
                feature = encodeFeature(merged),
                updatedAt = System.currentTimeMillis(),
                sampleCount = n + 1
            )
            db.upsertSpeaker(updated)
            return updated
        }
        // 编号 = 现存所有"未知N"的最大 N + 1：改名/删除档案后也不会与在用编号撞车
        val next = (speakers.maxOfOrNull { s ->
            Regex("^未知(\\d+)$").find(s.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } ?: 0) + 1
        val created = SpeakerProfile(
            id = UUID.randomUUID().toString(),
            name = "未知$next",
            feature = encodeFeature(feature),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            sampleCount = 1
        )
        db.upsertSpeaker(created)
        return created
    }

    /** 融合两条声纹档案（云端批内分离判定同源时调用）：特征按样本量加权混合，记录/关系一并并档。 */
    fun mergeProfiles(db: MemoryDb, fromId: String, intoId: String) {
        if (fromId == intoId) return
        val into = db.getSpeaker(intoId) ?: return
        val from = db.getSpeaker(fromId) ?: return
        val a = decodeFeature(into.feature)
        val b = decodeFeature(from.feature)
        val total = into.sampleCount + from.sampleCount
        if (a.size == b.size && a.isNotEmpty() && (a.any { it != 0.0 } || b.any { it != 0.0 })) {
            val merged = if (into.sampleCount >= from.sampleCount) blend(a, b) else blend(b, a)
            db.upsertSpeaker(
                into.copy(
                    feature = encodeFeature(merged),
                    sampleCount = total,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else if (total != into.sampleCount) {
            db.upsertSpeaker(into.copy(sampleCount = total, updatedAt = System.currentTimeMillis()))
        }
        db.mergeSpeaker(fromId, intoId)
    }

    private fun extractFeature(pcm: ByteArray): DoubleArray {
        val frameBytes = 1600 // 50ms @16k = 800 samples * 2 bytes
        val frameSamples = frameBytes / 2
        val frames = mutableListOf<DoubleArray>()
        var offset = 0
        while (offset + frameBytes <= pcm.size) {
            val frame = DoubleArray(frameSamples)
            for (i in 0 until frameSamples) {
                val b0 = pcm[offset + i * 2].toInt() and 0xff
                val b1 = pcm[offset + i * 2 + 1].toInt() shl 8
                frame[i] = (b0 or b1).toShort().toDouble()
            }
            frames.add(frame)
            offset += frameBytes
        }
        if (frames.isEmpty()) return DoubleArray(FEATURE_DIM)

        val rmsList = mutableListOf<Double>()
        val zcrList = mutableListOf<Double>()
        val pitchList = mutableListOf<Double>()
        for (f in frames) {
            var sumSq = 0.0
            var zcr = 0
            for (i in f.indices) {
                sumSq += f[i] * f[i]
                if (i > 0 && ((f[i] >= 0 && f[i - 1] < 0) || (f[i] < 0 && f[i - 1] >= 0))) zcr++
            }
            val rms = sqrt(sumSq / f.size)
            rmsList.add(rms)
            zcrList.add(zcr.toDouble() / f.size)
            val pitch = estimatePitch(f)
            if (pitch != null) pitchList.add(pitch)
        }

        val feature = DoubleArray(FEATURE_DIM)
        feature[0] = mean(rmsList)
        feature[1] = std(rmsList, feature[0])
        feature[2] = mean(zcrList)
        feature[3] = std(zcrList, feature[2])
        feature[4] = if (pitchList.isNotEmpty()) mean(pitchList) else 0.0
        feature[5] = if (pitchList.isNotEmpty()) std(pitchList, feature[4]) else 0.0
        // 归一化到 0..1 量级，便于余弦相似度
        val max = feature.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        for (i in feature.indices) feature[i] /= max
        return feature
    }

    private fun estimatePitch(frame: DoubleArray): Double? {
        val n = frame.size
        var bestLag = -1
        var bestCorr = 0.3
        for (lag in 20..200) {
            if (lag >= n) break
            var corr = 0.0
            var energy = 0.0
            for (i in 0 until n - lag) {
                corr += frame[i] * frame[i + lag]
                energy += frame[i] * frame[i]
            }
            if (energy <= 0) continue
            val c = corr / energy
            if (c > bestCorr) {
                bestCorr = c
                bestLag = lag
            }
        }
        return if (bestLag > 0) 16000.0 / bestLag else null
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom <= 0) 0.0 else dot / denom
    }

    private fun existingFeature(s: SpeakerProfile): DoubleArray = decodeFeature(s.feature)

    private fun blend(a: DoubleArray, b: DoubleArray): DoubleArray =
        DoubleArray(a.size) { a[it] * 0.7 + b[it] * 0.3 }

    private fun mean(list: List<Double>): Double = if (list.isEmpty()) 0.0 else list.sum() / list.size

    private fun std(list: List<Double>, mean: Double): Double =
        if (list.size < 2) 0.0 else sqrt(list.sumOf { (it - mean).pow(2) } / list.size)
}
