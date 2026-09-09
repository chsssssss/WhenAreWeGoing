package com.github.chsssssss.eonje.ui.resolve

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import com.github.chsssssss.eonje.ui.navigation.EonjeDestinations
import com.github.chsssssss.eonje.widget.InboxWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val extractedCandidateRepository: ExtractedCandidateRepository,
    @ApplicationContext private val context: Context,
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
            val isMulti = post?.status == ResolveStatus.NEEDS_REVIEW
            _uiState.update {
                it.copy(
                    instagramUrl = post?.instagramUrl.orEmpty(),
                    thumbnailUrl = post?.thumbnailUrl,
                    subtitle = post?.let { p -> RelativeTimeFormatter.format(p.createdAt) + " 저장" }.orEmpty(),
                    isLoadingPost = false,
                    isMultiMode = isMulti,
                )
            }
            if (isMulti) {
                extractedCandidateRepository.observeForPost(postId).collect { rows ->
                    _uiState.update { it.copy(multiGroups = buildGroups(rows)) }
                }
            }
        }

        viewModelScope.launch {
            queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query -> runSearch(query) }
        }
    }

    private fun buildGroups(rows: List<ExtractedCandidateEntity>): List<MultiCandidateGroup> =
        rows.groupBy { it.extractionIndex }
            .toSortedMap()
            .map { (index, group) ->
                val sorted = group.sortedBy { it.rank }
                val candidates = sorted.map { it.toPlaceCandidate() }
                MultiCandidateGroup(
                    extractionIndex = index,
                    extractedName = sorted.first().extractedName,
                    candidates = candidates,
                    selected = candidates.firstOrNull(),
                    checked = candidates.isNotEmpty(),
                )
            }

    private fun ExtractedCandidateEntity.toPlaceCandidate() = PlaceCandidate(
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = address,
        category = category,
        latitude = latitude,
        longitude = longitude,
    )

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

    fun onToggleGroupChecked(extractionIndex: Int) {
        _uiState.update { state ->
            state.copy(
                multiGroups = state.multiGroups.map {
                    if (it.extractionIndex == extractionIndex) it.copy(checked = !it.checked) else it
                }
            )
        }
    }

    fun onToggleGroupExpanded(extractionIndex: Int) {
        _uiState.update { state ->
            state.copy(
                multiGroups = state.multiGroups.map {
                    if (it.extractionIndex == extractionIndex) it.copy(expanded = !it.expanded) else it
                }
            )
        }
    }

    fun onSelectMultiCandidate(extractionIndex: Int, candidate: PlaceCandidate) {
        _uiState.update { state ->
            state.copy(
                multiGroups = state.multiGroups.map {
                    if (it.extractionIndex == extractionIndex) {
                        it.copy(selected = candidate, checked = true, expanded = false)
                    } else it
                }
            )
        }
    }

    fun onConfirm() {
        if (_uiState.value.isMultiMode) onConfirmMulti() else onConfirmSingle()
    }

    private fun onConfirmSingle() {
        val state = _uiState.value
        val candidate = state.selected ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            savePlace(candidate)
            savedPostRepository.updateStatus(postId, ResolveStatus.RESOLVED)
            InboxWidget().updateAll(context)

            _uiState.update { it.copy(isSaving = false) }
            _events.send(ResolveEvent.Toast("${candidate.name} 저장됨"))
            _events.send(ResolveEvent.NavigateBack)
        }
    }

    private fun onConfirmMulti() {
        val state = _uiState.value
        val toSave = state.multiGroups.filter { it.checked && it.selected != null }
        if (toSave.isEmpty() || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            toSave.forEach { group -> savePlace(group.selected!!) }
            savedPostRepository.updateStatus(postId, ResolveStatus.RESOLVED)
            extractedCandidateRepository.replaceForPost(postId, emptyList())
            InboxWidget().updateAll(context)

            _uiState.update { it.copy(isSaving = false) }
            val message = if (toSave.size == 1) "${toSave.first().selected!!.name} 저장됨" else "${toSave.size}곳 저장됨"
            _events.send(ResolveEvent.Toast(message))
            _events.send(ResolveEvent.NavigateBack)
        }
    }

    private suspend fun savePlace(candidate: PlaceCandidate) {
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
    }
}
