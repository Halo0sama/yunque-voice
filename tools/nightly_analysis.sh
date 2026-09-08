#!/bin/bash
# 云雀·夜间分析作业（零模型调用，纯脚本）
# 数据流：手机导出包 → 夸克同步 → 网盘 mac/云雀语音/phone_sync/ → 本脚本消费 → 日报+建议写回 reports/
# 由 zcode 定时任务 23:30 触发；无新包时安静退出。

set -u
SYNC_ROOT="/Volumes/dav/quark/mac/云雀语音"
IN_DIR="$SYNC_ROOT/phone_sync"
REPORT_DIR="$SYNC_ROOT/reports"
LOG="$SYNC_ROOT/analysis.log"
DAY=$(date +%Y-%m-%d)
WORK="/tmp/yunque_analysis"

mkdir -p "$REPORT_DIR" "$WORK"

# 找 phone_sync 里最新的导出包（夸克从手机同步上来的）
LATEST_TAR=$(ls -t "$IN_DIR"/yunque_export_*.tar 2>/dev/null | head -1)
# 兼容：找不到就找 nightliy 目录里 Mac 端自己拉的（无线调试在线时的旧路径）
if [ -z "$LATEST_TAR" ]; then
    LATEST_TAR=$(ls -t "$SYNC_ROOT"/nightly/*/backup_*.tar.gz 2>/dev/null | head -1)
fi
if [ -z "$LATEST_TAR" ]; then
    echo "[$(date '+%F %T')] $DAY 无新导出包，跳过" >> "$LOG"
    exit 0
fi
AGE_H=$(( ( $(date +%s) - $(stat -f %m "$LATEST_TAR") ) / 3600 ))
if [ "$AGE_H" -gt 30 ]; then
    echo "[$(date '+%F %T')] $DAY 最新包已 ${AGE_H}h 旧（$LATEST_TAR），跳过" >> "$LOG"
    exit 0
fi

# 解包
rm -rf "$WORK" && mkdir -p "$WORK/data"
tar -xf "$LATEST_TAR" -C "$WORK/data" 2>/dev/null
DB="$WORK/data/yunque_memory.db"
[ -s "$DB" ] || { echo "[$(date '+%F %T')] $DAY 包内无 DB，跳过" >> "$LOG"; exit 0; }

# ── 分析 ─────────────────────────────────────────
REPORT="$REPORT_DIR/report_$DAY.md"
TODAY_DATE=$(date +%Y-%m-%d)
{
echo "# 云雀分析日报 $DAY"
echo
echo "数据来源：\`$(basename "$LATEST_TAR")\`"
echo
## 今日核心指标
STATS=$(sqlite3 "$DB" "SELECT stats FROM daily_stats WHERE date='$TODAY_DATE';")
if [ -n "$STATS" ]; then
    echo "## 今日运行指标"
    echo '```'
    echo "$STATS" | python3 -c "
import json,sys
s=json.loads(sys.stdin.read())
items=[('捕获语音段','segments_captured'),('噪声过滤','noise_filtered'),('决策次数','decide_calls'),
('开口','decide_speak'),('沉默','decide_silent'),('决策失败','decide_fail'),
('记忆簇上传','upload_calls'),('上传句数','upload_sentences'),('检索次数','retrieval_calls'),
('检索脱靶','retrieval_miss'),('压缩次数','compaction_run'),('压缩折叠句','compaction_turns'),
('压缩新事实','compaction_facts'),('文字回应','text_reply')]
for label,k in items:
    if k in s: print(f'{label}: {s[k]:.0f}' if isinstance(s[k],(int,float)) else f'{label}: {s[k]}')
if 'decide_ms' in s and s.get('decide_calls'): print(f\"决策平均耗时: {s['decide_ms']/s['decide_calls']:.0f} ms\")
"
    echo '```'
else
    echo "## 今日无运行数据（聆听未开启或刚重置）"
fi
echo
## 画像
PROFILE=$(sqlite3 "$DB" "SELECT content FROM profile_doc;" 2>/dev/null)
BUDGET=$(sqlite3 "$DB" "SELECT budget FROM profile_doc;" 2>/dev/null)
echo "## 当前画像（${#PROFILE}/$BUDGET 字）"
if [ -n "$PROFILE" ]; then echo "> $PROFILE"; else echo "> （空——跨天压缩后自动生成，或在\"关于我\"页手动编辑）"; fi
echo
## 说话人分布（声纹健康度：单人多 day 占比过高=坍缩征兆）
echo "## 近3天说话人分布"
sqlite3 "$DB" "SELECT speaker_name, COUNT(*) FROM conversations WHERE ts > strftime('%s','now','-3 day')*1000 GROUP BY speaker_name ORDER BY 2 DESC LIMIT 8;" 2>/dev/null | awk -F'|' '{printf "- %s: %s 句\n", $1, $2}'
TOTAL3D=$(sqlite3 "$DB" "SELECT COUNT(*) FROM conversations WHERE ts > strftime('%s','now','-3 day')*1000;" 2>/dev/null)
TOP=$(sqlite3 "$DB" "SELECT COUNT(*) FROM conversations WHERE ts > strftime('%s','now','-3 day')*1000 GROUP BY speaker_name ORDER BY 2 DESC LIMIT 1;" 2>/dev/null)
if [ "${TOTAL3D:-0}" -gt 50 ] && [ "${TOP:-0}" -gt $((TOTAL3D * 95 / 100)) ]; then
    echo "- ⚠️ **声纹坍缩征兆**：近3天 $TOTAL3D 句中 $TOP 句归同一人（>95%）。若实际接触过多人，请检查\"身边的人\"档案并反馈。"
fi
echo
## 错误面
FAILS=$(sqlite3 "$DB" "SELECT decide_fail FROM daily_stats WHERE date='$TODAY_DATE';" 2>/dev/null)
UPFAIL=$(grep -c "簇上传失败\|上传失败" "$WORK/data/voice_mvp.log" 2>/dev/null)
ASRError=$(grep -c "ASR failed" "$WORK/data/voice_mvp.log" 2>/dev/null)
echo "## 健康度"
echo "- 决策失败: ${FAILS:-0} 次"
echo "- 日志中上传失败: ${UPFAIL:-0} 处"
echo "- 日志中 ASR 失败: ${ASRError:-0} 处（含停止时的取消风暴属正常）"
CRASH=$(grep -c "FATAL EXCEPTION" "$WORK/data/voice_mvp.log" 2>/dev/null)
[ "${CRASH:-0}" -gt 0 ] && echo "- ⚠️ **检测到崩溃 $CRASH 次**：请把本报告发给开发者"
echo
## 建议生成
echo "## 建议"
if [ "${FAILS:-0}" -gt 5 ]; then echo "- 决策失败偏多：检查网络与所选对话模型的 Key 有效性"
elif [ -n "$STATS" ]; then echo "- 运行平稳，无异常需要处理"
else echo "- 聆听数据尚少，先正常使用一两天再看"; fi
} > "$REPORT"

echo "[$(date '+%F %T')] $DAY 分析完成 → $REPORT" >> "$LOG"
echo "done: $REPORT"
