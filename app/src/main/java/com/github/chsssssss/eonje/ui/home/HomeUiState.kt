package com.github.chsssssss.eonje.ui.home

import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.domain.model.GeoPoint

/** 폴더 탭에서 고를 수 있는 세 가지 필터 — 전체, 미분류, 특정 폴더. */
sealed interface FolderFilter {
    data object All : FolderFilter
    data object Unclassified : FolderFilter
    data class ByFolder(val folderId: String) : FolderFilter
}

data class HomePlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val thumbnailUrl: String? = null,
    val folderId: String? = null,
    val folderName: String? = null,
    val memo: String = "",
)

data class HomePlacePost(
    val id: String,
    val label: String,
    val savedAt: String,
    val thumbnailUrl: String? = null,
)

data class HomeUiState(
    val places: List<HomePlace> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val folderFilter: FolderFilter = FolderFilter.All,
    val searchQuery: String = "",
    val selectedPlaceId: String? = null,
    val selectedPlacePosts: List<HomePlacePost> = emptyList(),
    val currentLocation: GeoPoint? = null,
    val folderPickerForPlaceId: String? = null,
) {
    /** 선택된 장소 — 바텀시트가 목록 모드인지 상세 모드인지도 이 값의 null 여부로 결정된다. */
    val highlighted: HomePlace? get() = places.firstOrNull { it.id == selectedPlaceId }
    val folderPickerForPlace: HomePlace? get() = places.firstOrNull { it.id == folderPickerForPlaceId }
}
