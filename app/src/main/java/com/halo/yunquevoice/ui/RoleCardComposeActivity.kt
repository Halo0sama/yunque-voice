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
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.RoleCard
import com.halo.yunquevoice.memory.RoleCardIO

class RoleCardComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassMaterialTheme { RoleCardContent() }
        }
    }
}

@Composable
fun RoleCardContent(embedded: Boolean = false) {
    val context = LocalContext.current
    val db = remember { MemoryDb(context) }
    var cards by remember { mutableStateOf(db.listRoleCards()) }
    var editing by remember { mutableStateOf<RoleCard?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var selectedExport by remember { mutableStateOf<RoleCard?>(null) }
    var deleteRoleTarget by remember { mutableStateOf<RoleCard?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                RoleCardIO.importTavernJson(text, db)
                cards = db.listRoleCards()
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val card = selectedExport ?: return@runCatching
                val text = RoleCardIO.exportWithWorldBook(card, db.listWorldEntries(null))
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            }
        }
    }
    val pngImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@runCatching
                val json = TavernPngIO.readPnghJson(bytes) ?: error("PNG 中没有找到角色卡数据")
                RoleCardIO.importTavernJson(json, db)
                cards = db.listRoleCards()
            }
        }
    }
    val pngExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            runCatching {
                val card = selectedExport ?: return@runCatching
                val text = RoleCardIO.exportWithWorldBook(card, db.listWorldEntries(null))
                val png = TavernPngIO.writePngWithJson(text)
                context.contentResolver.openOutputStream(uri)?.use { it.write(png) }
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
                Text("角色卡", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("当前角色", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = { showCreate = true }) { Text("新建") }
            TextButton(onClick = { importLauncher.launch("application/json") }) { Text("导入") }
        }

        val active = cards.firstOrNull { it.active }
        if (active != null) {
            YunqueGlassCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("当前启用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(active.name, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    if (active.description.isNotBlank()) {
                        Text(active.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = { editing = active }) { Text("编辑") }
                        TextButton(onClick = { selectedExport = active; exportLauncher.launch("${active.name}.json") }) { Text("导出") }
                    }
                }
            }
        } else {
            Text("默认使用内置“云雀”角色卡", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("其他角色", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        cards.filter { !it.active }.forEach { card ->
            YunqueGlassCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(card.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (card.source == "tavern") Text("Tavern", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (card.description.isNotBlank()) {
                        Text(card.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = {
                            db.setActiveRoleCard(card.id)
                            cards = db.listRoleCards()
                        }) { Text("启用") }
                        TextButton(onClick = { editing = card }) { Text("编辑") }
                        TextButton(onClick = { selectedExport = card; exportLauncher.launch("${card.name}.json") }) { Text("导出") }
                        TextButton(onClick = { deleteRoleTarget = card }) { Text("删除") }
                    }
                }
            }
        }

    deleteRoleTarget?.let { card ->
        ConfirmDeleteDialog(
            text = "确定删除角色卡「${card.name}」吗？",
            onConfirm = {
                db.deleteRoleCard(card.id)
                cards = db.listRoleCards()
                deleteRoleTarget = null
            },
            onDismiss = { deleteRoleTarget = null }
        )
    }

    if (showCreate) {
        RoleCardEditor(
            initial = null,
            onSave = { name, desc, personality, scenario, first, example, system, post, alt, notes ->
                db.addRoleCard(name, desc, personality, scenario, first, example, system, post, alternateGreetings = alt, creatorNotes = notes, active = cards.isEmpty())
                cards = db.listRoleCards()
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }
    editing?.let { card ->
        RoleCardEditor(
            initial = card,
            onSave = { name, desc, personality, scenario, first, example, system, post, alt, notes ->
                db.updateRoleCard(card.id, card.copy(name=name, description=desc, personality=personality, scenario=scenario, firstMessage=first, exampleDialogue=example, systemPrompt=system, postHistoryInstructions=post, alternateGreetings=alt, creatorNotes=notes))
                cards = db.listRoleCards()
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}
}

@Composable
private fun RoleCardEditor(
    initial: RoleCard?,
    onSave: (String, String, String, String, String, String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var personality by remember { mutableStateOf(initial?.personality ?: "") }
    var scenario by remember { mutableStateOf(initial?.scenario ?: "") }
    var first by remember { mutableStateOf(initial?.firstMessage ?: "") }
    var example by remember { mutableStateOf(initial?.exampleDialogue ?: "") }
    var system by remember { mutableStateOf(initial?.systemPrompt ?: "") }
    var post by remember { mutableStateOf(initial?.postHistoryInstructions ?: "") }
    var alt by remember { mutableStateOf(initial?.alternateGreetings ?: "") }
    var notes by remember { mutableStateOf(initial?.creatorNotes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建角色卡" else "编辑角色卡") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名字") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("简介") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = personality, onValueChange = { personality = it }, label = { Text("性格") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = scenario, onValueChange = { scenario = it }, label = { Text("场景") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text("首次见面语") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("示范对话") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = system, onValueChange = { system = it }, label = { Text("系统设定") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = post, onValueChange = { post = it }, label = { Text("对话后指令") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = alt, onValueChange = { alt = it }, label = { Text("备用开场白") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("作者备注") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name, desc, personality, scenario, first, example, system, post, alt, notes) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
