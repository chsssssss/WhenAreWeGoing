package com.github.chsssssss.eonje.ui.accounts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.components.PlaceholderImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    AccountsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onAddAccountClick = viewModel::onAddAccountClick,
        onRetrySync = viewModel::onRetrySync,
        modifier = modifier,
    )

    if (uiState.isAddSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissAddSheet,
            sheetState = rememberModalBottomSheetState(),
            containerColor = EonjeColors.surface,
        ) {
            AddAccountSheetContent(
                username = uiState.usernameInput,
                onUsernameChange = viewModel::onUsernameChange,
                onCancel = viewModel::onDismissAddSheet,
                onConfirm = viewModel::onConfirmRegister,
            )
        }
    }
}

@Composable
private fun AccountsContent(
    uiState: AccountsUiState,
    onNavigateBack: () -> Unit,
    onAddAccountClick: () -> Unit,
    onRetrySync: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(EonjeColors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(12.dp, 14.dp, 12.dp, 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onNavigateBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = EonjeColors.textSecondary)
                }
                Text("계정 관리", style = Typography.titleMedium, color = EonjeColors.textPrimary)
            }

            Column(modifier = Modifier.padding(24.dp, 8.dp, 24.dp, 20.dp)) {
                Text("맛집 계정 ${uiState.accounts.size}", style = Typography.headlineMedium, color = EonjeColors.textPrimary)
                Text(
                    "등록한 계정의 새 게시물을 인박스로 모읍니다",
                    style = Typography.bodyMedium,
                    color = EonjeColors.textMuted,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.accounts, key = { it.username }) { account ->
                    AccountRow(account = account, onRetrySync = { onRetrySync(account.username) })
                }
            }
        }

        FilledPillButton(
            text = "계정 추가",
            icon = Icons.Filled.Add,
            onClick = onAddAccountClick,
            height = 60.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp, 16.dp, 20.dp, 28.dp),
        )
    }
}

@Composable
private fun AccountRow(account: AccountUiModel, onRetrySync: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(EonjeColors.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceholderImage(modifier = Modifier.size(52.dp), circle = true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(account.username, style = Typography.titleMedium, color = EonjeColors.textPrimary)
            if (account.isError) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onRetrySync),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = EonjeColors.warning, modifier = Modifier.size(13.dp))
                    Text(account.statusText, style = Typography.bodySmall, color = EonjeColors.warning)
                }
            } else {
                Text(account.statusText, style = Typography.bodySmall, color = EonjeColors.textMuted)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EonjeColors.iconMuted)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun AccountsScreenPreview() {
    EonjeTheme {
        AccountsContent(
            uiState = AccountsUiState(),
            onNavigateBack = {},
            onAddAccountClick = {},
            onRetrySync = {},
        )
    }
}
