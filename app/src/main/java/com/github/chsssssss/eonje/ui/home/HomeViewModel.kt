package com.github.chsssssss.eonje.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.GeoPoint
import com.github.chsssssss.eonje.domain.repository.FolderRepository
import com.github.chsssssss.eonje.domain.repository.LocationRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedPostRepository: SavedPostRepository,
    private val placeRepository: PlaceRepository,
    private val folderRepository: FolderRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedPlaceId = MutableStateFlow<String?>(null)
    private val currentLocation = MutableStateFlow<GeoPoint?>(null)
    private val folderFilter = MutableStateFlow<FolderFilter>(FolderFilter.All)
    private val folderPickerForPlaceId = MutableStateFlow<String?>(null)

    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages = _toastMessages.receiveAsFlow()

    // combine은 인자 5개까지만 받아서, 화면 상태들을 하나로 묶어 한 자리를 차지하게 한다.
    private val viewState: Flow<ViewState> =
        combine(selectedPlaceId, currentLocation, folderPickerForPlaceId) { selectedId, location, pickerFor ->
            ViewState(selectedId, location, pickerFor)
        }

    val uiState: StateFlow<HomeUiState> = combine(
        placeRepository.observeAll(),
        searchQuery,
        folderRepository.observeAll(),
        folderFilter,
        viewState,
    ) { places, query, folders, filter, (selectedPlaceId, currentLocation, folderPickerForPlaceId) ->
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
        val folderNamesById = folders.associate { it.id to it.name }
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
                place.toHomePlace(posts.firstNotNullOfOrNull { it.thumbnailUrl }, folderNamesById[place.folderId])
            },
            folders = folders,
            folderFilter = filter,
            searchQuery = query,
            selectedPlaceId = selectedPlaceId,
            selectedPlacePosts = selectedPosts,
            currentLocation = currentLocation,
            folderPickerForPlaceId = folderPickerForPlaceId,
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

    fun onDirectionsClick() {
        _toastMessages.trySend("길찾기 연결은 아직 준비 중이에요")
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
}

private data class ViewState(
    val selectedPlaceId: String?,
    val currentLocation: GeoPoint?,
    val folderPickerForPlaceId: String?,
)

private fun PlaceEntity.toHomePlace(thumbnailUrl: String?, folderName: String?) = HomePlace(
    id = id,
    name = name ?: "이름 없는 장소",
    category = category.orEmpty(),
    address = address.orEmpty(),
    latitude = latitude,
    longitude = longitude,
    thumbnailUrl = thumbnailUrl,
    folderId = folderId,
    folderName = folderName,
    memo = memo.orEmpty(),
)

private fun SavedPostEntity.toHomePlacePost() = HomePlacePost(
    id = id,
    label = instagramUrl,
    savedAt = RelativeTimeFormatter.format(createdAt) + " 저장",
    thumbnailUrl = thumbnailUrl,
)
