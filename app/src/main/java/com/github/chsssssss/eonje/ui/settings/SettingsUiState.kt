package com.github.chsssssss.eonje.ui.settings

import com.github.chsssssss.eonje.domain.model.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isSeeding: Boolean = false,
)
