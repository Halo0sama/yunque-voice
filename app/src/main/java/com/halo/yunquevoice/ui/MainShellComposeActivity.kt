package com.halo.yunquevoice.ui

import android.app.DatePickerDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nadeemiqbal.liquidglass.GlassButton
import io.github.nadeemiqbal.liquidglass.GlassBottomSheet
import io.github.nadeemiqbal.liquidglass.GlassCard
import io.github.nadeemiqbal.liquidglass.GlassNavBar
import io.github.nadeemiqbal.liquidglass.liquidGlass
import io.github.nadeemiqbal.liquidglass.rememberLiquidGlassState
import com.halo.yunquevoice.memory.ConversationRecord
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.RelationshipRecord
import com.halo.yunquevoice.memory.SpeakerProfile
import com.halo.yunquevoice.memory.WorkingMemory
import com.halo.yunquevoice.service.AlwaysOnListeningService
import com.halo.yunquevoice.voice.MemoryUploader
import com.halo.yunquevoice.voice.Store
import com.halo.yunquevoice.voice.VoiceMvpClient
import com.halo.yunquevoice.voice.VoiceMvpLog
import kotlinx.coroutines.launch
import kotlin.math.PI
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainShellComposeActivity : ComponentActivity() {

    companion object {
        // 通知点击"云雀有话要说"时请求打开对话面板；AppShell 消费后复位
        val openChatTab = androidx.compose.runtime.mutableStateOf(false)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent?.getStringExtra("open_tab") == "chat") openChatTab.value = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getStringExtra("open_tab") == "chat") openChatTab.value = true
        enableEdgeToEdge()
        setContent {
            GlassMaterialTheme {
                val crashFile = File(cacheDir, "crash.log")
                var recovery by remember { mutableStateOf(crashFile.exists()) }
                if (recovery) {
                    CrashRecoveryScreen(
                        logText = runCatching { crashFile.readText() }.getOrDefault(""),
                        onClear = {
                            crashFile.delete()
                            recovery = false
                        }
                    )
                } else {
                    AppShell()
                }
            }
        }
    }
}

@Composable
private fun CrashRecoveryScreen(logText: String, onClear: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("兼容模式", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "检测到上次启动异常，已进入兼容模式。以下是崩溃日志，你可以复制或分享给我，然后清除并正常启动。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                logText.ifBlank { "（没有读取到日志）" },
                modifier = Modifier.padding(16.dp).heightIn(max = 300.dp),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Row(Modifier.padding(top = 16.dp)) {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(logText))
                android.widget.Toast.makeText(context, "日志已复制", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("复制日志") }
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, logText)
                }
                context.startActivity(Intent.createChooser(send, "分享日志"))
            }) { Text("导出/分享") }
            TextButton(onClick = onClear) { Text("清除并正常启动") }
        }
    }
}

private enum class ShellTab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Filled.Home),
    Chat("对话", Icons.Filled.Chat),
    Memory("记忆", Icons.Filled.MenuBook),
    People("人物", Icons.Filled.Person),
    Settings("设置", Icons.Filled.Settings)
}

