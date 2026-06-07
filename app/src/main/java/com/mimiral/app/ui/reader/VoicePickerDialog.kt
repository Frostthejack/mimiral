package com.mimiral.app.ui.reader

import android.content.Context
import android.speech.tts.Voice
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mimiral.app.R
import java.util.Locale

/**
 * Dialog for selecting a TTS voice from available voices.
 *
 * Self-initializes a TextToSpeech engine to query available voices,
 * then displays them in a scrollable list grouped by locale.
 *
 * @param currentVoiceName Name of the currently selected voice (empty = default)
 * @param onVoiceSelected Called when user selects a voice
 * @param onDismiss Called when dialog is dismissed
 */
@Composable
fun VoicePickerDialog(
    availableVoices: Set<Voice> = emptySet(),
    currentVoiceName: String,
    onVoiceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var voices by remember { mutableStateOf<Set<Voice>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    // Initialize TTS engine to query voices
    DisposableEffect(context) {
        val tts = TextToSpeech(context.applicationContext, null)
        ttsEngine = tts
        onDispose {
            tts.shutdown()
        }
    }

    // Query voices once engine is ready
    LaunchedEffect(ttsEngine) {
        val engine = ttsEngine
        if (engine != null) {
            // Small delay to let engine initialize
            kotlinx.coroutines.delay(500)
            voices = engine.voices ?: emptySet()
            isLoading = false
        }
    }

    val voicesList = remember(voices) {
        voices.sortedWith(
            compareBy<Voice> { it.locale.displayName }
                .thenBy { it.name }
        )
    }

    var selectedVoice by remember(currentVoiceName) {
        mutableStateOf(currentVoiceName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.tts_voice_picker_title))
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (voicesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No voices available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    // "Default" option at top
                    item {
                        VoicePickerItem(
                            label = stringResource(R.string.tts_voice_default),
                            subtitle = "System default voice",
                            isSelected = selectedVoice.isBlank(),
                            onClick = { selectedVoice = "" }
                        )
                    }

                    items(voicesList) { voice ->
                        VoicePickerItem(
                            label = voice.name,
                            subtitle = "${voice.locale.displayName} · ${voiceQualityLabel(voice)}",
                            isSelected = selectedVoice == voice.name,
                            onClick = { selectedVoice = voice.name }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onVoiceSelected(selectedVoice)
                }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun VoicePickerItem(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun voiceQualityLabel(voice: Voice): String {
    return when {
        voice.quality >= Voice.QUALITY_VERY_HIGH -> "Very High"
        voice.quality >= Voice.QUALITY_HIGH -> "High"
        voice.quality >= Voice.QUALITY_NORMAL -> "Normal"
        else -> "Low"
    }
}
