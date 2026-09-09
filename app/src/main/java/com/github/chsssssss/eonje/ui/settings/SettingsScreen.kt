package com.github.chsssssss.eonje.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@Composable
fun SettingsScreen(
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsContent(
        uiState = uiState,
        onNavigateToAccounts = onNavigateToAccounts,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EonjeColors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("설정", style = Typography.headlineMedium, color = EonjeColors.textPrimary)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsRow(label = "계정 관리", value = uiState.lastSyncedLabel, onClick = onNavigateToAccounts)
            SettingsInfoRow(label = "동기화 주기", value = uiState.syncIntervalLabel)
            SettingsInfoRow(
                label = "인스타그램 연동 토큰",
                value = uiState.tokenStatusLabel,
                isWarning = uiState.isTokenExpired,
            )
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = Typography.bodyLarge, color = EonjeColors.textPrimary)
            if (value != null) {
                Text(value, style = Typography.labelSmall, color = EonjeColors.textMuted)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EonjeColors.iconMuted)
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String, isWarning: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Typography.bodyLarge, color = EonjeColors.textPrimary)
        Text(value, style = Typography.bodyMedium, color = if (isWarning) EonjeColors.warning else EonjeColors.textMuted)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun SettingsScreenPreview() {
    EonjeTheme {
        SettingsContent(uiState = SettingsUiState(), onNavigateToAccounts = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun SettingsScreenTokenExpiredPreview() {
    EonjeTheme {
        SettingsContent(
            uiState = SettingsUiState(isTokenExpired = true, tokenStatusLabel = "만료됨 · 재연결이 필요해요"),
            onNavigateToAccounts = {},
        )
    }
}