@Composable
private fun YunqueMaterialTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val baseScheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    val scheme = if (Store.themeMode(context) == Store.THEME_GLASS) {
        baseScheme.copy(
            surface = baseScheme.surface.copy(alpha = 0.72f),
            surfaceVariant = baseScheme.surfaceVariant.copy(alpha = 0.55f),
            background = baseScheme.background.copy(alpha = 0.94f),
            primaryContainer = baseScheme.primaryContainer.copy(alpha = 0.68f),
            secondaryContainer = baseScheme.secondaryContainer.copy(alpha = 0.60f),
            tertiaryContainer = baseScheme.tertiaryContainer.copy(alpha = 0.60f)
        )
    } else {
        baseScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun AppShell() {
    val context = LocalContext.current
    val openChat = (context as? android.app.Activity)?.intent?.getStringExtra("open_tab") == "chat"
    // remember 必须保留：否则每次 recompose 都会重建 state，把通知跳转设置的 tab 打回首页
    var tab by remember { mutableStateOf(if (openChat) ShellTab.Chat else ShellTab.Home) }
    LaunchedEffect(MainShellComposeActivity.openChatTab.value) {
        if (MainShellComposeActivity.openChatTab.value) {
            tab = ShellTab.Chat
            MainShellComposeActivity.openChatTab.value = false
        }
    }
    val glass = Store.themeMode(context) == Store.THEME_GLASS || Store.themeMode(context) == Store.THEME_GLASS17
    Scaffold(
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .then(
                            if (glass) Modifier.background(Color.White.copy(alpha = 0.22f))
                            else Modifier.background(MaterialTheme.colorScheme.surface)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                ) {
                    Row(Modifier.fillMaxHeight()) {
                        ShellTab.values().forEach { item ->
                            val selected = tab == item
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { tab = item },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    item.label,
                                    fontSize = 11.sp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (tab == ShellTab.Chat) {
            // 聊天面板自带 LazyColumn，不进外层 verticalScroll，避免同向嵌套滚动
            ChatScreen(context, Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                when (tab) {
                    ShellTab.Home -> HomeScreen(context)
                    ShellTab.Memory -> MemoryScreen(context)
                    ShellTab.People -> PeopleScreen(context)
                    ShellTab.Settings -> SettingsScreen(context)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(context: android.content.Context) {
    var running by remember { mutableStateOf(AlwaysOnListeningService.isRunning) }
    var listenOnly by remember { mutableStateOf(Store.listenOnlyEnabled(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            running = AlwaysOnListeningService.isRunning
            listenOnly = Store.listenOnlyEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    fun startListening() {
        context.startForegroundService(Intent(context, AlwaysOnListeningService::class.java).apply {
            action = AlwaysOnListeningService.ACTION_START
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            startListening()
        }
    }

    Text("云雀·私人助理", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(
        when {
            running && listenOnly -> "正在聆听（仅聆听，云雀不会开口）"
            running -> "正在聆听（可打断）"
            else -> "未开始聆听"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.size(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("状态", fontWeight = FontWeight.Bold)
            QuickButton(
                if (running) "停止聆听" else "开始聆听",
                onClick = {
                    if (running) {
                        context.startService(Intent(context, AlwaysOnListeningService::class.java).apply {
                            action = AlwaysOnListeningService.ACTION_STOP
                        })
                    } else {
                        val missing = mutableListOf<String>()
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            missing.add(Manifest.permission.RECORD_AUDIO)
                        }
                        if (Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            missing.add(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (missing.isEmpty()) {
                            startListening()
                        } else {
                            permissionLauncher.launch(missing.toTypedArray())
                        }
                    }
                },
                active = running
            )
            QuickButton("打断播报", onClick = {
                context.startService(Intent(context, AlwaysOnListeningService::class.java).apply {
                    action = AlwaysOnListeningService.ACTION_INTERRUPT
                })
            })
            QuickButton(
                if (listenOnly) "关闭仅聆听（恢复播报）" else "仅聆听（保持安静）",
                onClick = {
                    val next = !listenOnly
                    Store.saveListenOnly(context, next)
                    listenOnly = next
                    if (running) {
                        context.startService(Intent(context, AlwaysOnListeningService::class.java).apply {
                            action = AlwaysOnListeningService.ACTION_SET_LISTEN_ONLY
                            putExtra("enabled", next)
                        })
                    }
                },
                active = listenOnly
            )
        }
    }

    Spacer(Modifier.size(20.dp))
    Text("今日摘要", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text("云雀还没有为你总结今日内容", modifier = Modifier.padding(16.dp))
    }

    Spacer(Modifier.size(20.dp))
}

@Composable
private fun ChatScreen(context: android.content.Context, modifier: Modifier = Modifier) {
    val db = remember { MemoryDb(context) }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(db.recentConversations(200).asReversed()) }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val timeFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

    // 面板可见时轮询时间线：语音旁听、仅聆听文字回应都会实时冒出来
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val fresh = db.recentConversations(200).asReversed()
            if (fresh.size != messages.size || fresh.lastOrNull()?.id != messages.lastOrNull()?.id) {
                messages = fresh
            }
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || thinking) return
        input = ""
        val speaker = Store.myName(context).ifBlank { "主人" }
        val turns = db.recentConversations(30).asReversed().map { c ->
            if (c.origin == "assistant") "assistant" to c.text
            else "user" to "（${c.speakerName ?: "某人"}说）${c.text}"
        }
        db.addConversation(
            ConversationRecord(id = 0, ts = System.currentTimeMillis(), speakerId = null, speakerName = speaker, text = text, origin = "user")
        )
        // 上云规则与聆听模式划等号：同一条簇合并/verbatim 链路
        MemoryUploader.enqueue(context, db, text, speaker)
        WorkingMemory.stat(db, "chat_typed")
        thinking = true
        scope.launch {
            runCatching {
                VoiceMvpClient.chat(
                    Store.llmActiveKey(context), text, context,
                    recentTurns = turns, workingSummary = db.loadSessionState().summary
                )
            }.onSuccess { reply ->
                if (reply.isNotBlank()) {
                    db.addConversation(
                        ConversationRecord(id = 0, ts = System.currentTimeMillis(), speakerId = null, speakerName = "云雀", text = reply, origin = "assistant")
                    )
                    WorkingMemory.stat(db, "chat_reply")
                }
            }.onFailure {
                WorkingMemory.stat(db, "chat_reply_fail")
                VoiceMvpLog.w("CHAT", "打字回复失败: ${it.message}")
            }
            thinking = false
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "和云雀说点什么吧——这里与语音聆听共用同一段对话和记忆。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(messages, key = { it.id }) { m ->
                val isLark = m.origin == "assistant"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isLark) Arrangement.Start else Arrangement.End
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isLark) 4.dp else 16.dp,
                            bottomEnd = if (isLark) 16.dp else 4.dp
                        ),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.text)
                            Text(
                                (if (isLark) "云雀 · " else "") + timeFmt.format(java.util.Date(m.ts)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (thinking) {
                item {
                    Text(
                        "云雀正在想…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("给云雀发消息") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            Spacer(Modifier.size(8.dp))
            IconButton(
                onClick = { send() },
                enabled = !thinking && input.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun MemoryScreen(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    val timeFmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
    var nodes by remember { mutableStateOf(listOf<com.halo.yunquevoice.voice.BailianMemory.MemoryNode>()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("加载中…") }
    var addDialog by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    var deleteNodeTarget by remember { mutableStateOf<com.halo.yunquevoice.voice.BailianMemory.MemoryNode?>(null) }
    var migrating by remember { mutableStateOf(false) }

    fun loadPage(p: Int) {
        scope.launch {
            busy = true
            runCatching { com.halo.yunquevoice.voice.BailianMemory.list(context, 20, p) }
                .onSuccess { (list, t) ->
                    nodes = if (p == 1) list else nodes + list
                    total = t
                    page = p
                    status = if (t == 0) "云端还没有记忆" else ""
                }
                .onFailure { status = "云端记忆加载失败：${it.message?.take(80)}" }
            busy = false
        }
    }

    fun search() {
        scope.launch {
            busy = true
            runCatching { com.halo.yunquevoice.voice.BailianMemory.search(context, query, topK = 20) }
                .onSuccess {
                    nodes = it
                    total = it.size
                    status = if (it.isEmpty()) "没有相关的云端记忆" else ""
                }
                .onFailure { status = "语义搜索失败：${it.message?.take(80)}" }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        // 一次性迁移：本地旧记忆上云，成功后清空本地表（云端为唯一真相源）
        if (!Store.localMemoriesMigrated(context)) {
            val db = MemoryDb(context)
            val local = db.listMemories()
            if (local.isNotEmpty()) {
                migrating = true
                runCatching {
                    com.halo.yunquevoice.voice.BailianMemory.addFacts(context, local.map { it.content })
                }.onSuccess {
                    db.clearMemories()
                    Store.setLocalMemoriesMigrated(context)
                    VoiceMvpLog.i("MEMORY", "本地 ${local.size} 条记忆已迁移上云并清空")
                }.onFailure {
                    status = "本地记忆迁移失败：${it.message?.take(60)}，下次打开重试"
                }
                migrating = false
            }
        }
        loadPage(1)
    }

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("语义搜索云端记忆") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = { if (query.isBlank()) loadPage(1) else search() },
            modifier = Modifier.size(56.dp).offset(y = 3.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Filled.Search, contentDescription = "搜索/刷新")
        }
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = { addText = ""; addDialog = true },
            modifier = Modifier.size(56.dp).offset(y = 3.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加记忆")
        }
    }
    if (migrating) Text("正在把本地旧记忆迁移上云…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (status.isNotBlank()) {
        Text(status, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (nodes.isNotEmpty()) {
        Text(
            if (query.isBlank()) "共 $total 条（云端）" else "语义匹配 ${nodes.size} 条",
            modifier = Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    nodes.forEach { m ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(m.content)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        timeFmt.format(java.util.Date(if (m.eventTsMs > 0) m.eventTsMs else m.createdAtMs)) +
                            if (m.score >= 0) "  ·  相关度 ${(m.score * 100).toInt()}%" else "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { deleteNodeTarget = m }) { Text("删除") }
                }
            }
        }
    }
    if (nodes.isNotEmpty() && query.isBlank() && nodes.size < total) {
        TextButton(onClick = { loadPage(page + 1) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "加载中…" else "加载更多（已加载 ${nodes.size}/$total）")
        }
    }

    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("添加记忆（存入云端）") },
            text = { OutlinedTextField(value = addText, onValueChange = { addText = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    if (addText.isNotBlank()) {
                        scope.launch {
                            runCatching { com.halo.yunquevoice.voice.BailianMemory.addCustom(context, addText.trim()) }
                                .onSuccess { addDialog = false; loadPage(1) }
                                .onFailure { status = "添加失败：${it.message?.take(80)}" }
                        }
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("取消") } }
        )
    }

    deleteNodeTarget?.let { m ->
        ConfirmDeleteDialog(
            text = "删除这条云端记忆？\n${m.content.take(50)}\n（不可恢复）",
            onConfirm = {
                scope.launch {
                    runCatching { com.halo.yunquevoice.voice.BailianMemory.delete(context, m.id) }
                        .onSuccess { deleteNodeTarget = null; loadPage(1) }
                        .onFailure { status = "删除失败：${it.message?.take(80)}" }
                }
            },
            onDismiss = { deleteNodeTarget = null }
        )
    }
}

@Composable
private fun RelationshipGraph(
    speakers: List<SpeakerProfile>,
    rels: List<RelationshipRecord>,
    onNodeClick: (SpeakerProfile) -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outline
    val nodeColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("关系图谱", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(speakers, rels) {
                detectTapGestures { offset ->
                    if (speakers.isEmpty()) return@detectTapGestures
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = min(size.width, size.height) * 0.38f
                    val hit = speakers.firstOrNull { s ->
                        val i = speakers.indexOf(s)
                        val angle = -PI / 2 + i * 2 * PI / speakers.size
                        val px = cx + radius * cos(angle).toFloat()
                        val py = cy + radius * sin(angle).toFloat()
                        val dx = offset.x - px
                        val dy = offset.y - py
                        dx * dx + dy * dy < 28f * 28f
                    }
                    if (hit != null) onNodeClick(hit)
                }
            }
    ) {
        if (speakers.isEmpty()) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) * 0.38f
        val positions = HashMap<String, Offset>()
        for ((i, s) in speakers.withIndex()) {
            val angle = -PI / 2 + i * 2 * PI / speakers.size
            positions[s.id] = Offset(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
        }
        for (r in rels) {
            val a = positions[r.sourceId] ?: continue
            val b = positions[r.targetId] ?: continue
            drawLine(lineColor, a, b, strokeWidth = 3f)
        }
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 30f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for (s in speakers) {
            val p = positions[s.id] ?: continue
            drawCircle(nodeColor, radius = 24f, center = p)
            drawContext.canvas.nativeCanvas.drawText(s.name.take(4), p.x, p.y + 5f, textPaint)
        }
        }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PeopleScreen(context: android.content.Context) {
    val db = MemoryDb(context)
    val speakers = db.listCanonicalSpeakers()
    val rels = db.listRelationships()
    val graphSpeakers = speakers.filter {
        !it.name.startsWith("未知") && !it.name.startsWith("云端说话人")
    }

    RelationshipGraph(graphSpeakers, rels, onNodeClick = {
        context.startActivity(Intent(context, VoiceManagerComposeActivity::class.java))
    })

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp).clickable { context.startActivity(Intent(context, AboutMeComposeActivity::class.java)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("关于我", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp).clickable { context.startActivity(Intent(context, VoiceManagerComposeActivity::class.java)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("身边的人", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun YunqueBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val glass17 = Store.themeMode(context) == Store.THEME_GLASS17
    if (glass17) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = Color.Transparent,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            ) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    content()
                }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, content = content)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(context: android.content.Context) {
    var deepKey by remember { mutableStateOf(Store.llmActiveKey(context)) }
    var dashKey by remember { mutableStateOf(Store.dashScopeKey(context)) }
    var workspace by remember { mutableStateOf(Store.workspaceId(context)) }
    var memoryLib by remember { mutableStateOf(Store.memoryLibraryId(context)) }
    var showAiCard by remember { mutableStateOf(false) }
    var aiProviderEdit by remember { mutableStateOf(Store.llmProvider(context)) }
    var keyDraft by remember { mutableStateOf(Store.llmActiveKey(context)) }
    var operitUrl by remember { mutableStateOf(Store.operitUrl(context)) }
    var operitToken by remember { mutableStateOf(Store.operitToken(context)) }
    var showOperit by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(Store.themeMode(context)) }
    var showTheme by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showRoleSheet by remember { mutableStateOf(false) }
    var showNotifSheet by remember { mutableStateOf(false) }
    var stopNotif by remember { mutableStateOf(Store.notificationControlEnabled(context)) }
    var interruptNotif by remember { mutableStateOf(Store.notificationInterruptEnabled(context)) }
    var showListenSheet by remember { mutableStateOf(false) }
    var textReply by remember { mutableStateOf(Store.listenOnlyTextReply(context)) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var audioSelTick by remember { mutableStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    QuickNav("AI 与接口") { showAiCard = true }
    QuickNav("主题") { showTheme = true }
    QuickNav("Operit 接入") { showOperit = true }
    QuickNav("自定义音色") { showVoiceSheet = true }
    QuickNav("通知栏控制") { showNotifSheet = true }
    QuickNav("仅聆听与对话") { showListenSheet = true }
    QuickNav("麦克风与音质") { showAudioSheet = true }
    if (showAudioSheet) {
        val am = context.getSystemService(android.media.AudioManager::class.java)
        var inSel by remember { mutableStateOf(Store.audioInputDevice(context)) }
        var outSel by remember { mutableStateOf(Store.audioOutput(context)) }
        fun applyInput(sel: String) {
            inSel = sel
            Store.saveAudioInputDevice(context, sel)
            context.startService(Intent(context, AlwaysOnListeningService::class.java).apply {
                action = AlwaysOnListeningService.ACTION_APPLY_AUDIO
            })
        }
        fun audioTypeName(t: Int): String = when (t) {
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "手机麦克风"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "手机扬声器"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙通话麦克风"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙耳机"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线带麦耳机"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB音频设备"
            else -> "音频设备"
        }
        YunqueBottomSheet(onDismiss = { showAudioSheet = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("麦克风与音质", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "设备显示系统原始名称。输入用手机麦时蓝牙耳机只负责出声，音乐视频音质不受影响；\n" +
                        "选蓝牙通话麦克风时走 SCO 通道，耳机里媒体音质会下降（蓝牙协议限制）。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("声音输入", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TextButton(
                    onClick = { applyInput("builtin") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (inSel == "builtin") "✓ 手机麦克风（推荐）" else "手机麦克风（推荐）") }
                am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                    .filter {
                        it.type in intArrayOf(
                            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                            android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                        )
                    }
                    .forEach { d ->
                        val key = "t${d.type}:a${d.address}"
                        val name = d.productName?.toString()?.ifBlank { audioTypeName(d.type) } ?: audioTypeName(d.type)
                        TextButton(onClick = { applyInput(key) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (inSel == key) "✓ $name" else name)
                        }
                    }
                Spacer(Modifier.size(8.dp))
                Text("声音输出（云雀说话）", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TextButton(
                    onClick = { outSel = "auto"; Store.saveAudioOutput(context, "auto") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (outSel == "auto") "✓ 跟随系统" else "跟随系统") }
                am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                    .filter {
                        it.type in intArrayOf(
                            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                            android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                        )
                    }
                    .forEach { d ->
                        val key = "t${d.type}:a${d.address}"
                        val name = d.productName?.toString()?.ifBlank { audioTypeName(d.type) } ?: audioTypeName(d.type)
                        TextButton(onClick = { outSel = key; Store.saveAudioOutput(context, key) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (outSel == key) "✓ $name" else name)
                        }
                    }
            }
        }
    }
    if (showListenSheet) {
        YunqueBottomSheet(onDismiss = { showListenSheet = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("仅聆听", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "仅聆听时云雀只听不说。\u201c云雀有话要说\u201d决定它是否用文字在对话面板里回应：关闭则完全沉默、零消耗。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        val next = !textReply
                        Store.saveListenOnlyTextReply(context, next)
                        textReply = next
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (textReply) "✓ 云雀有话要说（文字回应，正常消耗决策）"
                        else "云雀有话要说（关闭 = 完全沉默零消耗）"
                    )
                }
            }
        }
    }
    if (showNotifSheet) {
        YunqueBottomSheet(onDismiss = { showNotifSheet = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("通知栏控制", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = {
                    val next = !stopNotif
                    if (next && Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Store.saveNotificationControl(context, next)
                    stopNotif = next
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (stopNotif) "✓ 点击停止聆听" else "点击停止聆听")
                }
                TextButton(onClick = {
                    val next = !interruptNotif
                    if (next && Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Store.saveNotificationInterrupt(context, next)
                    interruptNotif = next
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (interruptNotif) "✓ 点击打断播报" else "点击打断播报")
                }
            }
        }
    }
    if (showAiCard) {
        YunqueBottomSheet(onDismiss = { showAiCard = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("AI 与接口", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("对话模型（点击即切换，各家的 Key 分别保存）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Store.LLM_PROVIDERS.forEach { p ->
                        val label = when (p) {
                            Store.LLM_ZHIPU -> "智谱 GLM"
                            Store.LLM_QWEN -> "Qwen（阿里）"
                            else -> "DeepSeek"
                        }
                        FilterChip(
                            selected = aiProviderEdit == p,
                            onClick = {
                                aiProviderEdit = p
                                keyDraft = Store.llmKey(context, p)
                                Store.saveLlmProvider(context, p)
                            },
                            label = { Text(if (aiProviderEdit == p) "✓ $label" else label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = {
                        keyDraft = it
                        Store.saveLlmKey(context, aiProviderEdit, it)
                    },
                    label = { Text("${when (aiProviderEdit) { Store.LLM_ZHIPU -> "智谱"; Store.LLM_QWEN -> "阿里Qwen"; else -> "DeepSeek" }} API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.padding(top = 8.dp))
                OutlinedTextField(value = dashKey, onValueChange = { dashKey = it }, label = { Text("阿里云百炼 API Key（识别/语音/记忆）") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.padding(top = 8.dp))
                OutlinedTextField(value = workspace, onValueChange = { workspace = it }, label = { Text("业务空间 ID") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.padding(top = 8.dp))
                OutlinedTextField(value = memoryLib, onValueChange = { memoryLib = it }, label = { Text("记忆库 ID（控制台-记忆库 页面可查）") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    Store.saveKeys(context, deepKey, dashKey, workspace, memoryLib)
                    showAiCard = false
                }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("保存") }
            }
        }
    }
    if (showTheme) {
        YunqueBottomSheet(onDismiss = { showTheme = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("主题", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                listOf(
                    Store.THEME_MONET to "莫奈取色",
                    Store.THEME_LARK to "温暖纸感",
                    Store.THEME_GLASS17 to "液态玻璃"
                ).forEach { (mode, label) ->
                    TextButton(onClick = {
                        themeMode = mode
                        Store.saveThemeMode(context, mode)
                        showTheme = false
                        (context as? android.app.Activity)?.recreate()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (themeMode == mode) "✓ $label" else label)
                    }
                }
            }
        }
    }
    if (showOperit) {
        YunqueBottomSheet(onDismiss = { showOperit = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("Operit 接入", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = operitUrl, onValueChange = { operitUrl = it }, label = { Text("Operit 地址") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.padding(top = 8.dp))
                OutlinedTextField(value = operitToken, onValueChange = { operitToken = it }, label = { Text("Bearer Token") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    Store.saveOperit(context, operitUrl, operitToken)
                    showOperit = false
                }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("保存") }
            }
        }
    }
    QuickNav("角色卡") { showRoleSheet = true }

    if (showVoiceSheet) {
        YunqueBottomSheet(onDismiss = { showVoiceSheet = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("自定义音色", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                VoiceCloneContent(embedded = true)
            }
        }
    }
    if (showRoleSheet) {
        YunqueBottomSheet(onDismiss = { showRoleSheet = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("角色卡", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                RoleCardContent(embedded = true)
            }
        }
    }
}

@Composable
private fun QuickNav(label: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val glass17 = Store.themeMode(context) == Store.THEME_GLASS17
    val borderColor = if (glass17) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.5.dp, borderColor, MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (glass17) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickButton(label: String, onClick: () -> Unit, active: Boolean = false) {
    val context = LocalContext.current
    val glass17 = Store.themeMode(context) == Store.THEME_GLASS17
    if (glass17) {
        val state = rememberLiquidGlassState()
        GlassButton(
            state = state,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = true
        ) {
            Text(label, modifier = Modifier.padding(14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    } else {
        val container = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
        val content = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = container)
        ) {
            Text(label, modifier = Modifier.padding(14.dp), color = content, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
