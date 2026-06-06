package com.mimiral.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.mimiral.app.data.local.scanner.ExternalBookHandler
import com.mimiral.app.tts.TTSManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@Suppress("CustomClassInNonCustomPackage") // BuildConfig is generated in this package

@HiltAndroidApp
class MimiralApp : Application() {

    @Inject
    lateinit var ttsManager: TTSManager

    @Inject
    lateinit var externalBookHandler: ExternalBookHandler

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
     * Enable StrictMode for debug builds to detect network-on-main-thread
     * violations only. We intentionally avoid detectAll() because it enables
     * disk-read/disk-write detection, which fires on every Room and Hilt
     * call during cold start. On slow emulators or low-end devices the
     * combined StrictMode penalty logging + JIT verification overhead exceeds
     * the 10 s startup timeout and triggers an ANR.
     *
     * Baseline Profiles + R8 full mode already mitigate most startup I/O;
     * StrictMode is kept only for network catches during development.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }
}
