package com.github.chsssssss.eonje.ui.placedetail

import com.github.chsssssss.eonje.data.local.TagEntity

data class PlacePost(
    val id: String,
    val label: String,
    val savedAt: String,
)

data class PlaceDetailUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val category: String = "",
    val address: String = "",
    val memo: String = "",
    val posts: List<PlacePost> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val showTagPicker: Boolean = false,
)
