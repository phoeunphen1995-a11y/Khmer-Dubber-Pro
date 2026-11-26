package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.VideoProjectEntity
import com.example.data.model.DubbingVoiceProfile
import com.example.data.model.ProcessingStage
import com.example.data.model.SourceLanguage
import com.example.data.model.SrtFormatter
import com.example.data.model.VideoFormat
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TurquoiseContainer
import com.example.ui.theme.TurquoiseDeep
import com.example.ui.theme.TurquoisePulse
import com.example.ui.viewmodel.StudioUiState
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    uiState: StudioUiState,
    appLanguage: AppLanguage,
    isOnline: Boolean,
    onImportVideo: (Uri) -> Unit,
    onSourceLanguageSelected: (SourceLanguage) -> Unit,
    onVoiceProfileSelected: (DubbingVoiceProfile) -> Unit,
    onDubbingToggled: (Boolean) -> Unit,
    onSubtitlesToggled: (Boolean) -> Unit,
    onPreserveBgmToggled: (Boolean) -> Unit,
    onBgmDuckingChanged: (Float) -> Unit,
    onStartOrResume: () -> Unit,
    onCancelProcessing: () -> Unit,
    onDismissError: () -> Unit,
    onOpenPreview: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportVideo(uri)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImportVideo(uri)
    }

    val launchDocumentPicker = {
        documentPickerLauncher.launch(
            arrayOf(
                "video/mp4",
                "video/quicktime",
                "video/x-matroska",
                "video/webm",
                "video/x-msvideo",
                "video/avi",
                "video/*"
            )
        )
    }

    val launchPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianVoid, MidnightNavy, ObsidianVoid)
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Studio Banner Card
            HeroStudioCard(
                badge = strings.heroBadge,
                title = strings.heroTitle,
                subtitle = strings.heroSubtitle
            )

            // 2. Import or Active Video Card
            val activeProject = uiState.activeProject
            if (activeProject == null) {
                ImportDropzoneCard(
                    isImporting = uiState.isImporting,
                    onBrowseFiles = launchDocumentPicker,
                    onPickGallery = launchPhotoPicker
                )
            } else {
                ImportedVideoCard(
                    project = activeProject,
                    isImporting = uiState.isImporting,
                    isProcessing = uiState.isProcessing,
                    onChangeVideo = launchDocumentPicker
                )
            }

            // 3. Error / Interruption Feedback Card
            val errorMsg = uiState.errorMessage ?: activeProject?.errorMessage
            AnimatedVisibility(visible = !errorMsg.isNullOrBlank() && !uiState.isProcessing) {
                ErrorCalloutCard(
                    message = errorMsg.orEmpty(),
                    onRetry = onStartOrResume,
                    onDismiss = onDismissError,
                    retryLabel = strings.retryProcessing
                )
            }

            // 4. Live Processing Progress Card
            AnimatedVisibility(
                visible = activeProject != null &&
                    activeProject.stage != ProcessingStage.IDLE
            ) {
                if (activeProject != null) {
                    ProcessingProgressCard(
                        project = activeProject,
                        isProcessing = uiState.isProcessing,
                        onCancel = onCancelProcessing,
                        onRetry = onStartOrResume,
                        onOpenPreview = { onOpenPreview(activeProject.id) }
                    )
                }
            }

            // 5. Source Language -> Khmer & Voice Profile Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Direction Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.sourceLanguageLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.KHMER) {
                                    uiState.selectedSourceLanguage.khmerName
                                } else {
                                    uiState.selectedSourceLanguage.englishName
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Translate to Khmer",
                            tint = TurquoisePulse,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = strings.targetLanguageLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = TurquoiseContainer,
                                border = BorderStroke(1.dp, TurquoisePulse.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = strings.khmerTargetFixed,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TurquoisePulse,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Source Language Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceLanguage.entries.forEach { lang ->
                            val selected = uiState.selectedSourceLanguage == lang
                            FilterChip(
                                selected = selected,
                                onClick = { onSourceLanguageSelected(lang) },
                                enabled = !uiState.isProcessing,
                                label = {
                                    Text(
                                        text = if (appLanguage == AppLanguage.KHMER) lang.khmerName else lang.englishName,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TurquoiseContainer,
                                    selectedLabelColor = TurquoisePulse,
                                    containerColor = StudioSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = StudioOutline,
                                    selectedBorderColor = TurquoisePulse
                                ),
                                modifier = Modifier.testTag("source_lang_${lang.code}")
                            )
                        }
                    }

                    // Voice Profile Selector
                    Text(
                        text = strings.dubbingVoiceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DubbingVoiceProfile.entries.forEach { profile ->
                            val selected = uiState.selectedVoiceProfile == profile
                            FilterChip(
                                selected = selected,
                                onClick = { onVoiceProfileSelected(profile) },
                                enabled = !uiState.isProcessing,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (appLanguage == AppLanguage.KHMER) profile.khmerName else profile.englishName,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TurquoiseContainer,
                                    selectedLabelColor = TurquoisePulse,
                                    selectedLeadingIconColor = TurquoisePulse,
                                    containerColor = StudioSurfaceElevated,
                                    labelColor = TextSecondary,
                                    iconColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = StudioOutline,
                                    selectedBorderColor = TurquoisePulse
                                ),
                                modifier = Modifier.testTag("voice_profile_${profile.id}")
                            )
                        }
                    }
                }
            }

            // 6. AI Audio & Subtitle Pipeline Options Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.aiOptionsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    PipelineToggleRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = strings.enableKhmerDubbing,
                        subtitle = strings.enableKhmerDubbingDesc,
                        checked = uiState.enableDubbing,
                        enabled = !uiState.isProcessing,
                        testTag = "toggle_dubbing",
                        onCheckedChange = onDubbingToggled
                    )

                    PipelineToggleRow(
                        icon = Icons.Default.Subtitles,
                        title = strings.enableKhmerSrt,
                        subtitle = strings.enableKhmerSrtDesc,
                        checked = uiState.enableSubtitles,
                        enabled = !uiState.isProcessing,
                        testTag = "toggle_subtitles",
                        onCheckedChange = onSubtitlesToggled
                    )

                    PipelineToggleRow(
                        icon = Icons.Default.MusicNote,
                        title = strings.preserveBgmSfx,
                        subtitle = strings.preserveBgmSfxDesc,
                        checked = uiState.preserveBgm,
                        enabled = !uiState.isProcessing,
                        testTag = "toggle_preserve_bgm",
                        onCheckedChange = onPreserveBgmToggled
                    )

                    AnimatedVisibility(visible = uiState.preserveBgm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(StudioSurfaceElevated)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.bgmLevelLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${(uiState.bgmDuckingLevel * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TurquoisePulse
                                )
                            }
                            Slider(
                                value = uiState.bgmDuckingLevel,
                                onValueChange = onBgmDuckingChanged,
                                valueRange = 0.05f..0.75f,
                                enabled = !uiState.isProcessing,
                                colors = SliderDefaults.colors(
                                    thumbColor = TurquoisePulse,
                                    activeTrackColor = TurquoisePulse,
                                    inactiveTrackColor = StudioOutline
                                ),
                                modifier = Modifier.testTag("bgm_mix_slider")
                            )
                        }
                    }
                }
            }

            // 7. Primary Action Button
            val canStart = activeProject != null && !uiState.isImporting && !uiState.isProcessing
            Button(
                onClick = onStartOrResume,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("translate_dub_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TurquoisePulse,
                    contentColor = Color(0xFF021E1B),
                    disabledContainerColor = StudioSurfaceElevated,
                    disabledContentColor = TextMuted
                )
            ) {
                if (uiState.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TurquoisePulse,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.stageAiTranslating,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (activeProject?.stage == ProcessingStage.PAUSED_OFFLINE ||
                            activeProject?.stage == ProcessingStage.FAILED
                        ) {
                            strings.retryProcessing
                        } else {
                            strings.startTranslation
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (!isOnline && activeProject != null && activeProject.stage != ProcessingStage.COMPLETED) {
                Text(
                    text = strings.offlineBannerSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AmberWarm,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroStudioCard(
    badge: String,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, StudioOutline, MaterialTheme.shapes.large)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_studio_banner),
            contentDescription = "Khmer AI Video Translator studio banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ObsidianVoid.copy(alpha = 0.92f),
                            MidnightNavy.copy(alpha = 0.78f),
                            ObsidianVoid.copy(alpha = 0.55f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = TurquoiseContainer.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, TurquoisePulse.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = TurquoisePulse,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = TurquoisePulse
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ImportDropzoneCard(
    isImporting: Boolean,
    onBrowseFiles: () -> Unit,
    onPickGallery: () -> Unit
) {
    val strings = LocalStrings.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.5.dp, TurquoisePulse.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(TurquoiseContainer),
                contentAlignment = Alignment.Center
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = TurquoisePulse,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = strings.importVideoTitle,
                        tint = TurquoisePulse,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Text(
                text = strings.importVideoTitle,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = strings.importVideoHint,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Format badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoFormat.entries.forEach { fmt ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioOutline)
                    ) {
                        Text(
                            text = fmt.extension,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanGlow,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onBrowseFiles,
                    enabled = !isImporting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("browse_video_files_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TurquoisePulse,
                        contentColor = Color(0xFF021E1B)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.pickFromFiles,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onPickGallery,
                    enabled = !isImporting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_video_gallery_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StudioSurfaceHighest,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.pickFromGallery,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportedVideoCard(
    project: VideoProjectEntity,
    isImporting: Boolean,
    isProcessing: Boolean,
    onChangeVideo: () -> Unit
) {
    val strings = LocalStrings.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, TurquoisePulse.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 74.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(StudioSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = project.thumbnailPath?.let(::File)?.takeIf { it.exists() }
                if (thumbFile != null) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = project.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = project.title,
                        tint = TurquoisePulse,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ObsidianVoid.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                ) {
                    Text(
                        text = project.format,
                        style = MaterialTheme.typography.labelSmall,
                        color = TurquoisePulse,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeMb = String.format(java.util.Locale.US, "%.1f MB", project.fileSizeBytes / (1024.0 * 1024.0))
                val res = if (project.width > 0 && project.height > 0) " • ${project.width}×${project.height}" else ""
                Text(
                    text = "${SrtFormatter.formatDisplayDuration(project.durationMs)} • $sizeMb$res",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedButton(
                    onClick = onChangeVideo,
                    enabled = !isImporting && !isProcessing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .testTag("change_video_button")
                ) {
                    Text(
                        text = strings.replaceVideo,
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanGlow
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingProgressCard(
    project: VideoProjectEntity,
    isProcessing: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenPreview: () -> Unit
) {
    val strings = LocalStrings.current
    val stage = project.stage
    val stageText = when (stage) {
        ProcessingStage.IMPORTING -> strings.stageImporting
        ProcessingStage.EXTRACTING_AUDIO -> strings.stageExtractingAudio
        ProcessingStage.TRANSCRIBING_TRANSLATING -> strings.stageAiTranslating
        ProcessingStage.SYNTHESIZING_VOICE -> strings.stageSynthesizingVoice
        ProcessingStage.MIXING_RENDERING -> strings.stageMixingRender
        ProcessingStage.GENERATING_SRT -> strings.stageGeneratingSrt
        ProcessingStage.COMPLETED -> strings.stageCompleted
        ProcessingStage.PAUSED_OFFLINE -> strings.stagePausedOffline
        ProcessingStage.FAILED -> strings.stageFailed
        ProcessingStage.IDLE -> ""
    }

    val accentColor = when (stage) {
        ProcessingStage.COMPLETED -> EmeraldSuccess
        ProcessingStage.PAUSED_OFFLINE -> AmberWarm
        ProcessingStage.FAILED -> CrimsonAlert
        else -> TurquoisePulse
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (stage) {
                            ProcessingStage.COMPLETED -> Icons.Default.CheckCircle
                            ProcessingStage.PAUSED_OFFLINE, ProcessingStage.FAILED -> Icons.Default.ErrorOutline
                            else -> Icons.Default.AudioFile
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stageText,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "${(project.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }

            LinearProgressIndicator(
                progress = { project.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .testTag("video_processing_progress"),
                color = accentColor,
                trackColor = StudioOutline
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isProcessing) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("cancel_processing_button")
                    ) {
                        Text(strings.cancelProcessing, color = CrimsonAlert)
                    }
                } else if (stage == ProcessingStage.COMPLETED) {
                    Button(
                        onClick = onOpenPreview,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TurquoisePulse,
                            contentColor = Color(0xFF021E1B)
                        ),
                        modifier = Modifier.testTag("open_preview_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.openPreviewExport)
                    }
                } else if (stage == ProcessingStage.PAUSED_OFFLINE || stage == ProcessingStage.FAILED) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TurquoiseDeep,
                            contentColor = Color(0xFF021E1B)
                        ),
                        modifier = Modifier.testTag("retry_processing_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.retryProcessing)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCalloutCard(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CrimsonContainer),
        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = CrimsonAlert
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFD8DD)
                )
            }
            FilledTonalButton(
                onClick = onRetry,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AmberContainer,
                    contentColor = AmberWarm
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(retryLabel, style = MaterialTheme.typography.labelMedium)
            }
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss error",
                tint = TextSecondary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onDismiss)
            )
        }
    }
}

@Composable
private fun PipelineToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) TurquoiseContainer else StudioSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) TurquoisePulse else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF021E1B),
                checkedTrackColor = TurquoisePulse,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = StudioSurfaceElevated,
                uncheckedBorderColor = StudioOutline
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
