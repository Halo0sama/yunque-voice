package com.halo.yunquevoice.service.voice

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

class MvpRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.i(MvpVoiceInteractionService.TAG, "Recognition onStartListening")
    }

    override fun onCancel(listener: Callback?) {
        Log.i(MvpVoiceInteractionService.TAG, "Recognition onCancel")
    }

    override fun onStopListening(listener: Callback?) {
        Log.i(MvpVoiceInteractionService.TAG, "Recognition onStopListening")
    }
}
