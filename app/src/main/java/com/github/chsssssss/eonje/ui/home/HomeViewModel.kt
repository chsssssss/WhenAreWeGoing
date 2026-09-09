package com.github.chsssssss.eonje.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    savedPostRepository: SavedPostRepository,
    placeRepository: PlaceRepository,
) : ViewModel() {

    private val isMapView = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> = combine(
        savedPostRepository.observeUnresolved(),
        placeRepository.observeAll(),
        isMapView,
    ) { pendingPosts, places, mapView ->
        HomeUiState(
            isMapView = mapView,
            pendingCount = pendingPosts.size,
            places = places.map { it.toHomePlace() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun toggleView() {
        isMapView.update { !it }
    }
}

private fun PlaceEntity.toHomePlace() = HomePlace(
    id = id,
    name = name ?: "이름 없는 장소",
    category = category.orEmpty(),
    address = address.orEmpty(),
)
