package com.mimiral.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.mimiral.app.data.local.scanner.ExternalBookHandler
import com.mimiral.app.data.local.scanner.ExternalBookResult
import com.mimiral.app.data.local.settings.ReaderSettings
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.navigation.MimiralNavGraph
import com.mimiral.app.navigation.routeForBookFormat
import com.mimiral.app.ui.theme.MimiralTheme
import com.mimiral.app.ui.theme.rememberMimiralThemeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a pending external book to be opened (from ACTION_VIEW intent).
 */
data class PendingExternalBook(
    val uri: Uri,
    val mimeType: String?
)

/**
 * Represents a processed external book ready for navigation.
 */
data class ResolvedExternalBook(
    val bookId: Int,
    val format: String
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Volume key event channel to forward from Activity to Compose
    private var volumeKeyEventCallback: ((Int) -> Boolean)? = null

    /** External book handler injected by Hilt. */
    lateinit var externalBookHandler: ExternalBookHandler

    /** Flow of pending external books from ACTION_VIEW intents. */
    private val _pendingExternalBook = MutableStateFlow<PendingExternalBook?>(null)
    val pendingExternalBook: StateFlow<PendingExternalBook?> = _pendingExternalBook.asStateFlow()

    /** Flow of resolved external books ready for navigation. */
    private val _resolvedExternalBook = MutableStateFlow<ResolvedExternalBook?>(null)
    val resolvedExternalBook: StateFlow<ResolvedExternalBook?> = _resolvedExternalBook.asStateFlow()

    /** Error message from external book import, if any. */
    private val _externalBookError = MutableStateFlow<String?>(null)
    val externalBookError: StateFlow<String?> = _externalBookError.asStateFlow()

    fun setVolumeKeyEventCallback(callback: ((Int) -> Boolean)?) {
        volumeKeyEventCallback = callback
    }

    /**
     * Process a pending external book URI: import it and resolve to a book ID.
     * Called from Compose layer after the navController is available.
     */
    suspend fun processExternalBook(pending: PendingExternalBook) {
        when (val result = externalBookHandler.importFromUri(pending.uri, pending.mimeType)) {
            is ExternalBookResult.Success -> {
                _resolvedExternalBook.value = ResolvedExternalBook(result.bookId, result.format)
            }
            is ExternalBookResult.AlreadyExists -> {
                _resolvedExternalBook.value = ResolvedExternalBook(result.bookId, result.format)
            }
            is ExternalBookResult.Error -> {
                _externalBookError.value = result.message
            }
        }
    }

    /**
     * Clear the pending external book after processing has started.
     */
    fun clearPendingExternalBook() {
        _pendingExternalBook.value = null
    }

    /**
     * Clear the resolved external book after navigation has occurred.
     */
    fun clearResolvedExternalBook() {
        _resolvedExternalBook.value = null
    }

    /**
     * Clear the error message.
     */
    fun clearExternalBookError() {
        _externalBookError.value = null
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

        // ExternalBookHandler is injected by Hilt, but we need to access it
        // via manual injection since we can't use @Inject on fields in Activity
        // with Hilt without @AndroidEntryPoint field injection
        externalBookHandler = (application as MimiralApp).externalBookHandler

        // Check for ACTION_VIEW intent
        handleIncomingIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Check if the intent is an ACTION_VIEW for an ebook file.
     * If so, set the pending external book for the Compose layer to process.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            if (uri != null) {
                val mimeType = intent.type
                _pendingExternalBook.value = PendingExternalBook(uri, mimeType)
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

    // Handle external book from ACTION_VIEW intent
    val pendingBook by activity.pendingExternalBook.collectAsState()
    val resolvedBook by activity.resolvedExternalBook.collectAsState()

    // Process pending external book (import into library)
    LaunchedEffect(pendingBook) {
        val pending = pendingBook
        if (pending != null) {
            activity.processExternalBook(pending)
            activity.clearPendingExternalBook()
        }
    }

    // Navigate to reader when book is resolved
    LaunchedEffect(resolvedBook) {
        val resolved = resolvedBook
        if (resolved != null) {
            val route = routeForBookFormat(resolved.bookId, resolved.format)
            navController.navigate(route)
            activity.clearResolvedExternalBook()
        }
    }

    MimiralNavGraph(navController = navController)
}
