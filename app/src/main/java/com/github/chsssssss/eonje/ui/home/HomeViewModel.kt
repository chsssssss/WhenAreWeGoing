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

    private val filteredSelection: Flow<Pair<String?, Set<String>?>> = selectedTagId.flatMapLatest { tagId ->
        if (tagId == null) {
            flowOf<Pair<String?, Set<String>?>>(null to null)
        } else {
            tagRepository.observePlaceIdsForTag(tagId).map { ids -> tagId to ids.toSet() }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        savedPostRepository.observeUnresolved(),
        placeRepository.observeAll(),
        tagRepository.observeAll(),
        filteredSelection,
        isMapView,
    ) { pendingPosts, places, tags, selection, mapView ->
        val (selTagId, allowedIds) = selection
        val visiblePlaces = if (allowedIds == null) places else places.filter { it.id in allowedIds }
        HomeUiState(
            isMapView = mapView,
            pendingCount = pendingPosts.size,
            places = visiblePlaces.map { it.toHomePlace() },
            totalCount = places.size,
            tags = tags,
            selectedTagId = selTagId,
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
}

private fun PlaceEntity.toHomePlace() = HomePlace(
    id = id,
    name = name ?: "이름 없는 장소",
    category = category.orEmpty(),
    address = address.orEmpty(),
    latitude = latitude,
    longitude = longitude,
)
