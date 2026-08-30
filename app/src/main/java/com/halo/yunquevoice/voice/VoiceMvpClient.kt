package com.halo.yunquevoice.voice

import android.content.Context
import com.halo.yunquevoice.memory.MemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

object VoiceMvpClient {

    private const val ASR_MODEL = "qwen3-asr-flash"
    private const val TTS_MODEL = "qwen-audio-3.0-tts-flash"
    private const val TTS_VOICE = "longanxiaoxin"

    private const val ASR_URL =
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private const val TTS_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"

    /** 对话模型供应商路由：均为 OpenAI 兼容协议（含工具调用）。 */
    private fun llmEndpoint(provider: String): String = when (provider) {
        Store.LLM_ZHIPU -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        Store.LLM_QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        else -> "https://api.deepseek.com/chat/completions"
    }

    private fun llmModel(provider: String): String = when (provider) {
        Store.LLM_ZHIPU -> "glm-5.3-flash"
        Store.LLM_QWEN -> "qwen3.8-flash"
        else -> "deepseek-v4-flash"
    }

    /**
     * 各家思考模式（实测）：
     * - deepseek-v4-flash：thinking.type=disabled 可关
     * - qwen3.8-flash：enable_thinking=false 可关
     * - glm-5.3-flash：常思考不可关，depth=low 最浅
     * 实时对话路径统一走"关闭或最浅"，压缩/提炼等夜间任务保持各家默认。
     */
    private fun applyRealtimeThinking(body: JSONObject, provider: String) {
        when (provider) {
            Store.LLM_DEEPSEEK -> body.put("thinking", JSONObject().put("type", "disabled"))
            Store.LLM_QWEN -> body.put("enable_thinking", false)
            Store.LLM_ZHIPU -> body.put("thinking", JSONObject().put("type", "enabled").put("depth", "low"))
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun maskKey(key: String): String =
        if (key.length <= 8) "***" else key.take(4) + "..." + key.takeLast(4)

    /** 调本地 ExpressAssistant MCP/HTTP 服务 */
    private fun localPost(path: String, body: String): String {
        val req = Request.Builder()
            .url("http://127.0.0.1:8765$path")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("本地接口 HTTP ${resp.code}: ${text.take(200)}")
            return text
        }
    }

    private fun mcpCall(name: String, args: JSONObject): String {
        val reqBody = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", System.currentTimeMillis())
            .put("method", "tools/call")
            .put("params", JSONObject().put("name", name).put("arguments", args))
        val resp = JSONObject(localPost("/mcp", reqBody.toString()))
        val result = resp.optJSONObject("result")
            ?: throw IllegalStateException("本地 MCP 无 result: ${resp.toString().take(200)}")
        val content = result.optJSONArray("content")?.getJSONObject(0)?.optString("text")
            ?: throw IllegalStateException("本地 MCP 返回无文本")
        return content
    }

    private fun postJson(url: String, key: String, body: JSONObject, label: String): String {
        val t0 = System.currentTimeMillis()
        VoiceMvpLog.i("HTTP", "$label -> POST $url key=${maskKey(key)}")
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - t0
                VoiceMvpLog.i("HTTP", "$label <- ${resp.code} ${ms}ms bodyLen=${text.length}")
                if (!resp.isSuccessful) {
                    VoiceMvpLog.e("HTTP", "$label failed body=${text.take(500)}")
                    throw IllegalStateException("HTTP ${resp.code}: ${text.take(300)}")
                }
                return text
            }
        } catch (e: Throwable) {
            VoiceMvpLog.e("HTTP", "$label exception: ${e.message}", e)
            throw e
        }
    }

    /* ───────────── 云雀工具 ───────────── */

    private fun toolDefinitions(): JSONArray {
        val noParams = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject())
            .put("required", JSONArray())
        return JSONArray()
            .put(
                JSONObject().put(
                    "type", "function"
                ).put(
                    "function", JSONObject()
                        .put("name", "get_current_time")
                        .put("description", "获取当前时间，例如 19点45分")
                        .put("parameters", noParams)
                )
            )
            .put(
                JSONObject().put(
                    "type", "function"
                ).put(
                    "function", JSONObject()
                        .put("name", "get_current_date")
                        .put("description", "获取今天的日期和星期，例如 2026年8月23日 星期日")
                        .put("parameters", noParams)
                )
            )
            .put(
                JSONObject().put(
                    "type", "function"
                ).put(
                    "function", JSONObject()
                        .put("name", "get_battery")
                        .put("description", "获取手机当前电量百分比")
                        .put("parameters", noParams)
                )
            )
            .put(
                JSONObject().put(
                    "type", "function"
                ).put(
                    "function", JSONObject()
                        .put("name", "express_summarize")
                        .put("description", "联动云雀快递助手，汇总当前所有快递情况；可带自然语言问题")
                        .put(
                            "parameters", JSONObject()
                                .put("type", "object")
                                .put(
                                    "properties", JSONObject()
                                        .put("question", JSONObject().put("type", "string").put("description", "可选问题"))
                                )
                                .put("required", JSONArray())
                        )
                )
            )
            .put(
                JSONObject().put(
                    "type", "function"
                ).put(
                    "function", JSONObject()
                        .put("name", "express_list")
                        .put("description", "联动云雀快递助手，返回全部快递的精简列表")
                        .put("parameters", noParams)
                )
            )
            .put(
                JSONObject().put(
                    "type", "function"
                ).put(
                    "function", JSONObject()
                        .put("name", "express_detail")
                        .put("description", "联动云雀快递助手，查询某个快递的完整物流轨迹")
                        .put(
                            "parameters", JSONObject()
                                .put("type", "object")
                                .put(
                                    "properties", JSONObject()
                                        .put("mailNo", JSONObject().put("type", "string").put("description", "快递单号"))
                                )
                                .put("required", JSONArray().put("mailNo"))
                        )
                )
            )
    }

    private suspend fun executeTool(name: String, args: JSONObject, context: Context?): String {
        VoiceMvpLog.i("TOOL", "调用工具: $name args=${args.toString()}")
        return when (name) {
            "get_current_time" -> LocalInfo.answerBasic(context, "现在几点？") ?: "未知时间"
            "get_current_date" -> LocalInfo.answerBasic(context, "今天日期？") ?: "未知日期"
            "get_battery" -> LocalInfo.answerBasic(context, "电量？") ?: "未知电量"
            "express_summarize" -> mcpCall("summarize", args)
            "express_list" -> mcpCall("list_packages", args)
            "express_detail" -> mcpCall("package_detail", args)
            else -> if (context != null && OperitClient.isConfigured(context)) {
                OperitClient.invoke(context, name, args)
            } else {
                JSONObject().put("error", "未知工具: $name").toString()
            }
        }
    }

    /** 本地意图探测：识别到时间/日期/电量类问题，强制调用对应工具。 */
    private fun detectTool(question: String): String? {
        val q = question.lowercase()
        return when {
            q.contains("几点") || q.contains("时间") -> "get_current_time"
            q.contains("几号") || q.contains("日期") || q.contains("星期") || q.contains("周几") -> "get_current_date"
            q.contains("电量") || q.contains("电池") -> "get_battery"
            else -> null
        }
    }

    private suspend fun completeWithTools(
        deepSeekKey: String,
        system: String,
        user: String,
        context: Context?,
        label: String,
        forcedTool: String? = null,
        prefixTurns: JSONArray = JSONArray()
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
        for (i in 0 until prefixTurns.length()) messages.put(prefixTurns.getJSONObject(i))
        messages.put(JSONObject().put("role", "user").put("content", user))
        var answer = ""
        val toolDefs = toolDefinitions()
        if (context != null && OperitClient.isConfigured(context)) {
            OperitClient.listTools(context)?.let { ext ->
                for (i in 0 until ext.length()) toolDefs.put(ext.getJSONObject(i))
            }
        }
        val provider = if (context != null) Store.llmProvider(context) else Store.LLM_DEEPSEEK
        for (turn in 0 until 4) {
            val body = JSONObject()
                .put("model", llmModel(provider))
                .put("stream", false)
                .put("temperature", if (label == "DECIDE") 0.2 else 0.3)
                .put("messages", messages)
                .put("tools", toolDefs)
            // 实时对话路径：思考关闭或最浅（各家能力见 applyRealtimeThinking）
            applyRealtimeThinking(body, provider)
            val resp = JSONObject(postJson(llmEndpoint(provider), deepSeekKey, body, label))
            val message = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls != null && toolCalls.length() > 0) {
                VoiceMvpLog.i(label, "收到工具调用 ${toolCalls.length()} 个")
                val assistantMsg = JSONObject()
                    .put("role", "assistant")
                    .put("content", message.optString("content"))
                    .put("tool_calls", toolCalls)
                messages.put(assistantMsg)
                for (i in 0 until toolCalls.length()) {
                    val call = toolCalls.getJSONObject(i)
                    val fn = call.getJSONObject("function")
                    val name = fn.optString("name")
                    val argObj = try {
                        JSONObject(fn.optString("arguments"))
                    } catch (e: Throwable) {
                        JSONObject()
                    }
                    val result = executeTool(name, argObj, context)
                    messages.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", call.optString("id"))
                            .put("content", result)
                    )
                }
            } else {
                answer = message.optString("content").trim()
                break
            }
        }
        answer
    }

    /* ───────────── 对外 API ───────────── */

    suspend fun transcribe(dashScopeKey: String, wav: File): String = withContext(Dispatchers.IO) {
        VoiceMvpLog.i("ASR", "开始识别: ${wav.absolutePath} size=${wav.length()}")
        val t0 = System.currentTimeMillis()
        val base64 = Base64.getEncoder().encodeToString(wav.readBytes())
        val dataUrl = "data:audio/wav;base64,$base64"
        val body = JSONObject()
            .put("model", ASR_MODEL)
            .put("stream", false)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "input_audio")
                                    .put("input_audio", JSONObject().put("data", dataUrl))
                            )
                        )
                )
            )
            .put("asr_options", JSONObject().put("enable_itn", true))
        val resp = JSONObject(postJson(ASR_URL, dashScopeKey, body, "ASR"))
        val content = resp.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
            .trim()
        VoiceMvpLog.i("ASR", "识别完成 ${System.currentTimeMillis() - t0}ms text=${content.take(200)}")
        require(content.isNotEmpty()) { "ASR 返回空文本" }
        content
    }

    private fun buildRoleContext(context: Context?, text: String): String {
        if (context == null) return ""
        val db = MemoryDb(context)
        val card = db.getActiveRoleCard() ?: return ""
        val sb = StringBuilder()
        sb.append("\n\n【当前角色卡：${card.name}】\n")
        sb.append("用户称呼：主人（不要直呼其名）。\n")
        if (card.description.isNotBlank()) sb.append("简介：${card.description}\n")
        if (card.personality.isNotBlank()) sb.append("性格：${card.personality}\n")
        if (card.scenario.isNotBlank()) sb.append("场景：${card.scenario}\n")
        if (card.systemPrompt.isNotBlank()) sb.append("系统设定：${card.systemPrompt}\n")
        if (card.postHistoryInstructions.isNotBlank()) sb.append("对话后指令：${card.postHistoryInstructions}\n")
        val hits = db.matchWorldEntries(text, card.id)
        if (hits.isNotEmpty()) {
            sb.append("【世界书命中】\n")
            hits.forEach { sb.append("- ${it.content}\n") }
        }
        return sb.toString()
    }

    /** 不带工具的原始补全：供压缩/提炼等结构化任务使用。 */
    suspend fun completeRaw(deepSeekKey: String, system: String, user: String, label: String, provider: String = Store.LLM_DEEPSEEK): String =
        withContext(Dispatchers.IO) {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user))
            val body = JSONObject()
                .put("model", llmModel(provider))
                .put("stream", false)
                .put("temperature", 0.2)
                .put("max_tokens", 2000)
                .put("messages", messages)
            val resp = JSONObject(postJson(llmEndpoint(provider), deepSeekKey, body, label))
            val content = resp.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .trim()
            require(content.isNotEmpty()) { "$label 返回空内容" }
            content
        }

    suspend fun chat(
        deepSeekKey: String,
        question: String,
        context: Context? = null,
        recentTurns: List<Pair<String, String>> = emptyList(),
        workingSummary: String = ""
    ): String {
        VoiceMvpLog.i("LLM", "开始思考: question=${question.take(200)}")
        val memoryText = if (context != null && Store.cloudMemoryEnabled(context)) {
            runCatching { BailianMemory.search(context, question) }
                .getOrElse {
                    VoiceMvpLog.w("BAILIAN", "对话记忆检索失败，本次无记忆上下文: ${it.message}")
                    emptyList()
                }
                .joinToString("\n") { "记忆中：${it.content}" }
        } else ""
        val summaryText = if (workingSummary.isBlank()) "" else "\n【近期对话摘要】\n$workingSummary\n"
        val profileText = if (context != null) {
            val p = MemoryDb(context).loadProfileDoc().content
            if (p.isNotBlank()) "\n【关于主人的画像】\n$p\n" else ""
        } else ""
        val system = "你是云雀。回答要简短、口语化，适合直接读出来；不要用 Markdown、列表或代码块，尽量控制在两三句话。" +
            "当用户询问时间、日期或电量时，请调用对应工具获取真实信息后再回答。" +
            profileText +
            summaryText +
            (if (memoryText.isNotBlank()) "\n\n$memoryText\n" else "") +
            buildRoleContext(context, question)
        val turns = JSONArray()
        // 近 30 轮作为对话格式的短期上下文；更早的靠 workingSummary 与记忆检索覆盖
        for ((role, content) in recentTurns.takeLast(30)) {
            turns.put(JSONObject().put("role", role).put("content", content))
        }
        val forcedTool = detectTool(question)
        val answer = completeWithTools(deepSeekKey, system, question, context, "LLM", forcedTool, turns)
        require(answer.isNotEmpty()) { "DeepSeek 返回空回答" }
        VoiceMvpLog.i("LLM", "最终回答: ${answer.take(200)}")
        return answer
    }

    /** 从对话中提取人物真实姓名与人物关系，返回 {names:[], relations:[]}。 */
    suspend fun extractRelations(deepSeekKey: String, batch: String): JSONObject {
        val system = "你是云雀的关系梳理器。从带说话人标签的对话中：1) 如果证据充分，推断说话人的真实姓名；" +
            "2) 推断人物之间关系。只输出 JSON，格式：{\"names\":[{\"speaker\":\"未知1\",\"name\":\"小明\",\"evidence\":\"理由\"}]," +
            "\"relations\":[{\"from\":\"小明\",\"to\":\"李雷\",\"relation\":\"同事\",\"evidence\":\"理由\"}]}。没有把握就不输出该条。"
        val content = completeWithTools(deepSeekKey, system, batch, null, "RELATION")
        return runCatching { JSONObject(content.trim()) }
            .getOrElse { JSONObject().put("names", JSONArray()).put("relations", JSONArray()) }
    }

    /** 从一段对话批里提取值得长期记住的事实，返回记忆条目列表。 */
    suspend fun extractMemories(deepSeekKey: String, batch: String): List<String> {
        val system = "你是云雀的记忆提取器。从对话文本中提取值得长期记住的事实（人物关系、偏好、计划、重要事件）。" +
            "只输出 JSON 数组，每项是一个字符串，不要输出解释。"
        val content = completeWithTools(deepSeekKey, system, batch, null, "MEMORY")
        return runCatching {
            val arr = JSONArray(content.trim())
            (0 until arr.length()).map { arr.getString(it).trim() }
        }.getOrElse {
            VoiceMvpLog.w("MEMORY", "记忆提取返回非 JSON，按单条保留: ${content.take(200)}")
            if (content.isBlank()) emptyList() else listOf(content.trim())
        }
    }

    /**
     * 旁听决策：返回 null 表示保持沉默；返回非空字符串表示要开口说的话。
     * history 为预格式化的工作记忆原话行（摘要之外的部分），summary 为滚动摘要。
     */
    suspend fun decide(
        deepSeekKey: String,
        mode: Int,
        transcript: String,
        history: List<String>,
        context: Context? = null,
        memories: List<String> = emptyList(),
        myInfo: String = "",
        summary: String = ""
    ): String? {
        val isBasic = detectTool(transcript) != null
        val hasWake = transcript.contains("云雀")
        val basicEnabled = context == null || Store.basicReplyEnabled(context)
        // 全局规则：基础问题开关关掉后，只有提及“云雀”时才回答这类问题；
        // 开关打开则“现在几点了”这种问题也直接回答。
        if (isBasic && !basicEnabled && !hasWake) {
            VoiceMvpLog.i("DECIDE", "基础问题回应关闭且未提及云雀，保持沉默: ${transcript.take(80)}")
            return null
        }
        val system = when (mode) {
            Store.LISTEN_MODE_PROACTIVE ->
                "你是云雀，正在旁听用户与别人的对话。如果你觉得能提供明确有用的建议，可以主动开口；" +
                    "否则保持沉默。用户问时间/日期/电量时属于直接提问，必须调用对应工具获取真实数据后 SPEAK 回答。" +
                    "输出格式：要么 SILENT，要么 SPEAK:后面跟你想说的话。"
            Store.LISTEN_MODE_WAKE ->
                "你是云雀。只有用户明确叫了“云雀”或直接向你提问时才回应；否则保持沉默。" +
                    "用户问时间/日期/电量时属于直接提问，必须调用对应工具获取真实数据后 SPEAK 回答。" +
                    "输出格式：要么 SILENT，要么 SPEAK:后面跟你想说的话。"
            else ->
                "你是云雀，正在旁听用户与别人的对话。只有当用户直接问你、或你能提供高价值的明确建议时才开口；" +
                    "不要为了存在感而插话。用户问时间/日期/电量时属于直接提问，必须调用对应工具获取真实数据后 SPEAK 回答。" +
                    "输出格式：要么 SILENT，要么 SPEAK:后面跟你想说的话。"
        }
        val roleContext = buildRoleContext(context, transcript)
        val contextText = history.joinToString("\n")
        val summaryText = if (summary.isBlank()) "" else "【近期对话摘要】\n$summary\n\n"
        val memoryText = if (memories.isNotEmpty()) {
            memories.takeLast(20).joinToString("\n") { "记忆中：$it" }
        } else {
            ""
        }
        val myInfoText = if (myInfo.isBlank()) "" else "我的信息：$myInfo\n"
        // 分层排布：稳定内容在前（人设/摘要/原话），动态检索贴着当前句，前缀缓存友好
        val prompt = "$roleContext$myInfoText$summaryText$contextText\n\n$memoryText\n\n当前这句话：$transcript\n\n请判断云雀是否应该开口。只回答 SILENT 或 SPEAK:内容。"
        VoiceMvpLog.i("DECIDE", "mode=$mode transcript=${transcript.take(120)}")
        val forcedTool = detectTool(transcript)
        val content = completeWithTools(deepSeekKey, system, prompt, context, "DECIDE", forcedTool)
        VoiceMvpLog.i("DECIDE", "result=${content.take(200)}")
        return if (content.startsWith("SPEAK:")) {
            val speech = content.removePrefix("SPEAK:").trim()
            speech.ifEmpty { null }
        } else {
            null
        }
    }

    suspend fun synthesize(dashScopeKey: String, text: String, out: File, voiceOverride: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val ttsVoice = voiceOverride?.takeIf { it.isNotBlank() } ?: TTS_VOICE
            VoiceMvpLog.i("TTS", "开始合成: text=${text.take(200)} voice=$ttsVoice")
            val t0 = System.currentTimeMillis()
            val body = JSONObject()
                .put("model", TTS_MODEL)
                .put(
                    "input",
                    JSONObject()
                        .put("text", text)
                        .put("voice", ttsVoice)
                        .put("format", "wav")
                        .put("sample_rate", 24000)
                )
            val resp = JSONObject(postJson(TTS_URL, dashScopeKey, body, "TTS"))
            val audio = resp.getJSONObject("output").getJSONObject("audio")
            val url = audio.optString("url")
            require(url.isNotBlank()) { "TTS 响应没有音频 URL" }
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { r ->
                VoiceMvpLog.i("TTS", "下载音频 HTTP=${r.code} len=${r.body?.contentLength() ?: -1}")
                if (!r.isSuccessful) throw IllegalStateException("下载音频失败 HTTP ${r.code}")
                val bytes = r.body?.bytes() ?: throw IllegalStateException("音频为空")
                out.writeBytes(bytes)
            }
            VoiceMvpLog.i("TTS", "合成完成 ${System.currentTimeMillis() - t0}ms file=${out.absolutePath} size=${out.length()}")
            true
        }
}
