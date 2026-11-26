package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object StudioRoute

@Serializable
object HistoryRoute

@Serializable
object SettingsRoute

@Serializable
data class PreviewRoute(val projectId: String)
