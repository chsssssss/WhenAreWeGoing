package com.github.chsssssss.eonje.ui.placedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.TagEntity
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.TagRepository
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import com.github.chsssssss.eonje.ui.navigation.EonjeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placeRepository: PlaceRepository,
    private val savedPostRepository: SavedPostRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle[EonjeDestinations.PLACE_DETAIL_ID_ARG])

    private val _uiState = MutableStateFlow(PlaceDetailUiState())
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages = _toastMessages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val place = placeRepository.findById(placeId)
            val postIds = placeRepository.postIdsForPlace(placeId)
            val posts = savedPostRepository.findByIds(postIds)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = place?.name ?: "이름 없는 장소",
                    category = place?.category.orEmpty(),
                    address = place?.address.orEmpty(),
                    memo = place?.memo.orEmpty(),
                    posts = posts.map { post ->
                        PlacePost(
                            id = post.id,
                            label = post.instagramUrl,
                            savedAt = RelativeTimeFormatter.format(post.createdAt) + " 저장",
                        )
                    },
                )
            }
        }

        viewModelScope.launch {
            combine(
                tagRepository.observeTagsForPlace(placeId),
                tagRepository.observeAll(),
            ) { attached, all -> attached to all }
                .collect { (attached, all) ->
                    _uiState.update { it.copy(tags = attached, allTags = all) }
                }
        }
    }

    fun onDirectionsClick() {
        _toastMessages.trySend("길찾기 연결은 아직 준비 중이에요")
    }

    fun onAddTagClick() {
        _uiState.update { it.copy(showTagPicker = true) }
    }

    fun onDismissTagPicker() {
        _uiState.update { it.copy(showTagPicker = false) }
    }

    fun onToggleTag(tag: TagEntity) {
        val isAttached = _uiState.value.tags.any { it.id == tag.id }
        viewModelScope.launch {
            if (isAttached) {
                tagRepository.detachTagFromPlace(placeId, tag.id)
            } else {
                tagRepository.attachTagToPlace(placeId, tag.id)
            }
        }
    }

    fun onCreateTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val tag = tagRepository.findOrCreateByName(name)
            tagRepository.attachTagToPlace(placeId, tag.id)
        }
    }
}
