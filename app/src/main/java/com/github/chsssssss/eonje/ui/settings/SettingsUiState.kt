package com.github.chsssssss.eonje.ui.settings

data class SettingsUiState(
    val syncIntervalLabel: String = "1일 1회 · Wi-Fi 연결 + 충전 중",
    val isTokenExpired: Boolean = false,
    val tokenStatusLabel: String = "정상",
    val lastSyncedLabel: String? = null,
)
