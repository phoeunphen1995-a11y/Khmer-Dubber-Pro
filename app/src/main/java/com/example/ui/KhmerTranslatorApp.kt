package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.di.AppContainer
import com.example.ui.components.LanguageToggleChip
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.SecurityTlsBadge
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.KhmerStrings
import com.example.ui.i18n.LocalStrings
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.PreviewRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.StudioRoute
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.KhmerTranslatorTheme
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TurquoiseContainer
import com.example.ui.theme.TurquoisePulse
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.PreviewViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.StudioViewModel

private data class TopLevelTab<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhmerTranslatorApp(appContainer: AppContainer) {
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            appContainer.settingsRepository,
            appContainer.translationRepository
        )
    )
    val studioViewModel: StudioViewModel = viewModel(
        factory = StudioViewModel.Factory(
            appContainer.translationRepository,
            appContainer.settingsRepository
        )
    )
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(appContainer.translationRepository)
    )

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val isOnline by studioViewModel.isOnline.collectAsStateWithLifecycle()

    val strings = if (settings.appLanguage == AppLanguage.KHMER) KhmerStrings else EnglishStrings

    KhmerTranslatorTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = backStackEntry?.destination
            val isPreviewRoute = currentDest?.hasRoute<PreviewRoute>() == true

            val tabs = listOf(
                TopLevelTab(
                    route = StudioRoute,
                    label = strings.studioTab,
                    selectedIcon = Icons.Filled.AutoAwesome,
                    unselectedIcon = Icons.Outlined.AutoAwesome,
                    testTag = "nav_studio"
                ),
                TopLevelTab(
                    route = HistoryRoute,
                    label = strings.historyTab,
                    selectedIcon = Icons.Filled.History,
                    unselectedIcon = Icons.Outlined.History,
                    testTag = "nav_history"
                ),
                TopLevelTab(
                    route = SettingsRoute,
                    label = strings.settingsTab,
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    testTag = "nav_settings"
                )
            )

            val navigateTopLevel: (Any) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianVoid)) {
                val useNavigationRail = maxWidth >= 600.dp && !isPreviewRoute

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MidnightNavy,
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        ) {
                            Spacer(modifier = Modifier.weight(0.2f))
                            tabs.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = TurquoisePulse,
                                        selectedTextColor = TurquoisePulse,
                                        indicatorColor = TurquoiseContainer,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        containerColor = ObsidianVoid,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = {
                            if (!isPreviewRoute) {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = strings.appBarTitle,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    actions = {
                                        SecurityTlsBadge(isOnline = isOnline)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        LanguageToggleChip(
                                            currentLanguage = settings.appLanguage,
                                            onToggleLanguage = settingsViewModel::setAppLanguage
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MidnightNavy
                                    )
                                )
                            }
                        },
                        bottomBar = {
                            if (!useNavigationRail && !isPreviewRoute) {
                                NavigationBar(
                                    containerColor = StudioSurface,
                                    tonalElevation = 0.dp
                                ) {
                                    tabs.forEach { tab ->
                                        val selected = currentDest?.hasRoute(tab.route::class) == true
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTopLevel(tab.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = tab.label,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = TurquoisePulse,
                                                selectedTextColor = TurquoisePulse,
                                                indicatorColor = TurquoiseContainer,
                                                unselectedIconColor = TextMuted,
                                                unselectedTextColor = TextMuted
                                            ),
                                            modifier = Modifier.testTag(tab.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (!isPreviewRoute) {
                                OfflineStatusBanner(visible = !isOnline)
                            }

                            NavHost(
                                navController = navController,
                                startDestination = StudioRoute,
                                modifier = Modifier.weight(1f)
                            ) {
                                composable<StudioRoute> {
                                    val studioState by studioViewModel.uiState.collectAsStateWithLifecycle()
                                    StudioScreen(
                                        uiState = studioState,
                                        appLanguage = settings.appLanguage,
                                        isOnline = isOnline,
                                        onImportVideo = studioViewModel::importVideoUri,
                                        onSourceLanguageSelected = studioViewModel::onSourceLanguageChanged,
                                        onVoiceProfileSelected = studioViewModel::onVoiceProfileChanged,
                                        onDubbingToggled = studioViewModel::onDubbingToggled,
                                        onSubtitlesToggled = studioViewModel::onSubtitlesToggled,
                                        onPreserveBgmToggled = studioViewModel::onPreserveBgmToggled,
                                        onBgmDuckingChanged = studioViewModel::onBgmDuckingChanged,
                                        onStartOrResume = { studioViewModel.startOrResumeProcessing() },
                                        onCancelProcessing = studioViewModel::cancelProcessing,
                                        onDismissError = studioViewModel::dismissError,
                                        onOpenPreview = { id ->
                                            navController.navigate(PreviewRoute(id))
                                        }
                                    )
                                }

                                composable<HistoryRoute> {
                                    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
                                    HistoryScreen(
                                        uiState = historyState,
                                        appLanguage = settings.appLanguage,
                                        onQueryChanged = historyViewModel::onQueryChanged,
                                        onFilterChanged = historyViewModel::onFilterChanged,
                                        onOpenPreview = { id ->
                                            navController.navigate(PreviewRoute(id))
                                        },
                                        onResumeJob = { id ->
                                            studioViewModel.startOrResumeProcessing(id)
                                            navigateTopLevel(StudioRoute)
                                        },
                                        onDeleteProject = historyViewModel::deleteProject
                                    )
                                }

                                composable<SettingsRoute> {
                                    val feedback by settingsViewModel.feedback.collectAsStateWithLifecycle()
                                    SettingsScreen(
                                        settings = settings,
                                        feedback = feedback,
                                        onSetLanguage = settingsViewModel::setAppLanguage,
                                        onSetDefaultSourceLang = settingsViewModel::setDefaultSourceLanguage,
                                        onSetDefaultVoice = settingsViewModel::setDefaultVoiceProfile,
                                        onSetDefaultBgmLevel = settingsViewModel::setDefaultBgmDuckingLevel,
                                        onSetBackendMode = settingsViewModel::setBackendMode,
                                        onTestHttpsUrl = settingsViewModel::saveAndTestHttpsEndpoint,
                                        onClearCache = settingsViewModel::clearCache
                                    )
                                }

                                composable<PreviewRoute> { entry ->
                                    val route = entry.toRoute<PreviewRoute>()
                                    val previewViewModel: PreviewViewModel = viewModel(
                                        key = route.projectId,
                                        factory = PreviewViewModel.Factory(
                                            projectId = route.projectId,
                                            repository = appContainer.translationRepository
                                        )
                                    )
                                    val previewState by previewViewModel.uiState.collectAsStateWithLifecycle()
                                    PreviewScreen(
                                        uiState = previewState,
                                        onBack = { navController.popBackStack() },
                                        onSelectAudioMode = previewViewModel::setAudioMode,
                                        onToggleDualSubtitles = previewViewModel::toggleDualSubtitles,
                                        onUpdateSegmentText = previewViewModel::updateSegmentKhmerText,
                                        onSaveSubtitleEdits = previewViewModel::saveSubtitleEdits,
                                        onExportVideo = previewViewModel::exportDubbedVideo,
                                        onExportSrt = previewViewModel::exportSrtFile,
                                        onBuildShareVideoIntent = previewViewModel::createShareVideoIntent,
                                        onBuildShareSrtIntent = previewViewModel::createShareSrtIntent,
                                        onClearStatusMessage = previewViewModel::clearStatusMessage
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
