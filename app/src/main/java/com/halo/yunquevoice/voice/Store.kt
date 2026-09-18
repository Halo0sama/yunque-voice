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
    private const val KEY_AUDIO_INPUT = "audio_input"
    private const val KEY_AUDIO_INPUT_DEVICE = "audio_input_device"
    private const val KEY_AUDIO_OUTPUT = "audio_output"
    private const val KEY_LISTENING_WAS_RUNNING = "listening_was_running"
    private const val KEY_BT_ACTION = "bt_key_action"
    private const val KEY_MIC_SOURCE = "mic_source"
    private const val KEY_BT_AUTOSWITCH = "bt_autoswitch"
    private const val KEY_BT_PROFILES = "bt_profiles"

    /** 拾音模式：voice_recognition（默认，系统语音优化）/ mic（标准）/ unprocessed（原始收录，保声纹细节） */
    const val MIC_VOICE_RECOGNITION = "voice_recognition"
    const val MIC_RAW = "mic"
    const val MIC_UNPROCESSED = "unprocessed"

    fun micSource(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MIC_SOURCE, MIC_VOICE_RECOGNITION)
            ?: MIC_VOICE_RECOGNITION

    fun saveMicSource(context: Context, v: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_MIC_SOURCE, v) }.also { }
    }

    /** 蓝牙连接自动切换单元总开关（关=一切连接事件不触发切换）。 */
    fun btAutoSwitchEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BT_AUTOSWITCH, false)

    fun saveBtAutoSwitch(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_BT_AUTOSWITCH, enabled) }
    }

    /** 设备档案：蓝牙地址 -> {listenAction: none/start_normal/start_listen_only/stop, mic: keep/device/phone} */
    data class BtProfile(val listenAction: String = "none", val mic: String = "keep")

    fun btProfiles(context: Context): Map<String, BtProfile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BT_PROFILES, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { k ->
                val p = o.getJSONObject(k)
                BtProfile(p.optString("listenAction", "none"), p.optString("mic", "keep"))
            }
        }.getOrDefault(emptyMap())
    }

    fun saveBtProfile(context: Context, address: String, profile: BtProfile) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = runCatching { JSONObject(prefs.getString(KEY_BT_PROFILES, "{}")) }.getOrElse { JSONObject() }
        o.put(address, JSONObject().put("listenAction", profile.listenAction).put("mic", profile.mic))
        prefs.edit { putString(KEY_BT_PROFILES, o.toString()) }
    }

    fun removeBtProfile(context: Context, address: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = runCatching { JSONObject(prefs.getString(KEY_BT_PROFILES, "{}")) }.getOrElse { JSONObject() }
        o.remove(address)
        prefs.edit { putString(KEY_BT_PROFILES, o.toString()) }
    }

    /** 蓝牙耳机语音助手功能键触发的动作：toggle_listen（默认）/ interrupt / speak_now。 */
    const val BT_TOGGLE_LISTEN = "toggle_listen"
    const val BT_INTERRUPT = "interrupt"
    const val BT_SPEAK_NOW = "speak_now"

    fun btAction(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BT_ACTION, BT_TOGGLE_LISTEN)
            ?: BT_TOGGLE_LISTEN

    fun saveBtAction(context: Context, action: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_BT_ACTION, action)
        }
    }

    /** 聆听是否应当处于运行状态（用于 START_STICKY 重启后自动恢复）。 */
    fun listeningWasRunning(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LISTENING_WAS_RUNNING, false)

    fun saveListeningWasRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_LISTENING_WAS_RUNNING, running)
        }
    }

    const val AUDIO_PHONE = "phone"
    const val AUDIO_EARPHONE = "earphone"

    /** 麦克风来源：手机麦克风（默认，蓝牙媒体音质不受影响）或耳机麦克风（走 SCO 通话通道）。 */
    fun audioInput(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AUDIO_INPUT, AUDIO_PHONE)
            ?: AUDIO_PHONE

    fun saveAudioInput(context: Context, input: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_AUDIO_INPUT, input)
        }
    }

    const val LLM_DEEPSEEK = "deepseek"
    const val LLM_ZHIPU = "zhipu"
    const val LLM_QWEN = "qwen"
    const val LLM_QWEN_OMNI = "qwen_omni"
    val LLM_PROVIDERS = listOf(LLM_DEEPSEEK, LLM_ZHIPU, LLM_QWEN, LLM_QWEN_OMNI)

    fun llmProvider(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LLM_PROVIDER, LLM_DEEPSEEK)
            ?: LLM_DEEPSEEK

    fun saveLlmProvider(context: Context, provider: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LLM_PROVIDER, provider)
        }
    }

    /** 供应商显示名。 */
    fun llmLabel(provider: String): String = when (provider) {
        LLM_ZHIPU -> "智谱 GLM"
        LLM_QWEN -> "Qwen（阿里）"
        LLM_QWEN_OMNI -> "Qwen Omni（音频）"
        else -> "DeepSeek"
    }

    /** 每供应商 Key（v0.11 起多 Key 独立保存；旧 deepseek_key 自动迁移）。 */
    fun llmKey(context: Context, provider: String): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("llm_key_$provider", null)?.let { return it }
        if (provider == LLM_DEEPSEEK) {
            val legacy = prefs.getString(KEY_DEEPSEEK, null).orEmpty()
            if (legacy.isNotBlank()) {
                prefs.edit { putString("llm_key_$provider", legacy) }
                return legacy
            }
        }
        return ""
    }

    fun saveLlmKey(context: Context, provider: String, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("llm_key_$provider", key.trim())
        }
    }

    /** 当前供应商的 Key，调用方一句拿到。 */
    fun llmActiveKey(context: Context): String = llmKey(context, llmProvider(context))

    /** 输入设备选择："builtin"（手机麦）或 "t<type>:a<address>"（具体设备，界面展示原始名）。 */
    fun audioInputDevice(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AUDIO_INPUT_DEVICE, "builtin")
            ?: "builtin"

    fun saveAudioInputDevice(context: Context, sel: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_AUDIO_INPUT_DEVICE, sel)
        }
    }

    /** TTS 输出："auto"（跟随系统）、"speaker"（手机扬声器）或 "t<type>:a<address>"（指定设备）。 */
    fun audioOutput(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AUDIO_OUTPUT, "auto") ?: "auto"

    fun saveAudioOutput(context: Context, sel: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_AUDIO_OUTPUT, sel)
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

    fun clearInterruptions(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_INTERRUPTIONS)
        }
    }
}
