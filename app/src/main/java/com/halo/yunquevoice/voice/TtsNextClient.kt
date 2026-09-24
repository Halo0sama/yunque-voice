package com.halo.yunquevoice.voice

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Qwen-Audio-3.1-TTS-Next WebSocket 客户端（实测协议，v0.27.0）：
 *  1. run-task（text_prompt 走 parameters，**必须非空**——这是 Next 创作型模型的文本入口）
 *  2. task-started → 自动合成
 *  3. 二进制帧 = mp3 音频块（追加）
 *  4. task-finished → 完成
 *
 * 同步封装（CountDownLatch 等待），失败/超时返回 null。
 */
object TtsNextClient {

    private const val URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"
    private const val TAG = "TTS31"
    private val ok = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * 合成整段语音到 outFile（mp3）。
     * @param text 要合成的文本
     * @param voice 音色名（3.1-tts-next 为创作型模型，音色由模型按内容自动演绎）
     * @return 成功返回 outFile，失败返回 null
     */
    fun synthesize(apiKey: String, text: String, outFile: File, timeoutSec: Long = 60): File? {
        val taskId = UUID.randomUUID().toString().replace("-", "")
        val latch = CountDownLatch(1)
        var failed: String? = null
        var out: File? = null

        val request = Request.Builder()
            .url(URL)
            .header("Authorization", "bearer $apiKey")
            .header("X-DashScope-DataInspection", "enable")
            .build()

        val ws = ok.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                val runTask = JSONObject().put(
                    "header", JSONObject()
                        .put("action", "run-task")
                        .put("task_id", taskId)
                        .put("streaming", "out")
                ).put(
                    "payload", JSONObject()
                        .put("task_group", "audio")
                        .put("task", "tts")
                        .put("function", "SpeechSynthesizer")
                        .put("model", "qwen-audio-3.1-tts-next")
                        .put("voice", voice)
                        .put("parameters", JSONObject()
                            .put("text_type", "PlainText")
                            .put("format", "mp3")
                            .put("sample_rate", 48000)
                            .put("text_prompt", text))
                        .put("input", JSONObject())
                )
                webSocket.send(runTask.toString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                outFile.appendBytes(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val d = JSONObject(text)
                    val ev = d.optJSONObject("header")?.optString("event") ?: return
                    when (ev) {
                        "task-started" -> Log.i(TAG, "task-started")
                        "task-finished" -> {
                            Log.i(TAG, "task-finished")
                            latch.countDown()
                        }
                        "task-failed" -> {
                            val p = d.optJSONObject("payload")
                            failed = p?.optString("error_message") ?: "task-failed"
                            Log.w(TAG, "task-failed: $failed")
                            latch.countDown()
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                failed = t.message
                latch.countDown()
            }
        })

        latch.await(timeoutSec, TimeUnit.SECONDS)
        ws.cancel()

        return if (outFile.exists() && outFile.length() > 10_000) {
            // 临时文件 → 重命名为 mp3
            val mp3 = File(outFile.parent, outFile.nameWithoutExtension + ".mp3")
            outFile.renameTo(mp3)
            mp3
        } else {
            Log.w(TAG, "合成产物缺失或过小")
            null
        }.also { f ->
            failed?.let { Log.w(TAG, "TTS-Next 失败: $it") }
        }
    }
}
