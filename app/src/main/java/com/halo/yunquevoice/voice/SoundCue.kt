package com.halo.yunquevoice.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * 开启/关闭聆听的提示音：用合成正弦波做一段柔和的上行/下行音阶。
 * 开启：C5 -> E5 -> G5（柔和上行）
 * 关闭：G5 -> E5 -> C5（柔和下行）
 */
object SoundCue {

    private const val SAMPLE_RATE = 22050

    private val START_MELODY = intArrayOf(392, 523, 659, 784)   // G3 C5 E5 G5
    private val STOP_MELODY = intArrayOf(784, 659, 523, 392)    // exact reverse

    private const val NOTE_MS = 200
    private const val NOTE_GAP_MS = 30
    private const val STOP_COOLDOWN_MS = 2000L

    @Volatile private var lastStopAt = 0L

    fun playStart() {
        playMelody(START_MELODY, "start")
    }

    fun playStop() {
        val now = System.currentTimeMillis()
        val gap = now - lastStopAt
        if (gap in 1 until STOP_COOLDOWN_MS) return
        lastStopAt = now
        playMelody(STOP_MELODY, "stop")
    }

    private fun playMelody(freqs: IntArray, name: String) {
        VoiceMvpLog.i("SOUND", "播放$name 提示音")
        val t = Thread {
            try {
                val pcm = generateMelody(freqs)
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(max(pcm.size, minBuf * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                val totalMs = freqs.size * (NOTE_MS + NOTE_GAP_MS).toLong()
                Thread.sleep(totalMs + 50)
                runCatching { track.stop() }
                runCatching { track.release() }
            } catch (e: Throwable) {
                VoiceMvpLog.w("SOUND", "提示音失败: ${e.message}")
            }
        }
        t.name = "yunque-sound"
        t.start()
    }

    private fun generateMelody(freqs: IntArray): ByteArray {
        val noteSamples = SAMPLE_RATE * NOTE_MS / 1000
        val gapSamples = SAMPLE_RATE * NOTE_GAP_MS / 1000
        val totalSamples = freqs.size * (noteSamples + gapSamples)
        val pcm = ByteArray(totalSamples * 2)
        var index = 0
        for (f in freqs) {
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                // 包络：短促起音 + 指数衰减，听感更柔和
                val attack = minOf(1.0, i / (SAMPLE_RATE * 0.01))
                val decay = Math.exp(-3.0 * i / noteSamples)
                val env = attack * decay
                val fundamental = sin(2 * PI * f * t)
                val harmonic = 0.25 * sin(2 * PI * f * 2 * t)
                val sample = (fundamental + harmonic) * env * 0.32
                val s = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                pcm[index++] = (s.toInt() and 0xff).toByte()
                pcm[index++] = ((s.toInt() shr 8) and 0xff).toByte()
            }
            // 音符间留一点静音
            for (i in 0 until gapSamples) {
                pcm[index++] = 0
                pcm[index++] = 0
            }
        }
        return pcm
    }
}
