package com.github.chsssssss.eonje.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.data.local.TagEntity
import com.github.chsssssss.eonje.ui.components.PlaceholderImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@Composable
fun HomeScreen(
    onPlaceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeContent(
        uiState = uiState,
        onToggleView = viewModel::toggleView,
        onPlaceClick = onPlaceClick,
        onSelectTag = viewModel::onSelectTag,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onToggleView: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onSelectTag: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EonjeColors.background)
    ) {
        if (uiState.isMapView) {
            KakaoMapView(
                places = uiState.places,
                highlightedId = uiState.highlighted?.id,
                onPlaceClick = onPlaceClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlaceList(places = uiState.places, onPlaceClick = onPlaceClick)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(20.dp, 16.dp, 20.dp, 0.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SearchBar()
            FilterChipsRow(
                totalCount = uiState.totalCount,
                tags = uiState.tags,
                selectedTagId = uiState.selectedTagId,
                pendingCount = uiState.pendingCount,
                onSelectTag = onSelectTag,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val highlighted = uiState.highlighted
            if (uiState.isMapView && highlighted != null) {
                PeekCard(
                    place = highlighted,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .clickable { onPlaceClick(highlighted.id) },
                )
            }
            MapListToggle(isMapView = uiState.isMapView, onToggle = onToggleView)
        }
    }
}

@Composable
private fun SearchBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(EonjeColors.surface.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = EonjeColors.textMuted, modifier = Modifier.size(20.dp))
        Text("가게 · 태그 검색", style = Typography.bodyMedium, color = EonjeColors.textMuted)
    }
}

@Composable
private fun FilterChipsRow(
    totalCount: Int,
    tags: List<TagEntity>,
    selectedTagId: String?,
    pendingCount: Int,
    onSelectTag: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(label = "전체 $totalCount", selected = selectedTagId == null, onClick = { onSelectTag(null) })
        tags.forEach { tag ->
            FilterChip(label = tag.name, selected = selectedTagId == tag.id, onClick = { onSelectTag(tag.id) })
        }
    }
    if (pendingCount > 0) {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(EonjeColors.accent.copy(alpha = 0.13f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(EonjeColors.accent, CircleShape))
            Text("정리 대기 $pendingCount", style = Typography.labelMedium, color = EonjeColors.accent)
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) EonjeColors.accent else EonjeColors.surface.copy(alpha = 0.8f))
            .then(if (!selected) Modifier.border(1.dp, EonjeColors.border, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Typography.labelMedium,
            color = if (selected) EonjeColors.onAccent else EonjeColors.textSecondary,
        )
    }
}

@Composable
private fun MapListToggle(isMapView: Boolean, onToggle: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(EonjeColors.surface)
                .padding(4.dp),
        ) {
            ToggleOption("지도", selected = isMapView, onClick = { if (!isMapView) onToggle() })
            ToggleOption("리스트", selected = !isMapView, onClick = { if (isMapView) onToggle() })
        }
    }
}

@Composable
private fun ToggleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(EonjeColors.surfaceVariant) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Typography.labelMedium,
            color = if (selected) EonjeColors.textPrimary else EonjeColors.textMuted,
        )
    }
}

@Composable
private fun PeekCard(place: HomePlace, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(EonjeColors.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceholderImage(modifier = Modifier.size(64.dp), cornerRadius = 14.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(place.name, style = Typography.titleMedium, color = EonjeColors.textPrimary)
            if (place.category.isNotBlank() || place.address.isNotBlank()) {
                Text(
                    listOf(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · "),
                    style = Typography.bodySmall,
                    color = EonjeColors.textMuted,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(EonjeColors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "장소 상세", tint = EonjeColors.onAccent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PlaceList(places: List<HomePlace>, onPlaceClick: (String) -> Unit) {
    if (places.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text("저장된 장소가 없어요", style = Typography.bodyMedium, color = EonjeColors.textMuted)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 196.dp, bottom = 168.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(places, key = { it.id }) { place ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaceClick(place.id) },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaceholderImage(modifier = Modifier.size(76.dp), cornerRadius = 14.dp)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(place.name, style = Typography.titleMedium, color = EonjeColors.textPrimary)
                    if (place.category.isNotBlank() || place.address.isNotBlank()) {
                        Text(
                            listOf(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · "),
                            style = Typography.bodySmall,
                            color = EonjeColors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

private val previewPlaces = listOf(
    HomePlace("p1", "연남토마", "파스타", "서울 마포구 동교로", 37.5657, 126.9247),
    HomePlace("p2", "잇쇼우", "이자카야", "서울 용산구 이태원로", 37.5347, 126.9946),
    HomePlace("p3", "소격동 국수집", "국수", "서울 종로구 소격동", 37.5773, 126.9822),
    HomePlace("p4", "한남 스시하루", "스시", "서울 용산구 한남대로", 37.5344, 127.0016),
)

private val previewTags = listOf(
    TagEntity("preset-date", "데이트", isPreset = true),
    TagEntity("preset-solo", "혼밥", isPreset = true),
    TagEntity("preset-group", "모임", isPreset = true),
    TagEntity("preset-cafe", "카페", isPreset = true),
)

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun HomeScreenMapPreview() {
    EonjeTheme {
        HomeContent(
            uiState = HomeUiState(
                isMapView = true,
                pendingCount = 7,
                places = previewPlaces,
                totalCount = previewPlaces.size,
                tags = previewTags,
            ),
            onToggleView = {},
            onPlaceClick = {},
            onSelectTag = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun HomeScreenListPreview() {
    EonjeTheme {
        HomeContent(
            uiState = HomeUiState(
                isMapView = false,
                pendingCount = 7,
                places = previewPlaces,
                totalCount = previewPlaces.size,
                tags = previewTags,
            ),
            onToggleView = {},
            onPlaceClick = {},
            onSelectTag = {},
        )
    }
}
