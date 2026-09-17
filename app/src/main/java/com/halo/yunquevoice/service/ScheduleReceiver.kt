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
        // silent 规则的方向已在排程时标好（start 时点=TYPE_STOP，end 时点=TYPE_START），此处原样执行
        val isStart = type == ScheduleStore.TYPE_START
        if (isStart) {
            // 恢复/开启聆听：仅非静音规则应用云雀状态（silent 的 START 只做恢复，不动仅聆听）
            when (if (rule?.silent != true) rule?.listenState else "") {
                "listen_only" -> com.halo.yunquevoice.voice.Store.saveListenOnly(context, true)
                "normal" -> com.halo.yunquevoice.voice.Store.saveListenOnly(context, false)
            }
        } else {
            // 停止聆听 + 清自动恢复标记（否则 STICKY 会在几秒内把聆听拉回来，停止形同虚设）
            com.halo.yunquevoice.voice.Store.saveListeningWasRunning(context, false)
        }
        val svc = Intent(context, AlwaysOnListeningService::class.java)
            .setAction(if (isStart) AlwaysOnListeningService.ACTION_START else AlwaysOnListeningService.ACTION_STOP)
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc) else context.startService(svc)
        notify(context, type)
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
