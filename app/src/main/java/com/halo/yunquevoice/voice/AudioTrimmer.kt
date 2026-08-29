package com.halo.yunquevoice.voice

import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AudioTrimmer {

    private const val SAMPLE_RATE = 16000
    private const val CHANNELS = 1
    private const val TARGET_SECONDS = 15

    /** 智能选取能量最高的 15 秒语音段；失败回退为裁剪前 15 秒。 */
    suspend fun trimSmart(cacheDir: File, input: File): File = withContext(Dispatchers.IO) {
        val output = File(cacheDir, "voice_sample_smart_${System.currentTimeMillis()}.wav")
        try {
            val raw = File(cacheDir, "voice_raw_${System.currentTimeMillis()}.pcm")
            val convert = FFmpegKit.execute(
                "-y -i ${input.absolutePath} -map 0:a:0 -vn -ac $CHANNELS -ar $SAMPLE_RATE -f s16le ${raw.absolutePath}"
            )
            if (convert.returnCode.getValue() == 0 && raw.exists() && raw.length() > 0) {
                val data = raw.readBytes()
                val bytesPerSecond = SAMPLE_RATE * CHANNELS * 2
                val totalSeconds = data.size / bytesPerSecond
                if (totalSeconds > TARGET_SECONDS) {
                    val chunk = bytesPerSecond
                    val energies = DoubleArray(totalSeconds)
                    for (s in 0 until totalSeconds) {
                        val base = s * chunk
                        var sum = 0.0
                        for (i in 0 until chunk step 2) {
                            val idx = base + i
                            if (idx + 1 < data.size) {
                                val v = ((data[idx].toInt() and 0xff) or (data[idx + 1].toInt() shl 8)).toShort()
                                sum += v.toDouble() * v
                            }
                        }
                        energies[s] = sum / (chunk / 2)
                    }
                    var bestStart = 0
                    var bestScore = -1.0
                    for (start in 0..(totalSeconds - TARGET_SECONDS)) {
                        var score = 0.0
                        for (j in 0 until TARGET_SECONDS) score += energies[start + j]
                        if (score > bestScore) {
                            bestScore = score
                            bestStart = start
                        }
                    }
                    val extract = FFmpegKit.execute(
                        "-y -ss $bestStart -i ${input.absolutePath} -t $TARGET_SECONDS -map 0:a:0 -vn -ar $SAMPLE_RATE -ac $CHANNELS -f wav ${output.absolutePath}"
                    )
                    raw.delete()
                    if (extract.returnCode.getValue() == 0 && output.exists() && output.length() > 0) return@withContext output
                } else {
                    raw.delete()
                }
            }
        } catch (e: Throwable) {
            VoiceMvpLog.e("TRIM", "smart trim failed: ${e.message}", e)
        }
        // 回退：直接前 15 秒
        if (!output.exists() || output.length() == 0L) {
            val session = FFmpegKit.execute(
                "-y -i ${input.absolutePath} -map 0:a:0 -vn -t $TARGET_SECONDS -ar $SAMPLE_RATE -ac $CHANNELS -f wav ${output.absolutePath}"
            )
            if (session.returnCode.getValue() == 0 && output.exists() && output.length() > 0) return@withContext output
        }
        input
    }
}
