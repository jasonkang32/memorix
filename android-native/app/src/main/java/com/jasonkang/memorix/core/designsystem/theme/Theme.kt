package com.jasonkang.memorix.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = MemorixPrimary,
    onPrimary = MemorixTextDark,
    secondary = MemorixSecondary,
    onSecondary = MemorixTextDark,
    tertiary = MemorixPersonalStart,
    background = MemorixSurfaceLight,
    onBackground = MemorixTextLight,
    surface = MemorixSurfaceLight,
    onSurface = MemorixTextLight,
    surfaceVariant = MemorixCardLight,
    onSurfaceVariant = MemorixMutedLight,
    outline = MemorixBorderLight,
)

private val DarkColors = darkColorScheme(
    primary = MemorixPrimary,
    onPrimary = MemorixTextDark,
    secondary = MemorixSecondary,
    onSecondary = MemorixTextDark,
    tertiary = MemorixPersonalStart,
    background = MemorixSurfaceDark,
    onBackground = MemorixTextDark,
    surface = MemorixSurfaceDark,
    onSurface = MemorixTextDark,
    surfaceVariant = MemorixCardDark,
    onSurfaceVariant = MemorixMutedDark,
    outline = MemorixBorderDark,
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
