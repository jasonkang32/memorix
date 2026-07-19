package com.mebonsoft.memorix.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorWhite = Color.White

private val LightColors = lightColorScheme(
    primary = MemorixPrimary,
    onPrimary = ColorWhite,
    primaryContainer = MemorixPrimarySoft,
    onPrimaryContainer = MemorixPrimary,
    secondary = MemorixWorkDeep,
    onSecondary = ColorWhite,
    secondaryContainer = MemorixWorkSoft,
    onSecondaryContainer = MemorixWorkDeep,
    tertiary = MemorixPersonalDeep,
    onTertiary = ColorWhite,
    tertiaryContainer = MemorixPersonalSoft,
    onTertiaryContainer = MemorixPersonalDeep,
    background = MemorixCanvas,
    onBackground = MemorixInk,
    surface = MemorixSurface,
    onSurface = MemorixInk,
    surfaceVariant = MemorixSurfaceElevated,
    onSurfaceVariant = MemorixMuted,
    outline = MemorixBorder,
    outlineVariant = MemorixBorderStrong,
    error = MemorixDanger,
    onError = ColorWhite,
    errorContainer = MemorixDangerSoft,
    onErrorContainer = MemorixDanger,
)

private val DarkColors = darkColorScheme(
    primary = MemorixPrimaryBright,
    onPrimary = MemorixInk,
    primaryContainer = MemorixPrimary,
    onPrimaryContainer = MemorixDarkText,
    secondary = MemorixWorkStart,
    onSecondary = ColorWhite,
    secondaryContainer = MemorixDarkSurfaceElevated,
    onSecondaryContainer = MemorixDarkText,
    tertiary = MemorixPersonalStart,
    onTertiary = MemorixInk,
    tertiaryContainer = MemorixDarkSurfaceElevated,
    onTertiaryContainer = MemorixDarkText,
    background = MemorixDarkCanvas,
    onBackground = MemorixDarkText,
    surface = MemorixDarkSurface,
    onSurface = MemorixDarkText,
    surfaceVariant = MemorixDarkSurfaceElevated,
    onSurfaceVariant = MemorixDarkMuted,
    outline = MemorixDarkBorder,
    outlineVariant = MemorixDarkBorder,
    error = MemorixDangerSoft,
    onError = MemorixDanger,
    errorContainer = MemorixDanger,
    onErrorContainer = ColorWhite,
)

@Composable
fun MemorixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
