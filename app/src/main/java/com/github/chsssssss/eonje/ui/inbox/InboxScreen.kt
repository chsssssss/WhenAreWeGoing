package com.github.chsssssss.eonje.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
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
import com.github.chsssssss.eonje.ui.components.CandidateStatusChip
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.components.PlaceholderImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@Composable
fun InboxScreen(
    onItemClick: (String) -> Unit,
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    InboxContent(
        uiState = uiState,
        onItemClick = onItemClick,
        onNavigateToAccounts = onNavigateToAccounts,
        modifier = modifier,
    )
}

@Composable
private fun InboxContent(
    uiState: InboxUiState,
    onItemClick: (String) -> Unit,
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EonjeColors.background)
    ) {
        Column(modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 18.dp)) {
            Text("인박스", style = Typography.headlineMedium, color = EonjeColors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (uiState.items.isEmpty()) "정리할 게시물이 없어요" else "${uiState.items.size}개 정리 대기",
                style = Typography.bodyMedium,
                color = EonjeColors.textMuted,
            )
        }

        if (uiState.showUnregisteredAccountBanner) {
            UnregisteredAccountBanner(
                onClick = onNavigateToAccounts,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
            )
        }

        if (uiState.items.isEmpty()) {
            EmptyInbox(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    InboxCard(item = item, onClick = { onItemClick(item.id) })
                }
            }

            FilledPillButton(
                text = "순서대로 정리하기",
                icon = Icons.Filled.Check,
                onClick = { onItemClick(uiState.items.first().id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 16.dp, 20.dp, 18.dp),
            )
        }
    }
}

@Composable
private fun UnregisteredAccountBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = EonjeColors.warning)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("자동으로 정리되지 않는 게시물이 있어요", style = Typography.bodyMedium, color = EonjeColors.textPrimary)
            Text("맛집 계정을 등록하면 다음부터 자동으로 매칭돼요", style = Typography.labelSmall, color = EonjeColors.textMuted)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EonjeColors.iconMuted)
    }
}

@Composable
private fun InboxCard(item: InboxItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(EonjeColors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceholderImage(
            modifier = Modifier.size(88.dp),
            imageUrl = item.thumbnailUrl,
            cornerRadius = 16.dp,
            label = "인스타 썸네일",
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(item.relativeTime, style = Typography.bodySmall, color = EonjeColors.textMuted)
            CandidateStatusChip(candidateCount = item.extractedCount)
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = EonjeColors.iconMuted,
        )
    }
}

@Composable
private fun EmptyInbox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("저장된 게시물이 없어요", style = Typography.titleMedium, color = EonjeColors.textSecondary)
            Spacer(Modifier.height(6.dp))
            Text("인스타그램에서 공유해보세요", style = Typography.bodyMedium, color = EonjeColors.textMuted)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun InboxScreenPreview() {
    EonjeTheme {
        InboxContent(
            uiState = InboxUiState(
                items = listOf(
                    InboxItem("1", "https://instagram.com/p/abc/", "어제 오후 11:24"),
                    InboxItem("2", "https://instagram.com/p/def/", "3일 전"),
                ),
                isLoading = false,
                showUnregisteredAccountBanner = true,
            ),
            onItemClick = {},
            onNavigateToAccounts = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun InboxScreenEmptyPreview() {
    EonjeTheme {
        InboxContent(uiState = InboxUiState(isLoading = false), onItemClick = {}, onNavigateToAccounts = {})
    }
}
