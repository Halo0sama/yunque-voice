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
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
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
import com.halo.yunquevoice.BuildConfig
import com.halo.yunquevoice.memory.ConversationRecord
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.RelationshipRecord
import com.halo.yunquevoice.memory.SpeakerProfile
import com.halo.yunquevoice.memory.WorkingMemory
import com.halo.yunquevoice.service.AlwaysOnListeningService
import com.halo.yunquevoice.voice.MemoryUploader
import com.halo.yunquevoice.voice.ScheduleRule
import com.halo.yunquevoice.voice.ScheduleStore
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
    // 启动后台检查 GitHub Releases 更新，有新版弹窗（本次会话只提醒一次）
    var updateInfo by remember { mutableStateOf<com.halo.yunquevoice.voice.UpdateChecker.UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadMsg by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadSpeed by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.halo.yunquevoice.voice.UpdateChecker.check(BuildConfig.VERSION_NAME)
        }?.let { updateInfo = it }
    }
    updateInfo?.let { info ->
        YunqueBottomSheet(onDismiss = { if (!downloading) updateInfo = null }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("发现新版本 ${info.tag}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(info.notes.take(280).ifBlank { "夜间自动优化与修复。" })
                Text(
                    "APK 大小：" + "%.1f".format(info.apkSize / 1048576.0) + " MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (downloading) {
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            (downloadProgress * 100).toInt().toString() + "%",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            downloadSpeed,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (downloadMsg.isNotBlank()) {
                    Text(downloadMsg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.size(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !downloading,
                        onClick = { updateInfo = null },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (downloading) "下载中…" else "下次再说") }
                    Button(
                        enabled = !downloading,
                        onClick = {
                            downloading = true
                            downloadMsg = ""
                            downloadProgress = 0f
                            val t0 = System.currentTimeMillis()
                            var lastBytes = 0L
                            var lastTime = t0
                            kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
                                val f = com.halo.yunquevoice.voice.UpdateChecker.download(
                                    context, info.apkUrl
                                ) { read, total ->
                                    val now = System.currentTimeMillis()
                                    if (now - lastTime >= 500) {
                                        val speed = (read - lastBytes) * 1000.0 / (now - lastTime) / 1048576.0
                                        lastBytes = read
                                        lastTime = now
                                        kotlinx.coroutines.MainScope().launch {
                                            downloadSpeed = "%.2f MB/s".format(speed)
                                            if (total > 0) downloadProgress = (read.toFloat() / total).coerceIn(0f, 1f)
                                        }
                                    }
                                }
                                kotlinx.coroutines.MainScope().launch {
                                    downloading = false
                                    if (f == null) {
                                        downloadMsg = "下载失败，可稍后重试或到 GitHub 手动下载"
                                        return@launch
                                    }
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, context.packageName + ".fileprovider", f)
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(android.content.Intent.ACTION_VIEW)
                                                .setDataAndType(uri, "application/vnd.android.package-archive")
                                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }.onFailure { downloadMsg = "已下载，拉起安装失败：" + (it.message ?: "") }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (downloading) "下载中…" else "下载并安装") }
                }
            }
        }
    }
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
    var speaking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            running = AlwaysOnListeningService.isRunning
            listenOnly = Store.listenOnlyEnabled(context)
            speaking = AlwaysOnListeningService.isSpeaking
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
    StatusBadge(
        when {
            speaking && running -> "云雀正在说话"
            running && listenOnly -> "仅聆听中"
            running -> "正在聆听"
            else -> "未在聆听"
        },
        active = running,
        speaking = speaking && running
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
            QuickButton("云雀请发言", onClick = {
                // 结合当下语境强制说一句；服务没跑会顺带拉起聆听
                context.startForegroundService(Intent(context, AlwaysOnListeningService::class.java).apply {
                    action = AlwaysOnListeningService.ACTION_SPEAK_NOW
                })
            })
            QuickButton(
                if (listenOnly) "关闭仅聆听" else "仅聆听（保持安静）",
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
    var showKeepAliveSheet by remember { mutableStateOf(false) }
    var showBtSheet by remember { mutableStateOf(false) }
    var btActionSel by remember { mutableStateOf(Store.btAction(context)) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var audioSelTick by remember { mutableStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    SettingsGroup("聆听") {
        QuickRow("仅聆听与对话") { showListenSheet = true }
        QuickRow("定时开关聆听") { showScheduleSheet = true }
        QuickRow("耳机功能键") { showBtSheet = true }
    }
    SettingsGroup("声音") {
        QuickRow("音频设备") { showAudioSheet = true }
        QuickRow("自定义音色") { showVoiceSheet = true }
    }
    SettingsGroup("AI 与数据") {
        QuickRow("AI 与接口") { showAiCard = true }
        QuickRow("Operit 接入") { showOperit = true }
        QuickRow("每日数据导出") { if (android.os.Environment.isExternalStorageManager()) {
                android.widget.Toast.makeText(context, "已授权：每日导出到 Download/yunque_export，由夸克同步上云", android.widget.Toast.LENGTH_LONG).show()
            } else {
                runCatching {
                    context.startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + context.packageName)))
                }.onFailure {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                android.widget.Toast.makeText(context, "请允许\"访问所有文件\"，每日导出才能写入 Download", android.widget.Toast.LENGTH_LONG).show()
            } }
    }
    SettingsGroup("外观与系统") {
        QuickRow("主题") { showTheme = true }
        QuickRow("通知栏控制") { showNotifSheet = true }
        QuickRow("后台保活指引") { showKeepAliveSheet = true }
    }
    if (showScheduleSheet) {
        ScheduleSheet(context, onDismiss = { showScheduleSheet = false })
    }
    if (showBtSheet) {
        YunqueBottomSheet(onDismiss = { showBtSheet = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("耳机功能键", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "蓝牙耳机的语音助手功能键按下时执行的动作（系统语音助手长按/快捷键唤起同理）：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    btActionSel = Store.BT_TOGGLE_LISTEN
                    Store.saveBtAction(context, Store.BT_TOGGLE_LISTEN)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (btActionSel == Store.BT_TOGGLE_LISTEN) "✓ 开始/停止聆听（默认）" else "开始/停止聆听（默认）")
                }
                TextButton(onClick = {
                    btActionSel = Store.BT_INTERRUPT
                    Store.saveBtAction(context, Store.BT_INTERRUPT)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (btActionSel == Store.BT_INTERRUPT) "✓ 打断播报" else "打断播报")
                }
                TextButton(onClick = {
                    btActionSel = Store.BT_SPEAK_NOW
                    Store.saveBtAction(context, Store.BT_SPEAK_NOW)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (btActionSel == Store.BT_SPEAK_NOW) "✓ 云雀请发言" else "云雀请发言")
                }
                Text(
                    "\"云雀请发言\"：让云雀结合当下语境说一句话——适用于它选择沉默、而你确实需要信息的时刻。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (showKeepAliveSheet) {
        val isXiaomi = remember { android.os.Build.MANUFACTURER.contains("Xiaomi", true) || android.os.Build.MANUFACTURER.contains("Redmi", true) }
        val ctx = LocalContext.current
        var whitelist by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            whitelist = runCatching {
                androidx.core.content.ContextCompat.getSystemService(ctx, android.os.PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false
            }.getOrDefault(false)
        }
        YunqueBottomSheet(onDismiss = { showKeepAliveSheet = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("后台保活指引", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    (if (isXiaomi) "你的设备是小米/红米，系统对后台较激进。请完成以下三步（每步一次即可）：\n\n" +
                        "1. 省电策略：设置 → 应用设置 → 应用管理 → 云雀 → 省电策略 → 选\"无限制\"\n" +
                        "2. 自启动：同一页面 → 打开\"自启动\"开关（被杀后自动恢复聆听依赖它）\n" +
                        "3. 后台锁定：最近任务里长按云雀卡片 → 锁定\n\n"
                        else "请允许云雀忽略电池优化（设置 → 应用 → 云雀 → 电池 → 不受限制）。\n\n") +
                        "代码层保障：服务被系统杀死后会自动重启并恢复聆听，无需手动干预。\n" +
                        "电池优化白名单状态：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (whitelist) "✓ 已在电池优化白名单" else "✗ 不在电池优化白名单",
                    color = if (whitelist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                TextButton(onClick = {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }, modifier = Modifier.fillMaxWidth()) { Text("打开电池优化设置") }
                if (isXiaomi) {
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(android.content.Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                            putExtra("extra_pkgname", ctx.packageName)
                        }) }.onFailure {
                            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))) }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("打开云雀应用信息（省电策略/自启动）") }
                }
            }
        }
    }
    if (showAudioSheet) {
        val am = context.getSystemService(android.media.AudioManager::class.java)
        var inSel by remember { mutableStateOf(Store.audioInputDevice(context)) }
        var outSel by remember { mutableStateOf(Store.audioOutput(context)) }
        var micSel by remember { mutableStateOf(Store.micSource(context)) }
        var btAuto by remember { mutableStateOf(Store.btAutoSwitchEnabled(context)) }
        var profiles by remember { mutableStateOf(Store.btProfiles(context)) }
        var editingAddr by remember { mutableStateOf<String?>(null) }
        var btDevices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
        val btPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) btDevices = bondedBtDevices(context)
        }
        LaunchedEffect(btAuto) {
            if (btAuto && btDevices.isEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= 31 &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) btPermLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                else btDevices = bondedBtDevices(context)
            }
        }
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
                Text("拾音模式", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "语音优化（默认）：系统降噪增强人声，识别最稳；\n原始收录：不做任何处理，保留完整环境声与声音细节（声纹特征更丰富，嘈杂环境可尝试）；\n标准：介于两者之间。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(Store.MIC_VOICE_RECOGNITION to "语音优化", Store.MIC_RAW to "标准", Store.MIC_UNPROCESSED to "原始收录").forEach { (v, label) ->
                        FilterChip(
                            selected = micSel == v,
                            onClick = {
                                micSel = v
                                Store.saveMicSource(context, v)
                                context.startService(Intent(context, AlwaysOnListeningService::class.java).apply {
                                    action = AlwaysOnListeningService.ACTION_APPLY_AUDIO
                                })
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
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
                Spacer(Modifier.size(10.dp))
                Text("连接蓝牙自动切换", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "总开关关闭时，任何连接事件都不触发切换。可为每台已配对设备设置连上后自动执行的动作；未配置的设备不动作。断开时不做恢复（由你或定时规则决定）。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("总开关", modifier = Modifier.weight(1f))
                    Switch(checked = btAuto, onCheckedChange = { btAuto = it; Store.saveBtAutoSwitch(context, it) })
                }
                if (btAuto) {
                    btDevices.forEach { dev ->
                        val addr = dev.first; val dname = dev.second
                        val prof = profiles[addr]
                        TextButton(onClick = { editingAddr = if (editingAddr == addr) null else addr }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (editingAddr == addr) "▾ " else "▸ ") + dname + (if (prof != null && (prof.listenAction != "none" || prof.mic != "keep")) " ●" else ""))
                        }
                        if (editingAddr == addr) {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))) {
                                Column(Modifier.padding(10.dp)) {
                                    var pa by remember(addr) { mutableStateOf(profiles[addr] ?: Store.BtProfile()) }
                                    Text("聆听动作", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("none" to "不动作", "start_normal" to "开启·正常", "start_listen_only" to "开启·仅聆听", "stop" to "停止聆听").forEach { (v, label) ->
                                            FilterChip(selected = pa.listenAction == v, onClick = { pa = pa.copy(listenAction = v) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                                        }
                                    }
                                    Text("麦克风", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("keep" to "不动作", "device" to "此设备麦克风", "phone" to "手机麦克风").forEach { (v, label) ->
                                            FilterChip(selected = pa.mic == v, onClick = { pa = pa.copy(mic = v) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { Store.removeBtProfile(context, addr); profiles = Store.btProfiles(context); editingAddr = null }, modifier = Modifier.weight(1f)) { Text("清除") }
                                        Button(onClick = { Store.saveBtProfile(context, addr, pa); profiles = Store.btProfiles(context); editingAddr = null }, modifier = Modifier.weight(1f)) { Text("保存") }
                                    }
                                }
                            }
                        }
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
                        val label = Store.llmLabel(p)
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
                    label = { Text(Store.llmLabel(aiProviderEdit) + " API Key") },
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


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(context: android.content.Context, onDismiss: () -> Unit) {
    var rules by remember { mutableStateOf(ScheduleStore.loadRules(context)) }
    var editing by remember { mutableStateOf<ScheduleRule?>(null) }
    var editingNew by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ScheduleRule?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var importPreview by remember { mutableStateOf<List<com.halo.yunquevoice.voice.IcsParser.ParsedCourse>?>(null) }
    var clearCoursesTarget by remember { mutableStateOf(false) }

    val icsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val courses = com.halo.yunquevoice.voice.IcsParser.parse(text)
                if (courses.isEmpty()) {
                    android.widget.Toast.makeText(context, "未解析到课程事件", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    importPreview = courses
                }
            }.onFailure {
                android.widget.Toast.makeText(context, "解析失败：" + (it.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun persist(next: List<ScheduleRule>) {
        rules = next.toMutableList()
        ScheduleStore.saveRules(context, next)
        refreshTick++
    }
    fun fmtTime(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    fun fmtDays(days: Set<Int>): String {
        if (days.size == 7) return "每天"
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        return (1..7).filter { it in days }.joinToString("") { names[it - 1] }
    }
    fun actionLabel(r: ScheduleRule) = when (r.action) {
        "stop" -> "停止聆听"
        "listen_only" -> "仅聆听"
        "auto_restore" -> "自动恢复"
        else -> "开启聆听"
    }

    val sheetScroll = rememberScrollState()
    YunqueBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(20.dp).navigationBarsPadding().verticalScroll(sheetScroll)) {
            Text("定时开关聆听", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Button(
                onClick = { icsLauncher.launch(arrayOf("text/calendar", "text/*", "application/*")) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) { Text("导入课程表（.ics）→ 上课自动静音") }

            val next = remember(refreshTick) { runCatching { ScheduleStore.nextTrigger(context) }.getOrNull() }
            if (next != null) {
                val t = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(next.first))
                Text(
                    "下次自动动作：$t " + (if (next.second == ScheduleStore.TYPE_START) "开启" else "停止") + "聆听",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Text("暂无生效规则", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (!ScheduleStore.canExact(context)) {
                Text("⚠️ 未授予精确闹钟权限，执行可能有几分钟误差", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            // 定时规则（手动）
            val manualRules = rules.filter { !it.isCourse }
            if (manualRules.isNotEmpty() || courseRulesOf(rules).isEmpty()) {
                Text("定时规则", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            manualRules.forEach { r ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editing = r; editingNew = false },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fmtTime(r.startMin) + " - " + fmtTime(r.endMin) + " · " + actionLabel(r), fontWeight = FontWeight.Bold)
                            Text(fmtDays(r.days), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = r.enabled, onCheckedChange = { on ->
                            persist(rules.map { if (it.id == r.id) it.copy(enabled = on) else it })
                        })
                    }
                }
            }

            // 课程表（独立分区）
            val courseRules = rules.filter { it.isCourse }
            if (courseRules.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, start = 4.dp)) {
                    Text(
                        "课程表 · 上课静音（${courseRules.size}条）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { clearCoursesTarget = true }) { Text("清空") }
                }
                courseRules.forEach { r ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editing = r; editingNew = false },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("🔇 " + fmtTime(r.startMin) + " - " + fmtTime(r.endMin) + " " + r.label, fontWeight = FontWeight.Bold)
                                Text(fmtDays(r.days) + " · 上课静音", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = r.enabled, onCheckedChange = { on ->
                                persist(rules.map { if (it.id == r.id) it.copy(enabled = on) else it })
                            })
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    android.util.Log.i("YunqueVoice", "添加规则点击")
                    editing = ScheduleRule(id = System.currentTimeMillis(), startMin = 8 * 60, endMin = 23 * 60, days = (1..7).toSet(), listenState = "normal")
                    editingNew = true
                }, modifier = Modifier.weight(1f)) { Text("+ 添加规则") }
                TextButton(onClick = {
                    persist(rules + ScheduleRule(id = System.currentTimeMillis(), startMin = 8 * 60, endMin = 23 * 60, days = (1..7).toSet(), listenState = "normal"))
                }, modifier = Modifier.weight(1f)) { Text("模板：每天 8-23") }
                TextButton(onClick = {
                    persist(rules + ScheduleRule(id = System.currentTimeMillis(), startMin = 8 * 60, endMin = 18 * 60, days = setOf(1, 2, 3, 4, 5), listenState = "normal"))
                }, modifier = Modifier.weight(1f)) { Text("模板：上学日 8-18") }
            }
        }
    }

    // 规则编辑弹层（独立于 sheet，全屏聚焦）
    editing?.let { e ->
        EditRuleDialog(rule = e, isNew = editingNew, onDismiss = { editing = null; pickerFor = null }) { saved ->
            persist(if (editingNew) rules + saved else rules.map { if (it.id == saved.id) saved else it })
        }
    }

    // 课程表导入
    importPreview?.let { courses ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("导入课程表") },
            text = {
                Column {
                    Text("解析到 ${courses.size} 个课程时段，将生成对应的“上课静音”规则：")
                    Spacer(Modifier.size(6.dp))
                    courses.take(6).forEach { c ->
                        Text("· ${c.name} " + listOf("一","二","三","四","五","六","日")[c.dayIso-1] + " %02d:%02d-%02d:%02d".format(c.startMin/60, c.startMin%60, c.endMin/60, c.endMin%60), style = MaterialTheme.typography.labelSmall)
                    }
                    if (courses.size > 6) Text("… 等共 ${courses.size} 条", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val base = System.currentTimeMillis()
                    val rulesNew = courses.mapIndexed { i, c ->
                        ScheduleRule(
                            id = base + i, startMin = c.startMin, endMin = c.endMin,
                            days = setOf(c.dayIso), silent = true, label = c.name, source = "course"
                        )
                    }
                    persist(rules + rulesNew)
                    importPreview = null
                    android.widget.Toast.makeText(context, "已导入 ${rulesNew.size} 条上课静音规则", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text("取消") } }
        )
    }

    if (clearCoursesTarget) {
        ConfirmDeleteDialog(
            text = "清空全部课程表导入的静音规则？\n（手动创建的定时规则不受影响）",
            onConfirm = { persist(rules.filter { !it.isCourse }); clearCoursesTarget = false },
            onDismiss = { clearCoursesTarget = false }
        )
    }

    deleteTarget?.let { r ->
        ConfirmDeleteDialog(
            text = "删除这条定时规则？\n" + fmtTime(r.startMin) + "-" + fmtTime(r.endMin) + " " + fmtDays(r.days),
            onConfirm = { persist(rules.filter { it.id != r.id }); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

private fun courseRulesOf(rules: List<ScheduleRule>) = rules.filter { it.isCourse }

/** 规则编辑：独立全宽弹层（时间轴 + 四态行为 + 星期）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditRuleDialog(
    rule: ScheduleRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ScheduleRule) -> Unit
) {
    var e by remember { mutableStateOf(rule) }
    var pickerFor by remember { mutableStateOf<String?>(null) }
    fun fmtTime(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加规则" else "编辑规则") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("时段行为", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Triple("normal", "开启聆听", "开始开启 · 结束停止"),
                        Triple("stop", "停止聆听", "开始停止 · 结束自动恢复（上课/会议）"),
                        Triple("listen_only", "仅聆听", "开始切仅聆听 · 结束停并恢复"),
                        Triple("auto_restore", "自动恢复", "结束恢复到开始前的聆听状态")
                    ).forEach { (v, label, desc) ->
                        Card(
                            Modifier.fillMaxWidth().clickable {
                                e = when (v) {
                                    "stop" -> e.copy(silent = true, listenState = "")
                                    "listen_only" -> e.copy(silent = false, listenState = "listen_only")
                                    "auto_restore" -> e.copy(silent = true, listenState = "auto_restore")
                                    else -> e.copy(silent = false, listenState = "normal")
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (e.action == v) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text((if (e.action == v) "✓ " else "") + label, fontWeight = FontWeight.Bold)
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { pickerFor = if (pickerFor == "start") null else "start" }, modifier = Modifier.weight(1f)) {
                        Text((if (pickerFor == "start") "✓ " else "") + "开始 " + fmtTime(e.startMin))
                    }
                    TextButton(onClick = { pickerFor = if (pickerFor == "end") null else "end" }, modifier = Modifier.weight(1f)) {
                        Text((if (pickerFor == "end") "✓ " else "") + "结束 " + fmtTime(e.endMin))
                    }
                }
                if (pickerFor != null) {
                    val initH = if (pickerFor == "start") e.startMin / 60 else e.endMin / 60
                    val initM = if (pickerFor == "start") e.startMin % 60 else e.endMin % 60
                    val tp = rememberTimePickerState(initialHour = initH, initialMinute = initM, is24Hour = true)
                    androidx.compose.material3.TimePicker(state = tp)
                    TextButton(onClick = {
                        e = if (pickerFor == "start") e.copy(startMin = tp.hour * 60 + tp.minute) else e.copy(endMin = tp.hour * 60 + tp.minute)
                        pickerFor = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("确定时间") }
                }
                Text("重复", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { idx, name ->
                        val day = idx + 1
                        FilterChip(
                            selected = day in e.days,
                            onClick = {
                                val days = e.days.toMutableSet()
                                if (day in days) days.remove(day) else days.add(day)
                                e = e.copy(days = days)
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = e.days.isNotEmpty(), onClick = { onSave(e); onDismiss() }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}


/** 状态徽章：呼吸动画状态点 + 胶囊底，聆听时"活着"的感觉。 */
@Composable
private fun StatusBadge(text: String, active: Boolean, speaking: Boolean = false) {
    val dotColor = when {
        speaking -> MaterialTheme.colorScheme.primary
        active -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "breathAlpha"
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                (if (speaking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .copy(alpha = 0.7f)
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dotColor.copy(alpha = if (active) breath else 0.9f))
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/** 设置分组：标题 + 组卡片（iOS 风格 inset list）。 */
@Composable
private fun SettingsGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val glass17 = Store.themeMode(LocalContext.current) == Store.THEME_GLASS17
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
        )
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (glass17) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(content = content)
        }
    }
}

/** 组内设置行（无卡背景，行间分隔线）。 */
@Composable
private fun QuickRow(label: String, last: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!last) androidx.compose.material3.HorizontalDivider(
            Modifier.padding(start = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}


private fun bondedBtDevices(context: android.content.Context): List<Pair<String, String>> =
    runCatching {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        adapter.bondedDevices.map { it.address to (runCatching { it.name }.getOrDefault(it.address.takeLast(5))) }
            .sortedBy { it.second }
    }.getOrDefault(emptyList())
