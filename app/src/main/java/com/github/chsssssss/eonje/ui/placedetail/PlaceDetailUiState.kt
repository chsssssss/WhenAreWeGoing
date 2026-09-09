package com.github.chsssssss.eonje.ui.placedetail

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
)
