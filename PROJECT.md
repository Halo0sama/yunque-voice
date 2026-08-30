# 云雀·私人助理 工程全览

## 项目定位
云雀·私人助理是一款 Android 端全天候语音私人助理：
- 持续聆听，自动判断是否该开口
- 使用 DeepSeek V4 Flash 做决策与对话
- 使用阿里云百炼 Qwen3-ASR-Flash 识别、Qwen-Audio-3.0-TTS-Flash 合成
- 支持云端多人说话人分离、本地声纹聚类
- 长期记忆、人物关系、关于我、角色卡、自定义音色
- 可联动 Operit AI 执行手机工具
- 本地 CLI/MCP 服务
- UI：Jetpack Compose + Material 3，含纸感/莫奈/液态玻璃多主题

## 技术栈
- Kotlin 2.3.21 + Jetpack Compose BOM 2025.09
- Android Gradle Plugin 8.7.3 + Gradle 8.9
- minSdk 26 / targetSdk 36
- SQLite（`yunque_memory.db`，版本 9）
- OkHttp、Coroutines、NanoHTTPD
- ffmpeg-kit（`dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`）
- LiquidGlass（`io.github.nadeemiqbal:liquid-glass:0.2.3`）
- 对话模型三家可切（v0.11.0）：DeepSeek deepseek-v4-flash / 智谱 glm-5.3-flash / 阿里 qwen3.8-flash，Key 各自保存一键切换（`Store.llmKey/provider`）
- 思考模式（实测）：deepseek `thinking.type=disabled` 可关；qwen `enable_thinking=false` 可关；智谱常思考仅 depth 分档（low/high/max）→ 实时路径统一"关或最浅"；**夜间压缩 thinkingMax**：deepseek 用顶层 `reasoning_effort=max`（官方标准参数；thinking 内无 depth 字段，传了被静默忽略——v0.13.3 修正）、智谱 `thinking.depth=max`、qwen 显式开启（质量优先）

