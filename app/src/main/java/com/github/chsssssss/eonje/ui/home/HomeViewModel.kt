package com.github.chsssssss.eonje.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import com.github.chsssssss.eonje.domain.model.GeoPoint
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.repository.FolderRepository
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.LocationRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedPostRepository: SavedPostRepository,
    private val placeRepository: PlaceRepository,
    private val folderRepository: FolderRepository,
    private val locationRepository: LocationRepository,
    private val kakaoLocalRepository: KakaoLocalRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedPlaceId = MutableStateFlow<String?>(null)
    private val currentLocation = MutableStateFlow<GeoPoint?>(null)
    private val folderFilter = MutableStateFlow<FolderFilter>(FolderFilter.All)
    private val folderPickerForPlaceId = MutableStateFlow<String?>(null)
    private val showCreateFolderSheet = MutableStateFlow(false)
    private val folderAssignForPlaceId = MutableStateFlow<String?>(null)
    private val folderAssignSelectedFolderId = MutableStateFlow<String?>(null)

    private val showAddPlaceSheet = MutableStateFlow(false)
    private val addPlaceQuery = MutableStateFlow("")
    private val addPlaceResults = MutableStateFlow<List<PlaceCandidate>>(emptyList())
    private val addPlaceSearching = MutableStateFlow(false)
    private val addPlaceError = MutableStateFlow<String?>(null)

    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages = _toastMessages.receiveAsFlow()

    val showAddPlace: StateFlow<Boolean> = showAddPlaceSheet

    // 메인 화면 상태의 거대한 combine 체인에 엮지 않고 따로 관리한다 — 검색 디바운스가 있는 독립된 흐름이라서다.
    val addPlaceUiState: StateFlow<AddPlaceUiState> = combine(
        addPlaceQuery, addPlaceResults, addPlaceSearching, addPlaceError,
    ) { query, results, searching, error ->
        AddPlaceUiState(query, results, searching, error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddPlaceUiState(),
    )

    init {
        viewModelScope.launch {
            addPlaceQuery
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query -> runAddPlaceSearch(query) }
        }
    }

    private suspend fun runAddPlaceSearch(query: String) {
        if (query.isBlank()) {
            addPlaceResults.value = emptyList()
            addPlaceSearching.value = false
            addPlaceError.value = null
            return
        }
        addPlaceSearching.value = true
        kakaoLocalRepository.searchPlaces(query)
            .onSuccess {
                addPlaceResults.value = it
                addPlaceSearching.value = false
                addPlaceError.value = null
            }
            .onFailure {
                addPlaceResults.value = emptyList()
                addPlaceSearching.value = false
                addPlaceError.value = it.message ?: "검색에 실패했어요"
            }
    }

    // combine은 인자 5개까지만 받아서, 상세화면 폴더 변경 시트의 상태 둘을 먼저 하나로 묶는다.
    private val folderAssignState: Flow<FolderAssignState> =
        combine(folderAssignForPlaceId, folderAssignSelectedFolderId) { placeId, selectedFolderId ->
            FolderAssignState(placeId, selectedFolderId)
        }

    // combine은 인자 5개까지만 받아서, 화면 상태들을 하나로 묶어 한 자리를 차지하게 한다.
    private val viewState: Flow<ViewState> =
        combine(
            selectedPlaceId,
            currentLocation,
            folderPickerForPlaceId,
            showCreateFolderSheet,
            folderAssignState,
        ) { selectedId, location, pickerFor, showCreateFolder, folderAssign ->
            ViewState(selectedId, location, pickerFor, showCreateFolder, folderAssign)
        }

    val uiState: StateFlow<HomeUiState> = combine(
        placeRepository.observeAll(),
        searchQuery,
        folderRepository.observeAll(),
        folderFilter,
        viewState,
    ) { places, query, folders, filter, (selectedPlaceId, currentLocation, folderPickerForPlaceId, showCreateFolder, folderAssign) ->
        val folderFiltered = when (filter) {
            FolderFilter.All -> places
            FolderFilter.Unclassified -> places.filter { it.folderId == null }
            is FolderFilter.ByFolder -> places.filter { it.folderId == filter.folderId }
        }
        val trimmedQuery = query.trim()
        val visiblePlaces = if (trimmedQuery.isEmpty()) {
            folderFiltered
        } else {
            folderFiltered.filter { place ->
                place.name.orEmpty().contains(trimmedQuery, ignoreCase = true) ||
                    place.category.orEmpty().contains(trimmedQuery, ignoreCase = true) ||
                    place.address.orEmpty().contains(trimmedQuery, ignoreCase = true)
            }
        }
        val foldersById = folders.associateBy { it.id }
        val placesWithPosts = visiblePlaces.map { place ->
            val postIds = placeRepository.postIdsForPlace(place.id)
            place to savedPostRepository.findByIds(postIds)
        }
        val selectedPosts = placesWithPosts
            .firstOrNull { (place, _) -> place.id == selectedPlaceId }
            ?.second.orEmpty()
            .map { it.toHomePlacePost() }

        HomeUiState(
            places = placesWithPosts.map { (place, posts) ->
                place.toHomePlace(posts.firstNotNullOfOrNull { it.thumbnailUrl }, foldersById[place.folderId])
            },
            folders = folders,
            folderFilter = filter,
            searchQuery = query,
            selectedPlaceId = selectedPlaceId,
            selectedPlacePosts = selectedPosts,
            currentLocation = currentLocation,
            folderPickerForPlaceId = folderPickerForPlaceId,
            showCreateFolderSheet = showCreateFolder,
            folderAssignForPlaceId = folderAssign.placeId,
            folderAssignSelectedFolderId = folderAssign.selectedFolderId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSelectPlace(placeId: String?) {
        selectedPlaceId.value = placeId
    }

    fun onSelectFolderFilter(filter: FolderFilter) {
        folderFilter.value = filter
    }

    /** 위치 권한이 확인된 뒤 호출한다. 못 잡으면 null로 남고 지도는 저장된 장소 기준으로 그려진다. */
    fun refreshCurrentLocation() {
        viewModelScope.launch {
            currentLocation.value = locationRepository.getCurrentLocation()
        }
    }

    fun onOpenFolderPicker(placeId: String) {
        folderPickerForPlaceId.value = placeId
    }

    fun onDismissFolderPicker() {
        folderPickerForPlaceId.value = null
    }

    fun onSelectFolder(folderId: String?) {
        val placeId = folderPickerForPlaceId.value ?: return
        folderPickerForPlaceId.value = null
        viewModelScope.launch { placeRepository.assignFolder(placeId, folderId) }
    }

    fun onCreateFolder(name: String) {
        if (name.isBlank()) return
        val placeId = folderPickerForPlaceId.value ?: return
        folderPickerForPlaceId.value = null
        viewModelScope.launch {
            val folder = folderRepository.findOrCreateByName(name)
            placeRepository.assignFolder(placeId, folder.id)
        }
    }

    fun onOpenCreateFolderSheet() {
        showCreateFolderSheet.value = true
    }

    fun onDismissCreateFolderSheet() {
        showCreateFolderSheet.value = false
    }

    fun onCreateFolderWithAppearance(name: String, color: Int, iconKey: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            folderRepository.createWithAppearance(name, color, iconKey)
            showCreateFolderSheet.value = false
        }
    }

    fun onDeleteFolder(folderId: String) {
        viewModelScope.launch {
            folderRepository.delete(folderId)
            val filter = folderFilter.value
            if (filter is FolderFilter.ByFolder && filter.folderId == folderId) {
                folderFilter.value = FolderFilter.All
            }
            if (folderAssignSelectedFolderId.value == folderId) {
                folderAssignSelectedFolderId.value = null
            }
        }
    }

    /** 상세화면 헤더의 마커 버튼 — 현재 배정된 폴더를 미리 체크한 채로 변경 시트를 연다. */
    fun onOpenFolderAssign(placeId: String) {
        val currentFolderId = uiState.value.places.firstOrNull { it.id == placeId }?.folderId
        folderAssignForPlaceId.value = placeId
        folderAssignSelectedFolderId.value = currentFolderId
    }

    /** 체크박스 토글 — 이미 체크된 폴더를 다시 누르면 전부 해제된다(장소당 폴더는 하나뿐이라 단일 선택). */
    fun onToggleFolderAssignSelection(folderId: String) {
        folderAssignSelectedFolderId.value =
            if (folderAssignSelectedFolderId.value == folderId) null else folderId
    }

    /** 아무 폴더도 체크 안 된 채로 저장하면 배정을 지우는 것과 같다(저장삭제). */
    fun onSaveFolderAssign() {
        val placeId = folderAssignForPlaceId.value ?: return
        val folderId = folderAssignSelectedFolderId.value
        viewModelScope.launch { placeRepository.assignFolder(placeId, folderId) }
        folderAssignForPlaceId.value = null
    }

    fun onDismissFolderAssign() {
        folderAssignForPlaceId.value = null
    }

    fun onOpenAddPlaceSheet() {
        showAddPlaceSheet.value = true
    }

    fun onDismissAddPlaceSheet() {
        showAddPlaceSheet.value = false
        addPlaceQuery.value = ""
        addPlaceResults.value = emptyList()
        addPlaceError.value = null
    }

    fun onAddPlaceQueryChange(query: String) {
        addPlaceQuery.value = query
    }

    /** 게시물 없이 검색 결과를 바로 장소로 저장한다 — 같은 카카오 장소가 이미 있으면 새로 만들지 않고 재사용한다. */
    fun onAddPlaceCandidateSelected(candidate: PlaceCandidate) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            placeRepository.save(
                PlaceEntity(
                    id = UUID.randomUUID().toString(),
                    name = candidate.name,
                    address = candidate.address,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    kakaoPlaceId = candidate.kakaoPlaceId,
                    category = candidate.category,
                    memo = null,
                    status = ResolveStatus.RESOLVED,
                    createdAt = now,
                    resolvedAt = now,
                    folderId = null,
                )
            )
            onDismissAddPlaceSheet()
            _toastMessages.trySend("${candidate.name} 저장됨")
        }
    }
}

private data class FolderAssignState(
    val placeId: String?,
    val selectedFolderId: String?,
)

private data class ViewState(
    val selectedPlaceId: String?,
    val currentLocation: GeoPoint?,
    val folderPickerForPlaceId: String?,
    val showCreateFolderSheet: Boolean,
    val folderAssign: FolderAssignState,
)

private fun PlaceEntity.toHomePlace(thumbnailUrl: String?, folder: FolderEntity?) = HomePlace(
    id = id,
    name = name ?: "이름 없는 장소",
    category = category.orEmpty(),
    address = address.orEmpty(),
    latitude = latitude,
    longitude = longitude,
    thumbnailUrl = thumbnailUrl,
    folderId = folderId,
    folderName = folder?.name,
    folderColor = folder?.color ?: FolderVisuals.DEFAULT_COLOR,
    folderIcon = folder?.iconKey ?: FolderVisuals.DEFAULT_ICON,
    memo = memo.orEmpty(),
)

private fun SavedPostEntity.toHomePlacePost() = HomePlacePost(
    id = id,
    label = instagramUrl,
    savedAt = RelativeTimeFormatter.format(createdAt) + " 저장",
    thumbnailUrl = thumbnailUrl,
)
