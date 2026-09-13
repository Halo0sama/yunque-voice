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
        // 系统语音助手唤起：按用户配置的耳机功能键动作分发
        val btAction = com.halo.yunquevoice.voice.Store.btAction(context)
        val action = when (btAction) {
            com.halo.yunquevoice.voice.Store.BT_INTERRUPT -> AlwaysOnListeningService.ACTION_INTERRUPT
            com.halo.yunquevoice.voice.Store.BT_SPEAK_NOW -> AlwaysOnListeningService.ACTION_SPEAK_NOW
            else -> if (AlwaysOnListeningService.isRunning) AlwaysOnListeningService.ACTION_STOP
            else AlwaysOnListeningService.ACTION_START
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
