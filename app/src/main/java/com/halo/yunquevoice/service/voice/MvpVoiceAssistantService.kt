package com.halo.yunquevoice.service.voice

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.halo.yunquevoice.service.AlwaysOnListeningService
import com.halo.yunquevoice.ui.VoiceAssistantComposeActivity

class MvpVoiceAssistantService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(MvpVoiceInteractionService.TAG, "MvpVoiceAssistantService onStartCommand: ${intent?.action}")
        // 小米蓝牙/语音触发 → 起停全天聆听
        if (intent?.action == "com.xiaoai.ACTION_BLUETOOTH_START_VOICEASSIST" ||
            intent?.action == "com.miui.voicetrigger.ACTION_VOICE_TRIGGER_START_VOICEASSIST"
        ) {
            val action = if (AlwaysOnListeningService.isRunning) {
                AlwaysOnListeningService.ACTION_STOP
            } else {
                AlwaysOnListeningService.ACTION_START
            }
            android.util.Log.i("YunqueVoice", "MvpVoiceAssistantService toggle action=$action running=${AlwaysOnListeningService.isRunning}")
            val listen = Intent(this, AlwaysOnListeningService::class.java)
                .setAction(action)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(listen) else startService(listen)
            return START_NOT_STICKY
        }
        val launch = Intent(this, VoiceAssistantComposeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            action = intent?.action
        }
        startActivity(launch)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
