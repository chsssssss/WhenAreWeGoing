package com.github.chsssssss.eonje.ui.resolve

import com.github.chsssssss.eonje.domain.model.PlaceCandidate

data class ResolveUiState(
    val postId: String = "",
    val instagramUrl: String = "",
    val subtitle: String = "",
    val isLoadingPost: Boolean = true,
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<PlaceCandidate> = emptyList(),
    val selected: PlaceCandidate? = null,
    val searchError: String? = null,
    val isSaving: Boolean = false,
)
