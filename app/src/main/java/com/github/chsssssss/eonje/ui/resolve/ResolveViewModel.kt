package com.github.chsssssss.eonje.ui.resolve

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import com.github.chsssssss.eonje.ui.navigation.EonjeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface ResolveEvent {
    data class Toast(val message: String) : ResolveEvent
    data object NavigateBack : ResolveEvent
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class ResolveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savedPostRepository: SavedPostRepository,
    private val placeRepository: PlaceRepository,
    private val kakaoLocalRepository: KakaoLocalRepository,
) : ViewModel() {

    private val postId: String = checkNotNull(savedStateHandle[EonjeDestinations.RESOLVE_POST_ID_ARG])

    private val _uiState = MutableStateFlow(ResolveUiState(postId = postId))
    val uiState: StateFlow<ResolveUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    private val _events = Channel<ResolveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val post = savedPostRepository.findById(postId)
            _uiState.update {
                it.copy(
                    instagramUrl = post?.instagramUrl.orEmpty(),
                    subtitle = post?.let { p -> RelativeTimeFormatter.format(p.createdAt) + " 저장" }.orEmpty(),
                    isLoadingPost = false,
                )
            }
        }

        viewModelScope.launch {
            queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query -> runSearch(query) }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false, searchError = null) }
            return
        }
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        kakaoLocalRepository.searchPlaces(query)
            .onSuccess { places ->
                _uiState.update { it.copy(results = places, isSearching = false) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(results = emptyList(), isSearching = false, searchError = error.message ?: "검색에 실패했어요")
                }
            }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value, selected = null) }
        queryFlow.value = value
    }

    fun onSelectCandidate(candidate: PlaceCandidate) {
        _uiState.update { it.copy(selected = candidate) }
    }

    fun onOpenOriginal() {
        _events.trySend(ResolveEvent.Toast("원본 링크는 아직 열 수 없어요"))
    }

    fun onConfirm() {
        val state = _uiState.value
        val candidate = state.selected ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val now = System.currentTimeMillis()
            val placeId = placeRepository.save(
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
                )
            )
            placeRepository.linkPostToPlace(postId, placeId)
            savedPostRepository.updateStatus(postId, ResolveStatus.RESOLVED)

            _uiState.update { it.copy(isSaving = false) }
            _events.send(ResolveEvent.Toast("${candidate.name} 저장됨"))
            _events.send(ResolveEvent.NavigateBack)
        }
    }
}
