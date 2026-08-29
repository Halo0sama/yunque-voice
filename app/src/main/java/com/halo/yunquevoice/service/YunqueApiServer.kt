package com.halo.yunquevoice.service

import android.content.Context
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.voice.Store
import fi.iki.elonen.NanoHTTPD
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
                    ok(db.listMemories().joinToString("\n") { it.content })
                uri == "/api/memory" && session.method == Method.POST -> {
                    val body = JSONObject(bodyOf(session))
                    db.addMemory(body.optString("content"))
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
                    "list_memories" -> db.listMemories().joinToString("\n") { it.content }
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
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }

    private fun ok(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    private fun error(status: Response.Status, message: String): Response =
        newFixedLengthResponse(status, "application/json", JSONObject().put("error", message).toString())
}
