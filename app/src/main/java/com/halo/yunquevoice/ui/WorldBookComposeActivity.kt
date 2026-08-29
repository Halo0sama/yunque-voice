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
import com.halo.yunquevoice.memory.RoleCardIO
import com.halo.yunquevoice.memory.WorldBook

class WorldBookComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassMaterialTheme { WorldBookScreen() }
        }
    }
}

@Composable
private fun WorldBookScreen() {
    val context = LocalContext.current
    val db = remember { MemoryDb(context) }
    var books by remember { mutableStateOf(db.listWorldBooks()) }
    var showBookDialog by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<WorldBook?>(null) }
    var deleteBookTarget by remember { mutableStateOf<Long?>(null) }
    var deleteEntryTarget by remember { mutableStateOf<Long?>(null) }
    var bookName by remember { mutableStateOf("") }
    var exportBook by remember { mutableStateOf<WorldBook?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                RoleCardIO.importWorldBookJson(text, db)
                books = db.listWorldBooks()
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val b = exportBook ?: return@runCatching
                val text = RoleCardIO.worldBookToJson(b, db.listWorldEntries(b.id))
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { (context as? android.app.Activity)?.finish() },
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Spacer(Modifier.size(8.dp))
            Text("世界书", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row {
            TextButton(onClick = { bookName = ""; showBookDialog = true }) { Text("新建世界书") }
            TextButton(onClick = { importLauncher.launch("application/json") }) { Text("导入世界书") }
        }

        if (books.isEmpty()) {
            Text("还没有世界书", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            books.forEach { book ->
                YunqueGlassCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(book.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        TextButton(onClick = { selectedBook = book }) { Text("管理条目") }
                        TextButton(onClick = {
                            exportBook = book
                            exportLauncher.launch("${book.name}.json")
                        }) { Text("导出世界书") }
                        TextButton(onClick = {
                            db.deleteWorldBook(book.id)
                            books = db.listWorldBooks()
                        }) { Text("删除世界书") }
                    }
                }
            }
        }
    }

    if (showBookDialog) {
        AlertDialog(
            onDismissRequest = { showBookDialog = false },
            title = { Text("新建世界书") },
            text = { OutlinedTextField(value = bookName, onValueChange = { bookName = it }) },
            confirmButton = {
                TextButton(onClick = {
                    if (bookName.isNotBlank()) {
                        db.addWorldBook(bookName.trim())
                        books = db.listWorldBooks()
                        showBookDialog = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showBookDialog = false }) { Text("取消") } }
        )
    }

    selectedBook?.let { book ->
        var entries by remember(book.id) { mutableStateOf(db.listWorldEntries(book.id)) }
        var showEntryDialog by remember { mutableStateOf(false) }
        var keys by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { selectedBook = null },
            title = { Text(book.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEach { e ->
                        Text("[${if (e.constant) "常驻" else "关键词"}] ${e.keys.take(30)} — ${e.content.take(30)}", modifier = Modifier.padding(vertical = 4.dp))
                        TextButton(onClick = { deleteEntryTarget = e.id }) { Text("删除该条") }
                    }
                    TextButton(onClick = { keys = ""; content = ""; showEntryDialog = true }) { Text("添加条目") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedBook = null }) { Text("关闭") } }
        )
        deleteEntryTarget?.let { id ->
            ConfirmDeleteDialog(
                text = "确定删除这条世界书条目吗？",
                onConfirm = {
                    db.deleteWorldEntry(id)
                    entries = db.listWorldEntries(book.id)
                    deleteEntryTarget = null
                },
                onDismiss = { deleteEntryTarget = null }
            )
        }

        if (showEntryDialog) {
            AlertDialog(
                onDismissRequest = { showEntryDialog = false },
                title = { Text("添加世界书条目") },
                text = {
                    Column {
                        OutlinedTextField(value = keys, onValueChange = { keys = it }, label = { Text("触发关键词（逗号分隔）") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (content.isNotBlank()) {
                            db.addWorldEntry(book.id, keys, content)
                            entries = db.listWorldEntries(book.id)
                            showEntryDialog = false
                        }
                    }) { Text("添加") }
                },
                dismissButton = { TextButton(onClick = { showEntryDialog = false }) { Text("取消") } }
            )
        }
    }

    deleteBookTarget?.let { id ->
        ConfirmDeleteDialog(
            text = "确定删除这本世界书吗？",
            onConfirm = {
                db.deleteWorldBook(id)
                books = db.listWorldBooks()
                deleteBookTarget = null
            },
            onDismiss = { deleteBookTarget = null }
        )
    }
}
