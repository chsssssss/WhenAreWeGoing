package com.github.chsssssss.eonje.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.domain.model.ThemeMode
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL =
    "https://app.notion.com/p/3e025d524d6b801186e1fe9c88fa93b4"
private const val CONTACT_EMAIL = "chaheesun42@gmail.com"

@Composable
fun SettingsScreen(
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fetcher = remember { GoogleCredentialFetcher() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    SettingsContent(
        themeMode = uiState.themeMode,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onOpenGuide = onOpenGuide,
        account = AccountUi(
            visible = uiState.accountSectionVisible,
            isLinked = uiState.account.isLinked,
            email = uiState.account.email,
            migrationPending = uiState.legacyMigrationPending,
            busy = uiState.busy,
            onLink = {
                scope.launch {
                    viewModel.onLinkStarted()
                    when (val result = fetcher.fetch(context)) {
                        is GoogleTokenResult.Token -> viewModel.onLinkToken(result.idToken)
                        GoogleTokenResult.Cancelled -> viewModel.onLinkTokenFailed(cancelled = true)
                        GoogleTokenResult.Failed -> viewModel.onLinkTokenFailed(cancelled = false)
                    }
                }
            },
            onSignOut = { viewModel.onShowDialog(AccountDialog.SIGN_OUT) },
            onDelete = {
                viewModel.onShowDialog(if (uiState.account.isLinked) AccountDialog.DELETE_ACCOUNT else AccountDialog.DELETE_DATA)
            },
        ),
        modifier = modifier,
    )

    when (uiState.dialog) {
        AccountDialog.SIGN_OUT -> ConfirmDialog(
            title = "로그아웃할까요?",
            message = "계정 데이터는 서버에 남아 있고, 다시 로그인하면 돌아와요. 이 기기에서는 빈 상태로 시작해요.",
            confirmLabel = "로그아웃",
            onConfirm = viewModel::onSignOutConfirmed,
            onDismiss = viewModel::onDismissDialog,
        )
        AccountDialog.DELETE_ACCOUNT, AccountDialog.DELETE_DATA -> {
            val linked = uiState.dialog == AccountDialog.DELETE_ACCOUNT
            ConfirmDialog(
                title = if (linked) "계정을 삭제할까요?" else "데이터를 삭제할까요?",
                message = (if (linked) "계정과 저장한 장소·폴더·게시물이 전부 지워지고 되돌릴 수 없어요. 본인 확인을 위해 Google 로그인을 한 번 더 해요."
                else "저장한 장소·폴더·게시물이 전부 지워지고 되돌릴 수 없어요."),
                confirmLabel = "삭제",
                onConfirm = {
                    viewModel.onDeleteStarted()
                    scope.launch {
                        if (!linked) {
                            viewModel.onDeleteConfirmed(null)
                        } else {
                            when (val result = fetcher.fetch(context)) {
                                is GoogleTokenResult.Token -> viewModel.onDeleteConfirmed(result.idToken)
                                GoogleTokenResult.Cancelled -> viewModel.onDeleteReauthFailed(cancelled = true)
                                GoogleTokenResult.Failed -> viewModel.onDeleteReauthFailed(cancelled = false)
                            }
                        }
                    }
                },
                onDismiss = viewModel::onDismissDialog,
            )
        }
        null -> Unit
    }
}

/** 설정 화면의 "계정" 섹션이 쓰는 값과 동작. */
private data class AccountUi(
    val visible: Boolean = false,
    val isLinked: Boolean = false,
    val email: String? = null,
    val migrationPending: Boolean = false,
    val busy: AccountBusy? = null,
    val onLink: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun SettingsContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onOpenGuide: () -> Unit,
    account: AccountUi,
    modifier: Modifier = Modifier,
) {
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

        if (account.visible) {
            SettingsSection(title = "계정") {
                AccountCard(account)
            }
        }

        SettingsSection(title = "정보") {
            SettingsCard {
                SettingsLinkRow(
                    label = "사용법",
                    icon = Icons.Filled.ChevronRight,
                    onClick = onOpenGuide,
                )
                SettingsLinkRow(
                    label = "개인정보처리방침",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
                    },
                )
                SettingsLinkRow(
                    label = "문의하기",
                    icon = Icons.Filled.Email,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, "[언제가지] 문의")
                        }
                        context.startActivity(intent)
                    },
                )
                SettingsInfoRow(label = "버전 정보", value = BuildConfig.VERSION_NAME)
            }
        }
    }
}

@Composable
private fun AccountCard(account: AccountUi) {
    val busy = account.busy != null
    SettingsCard {
        if (account.isLinked) {
            SettingsInfoRow(label = "연결된 계정", value = account.email ?: "Google")
            SettingsLinkRow(label = if (account.busy == AccountBusy.SIGNING_OUT) "로그아웃 중…" else "로그아웃", onClick = { if (!busy) account.onSignOut() }, icon = Icons.Filled.ChevronRight)
            SettingsLinkRow(label = if (account.busy == AccountBusy.DELETING) "삭제 중…" else "계정 삭제", onClick = { if (!busy) account.onDelete() }, icon = Icons.Filled.ChevronRight)
        } else {
            val linkEnabled = !busy && !account.migrationPending
            SettingsLinkRow(
                label = if (account.busy == AccountBusy.LINKING) "연결 중…" else "Google로 데이터 백업하기",
                onClick = { if (linkEnabled) account.onLink() },
                icon = Icons.Filled.ChevronRight,
                enabled = linkEnabled,
            )
            if (account.migrationPending) {
                Text(
                    text = "기존 데이터를 옮기는 중이에요. 끝나면 백업할 수 있어요.",
                    style = Typography.bodySmall,
                    color = EonjeColors.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    text = "계정을 연결하면 기기를 바꾸거나 앱을 다시 설치해도 저장한 장소를 가져올 수 있어요.",
                    style = Typography.bodySmall,
                    color = EonjeColors.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            SettingsLinkRow(label = if (account.busy == AccountBusy.DELETING) "삭제 중…" else "데이터 삭제", onClick = { if (!busy) account.onDelete() }, icon = Icons.Filled.ChevronRight)
        }
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
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = Typography.bodyLarge, color = if (enabled) EonjeColors.textPrimary else EonjeColors.textMuted)
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
            onOpenGuide = {},
            account = AccountUi(visible = true),
        )
    }
}
