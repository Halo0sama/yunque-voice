package com.halo.yunquevoice.service

import android.content.Context
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.voice.BailianMemory
import com.halo.yunquevoice.voice.Store
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * 云雀·私人助理本地 CLI/MCP 接口，对齐快递助手的 LocalApiServer。
 * 监听 127.0.0.1:8766。
 */
object YunqueApiServer {
    const val PORT = 8766

    @Volatile
    var instance: LocalServer? = null
        private set

    fun isRunning(): Boolean = instance != null

    fun start(context: Context) {
        synchronized(this) {
            if (instance != null) return
            try {
                LocalServer(context.applicationContext).also {
                    it.start(500, false)
                    instance = it
                }
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    fun stop() {
        synchronized(this) {
            instance?.stop()
            instance = null
        }
    }
}

class LocalServer(private val app: Context) : NanoHTTPD("127.0.0.1", YunqueApiServer.PORT) {

    private val db = MemoryDb(app)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.removeSuffix("/")
        return try {
            when {
                uri == "/api/health" && session.method == Method.GET ->
                    ok(JSONObject().put("ok", true).put("app", "yunque-voice").toString())
                uri == "/api/memories" && session.method == Method.GET ->
                    ok(runBlocking { cloudMemoriesJson(50, 1) }.toString())
                uri == "/api/memory" && session.method == Method.POST -> {
                    val body = JSONObject(bodyOf(session))
                    runBlocking { BailianMemory.addCustom(app, body.optString("content")) }
                    ok("{\"ok\":true}")
                }
                uri == "/api/memory/search" && session.method == Method.POST -> {
                    val body = JSONObject(bodyOf(session))
                    val nodes = runBlocking { BailianMemory.search(app, body.optString("query")) }
                    ok(nodesToJson(nodes).toString())
                }
                uri == "/api/memory/delete" && session.method == Method.POST -> {
                    val body = JSONObject(bodyOf(session))
                    runBlocking { BailianMemory.delete(app, body.optString("id")) }
                    ok("{\"ok\":true}")
                }
                uri == "/api/conversations" && session.method == Method.GET -> {
                    val q = session.parameters["q"]?.firstOrNull().orEmpty()
                    val list = if (q.isBlank()) db.recentConversations(100) else db.searchConversations(q)
                    ok(dbToJson(list).toString())
                }
                uri == "/api/speakers" && session.method == Method.GET -> {
                    val arr = JSONArray()
                    for (s in db.getSpeakers()) {
                        arr.put(JSONObject().put("id", s.id).put("name", s.name).put("samples", s.sampleCount))
                    }
                    ok(arr.toString())
                }
                uri == "/api/relationships" && session.method == Method.GET -> {
                    val arr = JSONArray()
                    for (r in db.listRelationships()) {
                        arr.put(JSONObject().put("from", r.sourceName).put("relation", r.relation).put("to", r.targetName).put("evidence", r.evidence))
                    }
                    ok(arr.toString())
                }
                uri == "/api/interruptions" && session.method == Method.GET -> {
                    val arr = JSONArray()
                    for (r in Store.interruptions(app)) {
                        arr.put(JSONObject().put("time", r.time).put("full", r.fullText).put("spoken", r.spokenText).put("missed", r.missedText))
                    }
                    ok(arr.toString())
                }
                uri == "/mcp" && session.method == Method.POST ->
                    ok(mcp(bodyOf(session)).toString())
                else -> error(Response.Status.NOT_FOUND, "not found")
            }
        } catch (e: Throwable) {
            error(Response.Status.INTERNAL_ERROR, e.message ?: "internal error")
        }
    }

    private fun mcp(body: String): JSONObject {
        val req = JSONObject(body)
        val id = req.opt("id") ?: JSONObject.NULL
        val method = req.optString("method")
        val params = req.optJSONObject("params") ?: JSONObject()
        val name = params.optString("name")
        val args = params.optJSONObject("arguments") ?: JSONObject()
        return if (method == "tools/call") {
            try {
                val text = when (name) {
                    "list_memories" -> runBlocking {
                        BailianMemory.list(app, 50, 1).first.joinToString("\n") { it.content }
                    }
                    "search_memory" -> {
                        val q = args.optString("query")
                        val nodes = runBlocking { BailianMemory.search(app, q) }
                        nodes.joinToString("\n") { "[${"%.2f".format(it.score)}] ${it.content}" }
                            .ifBlank { "没有相关记忆" }
                    }
                    "add_memory" -> {
                        val content = args.optString("content")
                        if (content.isNotBlank()) runBlocking { BailianMemory.addCustom(app, content) }
                        "ok"
                    }
                    "delete_memory" -> {
                        val nodeId = args.optString("id")
                        if (nodeId.isNotBlank()) runBlocking { BailianMemory.delete(app, nodeId) }
                        "ok"
                    }
                    "search_conversations" -> {
                        val q = args.optString("query")
                        db.searchConversations(q).joinToString("\n") { "[${it.speakerName ?: "未知"}] ${it.text}" }
                    }
                    "list_speakers" -> db.getSpeakers().joinToString("\n") { "${it.name}(${it.sampleCount})" }
                    "list_relationships" -> db.listRelationships().joinToString("\n") { "${it.sourceName} ${it.relation} ${it.targetName}" }
                    "add_memory" -> {
                        val content = args.optString("content")
                        if (content.isNotBlank()) db.addMemory(content)
                        "ok"
                    }
                    else -> "unknown tool: $name"
                }
                JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id)
                    .put("result", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))))
            } catch (e: Throwable) {
                JSONObject().put("jsonrpc", "2.0").put("id", id)
                    .put("error", JSONObject().put("code", -32603).put("message", e.message))
            }
        } else {
            JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("error", JSONObject().put("code", -32601).put("message", "method not found"))
        }
    }

    private fun nodesToJson(nodes: List<BailianMemory.MemoryNode>): JSONArray {
        val arr = JSONArray()
        for (n in nodes) {
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("content", n.content)
                    .put("ts", if (n.eventTsMs > 0) n.eventTsMs else n.createdAtMs)
                    .put("score", n.score)
            )
        }
        return arr
    }

    private suspend fun cloudMemoriesJson(pageSize: Int, pageNum: Int): JSONArray {
        val (nodes, _) = BailianMemory.list(app, pageSize, pageNum)
        return nodesToJson(nodes)
    }

    private fun dbToJson(list: List<com.halo.yunquevoice.memory.ConversationRecord>): JSONArray {
        val arr = JSONArray()
        for (c in list) {
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("ts", c.ts)
                    .put("speaker", c.speakerName ?: "未知")
                    .put("text", c.text)
                    .put("origin", c.origin)
                    .put("missed", c.missedText ?: "")
            )
        }
        return arr
    }

    private fun bodyOf(session: IHTTPSession): String {
        // 不用 parseBody：它按非 UTF-8 解码会把中文变 '?'，直接读原始字节按 UTF-8 解
        val len = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (len <= 0) return ""
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val r = session.inputStream.read(buf, read, len - read)
            if (r < 0) break
            read += r
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    private fun ok(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    private fun error(status: Response.Status, message: String): Response =
        newFixedLengthResponse(status, "application/json", JSONObject().put("error", message).toString())
}
