#!/bin/bash
# 云雀·夜间备份作业（零模型调用，纯 adb/脚本）
# 由 zcode 定时任务在 23:00 触发；产物写入夸克网盘同步文件夹，由手机端夸克同步到云端。
# 设计原则：连不上手机就安静退出；任何失败不影响退出码为 0（明天白天再修）。

set -u
DEST_ROOT="/Volumes/dav/quark/mac/云雀语音/nightly"
TS=$(date +%Y-%m-%d_%H%M)
DAY=$(date +%Y-%m-%d)
STASH="/tmp/yunque_nightly/$TS"
DEST="$DEST_ROOT/$TS"
LOG="$DEST_ROOT/backup.log"
mkdir -p "$STASH" "$DEST_ROOT"

log() { echo "[$(date '+%F %T')] $*" >> "$LOG"; }

# ── 0. 网盘挂载检查（目录可见即可；产物最后以单 tar 一次写入，避开 WebDAV 小 IO 中断）──
mkdir -p "$DEST" 2>/dev/null || { echo "[$(date '+%F %T')] $DAY 网盘不可达，跳过" >> "$LOG"; exit 0; }

# ── 1. 找手机（USB 优先，其次无线重连一次）─────────
SERIAL=""
for s in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
    # 模拟器跳过，只备份真机
    case "$s" in emulator-*) continue;; esac
    SERIAL="$s"; break
done
if [ -z "$SERIAL" ]; then
    # 无线重连：mDNS 自动发现处于无线调试状态的设备（约需数秒）
    NEW=$(timeout 12 adb mdns services 2>/dev/null | grep -o 'adb-.*\._adb-tls-connect\._tcp\.' | head -1)
    if [ -n "$NEW" ]; then
        adb connect "$NEW" > /dev/null 2>&1
        for s in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
            case "$s" in emulator-*|*.*) [ "${s#emulator-}" != "$s" ] && continue;; esac
            SERIAL="$s"; break
        done
    fi
fi
if [ -z "$SERIAL" ]; then
    log "$DAY 手机不可达（USB 未连且无线调试未发现），跳过"
    exit 0
fi
log "$DAY 开始备份，设备 $SERIAL"

PKG=com.halo.yunquevoice

# ── 2. 云端记忆库快照（调用手机侧配置的 Key/库ID，零模型花费）───────
UID_=$(adb -s "$SERIAL" shell "run-as $PKG cat shared_prefs/yunque_voice.xml" 2>/dev/null | grep -o 'memory_user_id">[^<]*' | cut -d'>' -f2 | tr -d '\r')
LIB=$(adb -s "$SERIAL" shell "run-as $PKG cat shared_prefs/yunque_voice.xml" 2>/dev/null | grep -o 'memory_library_id">[^<]*' | cut -d'>' -f2 | tr -d '\r')
WS=$(adb -s "$SERIAL" shell "run-as $PKG cat shared_prefs/yunque_voice.xml" 2>/dev/null | grep -o 'workspace_id">[^<]*' | cut -d'>' -f2 | tr -d '\r')
KEYLINE=$(grep -E '^DASHSCOPE_API_KEY' /Users/halo/dsharness/voice-mvp/secrets.env 2>/dev/null | cut -d= -f2)
if [ -n "$UID_" ] && [ -n "$LIB" ] && [ -n "$KEYLINE" ]; then
    python3 - "$UID_" "$LIB" "$KEYLINE" "$WS" "$STASH/cloud_memory.json" << 'PYEOF'
import json, sys, urllib.request
uid, lib, key, ws = sys.argv[1:5]
nodes, page = [], 1
try:
    while True:
        url = (f"https://dashscope.aliyuncs.com/api/v2/apps/memory/memory_nodes"
               f"?user_id={uid}&memory_library_id={lib}&page_size=50&page_num={page}")
        r = urllib.request.Request(url, headers={"Authorization": "Bearer "+key,
                                                 "X-DashScope-WorkspaceId": ws})
        d = json.loads(urllib.request.urlopen(r, timeout=30).read())
        nodes += d.get("memory_nodes", [])
        if len(nodes) >= d.get("total", 0) or not d.get("memory_nodes"): break
        page += 1
    json.dump({"user_id": uid, "total": len(nodes), "nodes": nodes},
              open(sys.argv[5], "w"), ensure_ascii=False, indent=1)
    print(f"cloud nodes: {len(nodes)}")
