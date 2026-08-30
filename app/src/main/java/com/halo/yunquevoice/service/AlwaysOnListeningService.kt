package com.halo.yunquevoice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.halo.yunquevoice.memory.ConversationRecord
import com.halo.yunquevoice.memory.MemoryDb
import com.halo.yunquevoice.memory.RelationshipExtractor
import com.halo.yunquevoice.memory.SpeakerEngine
import com.halo.yunquevoice.memory.SpeakerProfile
import com.halo.yunquevoice.memory.WorkingMemory
import com.halo.yunquevoice.ui.MainShellComposeActivity
import com.halo.yunquevoice.voice.BailianMemory
import com.halo.yunquevoice.voice.MemoryUploader
import com.halo.yunquevoice.voice.DashScopeFiletrans
import com.halo.yunquevoice.voice.DiarizedSentence
import com.halo.yunquevoice.voice.DashScopeUpload
import com.halo.yunquevoice.voice.InterruptionRecord
import com.halo.yunquevoice.voice.LocalInfo
import com.halo.yunquevoice.voice.SoundCue
import com.halo.yunquevoice.voice.Store
import com.halo.yunquevoice.voice.VoiceMvpClient
import com.halo.yunquevoice.voice.VoiceMvpLog
import com.halo.yunquevoice.voice.WavUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.max

/**
 * 全天候持续聆听服务：
 * - 麦克风从 start 读到 stop，中间不关闭
 * - 本地能量 VAD 切分语音片段
 * - 每段做 ASR + DeepSeek 决策，只在值得时说
 * - 支持 App/通知栏打断播报，并记录漏听起点
 */
class AlwaysOnListeningService : Service() {

