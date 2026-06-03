package com.mimiral.app.ui.reader

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mimiral.app.R
import com.mimiral.app.tts.TTSService
import com.mimiral.app.tts.TTSState
import java.util.Locale

/**
 * Persistent bottom bar for TTS playback controls.
 *
 * Shows play/pause toggle, stop button, speed slider (0.5x-3.0x),
 * pitch slider (0.5-2.0), and voice picker button.
 *
 * The bar is only visible when TTS has been initialized (state != IDLE).
 *
 * @param ttsState Current TTS playback state
 * @param currentSpeed Current speech rate (0.5-3.0)
 * @param currentPitch Current pitch (0.5-2.0)
 * @param currentVoiceName Currently selected voice name (empty = default)
 * @param onPlayPause Toggle play/pause
 * @param onStop Stop playback
 * @param onSpeedChanged Called when user adjusts speed slider
 * @param onPitchChanged Called when user adjusts pitch slider
 * @param onVoicePickerOpen Request to show voice picker dialog
 * @param modifier Layout modifier
 */
@Composable
fun TtsControlsBar(
    ttsState: TTSState,
    currentSpeed: Float,
    currentPitch: Float,
    currentVoiceName: String,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onPitchChanged: (Float) -> Unit,
    onVoicePickerOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying = ttsState == TTSState.PLAYING
    val isPaused = ttsState == TTSState.PAUSED
    val isActive = isPlaying || isPaused || ttsState == TTSState.READY

    if (!isActive) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Row 1: Transport controls + voice picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Transport buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Play/Pause toggle
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.tts_pause)
                            } else {
                                stringResource(R.string.tts_play)
                            },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Stop
                    IconButton(onClick = onStop) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.tts_stop),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Voice picker button
                TextButton(onClick = onVoicePickerOpen) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (currentVoiceName.isBlank()) {
                            stringResource(R.string.tts_voice_default)
                        } else {
                            currentVoiceName
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            // Row 2: Speed slider
            TtsSliderRow(
                label = stringResource(R.string.tts_speed),
                value = currentSpeed,
                valueRange = 0.5f..3.0f,
                valueFormat = stringResource(R.string.tts_speed_format),
                onValueChange = onSpeedChanged
            )

            // Row 3: Pitch slider
            TtsSliderRow(
                label = stringResource(R.string.tts_pitch),
                value = currentPitch,
                valueRange = 0.5f..2.0f,
                valueFormat = stringResource(R.string.tts_pitch_format),
                onValueChange = onPitchChanged
            )
        }
    }
}

/**
 * A labeled slider row for TTS parameter adjustment.
 */
@Composable
private fun TtsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormat: String,
    onValueChange: (Float) -> Unit
) {
    var sliderPosition by remember(value) { mutableFloatStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                onValueChange(sliderPosition)
            },
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format(Locale.US, valueFormat, sliderPosition),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}

/**
 * Convenience composable that creates intents for TTS control actions.
 * Use this from EpubReaderScreen to wire up the controls.
 */
object TtsControlsHelper {

    fun play(context: Context, text: String) {
        val intent = TTSService.createPlayIntent(context, text)
        context.startService(intent)
    }

    fun pause(context: Context) {
        val intent = TTSService.createPauseIntent(context)
        context.startService(intent)
    }

    fun resume(context: Context) {
        val intent = TTSService.createResumeIntent(context)
        context.startService(intent)
    }

    fun stop(context: Context) {
        val intent = TTSService.createStopIntent(context)
        context.startService(intent)
    }

    fun toggle(context: Context) {
        val intent = TTSService.createToggleIntent(context)
        context.startService(intent)
    }

    fun setSpeed(context: Context, speed: Float) {
        val intent = TTSService.createSetSpeedIntent(context, speed)
        context.startService(intent)
    }

    fun setPitch(context: Context, pitch: Float) {
        val intent = TTSService.createSetPitchIntent(context, pitch)
        context.startService(intent)
    }

    fun setVoice(context: Context, voiceName: String) {
        val intent = TTSService.createSetVoiceIntent(context, voiceName)
        context.startService(intent)
    }
}
