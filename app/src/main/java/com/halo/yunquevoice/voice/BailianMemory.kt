package com.halo.yunquevoice.voice

import android.content.Context
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
 * 阿里云百炼「记忆库」客户端 —— 云端是记忆的唯一真相源：
 * - append：旁听一句话就写一条观察记忆（verbatim，带说话人与时间）
 * - addFacts：把人工整理过的事实批量写入（迁移/手动添加用）
 * - search：语义检索相关记忆（Lite 档，便宜且实测召回更快）
 * - list / delete：记忆板块 UI 直连云端管理
 *
 * 请求必须带业务空间头（X-DashScope-WorkspaceId）与 memory_library_id，
 * 缺任何一个都会得到 ServiceNotOpened / 查不到数据（2026-08 实测结论）。
 */
object BailianMemory {

    private const val BASE = "https://dashscope.aliyuncs.com/api/v2/apps/memory"
    const val SEARCH_TOP_K = 6

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class MemoryNode(
        val id: String,
        val content: String,
        val createdAtMs: Long,
        val eventTsMs: Long,
        val score: Double = -1.0,
        val metaData: JSONObject = JSONObject()
    )

    private fun checkConfig(context: Context): Pair<String, String> {
        val key = Store.dashScopeKey(context)
        val lib = Store.memoryLibraryId(context)
        check(key.isNotBlank()) { "缺少阿里云百炼 API Key" }
        check(lib.isNotBlank()) { "缺少记忆库 ID（控制台-记忆库 页面可查）" }
        return key to lib
    }

