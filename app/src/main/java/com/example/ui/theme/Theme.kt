package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = TurquoisePulse,
    onPrimary = OnTurquoise,
    primaryContainer = TurquoiseContainer,
    onPrimaryContainer = TurquoisePulse,
    secondary = CyanGlow,
    onSecondary = ObsidianVoid,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanGlow,
    tertiary = AmberWarm,
    onTertiary = ObsidianVoid,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberWarm,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = MidnightNavy,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = MidnightNavy,
    surfaceContainer = StudioSurface,
    surfaceContainerHigh = StudioSurfaceElevated,
    surfaceContainerHighest = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutlineBright,
    error = CrimsonAlert,
    onError = ObsidianVoid,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonAlert
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KhmerTranslatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
