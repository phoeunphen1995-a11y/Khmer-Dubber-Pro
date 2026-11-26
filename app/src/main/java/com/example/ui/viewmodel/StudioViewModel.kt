package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsRepository
import com.example.data.local.VideoProjectEntity
import com.example.data.model.DubbingVoiceProfile
import com.example.data.model.ProcessingStage
import com.example.data.model.SourceLanguage
import com.example.data.repository.TranslationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioUiState(
    val activeProject: VideoProjectEntity? = null,
    val selectedSourceLanguage: SourceLanguage = SourceLanguage.AUTO,
    val selectedVoiceProfile: DubbingVoiceProfile = DubbingVoiceProfile.SREYPOV_FEMALE,
    val enableDubbing: Boolean = true,
    val enableSubtitles: Boolean = true,
    val preserveBgm: Boolean = true,
    val bgmDuckingLevel: Float = 0.25f,
    val isImporting: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

class StudioViewModel(
    private val repository: TranslationRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val activeProjectId = MutableStateFlow<String?>(null)
    private val _configState = MutableStateFlow(StudioUiState())
    private var processingJob: Job? = null

    val isOnline: StateFlow<Boolean> = repository.networkMonitor.isOnline

    val uiState: StateFlow<StudioUiState> = combine(
        _configState,
        repository.allProjects,
        activeProjectId
    ) { config, projects, currentId ->
        val project = if (currentId != null) {
            projects.firstOrNull { it.id == currentId }
        } else {
            config.activeProject
        }
        val processing = project?.stage in listOf(
            ProcessingStage.IMPORTING,
            ProcessingStage.EXTRACTING_AUDIO,
            ProcessingStage.TRANSCRIBING_TRANSLATING,
            ProcessingStage.SYNTHESIZING_VOICE,
            ProcessingStage.MIXING_RENDERING,
            ProcessingStage.GENERATING_SRT
        )
        config.copy(
            activeProject = project,
            isProcessing = processing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioUiState()
    )

    init {
        viewModelScope.launch {
            val defaults = settingsRepository.settingsFlow.first()
            _configState.update {
                it.copy(
                    selectedSourceLanguage = defaults.defaultSourceLanguage,
                    selectedVoiceProfile = defaults.defaultVoiceProfile,
                    enableDubbing = defaults.enableDubbingByDefault,
                    enableSubtitles = defaults.enableSubtitlesByDefault,
                    preserveBgm = defaults.preserveBgmByDefault,
                    bgmDuckingLevel = defaults.defaultBgmDuckingLevel
                )
            }
        }
    }

    fun onSourceLanguageChanged(language: SourceLanguage) {
        _configState.update { it.copy(selectedSourceLanguage = language) }
    }

    fun onVoiceProfileChanged(profile: DubbingVoiceProfile) {
        _configState.update { it.copy(selectedVoiceProfile = profile) }
    }

    fun onDubbingToggled(enabled: Boolean) {
        _configState.update { it.copy(enableDubbing = enabled) }
    }

    fun onSubtitlesToggled(enabled: Boolean) {
        _configState.update { it.copy(enableSubtitles = enabled) }
    }

    fun onPreserveBgmToggled(enabled: Boolean) {
        _configState.update { it.copy(preserveBgm = enabled) }
    }

    fun onBgmDuckingChanged(level: Float) {
        _configState.update { it.copy(bgmDuckingLevel = level.coerceIn(0.05f, 0.75f)) }
    }

    fun importVideoUri(uri: Uri) {
        processingJob?.cancel()
        viewModelScope.launch {
            _configState.update { it.copy(isImporting = true, errorMessage = null) }
            try {
                val cfg = _configState.value
                val imported = repository.importVideo(
                    uri = uri,
                    sourceLanguage = cfg.selectedSourceLanguage,
                    voiceProfile = cfg.selectedVoiceProfile,
                    enableDubbing = cfg.enableDubbing,
                    enableSubtitles = cfg.enableSubtitles,
                    preserveBgm = cfg.preserveBgm,
                    bgmDuckingLevel = cfg.bgmDuckingLevel
                )
                activeProjectId.value = imported.id
                _configState.update { it.copy(activeProject = imported, isImporting = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _configState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = e.localizedMessage ?: "មិនអាចនាំចូលវីដេអូបានទេ។"
                    )
                }
            }
        }
    }

    fun startOrResumeProcessing(projectIdOverride: String? = null) {
        val targetId = projectIdOverride ?: uiState.value.activeProject?.id ?: return
        activeProjectId.value = targetId
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _configState.update { it.copy(errorMessage = null) }
            try {
                repository.processProject(targetId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _configState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "ការបកប្រែត្រូវបានរំខាន។")
                }
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _configState.update { it.copy(errorMessage = null) }
    }

    fun dismissError() {
        _configState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val repository: TranslationRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(repository, settingsRepository) as T
        }
    }
}
