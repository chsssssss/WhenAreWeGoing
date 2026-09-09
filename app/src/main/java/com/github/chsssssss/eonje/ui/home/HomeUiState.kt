package com.github.chsssssss.eonje.ui.home

data class HomePlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
)

data class HomeUiState(
    val isMapView: Boolean = true,
    val pendingCount: Int = 0,
    val places: List<HomePlace> = emptyList(),
) {
    val highlighted: HomePlace? get() = places.firstOrNull()
}
