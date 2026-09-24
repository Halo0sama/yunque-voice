# 云雀·私人助理 — 项目状态快照

> 本文档由夜间作业维护，记录当前开发全貌。**上下文压缩后以此文件恢复记忆。**
> 最后更新：2026-09-20 凌晨（v0.26.4 之后，3.1 语音模型集成开工前）

## 一、项目定位

云雀·私人助理：Android 全天候语音助手（Kotlin + Jetpack Compose）。
核心循环：持续聆听 → 旁听决策（说/不说）→ 回应 + 三层记忆（工作记忆/画像/云端库）。
GitHub：https://github.com/Halo0sama/yunque-voice （公开，MIT，user: Halo0sama）
App 内自动更新：GitHub Releases 检查（已上线，用户手机 0.24.0 会弹窗到新版）。

## 二、当前版本与关键路径

- 当前 release：**v0.26.4**（真机已装；GitHub 最新 release 是 v0.26.4）
- 主副本：/Users/halo/dsharness/yunque-voice（git 仓库，main 分支）
- 模拟器：AVD `yunque37`（Android 17 API 37，x86_64 镜像 ABI 拆分后 debug 包用 app-x86_64-debug.apk / universal）
- 真机：小米 25102RKBEC（bb5ab72d），无线调试 mDNS 可发现；USB 时直连
- 测试素材（用户提供的游戏语音 m4a）：
  - /Users/halo/Downloads/bilibili - Mythend - 1v9猎人游戏,但是凋灵风暴【第三期】.m4a（主素材）
  - 同目录 1v7【第二期】.m4a、1v10【第一期】.m4a、1v18【剧本赛第零期】.m4a（备用）

## 三、架构（当前真实状态）

### 语音管线
```
麦克风/蓝牙 → AudioRecord(16kHz) → 能量VAD(自适应噪声地板) → wav 段
  → ASR: qwen3-asr-flash（OpenAI兼容 /compatible-mode/v1/chat/completions，base64 data URI）
  → 决策: 对话模型（四家可切）detectTool 命中→第一轮 tool_choice 强制工具→第二轮作答
  → TTS: qwen-audio-3.0-tts-flash（HTTP POST dashscope TTS 端点）→ 播报
```

### 对话模型供应商（Store.LLM_PROVIDERS 四家，Key 各自保存 llm_key_*）
| provider | 模型 | 端点 | 关思考 |
|---|---|---|---|
| deepseek（当前真机） | deepseek-flash | api.deepseek.com | tool_choice 仅第一轮 + thinking disabled |
| zhipu | glm-5.3-flash | open.bigmodel.cn（maas.qianwenaiapi 不对，是 bigmodel） | 常思考 depth=low |
| qwen | qwen3.8-flash | dashscope compatible-mode | enable_thinking=false |
| qwen_omni | qwen3.8-omni-flash | 同 dashscope | enable_thinking=false；**支持原生音频输入**（base64 wav 已实测） |

- **tool_choice 修复**（0.26.1）：forcedTool 命中时第一轮 `tool_choice={type:function,function:{name}}`，仅第一轮（后续轮会死循环调工具）
- **decideAudio**（直听，v0.26.0）：Qwen Omni 时音频 base64 直入决策（跳过 ASR 文本），开关 Store.audioDirectEnabled；heard 转写入时间线

### 记忆三层
1. 工作记忆：WorkingMemory.buildContext（48h 原话窗口 ≤80句 + session_state.summary 滚动摘要）
2. 画像文档：profile_doc 表（800→2000 字自适应），每晚压缩维护
3. 云端记忆库：阿里百炼（user_id=yunque-1ab1e44e-a082, lib=61f8798d…, workspace=ws-fk8y9qb4fdsm70w3）
   - 每晚压缩产出 facts → BailianMemory.addFacts → **递归二分重试**（内容审核拒收批：HTTP200/0节点，脏话触发，已实锤）
   - v0.26.4 起上传前 sanitize() 脏话→首字+*
   - **当前云端 total≈29+ 条**（9/19 验证：胡凯/赵继业/李辉/张乐天等命名条目语义检索命中）

### 定时系统
- ScheduleRule: silent+listenState 组合派生 action（normal/stop/listen_only/auto_restore）
- 课程表导入：IcsParser（WakeUp .ics）→ silent=true 规则（上课停、下课恢复）
- ScheduleReceiver + AlarmManager.setExactAndAllowWhileIdle；BootRescheduler 重排
- 已知未解：silent 规则的 END 恢复触发在部分场景执行了两次 STOP（0.23.1 日报待查节）

