package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsRepository
import com.example.data.local.StudioSettings
import com.example.data.model.BackendMode
import com.example.data.model.DubbingVoiceProfile
import com.example.data.model.SourceLanguage
import com.example.data.network.SecureUrlValidator
import com.example.data.repository.TranslationRepository
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsFeedback(
    val isTestingConnection: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val repository: TranslationRepository
) : ViewModel() {

    val settings: StateFlow<StudioSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StudioSettings()
        )

    private val _feedback = MutableStateFlow(SettingsFeedback())
    val feedback: StateFlow<SettingsFeedback> = _feedback.asStateFlow()

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setAppLanguage(language) }
    }

    fun setDefaultSourceLanguage(language: SourceLanguage) {
        viewModelScope.launch { settingsRepository.setDefaultSourceLanguage(language) }
    }

    fun setDefaultVoiceProfile(profile: DubbingVoiceProfile) {
        viewModelScope.launch { settingsRepository.setDefaultVoiceProfile(profile) }
    }

    fun setDefaultBgmDuckingLevel(level: Float) {
        viewModelScope.launch { settingsRepository.setDefaultBgmDuckingLevel(level) }
    }

    fun setBackendMode(mode: BackendMode) {
        viewModelScope.launch { settingsRepository.setBackendMode(mode) }
    }

    fun saveAndTestHttpsEndpoint(
        url: String,
        invalidUrlError: String,
        successMessage: String
    ) {
        if (!SecureUrlValidator.isValidHttpsUrl(url)) {
            _feedback.value = SettingsFeedback(
                isTestingConnection = false,
                statusMessage = invalidUrlError,
                isError = true
            )
            return
        }
        viewModelScope.launch {
            settingsRepository.setCustomHttpsUrl(url)
            _feedback.value = SettingsFeedback(isTestingConnection = true)
            try {
                val result = repository.backendClient.verifyHealth(url)
                if (result.isSuccess) {
                    _feedback.value = SettingsFeedback(
                        isTestingConnection = false,
                        statusMessage = successMessage,
                        isError = false
                    )
                } else {
                    _feedback.value = SettingsFeedback(
                        isTestingConnection = false,
                        statusMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "ការពិនិត្យការតភ្ជាប់ TLS បានបរាជ័យ។",
                        isError = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _feedback.value = SettingsFeedback(
                    isTestingConnection = false,
                    statusMessage = e.localizedMessage ?: "ការតភ្ជាប់ TLS បានបរាជ័យ។",
                    isError = true
                )
            }
        }
    }

    fun clearCache(message: String) {
        viewModelScope.launch {
            repository.clearTemporaryCache()
            _feedback.value = SettingsFeedback(statusMessage = message, isError = false)
        }
    }

    fun clearFeedback() {
        _feedback.value = SettingsFeedback()
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val repository: TranslationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, repository) as T
        }
    }
}
