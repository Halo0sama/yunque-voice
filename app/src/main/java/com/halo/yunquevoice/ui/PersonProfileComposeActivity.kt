package com.halo.yunquevoice.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import com.halo.yunquevoice.memory.MemoryDb

class PersonProfileComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val speakerId = intent.getStringExtra("speaker_id") ?: ""
        setContent {
            GlassMaterialTheme { PersonProfileScreen(speakerId) }
        }
    }
}

@Composable
private fun PersonProfileScreen(speakerId: String) {
    val context = LocalContext.current
    val db = remember { MemoryDb(context) }
    val speaker = remember(speakerId) { db.getSpeaker(speakerId) }
    var notes by remember { mutableStateOf(db.listAboutMe(speakerId)) }
    var addText by remember { mutableStateOf("") }
    var addDialog by remember { mutableStateOf(false) }
    var deleteAboutTarget by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
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
                speaker?.name ?: "未知",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(16.dp))

        YunqueGlassCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (notes.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "还没有关于这个人的记录",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { addText = ""; addDialog = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "添加")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "记录",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { addText = ""; addDialog = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "添加")
                        }
                    }
                    notes.forEach { n ->
                        YunqueGlassCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(12.dp)) {
                                Text(
                                    "[${if (n.source == "ai") "AI" else "手动"}] ${n.content}",
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { deleteAboutTarget = n.id }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }

        deleteAboutTarget?.let { id ->
            ConfirmDeleteDialog(
                text = "确定删除这条记录吗？",
                onConfirm = {
                    db.deleteAboutMe(id)
                    notes = db.listAboutMe(speakerId)
                    deleteAboutTarget = null
                },
                onDismiss = { deleteAboutTarget = null }
            )
        }

        if (addDialog) {
            AlertDialog(
                onDismissRequest = { addDialog = false },
                title = { Text("添加记录") },
                text = { OutlinedTextField(value = addText, onValueChange = { addText = it }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    TextButton(onClick = {
                        if (addText.isNotBlank()) {
                            db.addAboutMe(addText.trim(), "manual", speakerId)
                            notes = db.listAboutMe(speakerId)
                        }
                        addDialog = false
                    }) { Text("添加") }
                },
                dismissButton = { TextButton(onClick = { addDialog = false }) { Text("取消") } }
            )
        }
    }
}
