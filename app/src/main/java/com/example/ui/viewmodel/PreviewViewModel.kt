package com.example.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.VideoProjectEntity
import com.example.data.model.AudioPreviewMode
import com.example.data.model.SubtitleSegment
import com.example.data.repository.TranslationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PreviewUiState(
    val project: VideoProjectEntity? = null,
    val segments: List<SubtitleSegment> = emptyList(),
    val audioMode: AudioPreviewMode = AudioPreviewMode.DUBBED_WITH_BGM,
    val showDualSubtitles: Boolean = true,
    val statusMessage: String? = null,
    val isExporting: Boolean = false,
    val hasUnsavedSubtitleEdits: Boolean = false
)

class PreviewViewModel(
    private val projectId: String,
    private val repository: TranslationRepository
) : ViewModel() {

    private val _audioMode = MutableStateFlow(AudioPreviewMode.DUBBED_WITH_BGM)
    private val _showDual = MutableStateFlow(true)
    private val _editedSegments = MutableStateFlow<List<SubtitleSegment>?>(null)
    private val _transient = MutableStateFlow(Pair<Boolean, String?>(false, null))

    val uiState: StateFlow<PreviewUiState> = combine(
        repository.observeProject(projectId),
        _audioMode,
        _showDual,
        _editedSegments,
        _transient
    ) { project, mode, dual, edited, transient ->
        val segments = edited ?: project?.parsedSegments().orEmpty()
        PreviewUiState(
            project = project,
            segments = segments,
            audioMode = mode,
            showDualSubtitles = dual,
            isExporting = transient.first,
            statusMessage = transient.second,
            hasUnsavedSubtitleEdits = edited != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreviewUiState()
    )

    fun setAudioMode(mode: AudioPreviewMode) {
        _audioMode.value = mode
    }

    fun toggleDualSubtitles() {
        _showDual.update { !it }
    }

    fun updateSegmentKhmerText(segmentIndex: Int, newText: String) {
        val currentList = uiState.value.segments
        _editedSegments.value = currentList.map { seg ->
            if (seg.index == segmentIndex) seg.copy(khmerText = newText) else seg
        }
    }

    fun saveSubtitleEdits(savedMessage: String) {
        val edited = _editedSegments.value ?: return
        viewModelScope.launch {
            _transient.value = true to null
            try {
                repository.updateSubtitles(projectId, edited)
                _editedSegments.value = null
                _transient.value = false to savedMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _transient.value = false to (e.localizedMessage ?: "មិនអាចរក្សាទុក SRT បានទេ។")
            }
        }
    }

    fun exportDubbedVideo(successMessage: String) {
        val project = uiState.value.project ?: return
        val path = project.dubbedVideoPath ?: project.localSourceVideoPath
        val file = File(path)
        if (!file.exists()) {
            _transient.value = false to "រកមិនឃើញឯកសារវីដេអូដែលបាននាំចេញទេ។"
            return
        }
        viewModelScope.launch {
            _transient.value = true to null
            try {
                repository.exportHelper.saveVideoToDeviceMovies(file, project.title)
                _transient.value = false to successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _transient.value = false to (e.localizedMessage ?: "ការនាំចេញវីដេអូបានបរាជ័យ។")
            }
        }
    }

    fun exportSrtFile(successMessage: String) {
        val project = uiState.value.project ?: return
        val segments = uiState.value.segments
        viewModelScope.launch {
            _transient.value = true to null
            try {
                val srtFile = if (_editedSegments.value != null || project.srtFilePath == null) {
                    repository.updateSubtitles(project.id, segments)
                    File(repository.observeProject(project.id).let { project.srtFilePath ?: "" })
                } else {
                    File(project.srtFilePath)
                }
                val finalFile = if (srtFile.exists()) {
                    srtFile
                } else {
                    repository.exportHelper.writeSrtToAppStorage(
                        project.id,
                        com.example.data.model.SrtFormatter.toSrt(segments)
                    )
                }
                repository.exportHelper.saveSrtToDownloads(finalFile, project.title)
                _transient.value = false to successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _transient.value = false to (e.localizedMessage ?: "ការនាំចេញ .SRT បានបរាជ័យ។")
            }
        }
    }

    fun createShareVideoIntent(chooserTitle: String): Intent? {
        val project = uiState.value.project ?: return null
        val file = File(project.dubbedVideoPath ?: project.localSourceVideoPath)
        if (!file.exists()) return null
        return repository.exportHelper.buildShareIntent(file, "video/mp4", chooserTitle)
    }

    fun createShareSrtIntent(chooserTitle: String): Intent? {
        val project = uiState.value.project ?: return null
        val path = project.srtFilePath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return repository.exportHelper.buildShareIntent(file, "application/x-subrip", chooserTitle)
    }

    fun clearStatusMessage() {
        _transient.update { it.copy(second = null) }
    }

    class Factory(
        private val projectId: String,
        private val repository: TranslationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PreviewViewModel(projectId, repository) as T
        }
    }
}
