package com.halo.yunquevoice.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DiarizedSentence(
    val beginMs: Long,
    val endMs: Long,
    val text: String,
    val speakerId: Int
)

/**
 * DashScope 非实时文件转写（说话人分离）：
 * 1. 提交 qwen-audio-3.0-asr-flash-filetrans 任务（diarization_enabled=true）
 * 2. 轮询任务
 * 3. 下载转写 JSON，解析出带 speaker_id 的句子
 */
object DashScopeFiletrans {

    private const val MODEL = "qwen-audio-3.0-asr-flash-filetrans"
    private const val SUBMIT_PATH = "/api/v1/services/audio/asr/transcription"
    private const val TASK_PATH = "/api/v1/tasks/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        apiKey: String,
        ossUrl: String,
        workspaceId: String,
        diarization: Boolean = true
    ): List<DiarizedSentence> = withContext(Dispatchers.IO) {
        val taskId = submit(apiKey, ossUrl, workspaceId, diarization)
        VoiceMvpLog.i("FILETRANS", "任务已提交 taskId=$taskId")
        val transcriptionUrl = pollUntilDone(apiKey, workspaceId, taskId)
        VoiceMvpLog.i("FILETRANS", "任务完成，下载转写结果")
        val json = download(transcriptionUrl)
        parse(json)
    }

    private fun submit(apiKey: String, ossUrl: String, workspaceId: String, diarization: Boolean): String {
        val url = "https://$workspaceId.cn-beijing.maas.aliyuncs.com$SUBMIT_PATH"
        val body = JSONObject()
            .put("model", MODEL)
            .put("input", JSONObject().put("file_urls", JSONArray().put(ossUrl)))
            .put(
                "parameters",
                JSONObject().put("diarization_enabled", diarization).put("channel_id", JSONArray().put(0))
            )
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("X-DashScope-Async", "enable")
            .header("X-DashScope-OssResourceResolve", "enable")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("提交任务失败 HTTP ${resp.code}: ${text.take(200)}")
            return JSONObject(text).getJSONObject("output").getString("task_id")
        }
    }

    private suspend fun pollUntilDone(apiKey: String, workspaceId: String, taskId: String): String {
        for (i in 0 until 60) {
            val q = query(apiKey, workspaceId, taskId)
            val status = q.getJSONObject("output").optString("task_status")
            VoiceMvpLog.i("FILETRANS", "任务状态 $status (${i * 5}s)")
            if (status == "SUCCEEDED") {
                val results = q.getJSONObject("output").optJSONArray("results") ?: JSONArray()
                if (results.length() == 0) throw IllegalStateException("任务成功但没有结果")
                return results.getJSONObject(0).getString("transcription_url")
            }
            if (status == "FAILED" || status == "CANCELED") {
                throw IllegalStateException("任务失败: $status ${q.toString().take(300)}")
            }
            delay(5000)
        }
        throw IllegalStateException("任务超时")
    }

    private fun query(apiKey: String, workspaceId: String, taskId: String): JSONObject {
        val url = "https://$workspaceId.cn-beijing.maas.aliyuncs.com$TASK_PATH$taskId"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("查询任务失败 HTTP ${resp.code}: ${text.take(200)}")
            return JSONObject(text)
        }
    }

    private fun download(url: String): String {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载转写结果失败 HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun parse(json: String): List<DiarizedSentence> {
        val root = JSONObject(json)
        val sentences = findSentences(root)
        return sentences
    }

    private fun findSentences(obj: JSONObject): List<DiarizedSentence> {
        // 兼容多种返回结构：顶层 sentences，或 transcript -> sentences
        val arr = obj.optJSONArray("sentences")
            ?: obj.optJSONArray("transcripts")
            ?: obj.optJSONArray("result")
            ?: JSONArray()
        val out = mutableListOf<DiarizedSentence>()
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val sub = it.optJSONArray("sentences")
            if (sub != null && sub.length() > 0) {
                out.addAll(parseSentenceArray(sub))
            } else if (it.has("text")) {
                out.add(
                    DiarizedSentence(
                        beginMs = it.optLong("begin_time", 0),
                        endMs = it.optLong("end_time", 0),
                        text = it.optString("text"),
                        speakerId = it.optInt("speaker_id", -1)
                    )
                )
            }
        }
        return out
    }

    private fun parseSentenceArray(arr: JSONArray): List<DiarizedSentence> =
        (0 until arr.length()).map { i ->
            val it = arr.getJSONObject(i)
            DiarizedSentence(
                beginMs = it.optLong("begin_time", 0),
                endMs = it.optLong("end_time", 0),
                text = it.optString("text"),
                speakerId = it.optInt("speaker_id", -1)
            )
        }
}