except Exception as e:
    json.dump({"user_id": uid, "error": str(e)}, open(sys.argv[5], "w"), ensure_ascii=False)
    print("cloud snapshot failed:", e)
PYEOF
else
    echo '{"error":"missing config"}' > "$STASH/cloud_memory.json"
fi

# ── 3. 本地数据导出（DB + 配置 + 日志）────────────────
adb -s "$SERIAL" shell "run-as $PKG cat databases/yunque_memory.db" > "$STASH/yunque_memory.db" 2>/dev/null
adb -s $SERIAL exec-out "run-as $PKG sh -c 'sqlite3 databases/yunque_memory.db \"PRAGMA wal_checkpoint(TRUNCATE);\"'" >/dev/null 2>&1
adb -s "$SERIAL" shell "run-as $PKG cat shared_prefs/yunque_voice.xml" 2>/dev/null | sed -E 's/(llm_key_|deepseek_key|dashscope_key)[^<]*/\1***MASKED***/g' > "$STASH/prefs_masked.xml"
adb -s "$SERIAL" shell "run-as $PKG cat cache/voice_mvp.log" > "$STASH/voice_mvp.log" 2>/dev/null
adb -s "$SERIAL" shell "run-as $PKG sh -c 'cat cache/voice_mvp.log.old 2>/dev/null'" >> "$STASH/voice_mvp.log" 2>/dev/null

# ── 4. 当日指标摘要（人能直接读的日报）────────────────
if [ -s "$DEST/yunque_memory.db" ]; then
    {
        echo "# 云雀日报 $DAY"
        sqlite3 "$STASH/yunque_memory.db" "SELECT '今日统计: ' || stats FROM daily_stats WHERE date='$DAY';" 2>/dev/null
        sqlite3 "$STASH/yunque_memory.db" "SELECT '工作记忆: 摘要' || length(summary) || '字, 压缩至 ' || summarized_until FROM session_state;" 2>/dev/null
        sqlite3 "$STASH/yunque_memory.db" "SELECT '画像: ' || length(content) || '/' || budget || '字' FROM profile_doc;" 2>/dev/null
        sqlite3 "$STASH/yunque_memory.db" "SELECT '对话条数: ' || COUNT(*) FROM conversations WHERE ts >= strftime('%s','now','-1 day')*1000;" 2>/dev/null
        echo "云端节点: $(python3 -c "import json;print(json.load(open('$STASH/cloud_memory.json')).get('total','?'))" 2>/dev/null)"
    } > "$STASH/daily_report.md" 2>/dev/null
fi

# ── 5. 服务存活状态（只记录不干预）───────────────────
RUNNING=$(adb -s "$SERIAL" shell "dumpsys activity services $PKG" 2>/dev/null | grep -c ServiceRecord)
echo "listening_service_records=$RUNNING" >> "$STASH/daily_report.md" 2>/dev/null

adb -s "$SERIAL" disconnect > /dev/null 2>&1

# ── 6. 单 tar 包一次写入网盘（避开 WebDAV 多次小 IO 中断）─────────
if tar -czf "$DEST/backup_$TS.tar.gz" -C "$(dirname "$STASH")" "$TS" 2>/dev/null; then
    log "$DAY 备份完成 → $DEST/backup_$TS.tar.gz ($(du -h "$DEST/backup_$TS.tar.gz" | cut -f1))"
    rm -rf "$STASH"
    echo "[$(date '+%F %T')] done: $DEST/backup_$TS.tar.gz"
else
    # tar 写网盘失败：把本地 stash 原样搬过去再试一次
    cp -r "$STASH" "$DEST/" 2>/dev/null
    log "$DAY tar 写入失败，已降级为目录复制"
    echo "[$(date '+%F %T')] done(fallback): $DEST"
fi
