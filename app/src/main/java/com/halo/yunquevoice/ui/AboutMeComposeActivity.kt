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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.SpeakerEngine
import com.halo.yunquevoice.voice.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

class AboutMeComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassMaterialTheme { AboutMeScreen() }
        }
    }
}

@Composable
private fun AboutMeScreen() {
    val context = LocalContext.current
    val db = remember { MemoryDb(context) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Store.myName(context)) }
    var speakerId by remember { mutableStateOf(Store.mySpeakerId(context)) }
    var speakerList by remember { mutableStateOf(db.getSpeakers()) }
    val profileDoc = remember { db.loadProfileDoc() }
    var profile by remember { mutableStateOf(profileDoc.content) }
    var profileBudget by remember { mutableStateOf(profileDoc.budget) }
    var profileSavedTick by remember { mutableStateOf(0) }
    var recordStatus by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var hasVoice by remember { mutableStateOf(speakerId.isNotBlank()) }

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
                Store.saveMyInfo(context, name, "", speakerId)
                hasVoice = true
                recordStatus = "声纹已更新"
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(
                onClick = { (context as? android.app.Activity)?.finish() },
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.size(8.dp))
            Text("关于我", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))

        YunqueGlassCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("个人信息", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        Store.saveMyInfo(context, it, "", speakerId)
                    },
                    label = { Text("姓名（自动保存）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        recordVoiceprint()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Text(if (hasVoice) "重新录制声纹" else "录制我的声纹（5秒）")
                }
                if (recordStatus.isNotBlank()) {
                    Text(recordStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        YunqueGlassCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("关于我 · 画像文档", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text(
                        "${profile.length}/$profileBudget 字",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "云雀每晚从对话中自动维护这份画像（合并重复、消解矛盾、丢弃琐事）；你也可以直接编辑，它决定云雀有多懂你。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = profile,
                    onValueChange = { profile = it },
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        if (profileSavedTick > 0) "已保存" else "",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            val content = profile.trim()
                            db.saveProfileDoc(content, profileBudget)
                            profile = content
                            profileSavedTick++
                        }
                    ) { Text("保存") }
                }
            }
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
