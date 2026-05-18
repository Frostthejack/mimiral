package com.mimiral.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.mimiral.app.data.local.settings.ReaderSettings
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.navigation.MimiralNavGraph
import com.mimiral.app.ui.theme.MimiralTheme
import com.mimiral.app.ui.theme.rememberMimiralThemeState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Volume key event channel to forward from Activity to Compose
    private var volumeKeyEventCallback: ((Int) -> Boolean)? = null

    fun setVolumeKeyEventCallback(callback: ((Int) -> Boolean)?) {
        volumeKeyEventCallback = callback
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Intercept volume keys before system handles them
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val consumed = volumeKeyEventCallback?.invoke(keyCode) ?: false
            if (consumed) {
                return true // Consumed by reader - don't change system volume
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MimiralTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent()
                }
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    val activity = context as MainActivity
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = ReaderSettings())
    val navController = rememberNavController()

    // Initialize theme from DataStore
    rememberMimiralThemeState()

    // Register volume key callback with Activity
    LaunchedEffect(settings.volumeKeyNavigationEnabled) {
        if (settings.volumeKeyNavigationEnabled) {
            activity.setVolumeKeyEventCallback { keyCode ->
                // The Activity-level interception prevents system volume change
                true
            }
        } else {
            activity.setVolumeKeyEventCallback(null)
        }
    }

    MimiralNavGraph(navController = navController)
}
