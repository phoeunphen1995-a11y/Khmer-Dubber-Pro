package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.StudioSettings
import com.example.data.model.BackendMode
import com.example.data.model.DubbingVoiceProfile
import com.example.data.model.SourceLanguage
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.CrimsonAlert
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
import com.example.ui.viewmodel.SettingsFeedback
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: StudioSettings,
    feedback: SettingsFeedback,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetDefaultSourceLang: (SourceLanguage) -> Unit,
    onSetDefaultVoice: (DubbingVoiceProfile) -> Unit,
    onSetDefaultBgmLevel: (Float) -> Unit,
    onSetBackendMode: (BackendMode) -> Unit,
    onTestHttpsUrl: (url: String, invalidError: String, successMsg: String) -> Unit,
    onClearCache: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var endpointInput by remember(settings.customHttpsUrl) {
        mutableStateOf(settings.customHttpsUrl)
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            // 1. Bilingual Interface Language & Defaults Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = strings.uiLanguageSetting,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            val selected = settings.appLanguage == lang
                            FilterChip(
                                selected = selected,
                                onClick = { onSetLanguage(lang) },
                                label = { Text(lang.nativeName) },
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
                                modifier = Modifier.testTag("settings_lang_${lang.code}")
                            )
                        }
                    }

                    Text(
                        text = strings.defaultSourceLangSetting,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceLanguage.entries.forEach { lang ->
                            val selected = settings.defaultSourceLanguage == lang
                            FilterChip(
                                selected = selected,
                                onClick = { onSetDefaultSourceLang(lang) },
                                label = {
                                    Text(
                                        if (settings.appLanguage == AppLanguage.KHMER) lang.khmerName else lang.englishName
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
                                )
                            )
                        }
                    }

                    Text(
                        text = strings.defaultVoiceProfileSetting,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DubbingVoiceProfile.entries.forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetDefaultVoice(profile) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.defaultVoiceProfile == profile,
                                    onClick = { onSetDefaultVoice(profile) },
                                    colors = RadioButtonDefaults.colors(selectedColor = TurquoisePulse)
                                )
                                Text(
                                    text = if (settings.appLanguage == AppLanguage.KHMER) profile.khmerName else profile.englishName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.defaultBgmSetting,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "${(settings.defaultBgmDuckingLevel * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = TurquoisePulse
                        )
                    }
                    Slider(
                        value = settings.defaultBgmDuckingLevel,
                        onValueChange = onSetDefaultBgmLevel,
                        valueRange = 0.05f..0.75f,
                        colors = SliderDefaults.colors(
                            thumbColor = TurquoisePulse,
                            activeTrackColor = TurquoisePulse,
                            inactiveTrackColor = StudioOutline
                        )
                    )
                }
            }

            // 2. Secure HTTPS Backend & Zero-Secret APK Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = TurquoisePulse
                        )
                        Text(
                            text = strings.backendSecurityTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    // Mode 1: Firebase AI Logic
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetBackendMode(BackendMode.FIREBASE_AI_LOGIC) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = settings.backendMode == BackendMode.FIREBASE_AI_LOGIC,
                            onClick = { onSetBackendMode(BackendMode.FIREBASE_AI_LOGIC) },
                            colors = RadioButtonDefaults.colors(selectedColor = TurquoisePulse)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.firebaseAiModeName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = strings.firebaseAiModeDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    // Mode 2: Custom HTTPS Server
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetBackendMode(BackendMode.CUSTOM_HTTPS_SERVER) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = settings.backendMode == BackendMode.CUSTOM_HTTPS_SERVER,
                            onClick = { onSetBackendMode(BackendMode.CUSTOM_HTTPS_SERVER) },
                            colors = RadioButtonDefaults.colors(selectedColor = TurquoisePulse)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.customHttpsModeName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = strings.customHttpsModeDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    AnimatedVisibility(visible = settings.backendMode == BackendMode.CUSTOM_HTTPS_SERVER) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = endpointInput,
                                onValueChange = { endpointInput = it },
                                label = { Text(strings.customServerUrlLabel) },
                                placeholder = { Text(strings.customServerUrlPlaceholder, color = TextMuted) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Https,
                                        contentDescription = null,
                                        tint = CyanGlow
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TurquoisePulse,
                                    unfocusedBorderColor = StudioOutline,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_https_url_input")
                            )

                            Button(
                                onClick = {
                                    onTestHttpsUrl(
                                        endpointInput,
                                        strings.invalidHttpsUrlError,
                                        strings.connectionSuccessMsg
                                    )
                                },
                                enabled = !feedback.isTestingConnection,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TurquoisePulse,
                                    contentColor = Color(0xFF021E1B)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_https_button")
                            ) {
                                if (feedback.isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF021E1B),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(strings.testHttpsConnection)
                                }
                            }
                        }
                    }

                    // Server-side credential guarantee banner
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = TurquoiseContainer.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, TurquoisePulse.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = TurquoisePulse,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = strings.serverCredentialsNotice,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    if (!feedback.statusMessage.isNullOrBlank()) {
                        Text(
                            text = feedback.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (feedback.isError) CrimsonAlert else EmeraldSuccess
                        )
                    }
                }
            }

            // 3. Storage & Cache Card
            OutlinedButton(
                onClick = { onClearCache(strings.cacheClearedMsg) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("clear_cache_button"),
                border = BorderStroke(1.dp, StudioOutline),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.clearCacheAction, color = TextPrimary)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