### 每日数据链路
手机跨天压缩 → DailyExporter 导出 tar 到 Download/yunque_export → 夸克同步上云
→ Mac 网盘路径 /Volumes/dav/quark/我的备份/来自：REDMI K90 Pro Max手机备份/文件夹备份/Download/yunque_export/
→ nightly_analysis.sh 消费（**注意：此脚本 IN_DIR 仍指向旧 phone_sync 路径，需改到上述备份路径**）

### 设置页结构（四组）
聆听（仅聆听与对话/定时开关聆听/耳机功能键）、声音（音频设备/自定义音色）、
AI 与数据（AI 与接口/Operit/每日数据导出）、外观与系统（主题/通知栏控制/后台保活指引）+ 角色卡

## 四、3.1 语音模型集成（当前任务）

阿里 Qwen-Audio-3.1 系列五款（发布文章已全文读取）：
- **qwen-audio-3.1-asr-flash**：ASR。**已实测调通**：
  - endpoint: `https://maas.qianwenaiapi.com/api/v1/services/aigc/multimodal-generation/generation`（注意：不是 dashscope.aliyuncs.com！dashscope compatible-mode 对此模型 404）
  - input.messages[].content = `[{"type":"input_audio","input_audio":{"data":"<URL或dataURI>"}}]`
  - `parameters: {"format":"wav","sample_rate":"16000"}`（**顶层 parameters，缺它报 format is empty**）
  - 响应：`output.sentence.text`（带标点）+ `output.sentence.words[]`（字级时间戳）+ speaker_id（flash 版 null）
  - **润色（去语气词）默认未开启**——"嗯那个呃"仍在，润色开关待查（可能 ASR-Next 或参数）
- qwen-audio-3.1-tts-flash：**WebSocket** wss://maas.qianwenaiapi.com/api-ws/v1/inference（DashScope SDK SpeechSynthesizer model=3.1-tts-flash）；HTTP 端点未知。价格输入1.5/输出12 每M
- qwen-audio-3.1-realtime-plus：全双工实时（远期）
- asr-flash-filetrans / streaming：文件转写/流式变体

### 待验证
- [ ] 3.1-ASR 与 qwen3-asr-flash 同素材对比转写质量
- [ ] 3.1-TTS-Flash WebSocket 实测（音色、指令情绪）
- [ ] Realtime-Plus 能力边界调研（Agent 工具调用）

## 五、历史坑（勿重复）

1. **UI 状态无 remember** → recompose 重置（tab 打回首页那次）
2. **Tool choice 死循环**：tool_choice 放轮循环内每轮强制→4轮耗尽答案空；已改仅 turn==0
3. **forcedTool 参数未使用**（v0.11 潜伏）：时间类问题模型跳过工具幻觉时间；已用 tool_choice 修复
4. **声纹坍缩**：阈值0.82+特征漂移→多人塌一人；已改 0.93+增量均值
5. **云端内容审核静默拒收**：脏话批 HTTP200/0节点；已加二分隔离+sanitize 脱敏
6. **STICKY 空跑**：intent=null 不处理→服务复活但聆听停；已按 listeningWasRunning 恢复
7. **WebDAV 挂载**：后台会话 osascript 挂载会挂起等 GUI；失败即跳过勿阻塞
8. **版本号 python 替换静默落空**：锚点值多轮间变化；replace 后必须验证
9. **pendingIntent extras 混跑**：旧 alarm extras 与新代码并存期行为诡异；升级后首次触发丢弃即可

## 六、设备与环境

- Mac：zsh，adb 在 PATH，GH_TOKEN 每次从 /Users/halo/Documents/未命名3.docx 恢复（解压 word/document.xml 提取 ghp_ token），gh auth setup-git 配凭据
- 模拟器：emulator-5554（yunque37）；x86_64
- 真机无线：mDNS "adb-bb5ab72d-PpYWOC._adb-tls-connect._tcp"，USB 连接时 serial=bb5ab72d
- 密钥：/Users/halo/dsharness/voice-mvp/secrets.env（DASHSCOPE_API_KEY、DEEPSEEK_API_KEY）；Qwen Omni 用同一 DASHSCOPE Key；GitHub token 在 /tmp/gh_token（易失）
