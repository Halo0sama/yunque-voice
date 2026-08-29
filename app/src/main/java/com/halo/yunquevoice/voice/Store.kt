package com.halo.yunquevoice.voice

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private const val PREFS = "yunque_voice"
    private const val KEY_DEEPSEEK = "deepseek_key"
    private const val KEY_DASHSCOPE = "dashscope_key"
    private const val KEY_LISTEN_MODE = "listen_mode"
    private const val KEY_INTERRUPTIONS = "interruptions"
    private const val KEY_BASIC_REPLY = "basic_reply"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_CLOUD_MEMORY = "cloud_memory"
    private const val KEY_MY_NAME = "my_name"
    private const val KEY_MY_VOICE_DESC = "my_voice_desc"
    private const val KEY_MY_SPEAKER_ID = "my_speaker_id"
    private const val KEY_WORKSPACE = "workspace_id"
    private const val KEY_MEMORY_LIBRARY = "memory_library_id"
    private const val KEY_MEMORY_USER = "memory_user_id"
    private const val KEY_LOCAL_MIGRATED = "local_memories_migrated"
    private const val KEY_OPERIT_URL = "operit_url"
    private const val KEY_OPERIT_TOKEN = "operit_token"
    private const val KEY_VOICE_ID = "voice_id"
    private const val KEY_NOTIFICATION_CONTROL = "notification_control"
    private const val KEY_NOTIFICATION_INTERRUPT = "notification_interrupt"
    private const val KEY_LISTEN_ONLY = "listen_only"
    private const val KEY_LISTEN_ONLY_TEXT_REPLY = "listen_only_text_reply"
    private const val KEY_LLM_PROVIDER = "llm_provider"

    const val LLM_DEEPSEEK = "deepseek"
    const val LLM_ZHIPU = "zhipu"

    /** 对话/决策/提炼所用大模型的供应商。Key 复用 deepseek_key 存储（语义为"对话模型 Key"）。 */
    fun llmProvider(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LLM_PROVIDER, LLM_DEEPSEEK)
            ?: LLM_DEEPSEEK

    fun saveLlmProvider(context: Context, provider: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LLM_PROVIDER, provider)
        }
    }

    // 1 = 只在该说时说；2 = 更主动旁听建议；3 = 只响应“云雀”唤醒
    const val LISTEN_MODE_SAFE = 1
    const val LISTEN_MODE_PROACTIVE = 2
    const val LISTEN_MODE_WAKE = 3

    const val THEME_LARK = 0
    const val THEME_PLAIN = 1
    const val THEME_MONET = 2
    const val THEME_GLASS = 3
    const val THEME_GLASS17 = 4

    fun deepSeekKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEEPSEEK, "") ?: ""

    fun dashScopeKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DASHSCOPE, "") ?: ""

    fun notificationControlEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NOTIFICATION_CONTROL, false)

    fun saveNotificationControl(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NOTIFICATION_CONTROL, enabled)
        }
    }

    fun notificationInterruptEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NOTIFICATION_INTERRUPT, false)

    fun saveNotificationInterrupt(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NOTIFICATION_INTERRUPT, enabled)
        }
    }

    /** 仅聆听：照常记录与提炼，但不做决策、不播报。 */
    fun listenOnlyEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LISTEN_ONLY, false)

    fun saveListenOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_LISTEN_ONLY, enabled)
        }
    }

    /** 云雀有话要说：仅聆听时决策照跑，回复以文字写进对话面板（不播报）。关闭则完全沉默零消耗。 */
    fun listenOnlyTextReply(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LISTEN_ONLY_TEXT_REPLY, true)

    fun saveListenOnlyTextReply(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_LISTEN_ONLY_TEXT_REPLY, enabled)
        }
    }

    fun voiceId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_VOICE_ID, "") ?: ""

    fun saveVoiceId(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_VOICE_ID, id.trim())
        }
    }

    fun operitUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OPERIT_URL, "") ?: ""

    fun operitToken(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OPERIT_TOKEN, "") ?: ""

    fun saveOperit(context: Context, url: String, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_OPERIT_URL, url.trim())
            putString(KEY_OPERIT_TOKEN, token.trim())
        }
    }

    fun workspaceId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WORKSPACE, "") ?: ""

    fun memoryLibraryId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MEMORY_LIBRARY, "") ?: ""

    /** 云端记忆库的 user_id：安装级 UUID，首次调用时生成并持久化。 */
    fun memoryUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_MEMORY_USER, null)?.let { return it }
        val id = "yunque-" + java.util.UUID.randomUUID().toString().substring(0, 13)
        prefs.edit { putString(KEY_MEMORY_USER, id) }
        return id
    }

    /** 本地旧记忆是否已迁移上云并清空。 */
    fun localMemoriesMigrated(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCAL_MIGRATED, false)

    fun setLocalMemoriesMigrated(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_LOCAL_MIGRATED, true)
        }
    }

    fun saveKeys(context: Context, deepSeek: String, dashScope: String, workspace: String = "", memoryLibrary: String = "") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_DEEPSEEK, deepSeek.trim())
            putString(KEY_DASHSCOPE, dashScope.trim())
            if (workspace.isNotBlank()) putString(KEY_WORKSPACE, workspace.trim())
            if (memoryLibrary.isNotBlank()) putString(KEY_MEMORY_LIBRARY, memoryLibrary.trim())
        }
    }

    fun listenMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LISTEN_MODE, LISTEN_MODE_SAFE)

    fun saveListenMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_LISTEN_MODE, mode)
        }
    }

    /** 是否允许云雀回答时间/日期/电量这类基础问题。 */
    fun basicReplyEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BASIC_REPLY, true)

    fun saveBasicReplyEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_BASIC_REPLY, enabled)
        }
    }

    fun themeMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME_MODE, THEME_GLASS17)

    fun saveThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_THEME_MODE, mode)
        }
    }

    fun cloudMemoryEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CLOUD_MEMORY, true)

    fun saveCloudMemoryEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CLOUD_MEMORY, enabled)
        }
    }

    fun myName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MY_NAME, "") ?: ""

    fun myVoiceDesc(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MY_VOICE_DESC, "") ?: ""

    fun mySpeakerId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MY_SPEAKER_ID, "") ?: ""

    fun saveMyInfo(context: Context, name: String, voiceDesc: String, speakerId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_MY_NAME, name.trim())
            putString(KEY_MY_VOICE_DESC, voiceDesc.trim())
            putString(KEY_MY_SPEAKER_ID, speakerId.trim())
        }
    }

    fun saveInterruption(context: Context, record: InterruptionRecord) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString(KEY_INTERRUPTIONS, "[]")) }.getOrElse { JSONArray() }
        arr.put(
            JSONObject()
                .put("time", record.time)
                .put("full", record.fullText)
                .put("spoken", record.spokenText)
                .put("missed", record.missedText)
        )
        prefs.edit { putString(KEY_INTERRUPTIONS, arr.toString()) }
    }

    fun interruptions(context: Context): List<InterruptionRecord> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_INTERRUPTIONS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                InterruptionRecord(
                    time = o.optLong("time"),
                    fullText = o.optString("full"),
                    spokenText = o.optString("spoken"),
                    missedText = o.optString("missed")
                )
            }
        }.getOrElse { emptyList() }
    }
}
