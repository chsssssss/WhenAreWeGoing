package com.github.chsssssss.eonje.ui.home

import com.github.chsssssss.eonje.data.local.TagEntity

data class HomePlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val thumbnailUrl: String? = null,
)

data class HomeUiState(
    val isMapView: Boolean = true,
    val pendingCount: Int = 0,
    val places: List<HomePlace> = emptyList(),
    val totalCount: Int = 0,
    val tags: List<TagEntity> = emptyList(),
    val selectedTagId: String? = null,
) {
    val highlighted: HomePlace? get() = places.firstOrNull()
}
