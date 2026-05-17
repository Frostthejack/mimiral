package com.mimiral.app.tts

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TTSService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
}
