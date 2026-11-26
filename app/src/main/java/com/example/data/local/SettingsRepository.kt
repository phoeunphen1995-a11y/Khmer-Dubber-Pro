package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.BackendMode
import com.example.data.model.DubbingVoiceProfile
import com.example.data.model.SourceLanguage
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_settings")

data class StudioSettings(
    val appLanguage: AppLanguage = AppLanguage.KHMER,
    val defaultSourceLanguage: SourceLanguage = SourceLanguage.AUTO,
    val defaultVoiceProfile: DubbingVoiceProfile = DubbingVoiceProfile.SREYPOV_FEMALE,
    val enableDubbingByDefault: Boolean = true,
    val enableSubtitlesByDefault: Boolean = true,
    val preserveBgmByDefault: Boolean = true,
    val defaultBgmDuckingLevel: Float = 0.25f,
    val backendMode: BackendMode = BackendMode.FIREBASE_AI_LOGIC,
    val customHttpsUrl: String = ""
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val DEFAULT_SOURCE_LANG = stringPreferencesKey("default_source_lang")
        val DEFAULT_VOICE_PROFILE = stringPreferencesKey("default_voice_profile")
        val ENABLE_DUBBING = booleanPreferencesKey("enable_dubbing")
        val ENABLE_SUBTITLES = booleanPreferencesKey("enable_subtitles")
        val PRESERVE_BGM = booleanPreferencesKey("preserve_bgm")
        val BGM_DUCKING_LEVEL = floatPreferencesKey("bgm_ducking_level")
        val BACKEND_MODE = stringPreferencesKey("backend_mode")
        val CUSTOM_HTTPS_URL = stringPreferencesKey("custom_https_url")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            appLanguage = if (prefs[Keys.APP_LANGUAGE] == AppLanguage.ENGLISH.code) {
                AppLanguage.ENGLISH
            } else {
                AppLanguage.KHMER
            },
            defaultSourceLanguage = SourceLanguage.fromCode(
                prefs[Keys.DEFAULT_SOURCE_LANG] ?: SourceLanguage.AUTO.code
            ),
            defaultVoiceProfile = DubbingVoiceProfile.fromId(
                prefs[Keys.DEFAULT_VOICE_PROFILE] ?: DubbingVoiceProfile.SREYPOV_FEMALE.id
            ),
            enableDubbingByDefault = prefs[Keys.ENABLE_DUBBING] ?: true,
            enableSubtitlesByDefault = prefs[Keys.ENABLE_SUBTITLES] ?: true,
            preserveBgmByDefault = prefs[Keys.PRESERVE_BGM] ?: true,
            defaultBgmDuckingLevel = (prefs[Keys.BGM_DUCKING_LEVEL] ?: 0.25f).coerceIn(0.05f, 0.75f),
            backendMode = BackendMode.fromId(
                prefs[Keys.BACKEND_MODE] ?: BackendMode.FIREBASE_AI_LOGIC.id
            ),
            customHttpsUrl = prefs[Keys.CUSTOM_HTTPS_URL] ?: ""
        )
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.studioDataStore.edit { it[Keys.APP_LANGUAGE] = language.code }
    }

    suspend fun setDefaultSourceLanguage(language: SourceLanguage) {
        context.studioDataStore.edit { it[Keys.DEFAULT_SOURCE_LANG] = language.code }
    }

    suspend fun setDefaultVoiceProfile(profile: DubbingVoiceProfile) {
        context.studioDataStore.edit { it[Keys.DEFAULT_VOICE_PROFILE] = profile.id }
    }

    suspend fun setDefaultBgmDuckingLevel(level: Float) {
        context.studioDataStore.edit { it[Keys.BGM_DUCKING_LEVEL] = level.coerceIn(0.05f, 0.75f) }
    }

    suspend fun setBackendMode(mode: BackendMode) {
        context.studioDataStore.edit { it[Keys.BACKEND_MODE] = mode.id }
    }

    suspend fun setCustomHttpsUrl(url: String) {
        context.studioDataStore.edit { it[Keys.CUSTOM_HTTPS_URL] = url.trim() }
    }
}
