package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.model.AudioPreviewMode
import com.example.data.model.SrtFormatter
import com.example.data.model.SubtitleSegment
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TurquoiseContainer
import com.example.ui.theme.TurquoisePulse
import com.example.ui.viewmodel.PreviewUiState
import kotlinx.coroutines.delay
import java.io.File

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    uiState: PreviewUiState,
    onBack: () -> Unit,
    onSelectAudioMode: (AudioPreviewMode) -> Unit,
    onToggleDualSubtitles: () -> Unit,
    onUpdateSegmentText: (Int, String) -> Unit,
    onSaveSubtitleEdits: (String) -> Unit,
    onExportVideo: (String) -> Unit,
    onExportSrt: (String) -> Unit,
    onBuildShareVideoIntent: (String) -> Intent?,
    onBuildShareSrtIntent: (String) -> Intent?,
    onClearStatusMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val project = uiState.project

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var editingSegment by remember { mutableStateOf<SubtitleSegment?>(null) }
    var editingText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearStatusMessage()
        }
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val activeVideoPath = remember(project, uiState.audioMode) {
        when (uiState.audioMode) {
            AudioPreviewMode.DUBBED_WITH_BGM -> project?.dubbedVideoPath ?: project?.localSourceVideoPath
            AudioPreviewMode.ORIGINAL_AUDIO, AudioPreviewMode.BGM_ONLY -> project?.localSourceVideoPath
        }
    }

    LaunchedEffect(activeVideoPath, uiState.audioMode) {
        if (!activeVideoPath.isNullOrBlank() && File(activeVideoPath).exists()) {
            val resumePos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = exoPlayer.isPlaying
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(activeVideoPath))))
            exoPlayer.prepare()
            if (resumePos > 0L) exoPlayer.seekTo(resumePos)
            exoPlayer.volume = when (uiState.audioMode) {
                AudioPreviewMode.BGM_ONLY -> 0.45f
                else -> 1.0f
            }
            exoPlayer.playWhenReady = wasPlaying
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition
            delay(100L)
        }
    }

    val activeSubtitle = remember(uiState.segments, currentPositionMs) {
        uiState.segments.firstOrNull { currentPositionMs in it.startMs..it.endMs }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianVoid,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strings.previewTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        if (project != null) {
                            Text(
                                text = project.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backLabel,
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleDualSubtitles,
                        modifier = Modifier.testTag("toggle_dual_subtitles_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = strings.showDualSubtitles,
                            tint = if (uiState.showDualSubtitles) TurquoisePulse else TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightNavy)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Video Viewport with Live Khmer & Source Subtitles
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.large)
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (activeSubtitle != null) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.78f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = activeSubtitle.khmerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TurquoisePulse,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (uiState.showDualSubtitles && activeSubtitle.sourceText.isNotBlank()) {
                                    Text(
                                        text = activeSubtitle.sourceText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.9f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Audio Track Switcher Pills
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            AudioPreviewMode.DUBBED_WITH_BGM to strings.audioModeDubbed,
                            AudioPreviewMode.ORIGINAL_AUDIO to strings.audioModeOriginal,
                            AudioPreviewMode.BGM_ONLY to strings.audioModeBgmOnly
                        )
                        modes.forEach { (mode, label) ->
                            val selected = uiState.audioMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectAudioMode(mode) },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TurquoiseContainer,
                                    selectedLabelColor = TurquoisePulse,
                                    containerColor = StudioSurface,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = StudioOutline,
                                    selectedBorderColor = TurquoisePulse
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("audio_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }

                // 3. Optional TTS Voice Notice if km-KH voice data missing on device
                if (project != null && project.enableDubbing && !project.ttsAvailable) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = AmberContainer),
                            border = BorderStroke(1.dp, AmberWarm.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AmberWarm
                                )
                                Text(
                                    text = strings.ttsMissingNotice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFFEDC2),
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                        runCatching { context.startActivity(installIntent) }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = AmberWarm,
                                        contentColor = ObsidianVoid
                                    )
                                ) {
                                    Text(strings.installKhmerVoice, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // 4. Download & Share Action Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onExportVideo(strings.exportSuccessVideo) },
                            enabled = !uiState.isExporting,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("export_video_button"),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TurquoisePulse,
                                contentColor = Color(0xFF021E1B)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.exportDubbedVideo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        FilledTonalButton(
                            onClick = { onExportSrt(strings.exportSuccessSrt) },
                            enabled = !uiState.isExporting && uiState.segments.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_srt_button"),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = StudioSurfaceHighest,
                                contentColor = CyanGlow
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.exportSrtFile, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = onBuildShareVideoIntent(strings.shareOutput)
                                    ?: onBuildShareSrtIntent(strings.shareOutput)
                                if (intent != null) context.startActivity(intent)
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("share_output_button"),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, StudioOutline)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = strings.shareOutput,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 5. Subtitle Timeline Header + Save Button
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.subtitleTimelineTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = strings.editSubtitleHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        AnimatedVisibility(visible = uiState.hasUnsavedSubtitleEdits) {
                            Button(
                                onClick = { onSaveSubtitleEdits(strings.exportSuccessSrt) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TurquoisePulse,
                                    contentColor = Color(0xFF021E1B)
                                ),
                                modifier = Modifier.testTag("save_subtitles_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.saveSubtitles)
                            }
                        }
                    }
                }

                // 6. Subtitle Segments List
                items(uiState.segments, key = { it.index }) { segment ->
                    val isActive = currentPositionMs in segment.startMs..segment.endMs
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) TurquoiseContainer.copy(alpha = 0.55f) else StudioSurface
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isActive) TurquoisePulse else StudioOutline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                exoPlayer.seekTo(segment.startMs)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${segment.index}  ${SrtFormatter.formatDisplayDuration(segment.startMs)} → ${SrtFormatter.formatDisplayDuration(segment.endMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive) TurquoisePulse else CyanGlow
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = segment.khmerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                if (segment.sourceText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = segment.sourceText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    editingSegment = segment
                                    editingText = segment.khmerText
                                },
                                modifier = Modifier.testTag("edit_subtitle_${segment.index}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit subtitle ${segment.index}",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val segToEdit = editingSegment
    if (segToEdit != null) {
        AlertDialog(
            onDismissRequest = { editingSegment = null },
            containerColor = StudioSurfaceElevated,
            title = {
                Text(
                    text = "#${segToEdit.index} (${SrtFormatter.formatDisplayDuration(segToEdit.startMs)})",
                    color = TurquoisePulse
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (segToEdit.sourceText.isNotBlank()) {
                        Text(
                            text = segToEdit.sourceText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subtitle_edit_field"),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSegmentText(segToEdit.index, editingText)
                        editingSegment = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TurquoisePulse,
                        contentColor = Color(0xFF021E1B)
                    )
                ) {
                    Text(strings.saveSubtitles)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSegment = null }) {
                    Text(strings.cancelProcessing, color = TextSecondary)
                }
            }
        )
    }
}
