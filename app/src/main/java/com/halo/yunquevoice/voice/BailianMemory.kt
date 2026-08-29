package com.halo.yunquevoice.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 阿里云百炼「记忆库」客户端：
 * - AddMemory：把对话框写入云端记忆库
 * - SearchMemory：语义检索相关记忆，替换本地关键词挑选
 */
object BailianMemory {

    private const val BASE = "https://dashscope.aliyuncs.com/api/v2/apps/memory"
    const val USER_ID = "yunque-user-001"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun add(apiKey: String, messages: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        val msgs = JSONArray()
        for ((role, content) in messages) {
            msgs.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject().put("user_id", USER_ID).put("messages", msgs)
        val req = Request.Builder()
            .url("$BASE/add")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                VoiceMvpLog.e("BAILIAN", "AddMemory 失败 HTTP ${resp.code}: ${text.take(200)}")
                throw IllegalStateException("AddMemory 失败 HTTP ${resp.code}: ${text.take(200)}")
            }
            VoiceMvpLog.i("BAILIAN", "AddMemory 成功")
        }
    }

    suspend fun search(apiKey: String, query: String): List<String> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("user_id", USER_ID)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", query))
            )
            .put("max_results", 10)
        val req = Request.Builder()
            .url("$BASE/search")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                VoiceMvpLog.e("BAILIAN", "SearchMemory 失败 HTTP ${resp.code}: ${text.take(200)}")
                throw IllegalStateException("SearchMemory 失败 HTTP ${resp.code}: ${text.take(200)}")
            }
            val root = JSONObject(text)
            val nodes = root.optJSONArray("memory_nodes")
                ?: root.optJSONObject("output")?.optJSONArray("memory_nodes")
                ?: root.optJSONArray("results")
                ?: JSONArray()
            val out = mutableListOf<String>()
            for (i in 0 until nodes.length()) {
                val node = nodes.getJSONObject(i)
                val content = node.optString("content").takeIf { it.isNotBlank() }
                    ?: node.optJSONObject("memory")?.optString("content")
                if (content != null) out.add(content)
            }
            VoiceMvpLog.i("BAILIAN", "SearchMemory 返回 ${out.size} 条")
            out
        }
    }
}
