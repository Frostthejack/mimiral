package com.mimiral.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.mimiral.app.tts.TTSManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@Suppress("CustomClassInNonCustomPackage") // BuildConfig is generated in this package

@HiltAndroidApp
class MimiralApp : Application() {

    @Inject
    lateinit var ttsManager: TTSManager

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }

        // TTS initialization is deferred — it will be initialized lazily
        // when the user first opens a reader screen that uses TTS.
        // Previously, initializing TTS in onCreate() contributed to the
        // startup ANR because TextToSpeech constructor binds to the TTS
        // service on the main thread.
        Log.d("MimiralApp", "Application created — TTS init deferred to first use")
    }

    /**
     * Enable StrictMode for debug builds to detect main-thread I/O and other
     * violations that contribute to ANRs. Only runs in debug builds
     * (BuildConfig.DEBUG is false in release).
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }
}
