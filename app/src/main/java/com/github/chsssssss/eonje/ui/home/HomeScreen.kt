package com.github.chsssssss.eonje.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.components.FolderChip
import com.github.chsssssss.eonje.ui.components.FolderPickerContent
import com.github.chsssssss.eonje.ui.components.PlaceholderImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

private val LIST_SHEET_PEEK_HEIGHT = 108.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LocationPermissionRequester(onGranted = viewModel::refreshCurrentLocation)

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    HomeContent(
        uiState = uiState,
        onSelectPlace = viewModel::onSelectPlace,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSelectFolderFilter = viewModel::onSelectFolderFilter,
        onOpenFolderPicker = viewModel::onOpenFolderPicker,
        onDirectionsClick = viewModel::onDirectionsClick,
        modifier = modifier,
    )

    if (uiState.folderPickerForPlaceId != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissFolderPicker,
            sheetState = rememberModalBottomSheetState(),
            containerColor = EonjeColors.surface,
        ) {
            FolderPickerContent(
                folders = uiState.folders,
                selectedFolderId = uiState.folderPickerForPlace?.folderId,
                onSelectFolder = viewModel::onSelectFolder,
                onCreateFolder = viewModel::onCreateFolder,
            )
        }
    }
}

/**
 * 지도를 현재 위치 중심으로 띄우기 위한 권한 요청. 거부해도 저장한 장소는 그대로 보이고,
 * 지도만 저장된 장소 기준으로 그려진다.
 */
@Composable
private fun LocationPermissionRequester(onGranted: () -> Unit) {
    val context = LocalContext.current
    val onGrantedState = rememberUpdatedState(onGranted)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { result -> if (result.values.any { it }) onGrantedState.value() },
    )

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            onGrantedState.value()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }
}

/**
 * 지도가 항상 배경으로 깔리고, 그 위에 바텀시트가 얹히는 구조 — 토글 없이 시트 상태로
 * 지도/리스트 비중을 조절한다. 장소를 선택하면(마커든 카드든) 시트 내용이 목록에서 상세로
 * 바뀌면서 화면 절반까지 올라온다. 자유 드래그 스냅이 필요해지면 AnchoredDraggable로
 * 커스텀하는 건 이후 단계 — 지금은 BottomSheetScaffold 기본 2단(peek/expanded)으로 시작한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onSelectPlace: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectFolderFilter: (FolderFilter) -> Unit,
    onOpenFolderPicker: (String) -> Unit,
    onDirectionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailPeekHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    val highlighted = uiState.highlighted
    val sheetPeekHeight = if (highlighted != null) detailPeekHeight else LIST_SHEET_PEEK_HEIGHT

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        ),
    )

    // 선택 상태가 바뀔 때마다(마커/카드 탭, 닫기) 그 모드에 맞는 peek 높이로 시트를 되돌린다.
    LaunchedEffect(uiState.selectedPlaceId) {
        scaffoldState.bottomSheetState.partialExpand()
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetContainerColor = EonjeColors.surface,
        sheetContent = {
            if (highlighted != null) {
                PlaceDetailSheetContent(
                    place = highlighted,
                    posts = uiState.selectedPlacePosts,
                    onClose = { onSelectPlace(null) },
                    onOpenFolderPicker = { onOpenFolderPicker(highlighted.id) },
                    onDirectionsClick = onDirectionsClick,
                )
            } else {
                PlaceListSheetContent(
                    uiState = uiState,
                    onSelectFolderFilter = onSelectFolderFilter,
                    onPlaceClick = onSelectPlace,
                    onOpenFolderPicker = onOpenFolderPicker,
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            KakaoMapView(
                places = uiState.places,
                highlightedId = uiState.selectedPlaceId,
                currentLocation = uiState.currentLocation,
                onPlaceClick = onSelectPlace,
                modifier = Modifier.fillMaxSize(),
            )
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(20.dp, 16.dp, 20.dp, 0.dp),
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(EonjeColors.surface.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = EonjeColors.textMuted, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("가게 검색", style = Typography.bodyMedium, color = EonjeColors.textMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = Typography.bodyMedium.copy(color = EonjeColors.textPrimary),
                cursorBrush = SolidColor(EonjeColors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "검색어 지우기",
                tint = EonjeColors.textMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

/** 시트 목록 모드: 폴더 탭 한 줄 + 장소 카드 리스트. peek 상태에서는 탭 줄만 보인다. */
@Composable
private fun PlaceListSheetContent(
    uiState: HomeUiState,
    onSelectFolderFilter: (FolderFilter) -> Unit,
    onPlaceClick: (String) -> Unit,
    onOpenFolderPicker: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
        FolderTabsRow(folders = uiState.folders, selected = uiState.folderFilter, onSelect = onSelectFolderFilter)
        Box(modifier = Modifier.weight(1f)) {
            PlaceCardList(
                places = uiState.places,
                isSearching = uiState.searchQuery.isNotBlank(),
                onPlaceClick = onPlaceClick,
                onOpenFolderPicker = onOpenFolderPicker,
            )
        }
    }
}

