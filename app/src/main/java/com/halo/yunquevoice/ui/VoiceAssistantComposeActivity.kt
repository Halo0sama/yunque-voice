package com.halo.yunquevoice.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.halo.yunquevoice.voice.Store
import com.halo.yunquevoice.voice.VoiceMvpClient
import com.halo.yunquevoice.voice.VoiceMvpLog
import com.halo.yunquevoice.voice.WavUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class VoiceAssistantComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        VoiceMvpLog.init(this)

        // 语音触发：按用户配置的耳机功能键动作分发
        val btAction = com.halo.yunquevoice.voice.Store.btAction(this)
        val action = when (btAction) {
            com.halo.yunquevoice.voice.Store.BT_INTERRUPT -> com.halo.yunquevoice.service.AlwaysOnListeningService.ACTION_INTERRUPT
            com.halo.yunquevoice.voice.Store.BT_SPEAK_NOW -> com.halo.yunquevoice.service.AlwaysOnListeningService.ACTION_SPEAK_NOW
            else -> if (com.halo.yunquevoice.service.AlwaysOnListeningService.isRunning)
                com.halo.yunquevoice.service.AlwaysOnListeningService.ACTION_STOP
            else com.halo.yunquevoice.service.AlwaysOnListeningService.ACTION_START
        }
        VoiceMvpLog.i("TRIGGER", "activity bt=$btAction action=$action running=${com.halo.yunquevoice.service.AlwaysOnListeningService.isRunning}")
        val toggling = Intent(this, com.halo.yunquevoice.service.AlwaysOnListeningService::class.java)
            .setAction(action)
        if (action == com.halo.yunquevoice.service.AlwaysOnListeningService.ACTION_INTERRUPT) startService(toggling)
        else startForegroundService(toggling)
        finish()
        return

        setContent {
            val scheme = if (Build.VERSION.SDK_INT >= 31) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else {
                if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                VoiceScreen()
            }
        }
    }
}

@Composable
private fun VoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("云雀·私人助理") }
    var transcript by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    fun startPipeline() {
        scope.launch {
            try {
                val deep = Store.llmActiveKey(context)
                val dash = Store.dashScopeKey(context)
                if (deep.isBlank() || dash.isBlank()) {
                    status = "请先设置 API Key"
                    return@launch
                }
                status = "正在聆听 5 秒…"
                val pcm = withContext(Dispatchers.IO) { recordPcm() }
                val wav = File(context.cacheDir, "voice_compose_input.wav")
                withContext(Dispatchers.IO) { WavUtil.writeWav(wav, pcm, 16000) }
                status = "识别中…"
                val text = VoiceMvpClient.transcribe(dash, wav)
                transcript = text
                status = "思考中…"
                val reply = VoiceMvpClient.chat(deep, text, context)
                answer = reply
                status = "合成语音中…"
                val tts = File(context.cacheDir, "voice_compose_answer.wav")
                VoiceMvpClient.synthesize(dash, reply, tts, Store.voiceId(context).ifBlank { null })
                status = "播报中…"
                withContext(Dispatchers.Main) { playWav(tts.absolutePath) }
                status = "完成"
            } catch (e: Throwable) {
                VoiceMvpLog.e("VOICE", "pipeline failed: ${e.message}", e)
                status = "出错：${e.message?.take(100)}"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPipeline()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("云雀·私人助理", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.startsWith("正在") || status == "识别中…" || status == "思考中…" || status == "合成语音中…" || status == "播报中…") {
                VoiceWaveform()
            }
            Spacer(Modifier.size(16.dp))
            if (transcript.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("你说：$transcript", modifier = Modifier.padding(16.dp))
                }
                Spacer(Modifier.size(12.dp))
            }
            if (answer.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("云雀：$answer", modifier = Modifier.padding(16.dp))
                }
                Spacer(Modifier.size(12.dp))
            }
            Button(onClick = {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startPipeline()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) { Text("开始 / 再说一次") }
        }
    }
}

@Composable
private fun VoiceWaveform() {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse)
    )
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(12) { i ->
            val h = (10 + 22 * abs(sin(phase * 2 * PI + i * 0.5))).dp
            Box(
                Modifier
                    .width(4.dp)
                    .height(h)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun recordPcm(): ByteArray {
    val sampleRate = 16000
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (minBuf <= 0) throw IllegalStateException("不支持 16k 录音")
    val bufSize = max(minBuf, sampleRate * 2 * 2)
    val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
    if (record.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("AudioRecord 初始化失败")
    try {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(bufSize)
        record.startRecording()
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end) {
            val n = record.read(buf, 0, buf.size)
            if (n > 0) out.write(buf, 0, n)
        }
        record.stop()
        return out.toByteArray()
    } finally {
        runCatching { record.release() }
    }
}

private fun playWav(path: String) {
    val player = MediaPlayer()
    player.setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
