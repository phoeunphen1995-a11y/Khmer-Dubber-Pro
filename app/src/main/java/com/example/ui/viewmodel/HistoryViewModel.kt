package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.VideoProjectEntity
import com.example.data.model.ProcessingStage
import com.example.data.repository.TranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL,
    COMPLETED,
    INTERRUPTED
}

data class HistoryUiState(
    val projects: List<VideoProjectEntity> = emptyList(),
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL
)

class HistoryViewModel(
    private val repository: TranslationRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.allProjects,
        _query,
        _filter
    ) { all, query, filter ->
        val filtered = all.filter { project ->
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.COMPLETED -> project.stage == ProcessingStage.COMPLETED
                HistoryFilter.INTERRUPTED -> project.stage == ProcessingStage.FAILED ||
                    project.stage == ProcessingStage.PAUSED_OFFLINE
            }
            val q = query.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                project.title.lowercase().contains(q) ||
                project.format.lowercase().contains(q) ||
                project.sourceLanguageCode.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
        HistoryUiState(projects = filtered, query = query, filter = filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun onFilterChanged(filter: HistoryFilter) {
        _filter.value = filter
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    class Factory(private val repository: TranslationRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}
