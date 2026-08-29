package com.halo.yunquevoice.service.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.halo.yunquevoice.service.AlwaysOnListeningService

class MvpVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // 系统语音助手唤起时，切换“全天聆听”起停
        val action = if (AlwaysOnListeningService.isRunning) {
            AlwaysOnListeningService.ACTION_STOP
        } else {
            AlwaysOnListeningService.ACTION_START
        }
        android.util.Log.i("YunqueVoice", "session onShow toggle action=$action running=${AlwaysOnListeningService.isRunning}")
        val intent = Intent(context, AlwaysOnListeningService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
