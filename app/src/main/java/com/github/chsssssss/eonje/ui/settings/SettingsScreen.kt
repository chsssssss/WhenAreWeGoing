package com.github.chsssssss.eonje.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.domain.model.ThemeMode
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

private const val PRIVACY_POLICY_URL =
    "https://app.notion.com/p/3e025d524d6b801186e1fe9c88fa93b4"
private const val CONTACT_EMAIL = "chaheesun42@gmail.com"

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    SettingsContent(
        themeMode = uiState.themeMode,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        isSeeding = uiState.isSeeding,
        onSeedDummyData = viewModel::onSeedDummyData,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    isSeeding: Boolean,
    onSeedDummyData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EonjeColors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("설정", style = Typography.headlineMedium, color = EonjeColors.textPrimary)

        SettingsSection(title = "화면") {
            ThemeModeSelector(themeMode = themeMode, onThemeModeSelected = onThemeModeSelected)
        }

        SettingsSection(title = "정보") {
            SettingsCard {
                SettingsLinkRow(
                    label = "개인정보처리방침",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                    },
                )
                SettingsLinkRow(
                    label = "문의하기",
                    icon = Icons.Filled.Email,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, "[언제가지] 문의")
                        }
                        context.startActivity(intent)
                    },
                )
                SettingsInfoRow(label = "버전 정보", value = BuildConfig.VERSION_NAME)
            }
        }

        // 스크린샷 촬영용 더미 데이터 — 디버그 빌드에서만 노출된다(DaeguDummyDataSeeder 참고).
        if (BuildConfig.DEBUG) {
            FilledPillButton(
                text = if (isSeeding) "채우는 중…" else "디버그: 대구 더미 데이터 채우기",
                onClick = { if (!isSeeding) showConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("현재 데이터를 모두 지울까요?") },
            text = { Text("저장된 장소·폴더·게시물이 전부 지워지고 대구 더미 데이터로 채워져요. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onSeedDummyData()
                }) {
                    Text("채우기", color = EonjeColors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("취소")
                }
            },
            containerColor = EonjeColors.surface,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = Typography.labelLarge, color = EonjeColors.textMuted)
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.surface),
        content = content,
    )
}

@Composable
private fun ThemeModeSelector(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to "시스템 설정",
        ThemeMode.LIGHT to "라이트",
        ThemeMode.DARK to "다크",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EonjeColors.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (mode, label) ->
            val selected = mode == themeMode
            ThemeOptionChip(
                selected = selected,
                label = label,
                onClick = { onThemeModeSelected(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeOptionChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) EonjeColors.accent else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = Typography.labelMedium,
            color = if (selected) EonjeColors.onAccent else EonjeColors.textSecondary,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun SettingsLinkRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Filled.OpenInNew,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = Typography.bodyLarge, color = EonjeColors.textPrimary)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = EonjeColors.iconMuted,
            modifier = Modifier.height(20.dp),
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = Typography.bodyLarge, color = EonjeColors.textPrimary)
        Text(text = value, style = Typography.bodyLarge, color = EonjeColors.textMuted)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun SettingsScreenPreview() {
    EonjeTheme {
        SettingsContent(
            themeMode = ThemeMode.SYSTEM,
            onThemeModeSelected = {},
            isSeeding = false,
            onSeedDummyData = {},
        )
    }
}
