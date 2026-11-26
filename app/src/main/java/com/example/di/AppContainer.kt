package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsRepository
import com.example.data.network.NetworkMonitor
import com.example.data.repository.TranslationRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }
    val translationRepository: TranslationRepository by lazy {
        TranslationRepository(
            context = appContext,
            dao = database.videoProjectDao(),
            settingsRepository = settingsRepository,
            networkMonitor = networkMonitor
        )
    }
}
