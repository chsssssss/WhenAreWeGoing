package com.github.chsssssss.eonje.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.PlaceEntity
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
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    savedPostRepository: SavedPostRepository,
    placeRepository: PlaceRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    private val isMapView = MutableStateFlow(true)
    private val selectedTagId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

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
        isMapView,
    ) { pendingPosts, places, tags, filters, mapView ->
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
}

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