    private fun authorized(context: Context, url: String, method: String = "POST", body: JSONObject? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${Store.dashScopeKey(context)}")
        val workspace = Store.workspaceId(context)
        if (workspace.isNotBlank()) builder.header("X-DashScope-WorkspaceId", workspace)
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete(body?.toString()?.toRequestBody("application/json".toMediaType()))
            else -> builder.post((body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
        }
        return builder.build()
    }

    private fun call(context: Context, request: Request, label: String): JSONObject =
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                VoiceMvpLog.e("BAILIAN", "$label 失败 HTTP ${resp.code}: ${text.take(300)}")
                throw IllegalStateException("$label HTTP ${resp.code}: ${text.take(200)}")
            }
            JSONObject(text)
        }

    /**
     * 旁听写入：一句话一条，verbatim 存储，说话人与时间放在 meta_data。
     * 返回云端提炼/落库出的记忆节点数。
     */
    suspend fun append(context: Context, text: String, speakerName: String, tsMs: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) {
            val (_, lib) = checkConfig(context)
            val body = JSONObject()
                .put("user_id", Store.memoryUserId(context))
                .put("memory_library_id", lib)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user")
                            .put("content", "（${speakerName}说）$text")
                    )
                )
                .put(
                    "meta_data",
                    JSONObject()
                        .put("speaker", speakerName)
                        .put("captured_at", tsMs)
                )
        val resp = call(context, authorized(context, "$BASE/add", body = body), "AddMemory")
        val nodes = resp.optJSONArray("memory_nodes") ?: JSONArray()
            VoiceMvpLog.i("BAILIAN", "append 成功 ${nodes.length()} 条: ${text.take(60)}")
            nodes.length()
        }

    /** 批量写入既成事实（本地记忆迁移、手动整理）。 */
    suspend fun addFacts(context: Context, facts: List<String>): Int = withContext(Dispatchers.IO) {
        if (facts.isEmpty()) return@withContext 0
        val (_, lib) = checkConfig(context)
        val body = JSONObject()
            .put("user_id", Store.memoryUserId(context))
            .put("memory_library_id", lib)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", facts.joinToString("\n")))
            )
        val resp = call(context, authorized(context, "$BASE/add", body = body), "AddMemory(facts)")
        resp.optJSONArray("memory_nodes")?.length() ?: 0
    }

    /** 手动添加：verbatim 存储，不走云端提炼改写。返回新节点 id 或空。 */
    suspend fun addCustom(context: Context, content: String): String = withContext(Dispatchers.IO) {
        val (_, lib) = checkConfig(context)
        val body = JSONObject()
            .put("user_id", Store.memoryUserId(context))
            .put("memory_library_id", lib)
            .put("custom_content", content)
        val resp = call(context, authorized(context, "$BASE/add", body = body), "AddMemory(custom)")
        resp.optJSONArray("memory_nodes")?.optJSONObject(0)?.optString("memory_node_id").orEmpty()
    }

    /* ───────────── outbox：断网缓冲，恢复后重发 ───────────── */

    private const val OUTBOX_MAX = 500
    private val outboxLock = Any()

    private fun outboxFile(context: Context) = java.io.File(context.filesDir, "memory_outbox.jsonl")

    fun outboxSize(context: Context): Int =
        outboxFile(context).takeIf { it.exists() }?.readLines()?.count { it.isNotBlank() } ?: 0

    /** 清空 outbox（记忆内容整体重置时用）。 */
    fun clearOutbox(context: Context) {
        synchronized(outboxLock) { outboxFile(context).delete() }
    }

    /** 会话簇批量写入：一次调用带上整簇原话（计费按次，与条数无关，上限实测 ≥50 条）。 */
    suspend fun appendBatchReliably(context: Context, items: List<UploadItem>) {
        if (items.isEmpty()) return
        try {
            val (_, lib) = checkConfig(context)
            val msgs = JSONArray()
            for (it in items) {
                msgs.put(
                    JSONObject().put("role", "user")
                        .put("content", "（${it.speaker}说）${it.text}")
                )
            }
            val body = JSONObject()
                .put("user_id", Store.memoryUserId(context))
                .put("memory_library_id", lib)
                .put("messages", msgs)
                .put("meta_data", JSONObject().put("count", items.size))
            call(context, authorized(context, "$BASE/add", body = body), "AddMemory(batch)")
            VoiceMvpLog.i("BAILIAN", "簇上传成功 ${items.size} 句")
            flushOutbox(context)
        } catch (e: Throwable) {
            synchronized(outboxLock) {
                val f = outboxFile(context)
                val lines = if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
                if (lines.size < OUTBOX_MAX) {
                    val arr = JSONArray()
                    for (it in items) {
                        arr.put(JSONObject().put("text", it.text).put("speaker", it.speaker).put("ts", it.ts))
                    }
                    val line = JSONObject().put("mode", "batch").put("items", arr)
                    f.writeText((lines + line.toString()).joinToString("\n"))
                }
            }
            throw e
        }
    }

    data class UploadItem(val text: String, val speaker: String, val ts: Long)

    /** 写入失败时入 outbox；成功时顺手冲刷历史欠账。verbatim=true 用原样存储（显式"记住"指令），否则交给云端提炼。失败原样抛出由调用方记日志。 */
    suspend fun appendReliably(
        context: Context,
        text: String,
        speakerName: String,
        tsMs: Long = System.currentTimeMillis(),
        verbatim: Boolean = false
    ) {
        try {
            if (verbatim) addCustom(context, text) else append(context, text, speakerName, tsMs)
            flushOutbox(context)
        } catch (e: Throwable) {
            synchronized(outboxLock) {
                val f = outboxFile(context)
                val lines = if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
                if (lines.size < OUTBOX_MAX) {
                    val line = JSONObject()
                        .put("mode", if (verbatim) "verbatim" else "say")
                        .put("text", text).put("speaker", speakerName).put("ts", tsMs)
                    f.writeText((lines + line.toString()).joinToString("\n"))
                }
            }
            throw e
        }
    }

    /** 重发 outbox 里的欠账；仍失败的留下，其余清空。 */
    suspend fun flushOutbox(context: Context) = withContext(Dispatchers.IO) {
        val f = outboxFile(context)
        if (!f.exists()) return@withContext
        val lines = synchronized(outboxLock) { f.readLines().filter { it.isNotBlank() } }
        if (lines.isEmpty()) return@withContext
        VoiceMvpLog.i("BAILIAN", "冲刷 outbox ${lines.size} 条")
        val failed = mutableListOf<String>()
        for (line in lines) {
            try {
                val o = JSONObject(line)
                when (o.optString("mode")) {
                    "verbatim" -> addCustom(context, o.optString("text"))
                    "batch" -> {
                        val arr = o.optJSONArray("items") ?: JSONArray()
                        val items = (0 until arr.length()).map { i ->
                            val it = arr.getJSONObject(i)
                            UploadItem(it.optString("text"), it.optString("speaker", "未知"), it.optLong("ts"))
                        }
                        appendBatchReliably(context, items)
                    }
                    else -> append(context, o.optString("text"), o.optString("speaker", "未知"), o.optLong("ts"))
                }
            } catch (e: Throwable) {
                failed.add(line)
            }
        }
        synchronized(outboxLock) {
            if (failed.isEmpty()) f.delete() else f.writeText(failed.joinToString("\n"))
        }
    }

    /** 语义检索：返回最相关的记忆节点（Lite 档）。失败抛异常，由调用方决定是否降级。 */
    suspend fun search(context: Context, query: String, topK: Int = SEARCH_TOP_K): List<MemoryNode> =
        withContext(Dispatchers.IO) {
            val (_, lib) = checkConfig(context)
            val body = JSONObject()
                .put("user_id", Store.memoryUserId(context))
                .put("memory_library_id", lib)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", query)))
                .put("top_k", topK)
                .put("plan_version", "lite")
            val resp = call(context, authorized(context, "$BASE/memory_nodes/search", body = body), "SearchMemory")
            parseNodes(resp.optJSONArray("memory_nodes")) { it.optDouble("score", -1.0) }
        }

    /** 分页列出全部记忆（记忆板块 UI 用）。 */
    suspend fun list(context: Context, pageSize: Int = 20, pageNum: Int = 1): Pair<List<MemoryNode>, Int> =
        withContext(Dispatchers.IO) {
            val (_, lib) = checkConfig(context)
            val url = "$BASE/memory_nodes?user_id=${Store.memoryUserId(context)}" +
                "&memory_library_id=$lib&page_size=$pageSize&page_num=$pageNum"
            val resp = call(context, authorized(context, url, method = "GET"), "ListMemory")
            parseNodes(resp.optJSONArray("memory_nodes")) { -1.0 } to resp.optInt("total", 0)
        }

    /** 删除单条云端记忆，不可逆。 */
    suspend fun delete(context: Context, nodeId: String) = withContext(Dispatchers.IO) {
        checkConfig(context)
        call(context, authorized(context, "$BASE/memory_nodes/$nodeId", method = "DELETE", body = JSONObject()), "DeleteMemory")
        VoiceMvpLog.i("BAILIAN", "已删除记忆节点 $nodeId")
    }

    private fun parseNodes(arr: JSONArray?, scoreOf: (JSONObject) -> Double): List<MemoryNode> {
        if (arr == null) return emptyList()
        val out = mutableListOf<MemoryNode>()
        for (i in 0 until arr.length()) {
            val n = arr.getJSONObject(i)
            val id = n.optString("memory_node_id")
            val content = n.optString("content")
            if (id.isBlank() || content.isBlank()) continue
            out.add(
                MemoryNode(
                    id = id,
                    content = content,
                    createdAtMs = n.optLong("created_at") * 1000,
                    eventTsMs = n.optLong("timestamp", n.optLong("created_at")) * 1000,
                    score = scoreOf(n),
                    metaData = n.optJSONObject("meta_data") ?: JSONObject()
                )
            )
        }
        return out
    }
}
