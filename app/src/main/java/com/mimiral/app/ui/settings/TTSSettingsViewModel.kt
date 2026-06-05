package com.mimiral.app.ui.settings

import android.app.Application
import android.speech.tts.Voice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.settings.TTSSettings
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.tts.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class TTSSettingsUiState(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String = "",
    val localeTag: String = "",
    val availableVoices: Set<Voice> = emptySet(),
    val availableLocales: List<Locale> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TTSSettingsViewModel @Inject constructor(
    application: Application,
    private val ttsSettingsRepository: TTSSettingsRepository
) : AndroidViewModel(application) {

    private val ttsManager = TTSManager(application.applicationContext)

    private val _uiState = MutableStateFlow(TTSSettingsUiState())
    val uiState: StateFlow<TTSSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Collect persisted settings
            ttsSettingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        speechRate = settings.speechRate,
                        pitch = settings.pitch,
                        voiceName = settings.voiceName,
                        localeTag = settings.localeTag,
                        isLoading = false
                    )
                }
            }
        }

        // Initialize TTS engine to query available voices
        ttsManager.initialize { success ->
            if (success) {
                _uiState.update {
                    it.copy(
                        availableVoices = ttsManager.getAvailableVoices(),
                        availableLocales = ttsManager.getAvailableLocales()
                    )
                }
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        _uiState.update { it.copy(speechRate = rate) }
        viewModelScope.launch {
            ttsSettingsRepository.setSpeechRate(rate)
        }
        ttsManager.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        _uiState.update { it.copy(pitch = pitch) }
        viewModelScope.launch {
            ttsSettingsRepository.setPitch(pitch)
        }
        ttsManager.setPitch(pitch)
    }

    fun setVoice(voice: Voice?) {
        val voiceName = voice?.name ?: ""
        val localeTag = voice?.locale?.toLanguageTag() ?: ""
        _uiState.update { it.copy(voiceName = voiceName, localeTag = localeTag) }
        viewModelScope.launch {
            ttsSettingsRepository.setVoiceName(voiceName)
            ttsSettingsRepository.setLocaleTag(localeTag)
        }
        if (voice != null) {
            ttsManager.setVoice(voice)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            ttsSettingsRepository.resetToDefaults()
        }
        _uiState.update {
            it.copy(
                speechRate = 1.0f,
                pitch = 1.0f,
                voiceName = "",
                localeTag = ""
            )
        }
        ttsManager.setSpeechRate(1.0f)
        ttsManager.setPitch(1.0f)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
