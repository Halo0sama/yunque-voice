package com.halo.yunquevoice.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.halo.yunquevoice.voice.ScheduleStore
import com.halo.yunquevoice.voice.VoiceMvpLog

/**
 * 定时开关聆听触发器：到点执行开启/停止，发通知告知（隐私透明），并注册下一个计划点。
 * BOOT/时间变更后由 BootRescheduler 重排。
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduleStore.ACTION_FIRE) return
        val type = intent.getStringExtra("type") ?: return
        VoiceMvpLog.i("SCHEDULE", "定时触发：$type")
        val rule = ScheduleStore.loadRules(context).firstOrNull { it.id == intent.getLongExtra("ruleId", -1L) }
        val action = rule?.action ?: "normal"
        val isStart = type == ScheduleStore.TYPE_START
        val sp = context.getSharedPreferences("yunque_schedule", Context.MODE_PRIVATE)

        fun svc(start: Boolean) {
            val i = Intent(context, AlwaysOnListeningService::class.java)
                .setAction(if (start) AlwaysOnListeningService.ACTION_START else AlwaysOnListeningService.ACTION_STOP)
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
        when {
            // 停止聆听：开始→停（清 STICKY 标记防复活）；结束→恢复开启
            action == "stop" -> if (isStart) {
                com.halo.yunquevoice.voice.Store.saveListeningWasRunning(context, false)
                svc(false)
            } else {
                com.halo.yunquevoice.voice.Store.saveListeningWasRunning(context, true)
                svc(true)
            }
            // 自动恢复：开始→记住当前状态；结束→恢复到开始前状态
            action == "auto_restore" -> if (isStart) {
                sp.edit().putBoolean("auto_prev", AlwaysOnListeningService.isRunning).apply()
            } else {
                svc(sp.getBoolean("auto_prev", false))
            }
            // 仅聆听：开始→切仅聆听+开；结束→停并恢复正常
            action == "listen_only" -> if (isStart) {
                com.halo.yunquevoice.voice.Store.saveListenOnly(context, true)
                svc(true)
            } else {
                com.halo.yunquevoice.voice.Store.saveListenOnly(context, false)
                svc(false)
            }
            // 开启聆听：开始→开（正常）；结束→停
            else -> if (isStart) {
                com.halo.yunquevoice.voice.Store.saveListenOnly(context, false)
                svc(true)
            } else {
                svc(false)
            }
        }
        notify(context, if (isStart) ScheduleStore.TYPE_START else ScheduleStore.TYPE_STOP)
        ScheduleStore.armNext(context)
    }

    private fun notify(context: Context, type: String) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel("schedule", "定时开关聆听", NotificationManager.IMPORTANCE_LOW)
            )
            val notif = android.app.Notification.Builder(context, "schedule")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("云雀·私人助理")
                .setContentText(if (type == ScheduleStore.TYPE_START) "已按计划自动开启聆听" else "已按计划自动停止聆听")
                .setAutoCancel(true)
                .build()
            nm.notify(1004, notif)
        }
    }
}

/** 开机/时间变更/时区变更后重排定时计划。 */
class BootRescheduler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                ScheduleStore.armNext(context)
        }
    }
}
