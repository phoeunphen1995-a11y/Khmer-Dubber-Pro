package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.VideoProjectEntity
import com.example.data.model.ProcessingStage
import com.example.data.model.SourceLanguage
import com.example.data.model.SrtFormatter
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TurquoiseContainer
import com.example.ui.theme.TurquoisePulse
import com.example.ui.viewmodel.HistoryFilter
import com.example.ui.viewmodel.HistoryUiState
import java.io.File

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    appLanguage: AppLanguage,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (HistoryFilter) -> Unit,
    onOpenPreview: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(ObsidianVoid, MidnightNavy, ObsidianVoid))
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.historyTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChanged,
                placeholder = { Text(strings.searchHistoryHint, color = TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TurquoisePulse
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TurquoisePulse,
                    unfocusedBorderColor = StudioOutline,
                    focusedContainerColor = StudioSurface,
                    unfocusedContainerColor = StudioSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    HistoryFilter.ALL to strings.filterAll,
                    HistoryFilter.COMPLETED to strings.filterCompleted,
                    HistoryFilter.INTERRUPTED to strings.filterInterrupted
                )
                filters.forEach { (filter, label) ->
                    val selected = uiState.filter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChanged(filter) },
                        label = { Text(label) },
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
                        modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                    )
                }
            }

            if (uiState.projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_history),
                            contentDescription = strings.historyEmptyTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .aspectRatio(4f / 3f)
                                .clip(MaterialTheme.shapes.large)
                        )
                        Text(
                            text = strings.historyEmptyTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = strings.historyEmptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.projects, key = { it.id }) { project ->
                        HistoryProjectCard(
                            project = project,
                            appLanguage = appLanguage,
                            onOpenPreview = { onOpenPreview(project.id) },
                            onResumeJob = { onResumeJob(project.id) },
                            onDelete = { onDeleteProject(project.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryProjectCard(
    project: VideoProjectEntity,
    appLanguage: AppLanguage,
    onOpenPreview: () -> Unit,
    onResumeJob: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    val stage = project.stage
    val isCompleted = stage == ProcessingStage.COMPLETED
    val isInterrupted = stage == ProcessingStage.PAUSED_OFFLINE || stage == ProcessingStage.FAILED

    val statusColor = when {
        isCompleted -> EmeraldSuccess
        stage == ProcessingStage.PAUSED_OFFLINE -> AmberWarm
        stage == ProcessingStage.FAILED -> CrimsonAlert
        else -> TurquoisePulse
    }

    val statusContainer = when {
        isCompleted -> TurquoiseContainer
        stage == ProcessingStage.PAUSED_OFFLINE -> AmberContainer
        stage == ProcessingStage.FAILED -> CrimsonContainer
        else -> StudioSurfaceElevated
    }

    val srcLang = SourceLanguage.fromCode(project.detectedLanguageCode ?: project.sourceLanguageCode)
    val srcLabel = if (appLanguage == AppLanguage.KHMER) srcLang.khmerName else srcLang.englishName

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isCompleted) onOpenPreview() else onResumeJob()
            }
            .testTag("history_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 88.dp, height = 64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(StudioSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    val thumb = project.thumbnailPath?.let(::File)?.takeIf { it.exists() }
                    if (thumb != null) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = project.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = TurquoisePulse
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ObsidianVoid.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = project.format,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanGlow,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$srcLabel → ខ្មែរ • ${SrtFormatter.formatDisplayDuration(project.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusContainer
                    ) {
                        Text(
                            text = when (stage) {
                                ProcessingStage.COMPLETED -> strings.filterCompleted
                                ProcessingStage.PAUSED_OFFLINE -> strings.stagePausedOffline
                                ProcessingStage.FAILED -> strings.stageFailed
                                else -> stage.name
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_project_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = strings.deleteProject,
                        tint = TextMuted
                    )
                }
            }

            if (isCompleted || isInterrupted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isCompleted) {
                        Button(
                            onClick = onOpenPreview,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TurquoisePulse,
                                contentColor = Color(0xFF021E1B)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.openPreviewExport)
                        }
                    } else {
                        Button(
                            onClick = onResumeJob,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberWarm,
                                contentColor = ObsidianVoid
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.resumeJob)
                        }
                    }
                }
            }
        }
    }
}
