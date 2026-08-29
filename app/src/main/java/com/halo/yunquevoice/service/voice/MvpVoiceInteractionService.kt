package com.halo.yunquevoice.service.voice

import android.service.voice.VoiceInteractionService
import android.util.Log

class MvpVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "onReady: 语音助手服务就绪")
    }

    companion object {
        const val TAG = "YunqueVoice"
    }
}
