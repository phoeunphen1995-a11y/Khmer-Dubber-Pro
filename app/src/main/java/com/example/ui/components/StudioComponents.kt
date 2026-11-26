package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TurquoiseContainer
import com.example.ui.theme.TurquoisePulse

@Composable
fun LanguageToggleChip(
    currentLanguage: AppLanguage,
    onToggleLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val nextLang = if (currentLanguage == AppLanguage.ENGLISH) AppLanguage.KHMER else AppLanguage.ENGLISH
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable { onToggleLanguage(nextLang) }
            .testTag("language_toggle_chip"),
        shape = RoundedCornerShape(50),
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioOutline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "Switch interface language",
                tint = TurquoisePulse,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (currentLanguage == AppLanguage.ENGLISH) "EN • ខ្មែរ" else "ខ្មែរ • EN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SecurityTlsBadge(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val bgColor = if (isOnline) TurquoiseContainer else AmberContainer
    val fgColor = if (isOnline) TurquoisePulse else AmberWarm
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Lock else Icons.Default.CloudOff,
                contentDescription = if (isOnline) strings.httpsOnlineBadge else strings.httpsOfflineBadge,
                tint = fgColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isOnline) strings.httpsOnlineBadge else strings.httpsOfflineBadge,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OfflineStatusBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = MaterialTheme.shapes.medium,
            color = CrimsonContainer,
            border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CrimsonAlert.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = strings.offlineBannerTitle,
                        tint = CrimsonAlert,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.offlineBannerTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = CrimsonAlert
                    )
                    Text(
                        text = strings.offlineBannerSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFD8DD)
                    )
                }
            }
        }
    }
}
