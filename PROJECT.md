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
- SQLite（`yunque_memory.db`，版本 7）
- OkHttp、Coroutines、NanoHTTPD
- ffmpeg-kit（`dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`）
- LiquidGlass（`io.github.nadeemiqbal:liquid-glass:0.2.3`）

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
- `VoiceMvpClient`：ASR、DeepSeek、TTS、工具调用循环、角色上下文、Operit 工具合并。
- `SpeakerEngine`：本地声纹特征聚类。
- `DashScopeFiletrans`：阿里云文件转写 + DIAR。
- `DashScopeUpload`：阿里云临时文件上传。
- `AudioTrimmer`：智能选取 15 秒人声片段。
- `SoundCue`：开启/关闭提示音（上行/下行音阶）。
- `BailianMemory`：阿里云记忆库添加/检索。

### 记忆与数据
- **长期记忆纯云端**：每句旁听实时写入阿里云百炼记忆库（`BailianMemory.appendReliably`，verbatim + speaker/meta_data，噪声门过滤短句）；显式“记住”指令走 custom_content 原样存储；断网写入进 `filesDir/memory_outbox.jsonl`（上限 500 条），恢复后自动冲刷。
- 检索：`SearchMemory` Lite 档 top_k=6，注入 `decide()`（旁听决策）与 `chat()`（对话）。
- 记忆板块 UI 直连云端：ListMemory 分页列表 / 语义搜索 / DeleteMemory / 手动添加；首次打开自动把本地旧 memories 迁移上云并清空本地表。
- 请求必带 `X-DashScope-WorkspaceId` 头与 `memory_library_id`（缺一即 ServiceNotOpened，2026-08-29 实测）。
- `MemoryDb`：speakers / conversations / relationships / graph_positions / about_me / role_cards / world_books / world_entries（memories 表已退役，仅存迁移前残留）。
- `RelationshipExtractor`、`AboutMeExtractor`：自动提炼（每 5 段触发，本地结构化数据）。
- `RoleCardIO`：Tavern JSON/PNG 角色卡与世界书导入导出。
- `TavernPngIO`：PNG tEXt 角色卡读写。

### UI（Compose）
- `MainShellComposeActivity`：主界面 + 悬浮玻璃底栏 + Crash 兼容模式。
- `AboutMeComposeActivity`：关于我（姓名/声纹/记录）。
- `PersonProfileComposeActivity`：人物档案。
- `RoleCardComposeActivity`：角色卡管理。
- `WorldBookComposeActivity`：世界书管理。
- `VoiceCloneComposeActivity`：自定义音色/自带音色/试听。
- `VoiceManagerComposeActivity`：身边的人（筛选/声音列表/重命名/删除/样本试听）。
- `GlassTheme.kt`：多主题与玻璃组件。
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
- 记忆逐句上传按 AddMemory 计次计费（默认规则 Pro ¥0.03/次，可在控制台把记忆片段规则改 Lite 降到 ¥0.018）；后续可加“60 秒合并上传”开关省 50-70%
- 记忆片段规则默认 180 天过期（控制台可改），陪伴场景建议调长
- 云端记忆商业化后需 workspace 头 + memory_library_id，缺一会报 ServiceNotOpened

## 关键文件路径
- 主界面：`app/src/main/java/com/halo/yunquevoice/ui/MainShellComposeActivity.kt`
- 数据库：`app/src/main/java/com/halo/yunquevoice/memory/MemoryDb.kt`
- 语音客户端：`app/src/main/java/com/halo/yunquevoice/voice/VoiceMvpClient.kt`
- 服务：`app/src/main/java/com/halo/yunquevoice/service/`
- Manifest：`app/src/main/AndroidManifest.xml`
- 语音交互配置：`app/src/main/res/xml/voice_interaction.xml`
- 使用说明：`yunque-voice/使用说明.md`
