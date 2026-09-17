package com.halo.yunquevoice.voice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * 定时开关聆听（v0.19.0）：
 * - 规则 = 时段（start/end 分钟）+ 周几重复；end<=start 视为跨午夜
 * - 触发：AlarmManager.setExactAndAllowWhileIdle（系统级，不依赖 App 存活）
 * - 语义：计划点照常执行（与手动操作正交）；自动执行时发通知告知（隐私透明）
 */
data class ScheduleRule(
    val id: Long,
    var enabled: Boolean = true,
    var startMin: Int,          // 0..1439
    var endMin: Int,            // 0..1439，<=start 视为跨午夜
    var days: Set<Int>,         // ISO：1=一 … 7=日
    /** 兼容字段：""=正常聆听，listen_only=仅聆听。与 silent 组合派生时段行为（见 action）。 */
    var listenState: String = "",
    /** 时段内行为：false=开启聆听（默认），true=保持安静（上课/会议模式：到点停止，结束恢复） */
    var silent: Boolean = false,
    /** 标注（如课程名），UI 显示 */
    var label: String = "",
    /** 来源：manual=手动创建，course=课程表导入（兼容旧数据：label 非空且 silent 视作 course） */
    var source: String = "manual"
) {
    val isCourse: Boolean get() = source == "course" || (label.isNotBlank() && silent)

    /** 时段行为（v0.24.0 四态）：normal=开启聆听 / stop=停止聆听 / listen_only=仅聆听 / auto_restore=自动恢复 */
    val action: String get() = when {
        silent && listenState == "auto_restore" -> "auto_restore"
        silent -> "stop"
        listenState == "listen_only" -> "listen_only"
        else -> "normal"
    }
}

object ScheduleStore {

    private const val PREFS = "yunque_schedule"
    private const val KEY_RULES = "rules"
    const val ACTION_FIRE = "com.halo.yunquevoice.action.SCHEDULE_FIRE"
    private const val EXTRA_TYPE = "type"
    const val TYPE_START = "start"
    const val TYPE_STOP = "stop"

    fun loadRules(context: Context): MutableList<ScheduleRule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RULES, null)
            ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val days = mutableSetOf<Int>()
                val da = o.optJSONArray("days") ?: JSONArray()
                for (j in 0 until da.length()) days.add(da.getInt(j))
                ScheduleRule(
                    id = o.getLong("id"),
                    enabled = o.optBoolean("enabled", true),
                    startMin = o.getInt("start"),
                    endMin = o.getInt("end"),
                    days = days,
                    listenState = o.optString("listenState", ""),
                    silent = o.optBoolean("silent", false),
                    label = o.optString("label", ""),
                    source = o.optString("source", "manual")
                )
            }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun saveRules(context: Context, rules: List<ScheduleRule>) {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("enabled", r.enabled)
                    .put("start", r.startMin)
                    .put("end", r.endMin)
                    .put("days", JSONArray(r.days.sorted()))
                    .put("listenState", r.listenState)
                    .put("silent", r.silent)
                    .put("label", r.label)
                    .put("source", r.source)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RULES, arr.toString()).apply()
        armNext(context)
    }

    /** 下一次触发：(时刻ms, 类型, 规则)。无规则返回 null。 */
    fun nextTrigger(context: Context, now: Long = System.currentTimeMillis()): Triple<Long, String, ScheduleRule>? {
        val rules = loadRules(context).filter { it.enabled && it.days.isNotEmpty() }
        if (rules.isEmpty()) return null
        var best: Triple<Long, String, ScheduleRule>? = null
        for (offset in 0..8) {
            val day = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset) }
            // Calendar.MONDAY=2 … SUNDAY=1 → ISO 1..7
            val iso = ((day.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
            for (r in rules) {
                if (iso !in r.days) continue
                val dayStart = Calendar.getInstance().apply {
                    timeInMillis = day.timeInMillis
                    set(Calendar.HOUR_OF_DAY, r.startMin / 60); set(Calendar.MINUTE, r.startMin % 60)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val dayEnd = Calendar.getInstance().apply {
                    timeInMillis = day.timeInMillis
                    set(Calendar.HOUR_OF_DAY, r.endMin / 60); set(Calendar.MINUTE, r.endMin % 60)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    if (r.endMin <= r.startMin) add(Calendar.DAY_OF_YEAR, 1)
                }
                val startType = if (r.action == "stop" || r.action == "auto_restore") TYPE_STOP else TYPE_START
                val endType = if (r.action == "stop" || r.action == "normal") TYPE_START else TYPE_STOP
                if (dayStart.timeInMillis > now + 3000 && (best == null || dayStart.timeInMillis < best!!.first)) {
                    best = Triple(dayStart.timeInMillis, startType, r)
                }
                if (dayEnd.timeInMillis > now + 3000 && (best == null || dayEnd.timeInMillis < best!!.first)) {
                    best = Triple(dayEnd.timeInMillis, endType, r)
                }
            }
            if (best != null && offset >= 1) break // 已有明天内的结果，无需再往后
        }
        return best
    }

    /** 注册下一个闹钟（覆盖旧的）。规则清空时取消。 */
    fun armNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = firePendingIntent(context, type = "", ruleId = 0L) // 占位不用
        pi.cancel()
        am.cancel(pi)
        val next = nextTrigger(context) ?: return
        val intent = Intent(context, com.halo.yunquevoice.service.ScheduleReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_TYPE, next.second)
            .putExtra("ruleId", next.third.id)
        val pending = PendingIntent.getBroadcast(
            context, 10086, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.first, pending)
            VoiceMvpLog.i("SCHEDULE", "已注册下次定时：type=${next.second} at=${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(next.first))}")
        }.onFailure {
            // 无精确闹钟权限时降级：窗口闹钟（±几分钟误差）
            runCatching {
                am.setWindow(AlarmManager.RTC_WAKEUP, next.first, 5 * 60_000L, pending)
                VoiceMvpLog.w("SCHEDULE", "无精确闹钟权限，已降级为窗口闹钟（±5分钟误差）")
            }
        }
    }

    private fun firePendingIntent(context: Context, type: String, ruleId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, 10086,
            Intent(context, com.halo.yunquevoice.service.ScheduleReceiver::class.java)
                .setAction(ACTION_FIRE).putExtra(EXTRA_TYPE, type).putExtra("ruleId", ruleId),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun canExact(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
}
