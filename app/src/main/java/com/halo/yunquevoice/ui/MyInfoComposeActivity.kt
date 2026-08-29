package com.halo.yunquevoice.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.SpeakerEngine
import com.halo.yunquevoice.voice.Store

class MyInfoComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassMaterialTheme { MyInfoScreen() }
        }
    }
}

@Composable
private fun MyInfoScreen() {
    val context = LocalContext.current
    val db = remember { MemoryDb(context) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Store.myName(context)) }
    var voice by remember { mutableStateOf(Store.myVoiceDesc(context)) }
    var speakerList by remember { mutableStateOf(db.getSpeakers()) }
    var speakerId by remember { mutableStateOf(Store.mySpeakerId(context)) }
    var recordStatus by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }

    fun recordVoiceprint() {
        if (recording) return
        recording = true
        recordStatus = "正在录音 5 秒…"
        scope.launch {
            try {
                val pcm = withContext(Dispatchers.IO) { recordPcm() }
                val sp = SpeakerEngine.recognize(db, pcm)
                if (name.isNotBlank()) db.renameSpeaker(sp.id, name)
                speakerId = sp.id
                speakerList = db.getSpeakers()
                recordStatus = "声纹已添加：${sp.name}"
            } catch (e: Throwable) {
                recordStatus = "录音失败：${e.message}"
            } finally {
                recording = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recordVoiceprint()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("我的信息", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("姓名") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = voice, onValueChange = { voice = it }, label = { Text("我的声音描述") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                recordVoiceprint()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("录制我的声纹（5秒）") }
        if (recordStatus.isNotBlank()) {
            Text(recordStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text("我的声纹", style = MaterialTheme.typography.titleMedium)
        val bound = speakerList.firstOrNull { it.id == speakerId }
        Text(
            if (bound != null) "已绑定：${bound.name}" else "尚未绑定（录制后会绑定为我的声纹）",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = {
            Store.saveMyInfo(context, name, voice, speakerId)
        }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
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
        val out = ByteArrayOutputStream()
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
