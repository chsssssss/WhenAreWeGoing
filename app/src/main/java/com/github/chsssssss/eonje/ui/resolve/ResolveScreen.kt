package com.github.chsssssss.eonje.ui.resolve

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.components.OutlinedPillButton
import com.github.chsssssss.eonje.ui.components.PlaceholderImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@Composable
fun ResolveScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResolveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ResolveEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                ResolveEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    ResolveContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onQueryChange = viewModel::onQueryChange,
        onSelectCandidate = viewModel::onSelectCandidate,
        onOpenOriginal = viewModel::onOpenOriginal,
        onConfirm = viewModel::onConfirm,
        modifier = modifier,
    )
}

@Composable
private fun ResolveContent(
    uiState: ResolveUiState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectCandidate: (PlaceCandidate) -> Unit,
    onOpenOriginal: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(EonjeColors.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = EonjeColors.textSecondary)
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text("가게 찾기", style = Typography.titleMedium, color = EonjeColors.textPrimary)
                if (uiState.subtitle.isNotBlank()) {
                    Text(uiState.subtitle, style = Typography.labelSmall, color = EonjeColors.textMuted)
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(16.dp, 0.dp, 16.dp, 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(EonjeColors.surface),
        ) {
            PlaceholderImage(
                modifier = Modifier.size(96.dp),
                cornerRadius = 0.dp,
                label = "인스타 원본",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
                    .clickable(onClick = onOpenOriginal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = EonjeColors.accent,
                    modifier = Modifier.size(15.dp),
                )
                Text("원본 열기", style = Typography.labelMedium, color = EonjeColors.accent)
            }
        }

        Row(
            modifier = Modifier
                .padding(16.dp, 0.dp, 16.dp, 14.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(EonjeColors.surfaceVariant)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = EonjeColors.textMuted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = EonjeColors.textPrimary, fontSize = Typography.bodyLarge.fontSize),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (uiState.query.isEmpty()) {
                        Text("가게 이름으로 검색", style = Typography.bodyLarge, color = EonjeColors.textMuted)
                    }
                    inner()
                },
            )
        }

        ResultsArea(
            uiState = uiState,
            onSelectCandidate = onSelectCandidate,
            modifier = Modifier.weight(1f),
        )

        FilledPillButton(
            text = if (uiState.isSaving) "저장 중…" else "확정하고 저장",
            icon = if (!uiState.isSaving) Icons.Filled.Check else null,
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 12.dp, 20.dp, 18.dp),
        )
    }
}

@Composable
private fun ResultsArea(
    uiState: ResolveUiState,
    onSelectCandidate: (PlaceCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        when {
            uiState.isSearching -> CenteredMessage { CircularProgressIndicator(color = EonjeColors.accent) }
            uiState.searchError != null -> CenteredMessage {
                Text(uiState.searchError, style = Typography.bodyMedium, color = EonjeColors.warning)
            }
            uiState.query.isBlank() -> CenteredMessage {
                Text("가게 이름을 검색해보세요", style = Typography.bodyMedium, color = EonjeColors.textMuted)
            }
            uiState.results.isEmpty() -> CenteredMessage {
                Text("검색 결과가 없어요", style = Typography.bodyMedium, color = EonjeColors.textMuted)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.results, key = { it.kakaoPlaceId }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        selected = uiState.selected?.kakaoPlaceId == candidate.kakaoPlaceId,
                        onClick = { onSelectCandidate(candidate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CandidateRow(candidate: PlaceCandidate, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) EonjeColors.accent.copy(alpha = 0.10f) else EonjeColors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp, 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(candidate.name, style = Typography.titleMedium, color = EonjeColors.textPrimary)
            Text(candidate.address, style = Typography.bodySmall, color = EonjeColors.textMuted)
            if (!candidate.category.isNullOrBlank()) {
                Text(candidate.category, style = Typography.labelSmall, color = EonjeColors.textTertiary)
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = EonjeColors.accent)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun ResolveScreenPreview() {
    EonjeTheme {
        ResolveContent(
            uiState = ResolveUiState(
                subtitle = "3일 전 저장",
                query = "소하염",
                results = listOf(
                    PlaceCandidate("1", "소하염", "서울 성동구 연무장길 45", "한식", 37.544, 127.055),
                    PlaceCandidate("2", "대림창고", "서울 성동구 성수이로 78", "카페", 37.545, 127.056),
                ),
                selected = PlaceCandidate("1", "소하염", "서울 성동구 연무장길 45", "한식", 37.544, 127.055),
            ),
            onNavigateBack = {},
            onQueryChange = {},
            onSelectCandidate = {},
            onOpenOriginal = {},
            onConfirm = {},
        )
    }
}
