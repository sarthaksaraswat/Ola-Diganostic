package com.sarthak.olacan.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// Material Design 3 Dark Theme Tokens (from Ola CAN UI/UX App Design System)
// =========================================================================

val M3Surface = Color(0xFF111318)
val M3SurfaceContainerLowest = Color(0xFF0C0E13)
val M3SurfaceContainerLow = Color(0xFF191C20)
val M3SurfaceContainer = Color(0xFF1D2024)
val M3SurfaceContainerHigh = Color(0xFF282A2F)
val M3SurfaceContainerHighest = Color(0xFF33353A)

val M3OnSurface = Color(0xFFE2E2E9)
val M3OnSurfaceVariant = Color(0xFFC4C7D0)
val M3Outline = Color(0xFF8E9099)
val M3OutlineVariant = Color(0xFF44474E)

val M3Primary = Color(0xFFA8C7FA)
val M3OnPrimary = Color(0xFF00315C)
val M3PrimaryContainer = Color(0xFF004A77)
val M3OnPrimaryContainer = Color(0xFFC2E7FF)

val M3Secondary = Color(0xFFBBC7DB)
val M3SecondaryContainer = Color(0xFF3B4858)
val M3OnSecondaryContainer = Color(0xFFD7E3F8)

val M3Error = Color(0xFFFFB4AB)
val M3ErrorContainer = Color(0xFF93000A)
val M3OnErrorContainer = Color(0xFFFFDAD6)

val M3Warning = Color(0xFFF5BE5D)
val M3WarningContainer = Color(0xFF553F00)
val M3OnWarningContainer = Color(0xFFFFE088)

val M3Success = Color(0xFF7BD797)
val M3SuccessContainer = Color(0xFF00522B)
val M3OnSuccessContainer = Color(0xFF9CF6B3)

// Backward-compatible semantic aliases mapped to M3 Dark Theme tokens
val BgDark = M3Surface
val SurfaceDark = M3SurfaceContainer
val SurfaceElevated = M3SurfaceContainerHigh
val BorderDark = M3OutlineVariant.copy(alpha = 0.5f)

val PrimaryCyan = M3Primary
val AccentGreen = M3Success
val WarningAmber = M3Warning
val DangerRed = M3Error

val TextPrimary = M3OnSurface
val TextSecondary = M3OnSurfaceVariant
val TextMuted = M3Outline
