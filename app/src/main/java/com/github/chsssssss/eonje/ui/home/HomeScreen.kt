package com.github.chsssssss.eonje.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.ui.components.AddFolderContent
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.components.FolderAssignSheetContent
import com.github.chsssssss.eonje.ui.components.FolderChip
import com.github.chsssssss.eonje.ui.components.FolderMarkerIcon
import com.github.chsssssss.eonje.ui.components.FolderPickerContent
import com.github.chsssssss.eonje.ui.components.PlaceholderImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 바텀시트가 멈출 수 있는 세 지점 — 핸들만 보이는 접힘, 화면의 40%, 거의 전체 펼침. */
private enum class SheetStop { Peek, Mid, Full }

private val SHEET_PEEK_HEIGHT = 172.dp // 검색창이 시트 헤더로 들어오면서 핸들+검색창+폴더 탭이 다 보여야 한다
private const val SHEET_MID_FRACTION = 0.4f
private const val SHEET_FULL_FRACTION = 0.95f

// AnchoredDraggableState(initialValue, anchors) 생성자는 snapAnimationSpec을 초기화하지 않는다
// (lateinit 상태로 남음) — settle()에 직접 넘겨줘야 한다.
private val SHEET_SNAP_ANIMATION_SPEC = spring<Float>()

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
        onOpenFolderAssign = viewModel::onOpenFolderAssign,
        onOpenCreateFolderSheet = viewModel::onOpenCreateFolderSheet,
        onOpenAddPlaceSheet = viewModel::onOpenAddPlaceSheet,
        onDeleteFolder = viewModel::onDeleteFolder,
        modifier = modifier,
    )

    val showAddPlace by viewModel.showAddPlace.collectAsState()
    if (showAddPlace) {
        val addPlaceUiState by viewModel.addPlaceUiState.collectAsState()
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissAddPlaceSheet,
            sheetState = rememberModalBottomSheetState(),
            containerColor = EonjeColors.surface,
        ) {
            AddPlaceSheetContent(
                uiState = addPlaceUiState,
                onQueryChange = viewModel::onAddPlaceQueryChange,
                onSelectCandidate = viewModel::onAddPlaceCandidateSelected,
            )
        }
    }

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
                onDeleteFolder = viewModel::onDeleteFolder,
            )
        }
    }

    if (uiState.showCreateFolderSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissCreateFolderSheet,
            sheetState = rememberModalBottomSheetState(),
            containerColor = EonjeColors.surface,
        ) {
            AddFolderContent(onCreate = viewModel::onCreateFolderWithAppearance)
        }
    }

    if (uiState.folderAssignForPlaceId != null) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        var showDiscardDialog by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = {
                if (uiState.folderAssignHasChanges) showDiscardDialog = true else viewModel.onDismissFolderAssign()
            },
            sheetState = sheetState,
            containerColor = EonjeColors.surface,
        ) {
            FolderAssignSheetContent(
                placeName = uiState.folderAssignForPlace?.name.orEmpty(),
                folders = uiState.folders,
                selectedFolderId = uiState.folderAssignSelectedFolderId,
                willDeletePlace = uiState.folderAssignWillDeletePlace,
                onToggleFolder = viewModel::onToggleFolderAssignSelection,
                onSave = viewModel::onSaveFolderAssign,
                onDeleteFolder = viewModel::onDeleteFolder,
            )
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDiscardDialog = false
                    scope.launch { sheetState.show() }
                },
                title = { Text("변경사항을 저장하지 않고 닫을까요?") },
                text = { Text("지금 닫으면 바꾼 폴더가 저장되지 않아요.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        viewModel.onDismissFolderAssign()
                    }) {
                        Text("닫기", color = EonjeColors.warning)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        scope.launch { sheetState.show() }
                    }) {
                        Text("취소")
                    }
                },
                containerColor = EonjeColors.surface,
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
 * 바뀌면서 Mid(화면 40%) 지점까지 올라온다. 표준 BottomSheetScaffold는 정지 지점이 2개뿐이라
 * Peek/Mid/Full 3단을 다 쓰려고 AnchoredDraggableState로 직접 구현했다.
 */
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onSelectPlace: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectFolderFilter: (FolderFilter) -> Unit,
    onOpenFolderPicker: (String) -> Unit,
    onOpenFolderAssign: (String) -> Unit,
    onOpenCreateFolderSheet: () -> Unit,
    onOpenAddPlaceSheet: () -> Unit,
    onDeleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    var containerHeightPx by remember { mutableStateOf(0f) }

    val sheetState = remember {
        AnchoredDraggableState(
            initialValue = SheetStop.Peek,
            anchors = DraggableAnchors {
                SheetStop.Peek at 0f
                SheetStop.Mid at 0f
                SheetStop.Full at 0f
            },
        )
    }

    LaunchedEffect(containerHeightPx) {
        if (containerHeightPx <= 0f) return@LaunchedEffect
        val peekPx = with(density) { SHEET_PEEK_HEIGHT.toPx() }
        val midPx = containerHeightPx * SHEET_MID_FRACTION
        val fullPx = containerHeightPx * SHEET_FULL_FRACTION
        sheetState.updateAnchors(
            DraggableAnchors {
                SheetStop.Peek at (containerHeightPx - peekPx)
                SheetStop.Mid at (containerHeightPx - midPx)
                SheetStop.Full at (containerHeightPx - fullPx)
            }
        )
    }

    val highlighted = uiState.highlighted
    val isDetailMode = highlighted != null

    // 선택 상태가 바뀔 때마다(마커/카드 탭, 닫기) Mid로 스냅한다 — 목록/상세 공통.
    LaunchedEffect(uiState.selectedPlaceId, containerHeightPx) {
        if (containerHeightPx <= 0f) return@LaunchedEffect
        sheetState.animateTo(if (isDetailMode) SheetStop.Mid else SheetStop.Peek)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() },
    ) {
        KakaoMapView(
            places = uiState.places,
            highlightedId = uiState.selectedPlaceId,
            currentLocation = uiState.currentLocation,
            onPlaceClick = onSelectPlace,
            modifier = Modifier.fillMaxSize(),
        )

        val sheetHeightDp = with(density) { (containerHeightPx * SHEET_FULL_FRACTION).toDp() }
            .let { if (containerHeightPx > 0f) it else (screenHeightDp * SHEET_FULL_FRACTION).dp }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .offset { IntOffset(0, sheetState.requireOffset().roundToInt()) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(EonjeColors.surface),
            ) {
                if (isDetailMode) {
                    PlaceDetailSheetContent(
                        sheetState = sheetState,
                        place = highlighted,
                        posts = uiState.selectedPlacePosts,
                        onClose = { onSelectPlace(null) },
                        onOpenFolderAssign = { onOpenFolderAssign(highlighted.id) },
                    )
                } else {
                    PlaceListSheetContent(
                        sheetState = sheetState,
                        uiState = uiState,
                        onSearchQueryChange = onSearchQueryChange,
                        onSelectFolderFilter = onSelectFolderFilter,
                        onPlaceClick = onSelectPlace,
                        onOpenFolderPicker = onOpenFolderPicker,
                        onAddFolder = onOpenCreateFolderSheet,
                        onAddPlace = onOpenAddPlaceSheet,
                        onDeleteFolder = onDeleteFolder,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(EonjeColors.border)
        )
    }
}

/**
 * 리스트/상세 콘텐츠를 스크롤하다가 더 내릴 게 없으면(맨 위까지 다 스크롤됐는데 계속 아래로
 * 당기면) 그 남는 드래그양을 시트한테 넘겨서 시트가 대신 내려가게 한다 — 반대로 시트가 아직
 * Full까지 안 올라온 상태에서 위로 당기면 리스트를 스크롤하기 전에 시트부터 마저 올린다.
 */
@Composable
private fun rememberSheetNestedScrollConnection(
    sheetState: AnchoredDraggableState<SheetStop>,
): NestedScrollConnection = remember(sheetState) {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            return if (delta < 0 && sheetState.requireOffset() > sheetState.anchors.minPosition()) {
                Offset(0f, sheetState.dispatchRawDelta(delta))
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            return if (available.y > 0) {
                Offset(0f, sheetState.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return if (available.y < 0 && sheetState.requireOffset() > sheetState.anchors.minPosition()) {
                sheetState.settle(SHEET_SNAP_ANIMATION_SPEC)
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            return if (available.y > 0) {
                sheetState.settle(SHEET_SNAP_ANIMATION_SPEC)
                available
            } else {
                Velocity.Zero
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "가게 검색",
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(EonjeColors.surfaceVariant)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = EonjeColors.textMuted, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = Typography.bodyMedium, color = EonjeColors.textMuted)
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

/**
 * 카카오 로컬 검색으로 게시물 없이 장소를 바로 추가하는 시트. 결과를 탭하면 바로 저장되고 시트가
 * 닫힌다 — 같은 카카오 장소가 이미 저장돼 있으면 [PlaceRepository.save]가 새로 만들지 않고 재사용한다.
 */
@Composable
private fun AddPlaceSheetContent(
    uiState: AddPlaceUiState,
    onQueryChange: (String) -> Unit,
    onSelectCandidate: (PlaceCandidate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(20.dp, 4.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("장소 직접 추가", style = Typography.titleLarge, color = EonjeColors.textPrimary)
        SearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            placeholder = "장소 검색",
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 120.dp)) {
            when {
                uiState.isSearching -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EonjeColors.accent)
                }
                uiState.searchError != null -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(uiState.searchError, style = Typography.bodyMedium, color = EonjeColors.warning)
                }
                uiState.query.isBlank() -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("추가할 장소를 검색해보세요", style = Typography.bodyMedium, color = EonjeColors.textMuted)
                }
                uiState.results.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("검색 결과가 없어요", style = Typography.bodyMedium, color = EonjeColors.textMuted)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.results, key = { it.kakaoPlaceId }) { candidate ->
                        AddPlaceCandidateRow(candidate = candidate, onClick = { onSelectCandidate(candidate) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPlaceCandidateRow(candidate: PlaceCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.surfaceVariant)
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
    }
}

/**
 * 시트 목록 모드: 폴더 탭 한 줄 + 장소 카드 리스트. peek 상태에서는 탭 줄만 보인다.
 * 핸들뿐 아니라 폴더 탭 줄도 그 자체로 드래그 대상이다 — 가로 스크롤만 되는 영역이라
 * nestedScroll로는 세로 드래그가 시트에 전달되지 않아서, 헤더 전체를 직접 anchoredDraggable에 건다.
 */
@Composable
private fun PlaceListSheetContent(
    sheetState: AnchoredDraggableState<SheetStop>,
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectFolderFilter: (FolderFilter) -> Unit,
    onPlaceClick: (String) -> Unit,
    onOpenFolderPicker: (String) -> Unit,
    onAddFolder: () -> Unit,
    onAddPlace: () -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().anchoredDraggable(sheetState, Orientation.Vertical)) {
            SheetHandle()
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(EonjeColors.accent)
                        .clickable(onClick = onAddPlace)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("장소 추가", style = Typography.labelMedium, color = EonjeColors.onAccent)
                }
            }
            FolderTabsRow(
                folders = uiState.folders,
                selected = uiState.folderFilter,
                onSelect = onSelectFolderFilter,
                onAddFolder = onAddFolder,
                onDeleteFolder = onDeleteFolder,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .nestedScroll(rememberSheetNestedScrollConnection(sheetState)),
        ) {
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
private fun FolderTabsRow(
    folders: List<FolderEntity>,
    selected: FolderFilter,
    onSelect: (FolderFilter) -> Unit,
    onAddFolder: () -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    var folderPendingDelete by remember { mutableStateOf<FolderEntity?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(20.dp, 4.dp, 20.dp, 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(label = "전체", selected = selected is FolderFilter.All, onClick = { onSelect(FolderFilter.All) })
        folders.forEach { folder ->
            FilterChip(
                label = folder.name,
                selected = selected is FolderFilter.ByFolder && selected.folderId == folder.id,
                onClick = { onSelect(FolderFilter.ByFolder(folder.id)) },
                onLongClick = { folderPendingDelete = folder },
            )
        }
        FilterChip(
            label = "미분류",
            selected = selected is FolderFilter.Unclassified,
            onClick = { onSelect(FolderFilter.Unclassified) },
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.dp, EonjeColors.border, CircleShape)
                .clickable(onClick = onAddFolder),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "폴더 추가", tint = EonjeColors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }

    val target = folderPendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { folderPendingDelete = null },
            title = { Text("'${target.name}' 폴더를 삭제할까요?") },
            text = { Text("이 폴더에 있던 장소는 미분류로 이동해요.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(target.id)
                    folderPendingDelete = null
                }) {
                    Text("삭제", color = EonjeColors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingDelete = null }) {
                    Text("취소")
                }
            },
            containerColor = EonjeColors.surface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) EonjeColors.accent else EonjeColors.surfaceVariant)
            .then(if (!selected) Modifier.border(1.dp, EonjeColors.border, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
 * 시트 상세 모드: 마커든 리스트 카드든 장소를 탭하면 이 내용으로 바뀌면서 Mid(화면 40%)까지
 * 올라온다. 더 드래그하면 Full로 펼쳐져서 게시물까지 다 보인다. 이름 줄 오른쪽의 마커 버튼으로
 * 폴더를 바꾼다(체크 후 저장 — [FolderAssignSheetContent] 참고). 체크박스를 전부 해제하고
 * 저장하면 "저장삭제"로 배정이 지워지므로 장소 자체를 지우는 별도 버튼은 두지 않는다.
 * 핸들만 고정해서 드래그 전용으로 두고, 이름부터 게시물 목록까지는 전부 한 스크롤 영역에 넣는다 —
 * 스크롤이 맨 위에 닿으면 nestedScroll이 남는 드래그를 시트로 넘겨서, 어디를 잡고 끌어도
 * 시트가 오르내리는 동작은 그대로 유지된다.
 */
@Composable
private fun PlaceDetailSheetContent(
    sheetState: AnchoredDraggableState<SheetStop>,
    place: HomePlace,
    posts: List<HomePlacePost>,
    onClose: () -> Unit,
    onOpenFolderAssign: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().anchoredDraggable(sheetState, Orientation.Vertical)) {
            SheetHandle()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(rememberSheetNestedScrollConnection(sheetState))
                .verticalScroll(rememberScrollState())
                .padding(20.dp, 0.dp, 20.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        place.name,
                        style = Typography.headlineMedium,
                        color = EonjeColors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    // IconButton은 내부에서 48dp 정사각형 안에 콘텐츠를 가운데 정렬해서, Row를 Top으로
                    // 맞춰도 아이콘 자체는 아래로 밀려 보인다 — 제목 첫 줄에 맞춰 진짜로 위쪽에 붙이려고
                    // 터치 영역만 확보한 Box에 TopCenter로 직접 배치한다.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenFolderAssign),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        FolderMarkerIcon(color = place.folderColor, icon = place.folderIcon)
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(x = 8.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "닫기", tint = EonjeColors.textSecondary)
                    }
                }
                if (place.category.isNotBlank()) {
                    Text(place.category, style = Typography.bodyMedium, color = EonjeColors.textTertiary)
                }
                Text(place.address, style = Typography.bodyMedium, color = EonjeColors.textMuted)
            }

            PlaceholderImage(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                imageUrl = place.thumbnailUrl,
                cornerRadius = 16.dp,
                label = place.name,
            )

            FilledPillButton(
                text = "길찾기",
                icon = Icons.Filled.Place,
                onClick = {
                    val lat = place.latitude
                    val lng = place.longitude
                    if (lat == null || lng == null) {
                        Toast.makeText(context, "좌표 정보가 없어서 길찾기를 열 수 없어요", Toast.LENGTH_SHORT).show()
                        return@FilledPillButton
                    }
                    // 카카오맵 공식 공유 링크 — 앱이 깔려 있으면 앱에서, 없으면 모바일 웹에서 길찾기가 열린다.
                    val url = "https://map.kakao.com/link/to/${Uri.encode(place.name)},$lat,$lng"
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure {
                        Toast.makeText(context, "열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
                    }
                },
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
            onOpenFolderAssign = {},
            onOpenCreateFolderSheet = {},
            onOpenAddPlaceSheet = {},
            onDeleteFolder = {},
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
            onOpenFolderAssign = {},
            onOpenCreateFolderSheet = {},
            onOpenAddPlaceSheet = {},
            onDeleteFolder = {},
        )
    }
}
