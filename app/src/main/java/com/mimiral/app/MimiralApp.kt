package com.mimiral.app

import android.app.Application
import android.util.Log
import com.mimiral.app.data.local.scanner.ExternalBookHandler
import com.mimiral.app.tts.TTSManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MimiralApp : Application() {

    @Inject
    lateinit var ttsManager: TTSManager

    @Inject
    lateinit var externalBookHandler: ExternalBookHandler

    override fun onCreate() {
        super.onCreate()
        Log.d("MimiralApp", "Application starting — initializing TTS engine")
        ttsManager.initialize { success ->
            if (success) {
                Log.d("MimiralApp", "TTS engine initialized successfully")
            } else {
                Log.e("MimiralApp", "TTS engine initialization failed")
            }
        }
    }
}
