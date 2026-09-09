package com.github.chsssssss.eonje.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EonjeDarkColorScheme = darkColorScheme(
    primary = EonjeColors.accent,
    onPrimary = EonjeColors.onAccent,
    secondary = EonjeColors.accent,
    onSecondary = EonjeColors.onAccent,
    background = EonjeColors.background,
    onBackground = EonjeColors.textPrimary,
    surface = EonjeColors.surface,
    onSurface = EonjeColors.textPrimary,
    surfaceVariant = EonjeColors.surfaceVariant,
    onSurfaceVariant = EonjeColors.textSecondary,
    outline = EonjeColors.border,
    error = EonjeColors.warning,
)

@Composable
fun EonjeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EonjeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