## 构建与安装
```bash
cd yunque-voice
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
最新 APK：`/Users/halo/dsharness/yunque-voice/yunque-voice-latest.apk`

## 核心模块

### 服务层
- `AlwaysOnListeningService`：前台服务、16kHz AudioRecord、蓝牙 SCO、能量 VAD、队列、旁听决策、TTS 播报、打断漏听记录、云端 DIAR 批量转写、仅聆听模式（跳过决策与播报，记录照常）。
- `MvpVoiceInteractionService` / `MvpVoiceInteractionSession`：系统语音交互服务。
- `MvpVoiceAssistantService`：小米蓝牙/语音触发，切换全天聆听起停。
- `MvpRecognitionService`：Android 语音识别服务。

### 语音处理
- `VoiceMvpClient`：ASR、对话模型（DeepSeek/智谱可切）、TTS、工具调用循环、角色上下文、Operit 工具合并。
- `SpeakerEngine`：本地声纹特征聚类。
- `DashScopeFiletrans`：阿里云文件转写 + DIAR。
- `DashScopeUpload`：阿里云临时文件上传。
- `AudioTrimmer`：智能选取 15 秒人声片段。
- `SoundCue`：开启/关闭提示音（上行/下行音阶）。
- `BailianMemory`：阿里云记忆库添加/检索。

### 说话人分离（v0.9.0 重写：批内云端，跨批本地）
- **批内分人（云端权威）**：每 4 段触发 `runCloudDiarization`，DashScope 文件转写 DIAR 对同一份音频聚类——"这几句是否同一嗓音"以云端为准
- **跨批认人（本地声纹）**：簇的归属锚定在簇内多数派的**本地声纹档案**（`applyDiarization`），绝不使用批次编号（旧逻辑用每批内部编号当全局身份，跨批必撞：下午的同事被并进上午的"云端说话人0"）
- 同簇内少数派本地档案经 `SpeakerEngine.mergeProfiles` 融合（特征按样本量加权混合 + 记录/关系并档）
- "云端说话人N"档案不再产生（存量保留）；新声音编号 = 现存"未知N"最大编号+1（删除/改名不撞号）
- 已知取舍：本地声纹（六维特征）跨批偶发认错/漏认 → 表现为多余"未知N"或错归属，可在"身边的人"手动合并/改名纠正；记忆上云携带的名字为 DIAR 前的本地名（本地名即终名，除非事后手动改名）
- 测试钩子：`ACTION_TEST_DIAR --es texts 'A|B|C|D' --es clusters '0,1,0,1'`（纯文本验证批内合并逻辑）

### 记忆与数据（v0.8.0 起三层结构）
- **长期记忆（云端）**：会话簇合并上传——`MemoryUploader` 统一入口（服务旁听/打字输入/测试钩子共用），攒批（≥24 句 / 静默 2 分钟 / 停服务三条件冲刷），`BailianMemory.appendBatchReliably` 一次调用带整簇；断网整批入 `filesDir/memory_outbox.jsonl`（上限 500 行），恢复自动冲刷。
- **无本地”记住”关键词硬规则**（v0.8.0）：是否值得记住由云端记忆库 AI 提炼决断；控制台记忆片段规则指令可加”用户明确要求记住的内容必须原样保留”增强。
- **工作记忆（本地）**：`WorkingMemory`——决策上下文 = session_state.summary（滚动摘要）+ 近 48h 原话窗口（≤80 句/24K 字），全部从 conversations 表重建，重启零失忆；prompt 分层排布吃前缀缓存；chat() 带最近 12 轮对话格式上下文。
- **压缩（桥梁）**：惰性每日压缩 + 保险丝（>24K token）：旧原话折进摘要，新事实自动 AddMemory 上云。
- 检索：`SearchMemory` Lite 档 top_k=6，注入 `decide()` 与 `chat()`。
- VAD 自适应噪声地板（v0.8.0）：阈值 = max(500, 底噪EMA×4)，防持续底噪致段缓冲无限增长；另有 30 秒硬顶强制切段。
- **试用周观测**：`daily_stats` 表按日累计 segments_captured/noise_filtered/upload_calls/upload_sentences/retrieval_calls/hits/miss/decide_calls/speak/silent/fail/decide_ms/compaction_* 等指标；`GET /api/stats`（adb forward 8766）汇总最近 14 天 + 工作记忆状态 + outbox 欠账；`GET /api/log?n=300` 拉日志尾；MCP 工具 `get_stats`。VoiceMvpLog 落盘 cache/voice_mvp.log（4MB 轮转）。
- `MemoryDb` v9：+ session_state、daily_stats；`RelationshipExtractor`、`AboutMeExtractor` 保留（每 5 段触发）。

### UI（Compose）
- `MainShellComposeActivity`：主界面 + 悬浮玻璃底栏 + Crash 兼容模式。
- 五个 Tab：首页 / **对话**（v0.7.0，读 conversations 时间线，打字链路=chat()+MemoryUploader 上云，与语音同一记忆；仅聆听文字回应也显示在此）/ 记忆 / 人物 / 设置。
- `AboutMeComposeActivity`：关于我（姓名/声纹/记录）。
- `PersonProfileComposeActivity`：人物档案。
- `RoleCardComposeActivity`：角色卡管理。
- `WorldBookComposeActivity`：世界书管理。
- `VoiceCloneComposeActivity`：自定义音色/自带音色/试听。
- `VoiceManagerComposeActivity`：身边的人（筛选/声音列表/重命名/删除/样本试听）。
- `GlassTheme.kt`：多主题与玻璃组件。
- 注意：AppShell 的 tab state 必须 remember（否则 recompose 重建会把通知跳转设置的 tab 打回首页，v0.7.0 修复）。

### 仅聆听与"云雀有话要说"（v0.7.0）
- 仅聆听两档：`Store.listenOnlyTextReply`（默认开）= 决策照跑，回复不 TTS，写 conversations + 轻通知 1003（CLEAR_TOP 销毁重建跳对话 Tab）；关 = 跳过决策零消耗。
- 设置 → 仅聆听与对话 底栏 sheet 切换。
- 打字输入：ChatScreen send() → conversations(origin=user, speaker=主人称呼) → MemoryUploader.enqueue（与聆听同一噪声门/verbatim/簇合并链路）→ chat()(recentTurns=conversations 最近30轮 + workingSummary) → 回复写 conversations。
- `MainShellActivity` 等旧 View 已删除，工程为纯 Compose。

## 主题
- 莫奈取色（动态）
- 温暖纸感
- 液态玻璃（默认）
  - 壁纸模糊背景、半透明玻璃、白描边、悬浮底栏
  - 设置页二级菜单为底部弹出卡片
  - 通知栏控制可独立开关“点击停止聆听”和“点击打断播报”

## 角色卡
- 数据库表：role_cards、world_books、world_entries
- 默认内置“云雀”角色卡（称呼用户为“主人”）
- 支持 Tavern JSON/PNG 导入导出、世界书导入导出、关键词命中注入
- 运行时注入 DeepSeek：角色设定/性格/语气 + 世界书 + 全局记忆

## 自定义音色
- 设置 → 自定义音色
- 自带音色选择（弹出列表）
- 本地音频选择 → 智能裁剪 15 秒 → 临时 OSS 上传 → `voice-enrollment` 创建音色 → 保存 → 试听
- 需要阿里云百炼 API Key 与业务空间 ID

## 系统语音助手绑定
- Android 原生：设置 → 搜索“默认应用设置” → 语音助手 → 云雀·私人助理
- 小米 HyperOS/MIUI：设置 → 语音助手 → 系统快捷键唤起 / 语音蓝牙唤起 → 云雀·私人助理
- 相关文件：
  - `res/xml/voice_interaction.xml`
  - Manifest 中 `VoiceAssistantComposeActivity`、`MvpVoiceInteractionService`、`MvpVoiceAssistantService`、`MvpRecognitionService`
- 蓝牙耳机功能键行为：切换全天聆听起停（通过 Activity/Service/Session 三条路径）
- 若列表不显示：重启或重装；必要时用 Shizuku 执行 `settings put` 绑定 Android 默认助理

## 音频输入输出（v0.11.0）
- 设置 → 麦克风与音质：动态枚举系统输入/输出设备（显示原始 productName）
- 输入：AudioRecord.setPreferredDevice 锚定具体设备；蓝牙通话麦走 SCO+VOICE_COMMUNICATION，其余 VOICE_RECOGNITION+clearCommunicationDevice（保 A2DP 音质）
- 输出：MediaPlayer.setPreferredDevice（auto/扬声器/指定蓝牙耳机）
- 切换即生效（ACTION_APPLY_AUDIO 重启采集线程）

## 通知栏控制
- 设置 → 通知栏控制（二级菜单）
- 默认关闭；开启时请求通知权限
- 两个独立开关：
  - 点击停止聆听（主通知，点击即停）
  - 点击打断播报（独立通知，点击即打断）
- 主通知固定第三个操作“仅聆听/恢复播报”，运行中一键切换

## 仅聆听
- 开关：主界面状态卡按钮 + 通知栏操作（`Store.listenOnlyEnabled`，持久化）
- 开启后：采集/VAD/ASR/说话人/记录/记忆提炼照常；`handleTranscriptText` 在进历史后直接跳过决策与 TTS
- “记住…”手动记忆指令仍生效；通知文案“仅聆听中（保持安静）”，“点击打断播报”通知自动隐藏

## Operit 联动
- 设置 → Operit 接入：地址 + Bearer Token
- `OperitClient`：`GET /v1/tools`、`POST /v1/tools/invoke`
- DeepSeek 请求时自动并入 Operit 工具列表，由模型自主调用
- 工具执行结果回填模型继续推理

## CLI / MCP
- 本地服务：127.0.0.1:8766（YunqueApiServer）
- REST：/api/health、/api/memories、/api/memory（POST 添加）、/api/memory/search、/api/memory/delete、/api/conversations、/api/speakers、/api/relationships、/api/interruptions
- MCP JSON-RPC：/mcp（list_memories、search_memory、add_memory、delete_memory、search_conversations、list_speakers、list_relationships）
- 记忆端点均为云端代理；POST body 按 UTF-8 手工解码（绕开 NanoHTTPD 乱码问题）
- 工具：`yunque-voice/tools/yunque-cli.py`

## 隐私
- 数据全部本地 SQLite/SharedPreferences/缓存
- APK 不包含个人数据
- 不要分享数据库、日志、`secrets.env`

## 已知问题/后续
- 旧说话人没有历史音频样本，新样本从新版本开始保存
- MIUI 私有语音助手列表可能不收录第三方应用；已通过 Android 原生 + 小米广播/Activity/Session 多路径兼容
- 本地/云端说话人可能重复，后续做合并策略
- 自定义音色上传依赖阿里云临时 OSS；本地录制上传链路已通
- 真机 Android 16/17 曾有 16KB 对齐警告，已切换 16KB 对齐 ffmpeg-kit 修复
- 记忆簇合并按 AddMemory 计次计费（默认规则 Pro ¥0.03/次，可在控制台把记忆片段规则改 Lite 降到 ¥0.018）；后续可加“60 秒合并上传”开关省 50-70%
- 记忆片段规则默认 180 天过期（控制台可改），陪伴场景建议调长
- 云端记忆商业化后需 workspace 头 + memory_library_id，缺一会报 ServiceNotOpened
- VAD 段缓冲已有 30 秒硬顶（v0.7.0）：持续环境噪声曾致缓冲无限增长 OOM（209MB，Mac 底噪实锤触发）
- 仅聆听文字回应档每句照常消耗决策调用；打字对话的"hi"这类短句会被噪声门过滤不上云（≥4 字才传）

## 关键文件路径
- 主界面：`app/src/main/java/com/halo/yunquevoice/ui/MainShellComposeActivity.kt`
- 数据库：`app/src/main/java/com/halo/yunquevoice/memory/MemoryDb.kt`
- 语音客户端：`app/src/main/java/com/halo/yunquevoice/voice/VoiceMvpClient.kt`
- 服务：`app/src/main/java/com/halo/yunquevoice/service/`
- Manifest：`app/src/main/AndroidManifest.xml`
- 语音交互配置：`app/src/main/res/xml/voice_interaction.xml`
- 使用说明：`yunque-voice/使用说明.md`