    companion object {
        const val ACTION_START = "com.halo.yunquevoice.action.START_LISTEN"
        const val ACTION_STOP = "com.halo.yunquevoice.action.STOP_LISTEN"
        const val ACTION_INTERRUPT = "com.halo.yunquevoice.action.INTERRUPT"
        const val ACTION_SET_LISTEN_ONLY = "com.halo.yunquevoice.action.SET_LISTEN_ONLY"
        const val ACTION_STATUS = "com.halo.yunquevoice.action.STATUS"
        const val ACTION_TEST_TRANSCRIPT = "com.halo.yunquevoice.action.TEST_TRANSCRIPT"
        const val ACTION_TEST_UPLOAD = "com.halo.yunquevoice.action.TEST_UPLOAD"
        const val ACTION_TEST_FILETRANS = "com.halo.yunquevoice.action.TEST_FILETRANS"
        const val ACTION_TEST_RELATIONS = "com.halo.yunquevoice.action.TEST_RELATIONS"
        const val ACTION_TEST_COMPACTION = "com.halo.yunquevoice.action.TEST_COMPACTION"
        const val ACTION_TEST_DIAR = "com.halo.yunquevoice.action.TEST_DIAR"
        const val ACTION_APPLY_AUDIO = "com.halo.yunquevoice.action.APPLY_AUDIO"
        const val ACTION_TEST_WIPE = "com.halo.yunquevoice.action.TEST_WIPE"

        const val CHANNEL_ID = "always_on"
        const val NOTIF_INTERRUPT_ID = 1002
        const val NOTIF_ID = 1001
        const val NOTIF_CHAT_ID = 1003
        const val SAMPLE_RATE = 16000

        @Volatile
        var isRunning = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val segmentQueue = LinkedBlockingQueue<ByteArray>()
    private val recentChunks = ArrayDeque<ByteArray>()
    private val memoryDb: MemoryDb by lazy { MemoryDb(applicationContext) }
    @Volatile private var pendingMemoryCount = 0

    private var notificationManager: NotificationManager? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false
    @Volatile private var processing = false

    private data class PendingSegment(
        val pcm: ByteArray,
        val text: String,
        val convId: Long,
        val speakerId: String
    )

    private val segmentBuffer = ByteArrayOutputStream()
    private val preRoll = ArrayDeque<ByteArray>()
    private val pendingSegments = mutableListOf<PendingSegment>()

    @Volatile private var compacting = false
    @Volatile private var noiseFloor = 500.0
    @Volatile private var retrievalMissStreak = 0
    @Volatile private var diarizationRunning = false
    @Volatile private var inSpeech = false
    @Volatile private var silenceMs = 0L

    private var currentPlayer: MediaPlayer? = null
    private var currentTtsFile: File? = null
    private var currentTtsPlayStartMs = 0L
    private var currentAssistantConvId = -1L
    @Volatile private var speaking = false
    private var currentTtsText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        VoiceMvpLog.init(this)
        when (intent?.action) {
            ACTION_START -> {
                if (!capturing) SoundCue.playStart()
                startAsForeground()
                startCapture()
                YunqueApiServer.start(this)
                scope.launch {
                    runCatching { BailianMemory.flushOutbox(this@AlwaysOnListeningService) }
                    runCatching { maybeCompact() }
                }
                broadcastStatus("listening")
            }
            ACTION_TEST_COMPACTION -> {
                val cutoffHours = intent?.getLongExtra("cutoff_hours", 24L) ?: 24L
                scope.launch {
                    val deepKey = Store.llmActiveKey(this@AlwaysOnListeningService)
                    runCatching {
                        WorkingMemory.compact(this@AlwaysOnListeningService, memoryDb, deepKey, "手动", cutoffHours)
                    }
                    VoiceMvpLog.i("WORKMEM", "手动压缩触发完成")
                }
            }
            ACTION_STOP -> {
                SoundCue.playStop()
                YunqueApiServer.stop()
                stopEverything()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_APPLY_AUDIO -> {
                // 音频输入切换即生效：采集中的话重启采集线程
                if (capturing) {
                    stopCapture()
                    startCapture()
                }
            }
            ACTION_INTERRUPT -> {
                interruptPlayback()
            }
            ACTION_SET_LISTEN_ONLY -> {
                val enabled = intent?.getBooleanExtra("enabled", !Store.listenOnlyEnabled(this))
                    ?: !Store.listenOnlyEnabled(this)
                Store.saveListenOnly(this, enabled)
                VoiceMvpLog.i("SERVICE", if (enabled) "仅聆听模式：只听不说" else "仅聆听模式关闭：恢复播报")
                val nm = getSystemService(NotificationManager::class.java)
                if (enabled) {
                    nm?.cancel(NOTIF_INTERRUPT_ID)
                } else if (Store.notificationInterruptEnabled(this)) {
                    postInterruptNotification()
                }
                updateNotification(listenOnlyStatusText(), interrupting = true)
                broadcastStatus("listening")
            }
            ACTION_TEST_TRANSCRIPT -> {
                val text = intent?.getStringExtra("text") ?: "云雀，你觉得这件事怎么办？"
                VoiceMvpLog.i("SERVICE", "收到测试旁听文本：$text")
                // 测试钩子：与真实片段同一条上云链路（噪声门/verbatim/簇合并）
                MemoryUploader.enqueue(this, memoryDb, text, "主人")
                scope.launch { handleTranscriptText(text) }
            }
            ACTION_TEST_DIAR -> {
                // 测试钩子：texts 用 | 分隔，clusters 用 , 分隔（与 texts 一一对应的批次内簇号）
                val texts = intent?.getStringExtra("texts")?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() }
                val clusters = intent?.getStringExtra("clusters")?.split(",")?.map { it.trim().toIntOrNull() ?: 0 }
                if (texts != null && clusters != null && texts.size == clusters.size && texts.size >= 2) {
                    scope.launch {
                        val batch = texts.map { t ->
                            val sp = SpeakerEngine.recognize(memoryDb, ByteArray(32000))
                            val convId = memoryDb.addConversation(
                                ConversationRecord(0, System.currentTimeMillis(), sp.id, sp.name, t, "other")
                            )
                            PendingSegment(ByteArray(32000), t, convId, sp.id)
                        }
                        val sentences = clusters.mapIndexed { i, c ->
                            DiarizedSentence(i * 1000L, i * 1000L + 900, batch[i].text, c)
                        }
                        applyDiarization(batch, sentences)
                        VoiceMvpLog.i("DIAR", "测试分离完成：${texts.size} 句 / ${clusters.toSet().size} 簇")
                    }
                }
            }
            ACTION_TEST_WIPE -> {
                // 记忆内容整体重置：清对话时间线/工作记忆/画像/关于我/关系/统计/漏听/outbox，
                // extra speakers=true 时连声纹档案一起清。云端节点由外部脚本另行删除。
                val withSpeakers = intent?.getBooleanExtra("speakers", false) ?: false
                memoryDb.wipeMemoryContent(speakersToo = withSpeakers)
                Store.clearInterruptions(this)
                BailianMemory.clearOutbox(this)
                VoiceMvpLog.i("SERVICE", "记忆内容已全部清空（含声纹=$withSpeakers；角色卡与设置保留）")
            }
            ACTION_TEST_UPLOAD -> {
                val key = Store.dashScopeKey(this)
                val file = File(cacheDir, "yunque_proactive.wav").takeIf { it.exists() }
                    ?: File(cacheDir, "yunque_segment.wav")
                scope.launch {
                    runCatching {
                        DashScopeUpload.upload(key, file, "qwen-audio-3.0-asr-flash-filetrans")
                    }.onSuccess {
                        VoiceMvpLog.i("SERVICE", "测试上传成功 ossUrl=$it")
                    }.onFailure {
                        VoiceMvpLog.e("SERVICE", "测试上传失败: ${it.message}", it)
                    }
                }
            }
            ACTION_TEST_FILETRANS -> {
                val key = Store.dashScopeKey(this)
                val file = File(cacheDir, "yunque_proactive.wav").takeIf { it.exists() }
                    ?: File(cacheDir, "yunque_segment.wav")
                scope.launch {
                    runCatching {
                        val oss = DashScopeUpload.upload(key, file, "qwen-audio-3.0-asr-flash-filetrans")
                        DashScopeFiletrans.transcribe(key, oss, Store.workspaceId(this@AlwaysOnListeningService), true)
                    }.onSuccess { sentences ->
                        VoiceMvpLog.i("SERVICE", "说话人分离完成，句子 ${sentences.size} 条")
                        sentences.forEach {
                            VoiceMvpLog.i("SERVICE", "speaker${it.speakerId} ${it.beginMs}-${it.endMs}ms: ${it.text}")
                        }
                    }.onFailure {
                        VoiceMvpLog.e("SERVICE", "说话人分离失败: ${it.message}", it)
                    }
                }
            }
            ACTION_TEST_RELATIONS -> {
                val key = Store.llmActiveKey(this)
                scope.launch {
                    RelationshipExtractor.extract(memoryDb, key)
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        VoiceMvpLog.i("SERVICE", "onTaskRemoved: 用户从最近任务移除，播放关闭提示")
        SoundCue.playStop()
        stopEverything()
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        // 正常关闭/系统销毁都会尽量播一次关闭提示；重复调用由 SoundCue 去重
        SoundCue.playStop()
        YunqueApiServer.stop()
        stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    /* ───────────── 前台通知 ───────────── */

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        notificationManager = nm
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "持续聆听", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = buildNotification("云雀正在聆听", interrupting = true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        isRunning = true
        if (Store.notificationInterruptEnabled(this) && !Store.listenOnlyEnabled(this)) {
            postInterruptNotification()
        }
    }

    private fun listenOnlyStatusText(): String =
        if (Store.listenOnlyEnabled(this)) "云雀正在聆听（仅聆听，保持安静）" else "云雀正在聆听"

    private fun postInterruptNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val interrupt = PendingIntent.getService(
            this, 3,
            Intent(this, AlwaysOnListeningService::class.java).setAction(ACTION_INTERRUPT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("云雀·私人助理")
            .setContentText("点击打断播报")
            .setContentIntent(interrupt)
            .setOngoing(true)
            .build()
        nm.notify(NOTIF_INTERRUPT_ID, notif)
    }

    private fun buildNotification(status: String, interrupting: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainShellComposeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val interrupt = PendingIntent.getService(
            this, 1,
            Intent(this, AlwaysOnListeningService::class.java).setAction(ACTION_INTERRUPT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, AlwaysOnListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val listenOnly = PendingIntent.getService(
            this, 4,
            Intent(this, AlwaysOnListeningService::class.java)
                .setAction(ACTION_SET_LISTEN_ONLY)
                .putExtra("enabled", !Store.listenOnlyEnabled(this)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val listenOnlyOn = Store.listenOnlyEnabled(this)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("云雀·私人助理")
            .setContentText(
                when {
                    listenOnlyOn -> "仅聆听中（保持安静）"
                    Store.notificationControlEnabled(this) -> "点击停止聆听"
                    else -> status
                }
            )
            .setContentIntent(if (Store.notificationControlEnabled(this)) stop else open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "打断", interrupt)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止聆听", stop)
            .addAction(
                if (listenOnlyOn) android.R.drawable.ic_media_play else android.R.drawable.ic_lock_silent_mode_off,
                if (listenOnlyOn) "恢复播报" else "仅聆听",
                listenOnly
            )
        return builder.build()
    }

    private fun updateNotification(status: String, interrupting: Boolean) {
        notificationManager?.notify(NOTIF_ID, buildNotification(status, interrupting))
    }

    /* ───────────── 持续采集 ───────────── */

    private fun startCapture() {
        if (capturing) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            VoiceMvpLog.e("SERVICE", "AudioRecord getMinBufferSize=$minBuf")
            return
        }
        // 音频输入源：按用户选择的物理设备（原始名展示），蓝牙 SCO 麦仍需激活通话通道
        val sel = Store.audioInputDevice(this)
        val am0 = getSystemService(AudioManager::class.java)
        val preferred = findAudioDevice(am0, isInput = true, sel = sel)
        val scoWanted = preferred?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (sel == "builtin" && Store.audioInput(this) == Store.AUDIO_EARPHONE)
        val source = if (scoWanted) MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else MediaRecorder.AudioSource.VOICE_RECOGNITION
        if (scoWanted) {
            selectBluetoothInput()
        } else {
            clearBluetoothInput()
        }
        val bufferSize = max(minBuf, SAMPLE_RATE * 2 * 2)
        val record = AudioRecord(
            source,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            VoiceMvpLog.e("SERVICE", "AudioRecord init fail state=${record.state}")
            record.release()
            return
        }
        runCatching { if (preferred != null && !scoWanted) record.preferredDevice = preferred }
        audioRecord = record
        capturing = true
        record.startRecording()
        val inLabel = preferred?.productName?.ifBlank { null } ?: if (scoWanted) "耳机麦克风(SCO)" else "手机麦克风"
        VoiceMvpLog.i("SERVICE", "持续采集已启动 buffer=$bufferSize 输入=$inLabel")
        captureThread = Thread {
            val buf = ByteArray(bufferSize)
            while (capturing) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) {
                    processAudioFrame(buf.copyOf(n), n)
                }
            }
        }.apply { name = "yunque-capture"; start() }
    }

    private fun selectBluetoothInput() {
        runCatching {
            val am = getSystemService(AudioManager::class.java)
            if (Build.VERSION.SDK_INT >= 31) {
                val sco = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (sco != null) {
                    am.setCommunicationDevice(sco)
                    VoiceMvpLog.i("SERVICE", "已选择 BT SCO 输入: ${sco.productName}")
                } else {
                    VoiceMvpLog.w("SERVICE", "未发现 BT SCO 通信设备，使用默认输入")
                }
            } else {
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                VoiceMvpLog.i("SERVICE", "已通过 startBluetoothSco 启用 BT SCO")
            }
        }.onFailure {
            VoiceMvpLog.w("SERVICE", "selectBluetoothInput failed: ${it.message}")
        }
    }

    /** 手机麦克风模式：确保没有残留的通话路由，蓝牙媒体保持 A2DP 高音质。 */
    private fun clearBluetoothInput() {
        runCatching {
            val am = getSystemService(AudioManager::class.java)
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
            }
            VoiceMvpLog.i("SERVICE", "已切回手机麦克风输入（蓝牙媒体保持高音质）")
        }.onFailure {
            VoiceMvpLog.w("SERVICE", "clearBluetoothInput failed: ${it.message}")
        }
    }

    /** 按 "t<type>:a<address>" 或 "builtin"/"auto"/"speaker" 解析系统音频设备。 */
    private fun findAudioDevice(am: AudioManager, isInput: Boolean, sel: String): AudioDeviceInfo? {
        if (!sel.startsWith("t") || !sel.contains(":a")) return null
        return runCatching {
            val type = sel.substringAfter('t').substringBefore(":a").toInt()
            val addr = sel.substringAfter(":a")
            val flags = if (isInput) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS
            am.getDevices(flags).firstOrNull { it.type == type && it.address == addr }
        }.getOrNull()
    }

    /** TTS 输出设备：auto 跟随系统；speaker 扬声器；t<type>:a<addr> 指定设备（如蓝牙耳机 A2DP）。 */
    private fun resolveOutputDevice(): AudioDeviceInfo? {
        return when (val sel = Store.audioOutput(this)) {
            "auto" -> null
            "speaker" -> getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            else -> findAudioDevice(getSystemService(AudioManager::class.java), isInput = false, sel = sel)
        }
    }

    private fun stopCapture() {
        capturing = false
        captureThread?.let {
            runCatching { it.join(1000) }
        }
        captureThread = null
        audioRecord?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        audioRecord = null
        VoiceMvpLog.i("SERVICE", "采集已停止")
    }

    private fun processAudioFrame(frame: ByteArray, len: Int) {
        // 说话时暂不把 TTS 回声当作旁听内容
        if (speaking) {
            voiceMvpLog("SERVICE", "speaking，跳过 VAD")
            return
        }

        // 将大块按 ~100ms 切小，便于 VAD
        val frameSize = SAMPLE_RATE * 2 / 10
        var offset = 0
        while (offset < len) {
            val end = minOf(offset + frameSize, len)
            val chunk = frame.copyOfRange(offset, end)
            offset = end

            recentChunks.addLast(chunk)
            while (recentChunks.size > 30) recentChunks.removeFirst()

            val rms = rms(chunk)
            // 自适应噪声地板：无语音时缓慢跟踪环境底噪，阈值随环境抬高，
            // 避免"持续底噪略过固定阈值→段永远不结束→缓冲无限增长"
            if (!inSpeech) noiseFloor = noiseFloor * 0.999 + rms * 0.001
            val isVoice = rms > max(500.0, noiseFloor * 4)

            if (isVoice) {
                if (!inSpeech) {
                    inSpeech = true
                    silenceMs = 0
                    segmentBuffer.reset()
                    preRoll.clear()
                    // 把刚才 0.3 秒的预卷补进去，避免吞掉开头
                    for (c in recentChunks.takeLast(3)) {
                        segmentBuffer.write(c)
                    }
                }
                segmentBuffer.write(chunk)
                silenceMs = 0
                // 硬顶：单段最长 30 秒强制切段。持续环境噪声（电视/马路/房间底噪）会让
                // 600ms 静音判定永远等不到，缓冲会一直涨到 OOM
                if (segmentBuffer.size() >= SAMPLE_RATE * 2 * 30) {
                    val seg = segmentBuffer.toByteArray()
                    segmentBuffer.reset()
                    preRoll.clear()
                    if (seg.size > SAMPLE_RATE * 2 / 10 * 6) {
                        segmentQueue.offer(seg)
                        drainQueue()
                    }
                }
            } else if (inSpeech) {
                // 尾部静音缓冲：仍写入，直到确定结束
                segmentBuffer.write(chunk)
                silenceMs += 100
                if (silenceMs >= 600) {
                    val seg = segmentBuffer.toByteArray()
                    inSpeech = false
                    segmentBuffer.reset()
                    preRoll.clear()
                    if (seg.size > SAMPLE_RATE * 2 / 10 * 6) {
                        segmentQueue.offer(seg)
                        drainQueue()
                    }
                }
            }
        }
    }

    private fun rms(bytes: ByteArray): Double {
        var sum = 0.0
        var i = 0
        while (i + 1 < bytes.size) {
            val s = (bytes[i].toInt() and 0xff) or (bytes[i + 1].toInt() shl 8)
            sum += (s * s).toDouble()
            i += 2
        }
        return Math.sqrt(sum / (i / 2).coerceAtLeast(1))
    }

    private fun drainQueue() {
        if (processing) return
        processing = true
        scope.launch {
            try {
                while (true) {
                    val seg = segmentQueue.poll() ?: break
                    handleSegment(seg)
                }
            } finally {
                processing = false
            }
        }
    }

    /* ───────────── ASR + 决策 + 播报 ───────────── */

    private suspend fun handleSegment(seg: ByteArray) {
        val deepKey = Store.llmActiveKey(this)
        val dashKey = Store.dashScopeKey(this)
        if (deepKey.isBlank() || dashKey.isBlank()) {
            VoiceMvpLog.w("SERVICE", "keys missing, skip segment")
            return
        }
        val wav = File(cacheDir, "yunque_segment.wav")
        WavUtil.writeWav(wav, seg, SAMPLE_RATE)
        val text = runCatching { VoiceMvpClient.transcribe(dashKey, wav) }
            .getOrElse {
                VoiceMvpLog.e("SERVICE", "ASR failed: ${it.message}", it)
                return
            }
        if (text.isBlank()) return
        val speaker = SpeakerEngine.recognize(memoryDb, seg)
        // 保存该说话人的最新录音样本，便于“身边的人”里试听辨认
        runCatching {
            val dir = File(cacheDir, "speaker_samples").apply { mkdirs() }
            wav.copyTo(File(dir, "${speaker.id}.wav"), overwrite = true)
        }
        val convId = memoryDb.addConversation(
            ConversationRecord(
                id = 0,
                ts = System.currentTimeMillis(),
                speakerId = speaker.id,
                speakerName = speaker.name,
                text = text,
                origin = "other"
            )
        )
        VoiceMvpLog.i("MEMORY", "已记录说话人「${speaker.name}」: ${text.take(80)}")
        WorkingMemory.stat(memoryDb, "segments_captured")
        // 云端记忆统一入口：噪声门 / 记住 verbatim / 会话簇合并
        MemoryUploader.enqueue(this, memoryDb, text, speaker.name)
        pendingSegments.add(PendingSegment(seg, text, convId, speaker.id))
        if (pendingSegments.size >= 4 && !diarizationRunning) {
            scope.launch { runCloudDiarization() }
        }
        pendingMemoryCount++
        if (pendingMemoryCount >= 5) {
            pendingMemoryCount = 0
            val key = Store.llmActiveKey(this)
            scope.launch {
                RelationshipExtractor.extract(memoryDb, key)
            }
        }
        handleTranscriptText(text)
    }

    /* ───────────── 工作记忆压缩 ───────────── */

    /** 惰性压缩：跨天第一句，或工作窗口超保险丝。并发用 compacting 标志挡。 */
    private suspend fun maybeCompact() {
        if (compacting) return
        val db = memoryDb
        val ctx = WorkingMemory.buildContext(db)
        val needDaily = WorkingMemory.shouldDailyCompact(db)
        val overFuse = WorkingMemory.overFuse(ctx)
        if (!needDaily && !overFuse) return
        if (overFuse) WorkingMemory.stat(db, "fuse_trips")
        compacting = true
        try {
            val deepKey = Store.llmActiveKey(this)
            if (deepKey.isBlank()) return
            WorkingMemory.compact(this, db, deepKey, if (overFuse) "保险丝" else "每日")
        } finally {
            compacting = false
        }
    }

    private fun buildMyInfo(): String {
        val voice = Store.myVoiceDesc(this)
        val profile = memoryDb.loadProfileDoc().content
        return listOfNotNull(
            "称呼：主人",
            voice.takeIf { it.isNotBlank() }?.let { "声音：$it" },
            profile.takeIf { it.isNotBlank() }?.let { "关于主人的画像：$it" }
        ).joinToString("\n")
    }

    /* ───────────── 云端说话人分离回写 ───────────── */

    /**
     * 云端分离回写（v0.9.0 重写）：
     * - 批内分人：云端 DIAR 对同一份音频聚类，是"这几句是否同一嗓音"的权威
     * - 跨批认人：身份锚定在簇内多数派的**本地声纹档案**上，绝不使用批次编号
     *   （旧逻辑用每批内部的编号当全局身份，跨批次必然撞号误合并）
     * 同簇内的少数派本地档案融合进多数派（特征加权混合），对话记录改写到锚点档案。
     */
    private suspend fun applyDiarization(batch: List<PendingSegment>, sentences: List<DiarizedSentence>) {
        val groups = sentences.groupBy { it.speakerId }
        for ((cloudId, sents) in groups) {
            val matched = sents.mapNotNull { s -> matchSegment(s.text, batch) }
            if (matched.isEmpty()) continue
            val byLocal = matched.groupingBy { it.speakerId }.eachCount()
            val target = byLocal.entries
                .mapNotNull { memoryDb.getSpeaker(it.key) }
                .filter { it.canonical }
                .maxByOrNull { byLocal[it.id] ?: 0 } ?: continue
            var merges = 0
            for (localId in byLocal.keys) {
                if (localId == target.id) continue
                memoryDb.getSpeaker(localId)?.let {
                    SpeakerEngine.mergeProfiles(memoryDb, it.id, target.id)
                    merges++
                }
            }
            for (m in matched) {
                memoryDb.updateConversationSpeaker(m.convId, target.id, target.name)
            }
            WorkingMemory.stat(memoryDb, "diar_clusters")
            WorkingMemory.stat(memoryDb, "diar_merges", merges.toDouble())
            VoiceMvpLog.i("DIAR", "簇$cloudId → ${target.name}（${matched.size} 句，合并 $merges 个本地档案）")
        }
    }

    private suspend fun runCloudDiarization() {
        if (diarizationRunning) return
        diarizationRunning = true
        try {
            val batch = pendingSegments.toList()
            pendingSegments.clear()
            if (batch.size < 2) return
            val dashKey = Store.dashScopeKey(this)
            val pcm = concatPcm(batch)
            val wav = File(cacheDir, "diar_batch.wav")
            WavUtil.writeWav(wav, pcm, SAMPLE_RATE)
            val oss = DashScopeUpload.upload(dashKey, wav, "qwen-audio-3.0-asr-flash-filetrans")
            val sentences = DashScopeFiletrans.transcribe(dashKey, oss, Store.workspaceId(this@AlwaysOnListeningService), true)
            VoiceMvpLog.i("DIAR", "云端分离句子 ${sentences.size} 条")
            applyDiarization(batch, sentences)
        } catch (e: Throwable) {
            VoiceMvpLog.e("DIAR", "云端分离回写失败: ${e.message}", e)
        } finally {
            diarizationRunning = false
        }
    }

    private fun concatPcm(segments: List<PendingSegment>): ByteArray {
        val silence = ByteArray(SAMPLE_RATE * 2 * 3 / 10) // 300ms 静音
        val total = segments.sumOf { it.pcm.size } + silence.size * (segments.size - 1).coerceAtLeast(0)
        val out = ByteArray(total)
        var pos = 0
        for ((i, seg) in segments.withIndex()) {
            seg.pcm.copyInto(out, pos)
            pos += seg.pcm.size
            if (i < segments.size - 1) {
                silence.copyInto(out, pos)
                pos += silence.size
            }
        }
        return out
    }

    private fun matchSegment(sentence: String, segments: List<PendingSegment>): PendingSegment? {
        val a = sentence.filter { !it.isWhitespace() }
        if (a.isBlank()) return null
        return segments.maxByOrNull { seg ->
            val b = seg.text.filter { !it.isWhitespace() }
            val left = a.toSet()
            val right = b.toSet()
            left.intersect(right).size.toDouble() / left.size.coerceAtLeast(1)
        }?.takeIf { seg ->
            val b = seg.text.filter { !it.isWhitespace() }
            a.any { it in b } || b.any { it in a }
        }
    }

    /** 拿到一段旁听文字后：进上下文、决策、必要时 TTS 并播放。 */
    private suspend fun handleTranscriptText(text: String) {
        val deepKey = Store.llmActiveKey(this)
        val dashKey = Store.dashScopeKey(this)
        if (deepKey.isBlank() || dashKey.isBlank()) {
            VoiceMvpLog.w("SERVICE", "keys missing, skip transcript")
            return
        }
        VoiceMvpLog.i("SERVICE", "旁听: $text")

        // 仅聆听两档：文字回应（决策照跑，回复写对话面板不播报）/ 完全沉默（跳过决策零消耗）
        val listenOnly = Store.listenOnlyEnabled(this)
        val textReplyMode = listenOnly && Store.listenOnlyTextReply(this)
        if (listenOnly && !textReplyMode) {
            VoiceMvpLog.i("SERVICE", "仅聆听模式：跳过决策与播报，仅保留记录")
            updateNotification("仅聆听中（保持安静）", interrupting = true)
            broadcastStatus("listening")
            return
        }

        // 惰性压缩：跨天第一句决策前，或保险丝超限时，先折叠旧原话再决策
        maybeCompact()
        val working = WorkingMemory.buildContext(memoryDb)
        WorkingMemory.stat(memoryDb, "decide_calls")
        WorkingMemory.stat(memoryDb, "decide_ctx_tokens", working.estTokens().toDouble())

        val mode = Store.listenMode(this)
        val decideStart = System.currentTimeMillis()
        val reply = runCatching {
            // 空库退避：连续 20 次检索脱靶后，每 50 次决策才探测一次（记忆入库有提炼时延，避免全天空转）
            val skipRetrieval = retrievalMissStreak >= 20 && retrievalMissStreak % 50 != 0
            val memories = when {
                !Store.cloudMemoryEnabled(this) -> emptyList()
                skipRetrieval -> {
                    retrievalMissStreak++
                    emptyList()
                }
                else -> runCatching { BailianMemory.search(this, text).map { it.content } }
                    .onSuccess { list ->
                        retrievalMissStreak = if (list.isEmpty()) retrievalMissStreak + 1 else 0
                        WorkingMemory.stat(memoryDb, "retrieval_calls")
                        WorkingMemory.stat(memoryDb, "retrieval_hits", list.size.toDouble())
                        if (list.isEmpty()) WorkingMemory.stat(memoryDb, "retrieval_miss")
                    }
                    .getOrElse {
                        WorkingMemory.stat(memoryDb, "retrieval_fail")
                        VoiceMvpLog.w("BAILIAN", "记忆检索失败，本次无记忆上下文: ${it.message}")
                        emptyList()
                    }
            }
            VoiceMvpClient.decide(
                deepKey, mode, text, working.verbatimLines, this,
                memories = memories,
                myInfo = buildMyInfo(),
                summary = working.summary
            )
        }.getOrElse {
            WorkingMemory.stat(memoryDb, "decide_fail")
            VoiceMvpLog.e("SERVICE", "DECIDE failed: ${it.message}", it)
            return
        }
        WorkingMemory.stat(memoryDb, "decide_ms", (System.currentTimeMillis() - decideStart).toDouble())
        if (reply == null) {
            WorkingMemory.stat(memoryDb, "decide_silent")
            VoiceMvpLog.i("SERVICE", "决策：沉默")
            updateNotification("云雀正在聆听（刚旁听，未插话）", interrupting = true)
            broadcastStatus("listening")
            return
        }
        WorkingMemory.stat(memoryDb, "decide_speak")

        // 云雀有话要说：仅聆听下不合成不播报，回复写进对话时间线并弹轻通知
        if (textReplyMode) {
            WorkingMemory.stat(memoryDb, "text_reply")
            memoryDb.addConversation(
                ConversationRecord(
                    id = 0,
                    ts = System.currentTimeMillis(),
                    speakerId = null,
                    speakerName = "云雀",
                    text = reply,
                    origin = "assistant"
                )
            )
            postChatNotification(reply)
            updateNotification("云雀有话要说（已写入对话面板）", interrupting = true)
            broadcastStatus("listening")
            return
        }

        VoiceMvpLog.i("SERVICE", "决策：开口 -> ${reply.take(120)}")
        val ttsFile = File(cacheDir, "yunque_proactive.wav")
        val ok = runCatching { VoiceMvpClient.synthesize(dashKey, reply, ttsFile, Store.voiceId(this@AlwaysOnListeningService).ifBlank { null }) }
            .getOrElse {
                VoiceMvpLog.e("SERVICE", "TTS failed: ${it.message}", it)
                return
            }
        if (!ok) return
        currentAssistantConvId = memoryDb.addConversation(
            ConversationRecord(
                id = 0,
                ts = System.currentTimeMillis(),
                speakerId = null,
                speakerName = "云雀",
                text = reply,
                origin = "assistant"
            )
        )
        playTts(reply, ttsFile)
    }

    private fun postChatNotification(reply: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            this, 5,
            Intent(this, MainShellComposeActivity::class.java)
                .putExtra("open_tab", "chat")
                // CLEAR_TOP 触发销毁重建，onCreate 直接以对话 Tab 为初值（避免跨层状态通知的时序坑）
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("云雀有话要说")
            .setContentText(reply.take(60))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_CHAT_ID, notif)
    }

    private fun playTts(text: String, file: File) {
        mainHandler.post {
            if (speaking) {
                VoiceMvpLog.w("SERVICE", "already speaking, skip new TTS")
                return@post
            }
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(file.absolutePath)
                player.prepare()
                // 输出设备路由：auto 跟随系统；扬声器/指定蓝牙耳机在此锚定（A2DP 高音质）
                runCatching { resolveOutputDevice()?.let { player.preferredDevice = it } }
                currentPlayer = player
                currentTtsFile = file
                currentTtsPlayStartMs = System.currentTimeMillis()
                currentTtsText = text
                speaking = true
                player.setOnCompletionListener {
                    speaking = false
                    currentPlayer = null
                    currentTtsFile = null
                    currentTtsText = ""
                    currentAssistantConvId = -1L
                    VoiceMvpLog.i("SERVICE", "播报完成")
                    updateNotification("云雀正在聆听", interrupting = true)
                    broadcastStatus("listening")
                }
                player.start()
                VoiceMvpLog.i("SERVICE", "开始播报: ${text.take(120)}")
                updateNotification("云雀正在说话…", interrupting = true)
                broadcastStatus("speaking")
            } catch (e: Throwable) {
                VoiceMvpLog.e("SERVICE", "播放失败: ${e.message}", e)
                runCatching { player.release() }
            }
        }
    }

    /* ───────────── 打断 ───────────── */

    private fun interruptPlayback() {
        mainHandler.post {
            val player = currentPlayer ?: run {
                VoiceMvpLog.i("SERVICE", "打断触发，但当前没有播放")
                return@post
            }
            val text = currentTtsText
            // MediaPlayer 对这个 DashScope WAV 的 duration 不可靠，改用文件真实时长 + 墙上时间计算进度
            val fileLen = currentTtsFile?.length() ?: 0L
            val byteRate = SAMPLE_RATE * 2L
            val realDurationMs = if (fileLen > 44) ((fileLen - 44L) * 1000L / byteRate).coerceAtLeast(1L) else 1L
            val elapsed = System.currentTimeMillis() - currentTtsPlayStartMs
            val progress = (elapsed.toFloat() / realDurationMs).coerceIn(0f, 1f)
            val spokenChars = (text.length * progress).toInt().coerceIn(0, text.length)
            val spoken = text.substring(0, spokenChars)
            val missed = text.substring(spokenChars)

            Store.saveInterruption(
                this@AlwaysOnListeningService,
                InterruptionRecord(System.currentTimeMillis(), text, spoken, missed)
            )
            if (currentAssistantConvId > 0) {
                memoryDb.updateConversationMissed(currentAssistantConvId, missed)
                VoiceMvpLog.i("SERVICE", "已写入对话记录的漏听标记 convId=$currentAssistantConvId")
            }
            VoiceMvpLog.i(
                "SERVICE",
                "打断记录: spokenChars=$spokenChars/${text.length} missed=${missed.take(120)}"
            )

            runCatching { player.stop() }
            runCatching { player.release() }
            speaking = false
            currentPlayer = null
            currentTtsFile = null
            currentTtsText = ""
            currentAssistantConvId = -1L
            updateNotification("已打断，漏听部分已记录", interrupting = true)
            broadcastStatus("interrupted")
        }
    }

    /* ───────────── 工具 ───────────── */

    private fun stopEverything() {
        MemoryUploader.flush()
        notificationManager?.cancel(NOTIF_INTERRUPT_ID)
        stopCapture()
        interruptPlaybackSilently()
        isRunning = false
        VoiceMvpLog.i("SERVICE", "service stopped")
    }

    private fun interruptPlaybackSilently() {
        currentPlayer?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        currentPlayer = null
        currentTtsFile = null
        currentAssistantConvId = -1L
        speaking = false
        currentTtsText = ""
    }

    private fun broadcastStatus(status: String) {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra("status", status)
        )
    }

    private fun voiceMvpLog(tag: String, msg: String) {
        VoiceMvpLog.i(tag, msg)
    }
}