@Composable
private fun FolderTabsRow(folders: List<FolderEntity>, selected: FolderFilter, onSelect: (FolderFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(20.dp, 4.dp, 20.dp, 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(label = "전체", selected = selected is FolderFilter.All, onClick = { onSelect(FolderFilter.All) })
        folders.forEach { folder ->
            FilterChip(
                label = folder.name,
                selected = selected is FolderFilter.ByFolder && selected.folderId == folder.id,
                onClick = { onSelect(FolderFilter.ByFolder(folder.id)) },
            )
        }
        FilterChip(
            label = "미분류",
            selected = selected is FolderFilter.Unclassified,
            onClick = { onSelect(FolderFilter.Unclassified) },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) EonjeColors.accent else EonjeColors.surfaceVariant)
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
private fun PlaceCardList(
    places: List<HomePlace>,
    isSearching: Boolean,
    onPlaceClick: (String) -> Unit,
    onOpenFolderPicker: (String) -> Unit,
) {
    if (places.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                if (isSearching) "검색 결과가 없어요" else "저장된 장소가 없어요",
                style = Typography.bodyMedium,
                color = EonjeColors.textMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
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
                PlaceholderImage(modifier = Modifier.size(76.dp), imageUrl = place.thumbnailUrl, cornerRadius = 14.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(place.name, style = Typography.titleMedium, color = EonjeColors.textPrimary)
                    if (place.category.isNotBlank() || place.address.isNotBlank()) {
                        Text(
                            listOf(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · "),
                            style = Typography.bodySmall,
                            color = EonjeColors.textMuted,
                        )
                    }
                }
                FolderChip(folderName = place.folderName, onClick = { onOpenFolderPicker(place.id) })
            }
        }
    }
}

/**
 * 시트 상세 모드: 마커든 리스트 카드든 장소를 탭하면 이 내용으로 바뀌면서 화면 절반까지
 * 올라온다(peek 높이가 detailPeekHeight로 바뀜). 더 드래그하면 전체 펼침으로 게시물까지 다 보인다.
 */
@Composable
private fun PlaceDetailSheetContent(
    place: HomePlace,
    posts: List<HomePlacePost>,
    onClose: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onDirectionsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 4.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(place.name, style = Typography.headlineMedium, color = EonjeColors.textPrimary)
                if (place.category.isNotBlank()) {
                    Text(place.category, style = Typography.bodyMedium, color = EonjeColors.textTertiary)
                }
                Text(place.address, style = Typography.bodyMedium, color = EonjeColors.textMuted)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = EonjeColors.textSecondary)
            }
        }

        FolderChip(folderName = place.folderName, onClick = onOpenFolderPicker)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EonjeColors.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("메모", style = Typography.labelSmall, color = EonjeColors.textMuted)
            Text(place.memo.ifBlank { "메모가 없어요" }, style = Typography.bodyMedium, color = EonjeColors.textSecondary)
        }

        FilledPillButton(
            text = "길찾기",
            icon = Icons.Filled.Place,
            onClick = onDirectionsClick,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text("이 가게가 나온 게시물", style = Typography.titleMedium, color = EonjeColors.textPrimary)
                Text("${posts.size}", style = Typography.bodyMedium, color = EonjeColors.textMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                posts.forEach { post -> PlacePostRow(post) }
            }
        }
    }
}

@Composable
private fun PlacePostRow(post: HomePlacePost) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.surfaceVariant)
            .padding(12.dp)
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(post.label)))
                }.onFailure {
                    Toast.makeText(context, "열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
                }
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceholderImage(modifier = Modifier.size(56.dp), imageUrl = post.thumbnailUrl, cornerRadius = 12.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(post.label, style = Typography.bodyMedium, color = EonjeColors.textSecondary)
            Text(post.savedAt, style = Typography.labelSmall, color = EonjeColors.textMuted)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EonjeColors.iconMuted)
    }
}

private val previewPlaces = listOf(
    HomePlace("p1", "연남토마", "파스타", "서울 마포구 동교로", 37.5657, 126.9247),
    HomePlace("p2", "잇쇼우", "이자카야", "서울 용산구 이태원로", 37.5347, 126.9946),
    HomePlace("p3", "소격동 국수집", "국수", "서울 종로구 소격동", 37.5773, 126.9822),
    HomePlace("p4", "한남 스시하루", "스시", "서울 용산구 한남대로", 37.5344, 127.0016),
)

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun HomeScreenListPreview() {
    EonjeTheme {
        HomeContent(
            uiState = HomeUiState(places = previewPlaces),
            onSelectPlace = {},
            onSearchQueryChange = {},
            onSelectFolderFilter = {},
            onOpenFolderPicker = {},
            onDirectionsClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun HomeScreenDetailPreview() {
    EonjeTheme {
        HomeContent(
            uiState = HomeUiState(
                places = previewPlaces,
                selectedPlaceId = previewPlaces.first().id,
            ),
            onSelectPlace = {},
            onSearchQueryChange = {},
            onSelectFolderFilter = {},
            onOpenFolderPicker = {},
            onDirectionsClick = {},
        )
    }
}
