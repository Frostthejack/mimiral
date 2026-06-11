package com.mimiral.app.ui.settings

import android.speech.tts.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TTSSettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: TTSSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tts_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.tts_reset_defaults)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Voice Selection ──────────────────────────────
            Text(
                text = stringResource(R.string.tts_voice_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    VoiceSelector(
                        availableVoices = uiState.availableVoices,
                        currentVoiceName = uiState.voiceName,
                        onVoiceSelected = { voice -> viewModel.setVoice(voice) }
                    )
                }
            }

            // ── Speech Rate ──────────────────────────────────
            Text(
                text = stringResource(R.string.tts_speed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TTSSliderRow(
                        label = stringResource(R.string.tts_speed),
                        value = uiState.speechRate,
                        valueRange = 0.5f..3.0f,
                        valueFormat = stringResource(R.string.tts_speed_format),
                        onValueChange = { viewModel.setSpeechRate(it) }
                    )
                }
            }

            // ── Pitch ────────────────────────────────────────
            Text(
                text = stringResource(R.string.tts_pitch),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TTSSliderRow(
                        label = stringResource(R.string.tts_pitch),
                        value = uiState.pitch,
                        valueRange = 0.5f..2.0f,
                        valueFormat = stringResource(R.string.tts_pitch_format),
                        onValueChange = { viewModel.setPitch(it) }
                    )
                }
            }

            // ── Reset Button ─────────────────────────────────
            OutlinedButton(
                onClick = { viewModel.resetToDefaults() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.tts_reset_defaults))
            }
        }
    }
}

@Composable
private fun VoiceSelector(
    availableVoices: Set<Voice>,
    currentVoiceName: String,
    onVoiceSelected: (Voice?) -> Unit
) {
    val voicesList = remember(availableVoices) {
        availableVoices.sortedWith(
            compareBy<Voice> { it.locale.displayName }
                .thenBy { it.name }
        )
    }

    val selectedVoice = if (currentVoiceName.isBlank()) {
        null
    } else {
        voicesList.find { it.name == currentVoiceName }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.RecordVoiceOver,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (selectedVoice != null) {
                    selectedVoice.name
                } else {
                    stringResource(R.string.tts_voice_default)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (selectedVoice != null) {
                val voiceDesc = "${selectedVoice.locale.displayName} · ${voiceQualityLabel(
                    selectedVoice
                )}"
                Text(
                    text = voiceDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.tts_voice_default_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (voicesList.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        voicesList.forEach { voice ->
            val isSelected = currentVoiceName == voice.name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVoiceSelected(voice) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = isSelected,
                    onClick = { onVoiceSelected(voice) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = voice.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "${voice.locale.displayName} · ${voiceQualityLabel(voice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TTSSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormat: String,
    onValueChange: (Float) -> Unit
) {
    var sliderPosition by remember(value) { mutableFloatStateOf(value) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Speed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(48.dp)
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
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(48.dp)
        )
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

@Composable
private fun HorizontalDivider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
