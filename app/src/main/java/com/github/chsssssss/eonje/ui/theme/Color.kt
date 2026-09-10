package com.github.chsssssss.eonje.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class EonjePalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val mapBackground: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val iconMuted: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val progressTrack: Color,
    val navBackground: Color,
)

val EonjeDarkPalette = EonjePalette(
    background = Color(0xFF121316),
    surface = Color(0xFF1B1D21),
    surfaceVariant = Color(0xFF22252A),
    mapBackground = Color(0xFF15171B),
    border = Color(0xFF33363C),
    accent = Color(0xFFF2A65A),
    onAccent = Color(0xFF22160A),
    textPrimary = Color(0xFFE6E7EA),
    textSecondary = Color(0xFFC7CBD1),
    textTertiary = Color(0xFF9BA0A8),
    textMuted = Color(0xFF7C828B),
    iconMuted = Color(0xFF4E535A),
    success = Color(0xFF8FBF9F),
    warning = Color(0xFFD9A03C),
    danger = Color(0xFFD9615A),
    progressTrack = Color(0xFF2A2D33),
    navBackground = Color(0xFF181A1E),
)

val EonjeLightPalette = EonjePalette(
    background = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0ECE5),
    mapBackground = Color(0xFFF3F0EA),
    border = Color(0xFFE1DCD3),
    accent = Color(0xFFC97A32),
    onAccent = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF201F1D),
    textSecondary = Color(0xFF433F3A),
    textTertiary = Color(0xFF6B655D),
    textMuted = Color(0xFF867F76),
    iconMuted = Color(0xFFB8B0A4),
    success = Color(0xFF3D8A57),
    warning = Color(0xFFA9701D),
    danger = Color(0xFFC13E33),
    progressTrack = Color(0xFFE7E1D7),
    navBackground = Color(0xFFFFFFFF),
)

val LocalEonjePalette = staticCompositionLocalOf { EonjeDarkPalette }

/**
 * 기존 호출부(`EonjeColors.textPrimary` 등)를 그대로 두고 라이트/다크를 지원하기 위해
 * 각 색상을 컴포저블 getter로 노출한다 — [EonjeTheme] 안에서만 읽을 수 있다.
 */
object EonjeColors {
    val background: Color @Composable get() = LocalEonjePalette.current.background
    val surface: Color @Composable get() = LocalEonjePalette.current.surface
    val surfaceVariant: Color @Composable get() = LocalEonjePalette.current.surfaceVariant
    val mapBackground: Color @Composable get() = LocalEonjePalette.current.mapBackground
    val border: Color @Composable get() = LocalEonjePalette.current.border

    val accent: Color @Composable get() = LocalEonjePalette.current.accent
    val onAccent: Color @Composable get() = LocalEonjePalette.current.onAccent

    val textPrimary: Color @Composable get() = LocalEonjePalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalEonjePalette.current.textSecondary
    val textTertiary: Color @Composable get() = LocalEonjePalette.current.textTertiary
    val textMuted: Color @Composable get() = LocalEonjePalette.current.textMuted
    val iconMuted: Color @Composable get() = LocalEonjePalette.current.iconMuted

    val success: Color @Composable get() = LocalEonjePalette.current.success
    val warning: Color @Composable get() = LocalEonjePalette.current.warning
    val danger: Color @Composable get() = LocalEonjePalette.current.danger
    val progressTrack: Color @Composable get() = LocalEonjePalette.current.progressTrack

    val navBackground: Color @Composable get() = LocalEonjePalette.current.navBackground
}
