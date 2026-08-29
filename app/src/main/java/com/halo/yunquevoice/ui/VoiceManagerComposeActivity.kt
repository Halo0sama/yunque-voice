package com.halo.yunquevoice.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.SpeakerProfile
import kotlin.math.abs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class VoiceManagerComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassMaterialTheme { VoiceManagerScreen() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun VoiceManagerScreen() {
    val context = LocalContext.current
    val db = remember { MemoryDb(context) }
    var speakers by remember { mutableStateOf(db.listCanonicalSpeakers()) }
    var filter by remember { mutableStateOf("全部") }
    var sortNewest by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var timeQuick by remember { mutableStateOf("全部") }
    var editing by remember { mutableStateOf<SpeakerProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<SpeakerProfile?>(null) }
    var mergeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var actionTarget by remember { mutableStateOf<SpeakerProfile?>(null) }

    val now = System.currentTimeMillis()
    val visible = speakers.filter { s ->
        when (filter) {
            "已分类" -> !s.name.startsWith("未知") && !s.name.startsWith("云端说话人")
            "未分类" -> s.name.startsWith("未知") || s.name.startsWith("云端说话人")
            else -> true
        }
    }.filter { s ->
        (timeQuick == "七天内" || startDate == null || s.updatedAt >= startDate!!) &&
            (timeQuick == "七天内" || endDate == null || s.updatedAt <= endDate!!) &&
            (timeQuick != "七天内" || s.updatedAt >= now - 7 * 24 * 3600 * 1000L)
    }.let { list ->
        if (sortNewest) list.sortedByDescending { it.updatedAt }
        else list.sortedByDescending { it.sampleCount }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { (context as? android.app.Activity)?.finish() },
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "身边的人",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))

        YunqueGlassCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("筛选", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    listOf("全部", "已分类", "未分类").forEach { f ->
                        FilterPill(f, filter == f) { filter = f }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterPill("按频率", !sortNewest) { sortNewest = false }
                    FilterPill("按时间", sortNewest) { sortNewest = true }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    FilterPill("全部", timeQuick == "全部") {
                        timeQuick = "全部"
                        startDate = null
                        endDate = null
                    }
                    FilterPill("七天内", timeQuick == "七天内") {
                        timeQuick = "七天内"
                        startDate = null
                        endDate = null
                    }
                    FilterPill(if (startDate == null) "开始日期" else formatDate(startDate!!), startDate != null) {
                        openDatePicker(context, true) { startDate = it }
                    }
                    FilterPill(if (endDate == null) "结束日期" else formatDate(endDate!!), endDate != null) {
                        openDatePicker(context, false) { endDate = it }
                    }
                }
            }
        }

        YunqueGlassCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("声音列表", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (visible.isEmpty()) {
                    Text("没有符合条件的声音", modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    visible.forEach { s ->
                        YunqueGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                                .combinedClickable(
                                    onClick = {
                                        context.startActivity(Intent(context, PersonProfileComposeActivity::class.java).putExtra("speaker_id", s.id))
                                    },
                                    onLongClick = { actionTarget = s }
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                "${s.name} · ${s.sampleCount} 样本",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        editing?.let { s ->
            var name by remember { mutableStateOf(s.name) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("编辑人物") },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名字") }) },
                confirmButton = { TextButton(onClick = {
                    if (name.isNotBlank()) {
                        db.renameSpeaker(s.id, name.trim())
                        editing = null
                    }
                }) { Text("保存") } },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }
            )
        }

        actionTarget?.let { s ->
            var newName by remember { mutableStateOf(s.name) }
            AlertDialog(
                onDismissRequest = { actionTarget = null },
                text = {
                    Column {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { playSpeakerSample(context, s.id) }) { Text("随机播放ta的声音") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            val trimmed = newName.trim()
                            val existing = db.listCanonicalSpeakers().firstOrNull { it.id != s.id && it.name == trimmed }
                            if (existing != null) {
                                mergeTarget = s.id to existing.id
                                actionTarget = null
                            } else {
                                db.renameSpeaker(s.id, trimmed)
                                actionTarget = null
                            }
                        } else {
                            actionTarget = null
                        }
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = s; actionTarget = null }) { Text("删除") }
                }
            )
        }

        mergeTarget?.let { (fromId, toId) ->
            AlertDialog(
                onDismissRequest = { mergeTarget = null },
                title = { Text("合并声纹") },
                text = { Text("检测到已有同名声纹，确认将两者合并为同一人吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        db.mergeSpeaker(fromId, toId)
                        speakers = db.listCanonicalSpeakers()
                        mergeTarget = null
                    }) { Text("合并") }
                },
                dismissButton = { TextButton(onClick = { mergeTarget = null }) { Text("取消") } }
            )
        }

        deleteTarget?.let { s ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("删除声纹") },
                text = { Text("确定删除「${s.name}」吗？此操作不可恢复。") },
                confirmButton = { TextButton(onClick = { db.deleteSpeaker(s.id); deleteTarget = null }) { Text("删除") } },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
            )
        }
    }
}

private fun playSpeakerSample(context: android.content.Context, speakerId: String) {
    val file = File(context.cacheDir, "speaker_samples/$speakerId.wav")
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "暂无样本，多听一会儿再试", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setDataSource(file.absolutePath)
        player.prepare()
        player.start()
        player.setOnCompletionListener { it.release() }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
    )
}

private fun openDatePicker(context: android.content.Context, isStart: Boolean, onPick: (Long) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, y, m, d ->
            val c = Calendar.getInstance().apply {
                set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            onPick(c.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun formatDate(ms: Long): String = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ms))
