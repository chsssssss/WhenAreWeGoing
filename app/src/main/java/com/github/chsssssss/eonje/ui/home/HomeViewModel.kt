package com.github.chsssssss.eonje.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.GeoPoint
import com.github.chsssssss.eonje.domain.repository.LocationRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    savedPostRepository: SavedPostRepository,
    placeRepository: PlaceRepository,
    tagRepository: TagRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val isMapView = MutableStateFlow(true)
    private val selectedTagId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val selectedPlaceId = MutableStateFlow<String?>(null)
    private val currentLocation = MutableStateFlow<GeoPoint?>(null)

    // combine은 인자 5개까지만 받아서, 화면 상태들을 하나로 묶어 한 자리를 차지하게 한다.
    private val viewState: Flow<ViewState> =
        combine(isMapView, selectedPlaceId, currentLocation) { mapView, selectedId, location ->
            ViewState(mapView, selectedId, location)
        }

    private val filteredSelection: Flow<Pair<String?, Set<String>?>> = selectedTagId.flatMapLatest { tagId ->
        if (tagId == null) {
            flowOf<Pair<String?, Set<String>?>>(null to null)
        } else {
            tagRepository.observePlaceIdsForTag(tagId).map { ids -> tagId to ids.toSet() }
        }
    }

    private val filters: Flow<HomeFilters> = combine(filteredSelection, searchQuery) { selection, query ->
        HomeFilters(selectedTagId = selection.first, allowedPlaceIds = selection.second, query = query)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        savedPostRepository.observeUnresolved(),
        placeRepository.observeAll(),
        tagRepository.observeAll(),
        filters,
        viewState,
    ) { pendingPosts, places, tags, filters, (mapView, selectedPlaceId, currentLocation) ->
        val tagFiltered = if (filters.allowedPlaceIds == null) places else places.filter { it.id in filters.allowedPlaceIds }
        val query = filters.query.trim()
        val visiblePlaces = if (query.isEmpty()) {
            tagFiltered
        } else {
            tagFiltered.filter { place ->
                place.name.orEmpty().contains(query, ignoreCase = true) ||
                    place.category.orEmpty().contains(query, ignoreCase = true) ||
                    place.address.orEmpty().contains(query, ignoreCase = true)
            }
        }
        HomeUiState(
            isMapView = mapView,
            pendingCount = pendingPosts.size,
            places = visiblePlaces.map { place ->
                val postIds = placeRepository.postIdsForPlace(place.id)
                val thumbnailUrl = savedPostRepository.findByIds(postIds).firstNotNullOfOrNull { it.thumbnailUrl }
                place.toHomePlace(thumbnailUrl)
            },
            totalCount = places.size,
            tags = tags,
            selectedTagId = filters.selectedTagId,
            searchQuery = filters.query,
            selectedPlaceId = selectedPlaceId,
            currentLocation = currentLocation,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun toggleView() {
        isMapView.update { !it }
    }

    fun onSelectTag(tagId: String?) {
        selectedTagId.value = tagId
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSelectPlace(placeId: String?) {
        selectedPlaceId.value = placeId
    }

    /** 위치 권한이 확인된 뒤 호출한다. 못 잡으면 null로 남고 지도는 저장된 장소 기준으로 그려진다. */
    fun refreshCurrentLocation() {
        viewModelScope.launch {
            currentLocation.value = locationRepository.getCurrentLocation()
        }
    }
}

private data class ViewState(
    val isMapView: Boolean,
    val selectedPlaceId: String?,
    val currentLocation: GeoPoint?,
)

private data class HomeFilters(
    val selectedTagId: String?,
    val allowedPlaceIds: Set<String>?,
    val query: String,
)

private fun PlaceEntity.toHomePlace(thumbnailUrl: String?) = HomePlace(
    id = id,
    name = name ?: "이름 없는 장소",
    category = category.orEmpty(),
    address = address.orEmpty(),
    latitude = latitude,
    longitude = longitude,
    thumbnailUrl = thumbnailUrl,
)
