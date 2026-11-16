package com.sarthak.olacan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = M3Primary,
    onPrimary = M3OnPrimary,
    primaryContainer = M3PrimaryContainer,
    onPrimaryContainer = M3OnPrimaryContainer,
    secondary = M3Secondary,
    onSecondary = M3SecondaryContainer,
    secondaryContainer = M3SecondaryContainer,
    onSecondaryContainer = M3OnSecondaryContainer,
    error = M3Error,
    onError = M3ErrorContainer,
    errorContainer = M3ErrorContainer,
    onErrorContainer = M3OnErrorContainer,
    background = M3Surface,
    onBackground = M3OnSurface,
    surface = M3SurfaceContainer,
    onSurface = M3OnSurface,
    surfaceVariant = M3SurfaceContainerHigh,
    onSurfaceVariant = M3OnSurfaceVariant,
    outline = M3Outline,
    outlineVariant = M3OutlineVariant
)

@Composable
fun OlaCanViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
