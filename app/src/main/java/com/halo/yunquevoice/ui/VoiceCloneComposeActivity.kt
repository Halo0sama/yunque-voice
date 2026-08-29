package com.halo.yunquevoice.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.yunquevoice.voice.AudioTrimmer
import com.halo.yunquevoice.voice.DashScopeUpload
import com.halo.yunquevoice.voice.Store
import com.halo.yunquevoice.voice.VoiceMvpClient
import com.halo.yunquevoice.voice.VoiceMvpLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VoiceCloneComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassMaterialTheme { VoiceCloneContent() }
        }
    }
}

private val builtinVoices = listOf(
    "longanxiaoxin" to "龙安小新（默认）",
    "longanlingxin" to "龙安灵心",
    "longanlufeng" to "龙安鲁风",
    "longanfengyue" to "龙安风月",
    "longanyuanfei" to "龙安远飞",
    "longanlingxi" to "龙安灵犀",
    "longanhuan_v3" to "龙安欢v3",
    "longjielidou_v3" to "龙杰力斗v3",
    "longpaopao_v3" to "龙泡泡v3",
    "longhuohuo_v3" to "龙火火v3",
    "longchuanshu_v3" to "龙川蜀v3",
    "loongmary" to "Loong Mary"
)

@Composable
fun VoiceCloneContent(embedded: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("myvoice") }
    var status by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri)
                val out = File(context.cacheDir, "voice_sample_${System.currentTimeMillis()}.flac")
                FileOutputStream(out).use { fos -> input?.copyTo(fos) }
                selectedFile = out.absolutePath
                url = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        if (!embedded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { (context as? android.app.Activity)?.finish() },
                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                Spacer(Modifier.size(8.dp))
                Text("自定义音色", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            "上传 10~20 秒清晰的人声音频到可访问 URL，然后创建专属音色。创建成功后云雀会使用该音色播报。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text("当前音色：${Store.voiceId(context).ifBlank { "默认（longanxiaoxin）" }}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        Button(
            onClick = {
                scope.launch {
                    try {
                        status = "正在试听…"
                        val key = Store.dashScopeKey(context)
                        val currentVoice = Store.voiceId(context).ifBlank { "longanxiaoxin" }
                        val file = File(context.cacheDir, "voice_preview.wav")
                        VoiceMvpClient.synthesize(key, "你好，我是云雀，这是当前音色。", file, currentVoice)
                        playPreview(file.absolutePath)
                        status = "试听结束"
                    } catch (e: Throwable) {
                        status = "试听失败：${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("试听当前音色") }
        Button(onClick = { showVoiceDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("选择自带音色")
        }
        if (showVoiceDialog) {
            AlertDialog(
                onDismissRequest = { showVoiceDialog = false },
                title = { Text("选择自带音色") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        for ((id, label) in builtinVoices) {
                            TextButton(
                                onClick = {
                                    Store.saveVoiceId(context, id)
                                    showVoiceDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (Store.voiceId(context) == id) "✓ $label" else label) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showVoiceDialog = false }) { Text("关闭") } }
            )
        }
        Text("自定义克隆音色", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = { filePicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedFile == null) "选择本地音频文件" else "已选择：${File(selectedFile!!).name}")
        }
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("音频直链 URL（选本地文件可留空）") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = prefix, onValueChange = { prefix = it }, label = { Text("音色名前缀") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                status = "创建中…"
                try {
                    val key = Store.dashScopeKey(context)
                    val ws = Store.workspaceId(context)
                    if (key.isBlank() || ws.isBlank() || (url.isBlank() && selectedFile == null)) {
                        status = "请先填写 API Key、业务空间 ID，并选择音频或填写 URL"
                    } else {
                        val effectiveUrl = if (selectedFile != null) {
                            val trimmed = AudioTrimmer.trimSmart(context.cacheDir, File(selectedFile!!))
                            DashScopeUpload.upload(key, trimmed, "qwen-audio-3.0-tts-flash")
                        } else {
                            url.trim()
                        }
                        val voiceId = withContext(Dispatchers.IO) { createVoice(key, ws, prefix, effectiveUrl) }
                        Store.saveVoiceId(context, voiceId)
                        status = "创建成功：$voiceId"
                    }
                } catch (e: Throwable) {
                    status = "失败：${e.message}"
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("创建自定义音色") }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
    }
}

private fun playPreview(path: String) {
    val player = android.media.MediaPlayer()
    player.setAudioAttributes(
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    )
    player.setDataSource(path)
    player.prepare()
    player.start()
    while (player.isPlaying) {
        try { Thread.sleep(100) } catch (_: Throwable) { break }
    }
    player.release()
}

private fun createVoice(key: String, workspaceId: String, prefix: String, url: String): String {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val body = JSONObject()
        .put("model", "voice-enrollment")
        .put(
            "input", JSONObject()
                .put("action", "create_voice")
                .put("target_model", "qwen-audio-3.0-tts-flash")
                .put("prefix", prefix)
                .put("url", url)
        )
    val req = Request.Builder()
        .url("https://$workspaceId.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/customization")
        .header("Authorization", "Bearer $key")
        .header("Content-Type", "application/json")
        .header("X-DashScope-OssResourceResolve", "enable")
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .build()
    client.newCall(req).execute().use { r ->
        val text = r.body?.string() ?: ""
        VoiceMvpLog.i("VOICECLONE", "HTTP ${r.code}: ${text.take(500)}")
        if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}: $text")
        val obj = JSONObject(text)
        return obj.optJSONObject("output")?.optString("voice_id") ?: obj.optString("voice_id")
            .ifBlank { throw IllegalStateException("响应中没有 voice_id: $text") }
    }
}
