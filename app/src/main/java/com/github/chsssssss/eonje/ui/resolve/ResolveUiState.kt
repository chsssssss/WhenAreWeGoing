package com.github.chsssssss.eonje.ui.resolve

import com.github.chsssssss.eonje.domain.model.PlaceCandidate

data class MultiCandidateGroup(
    val extractionIndex: Int,
    val extractedName: String,
    val candidates: List<PlaceCandidate>,
    val selected: PlaceCandidate?,
    val checked: Boolean,
    val expanded: Boolean = false,
)

data class ResolveUiState(
    val postId: String = "",
    val instagramUrl: String = "",
    val thumbnailUrl: String? = null,
    val subtitle: String = "",
    val isLoadingPost: Boolean = true,
    val isMultiMode: Boolean = false,
    // 단일 모드: 직접 검색
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<PlaceCandidate> = emptyList(),
    val selected: PlaceCandidate? = null,
    val searchError: String? = null,
    // 다중 모드: 추출된 항목별 후보 + 체크박스
    val multiGroups: List<MultiCandidateGroup> = emptyList(),
    val isSaving: Boolean = false,
)
