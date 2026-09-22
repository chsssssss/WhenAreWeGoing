package com.github.chsssssss.eonje.ui.home

import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import com.github.chsssssss.eonje.domain.model.GeoPoint
import com.github.chsssssss.eonje.domain.model.PlaceCandidate

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
    val folderColor: Int = FolderVisuals.DEFAULT_COLOR,
    val folderIcon: String = FolderVisuals.DEFAULT_ICON,
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
    val showCreateFolderSheet: Boolean = false,
    val folderAssignForPlaceId: String? = null,
    val folderAssignSelectedFolderId: String? = null,
) {
    /** 선택된 장소 — 바텀시트가 목록 모드인지 상세 모드인지도 이 값의 null 여부로 결정된다. */
    val highlighted: HomePlace? get() = places.firstOrNull { it.id == selectedPlaceId }
    val folderPickerForPlace: HomePlace? get() = places.firstOrNull { it.id == folderPickerForPlaceId }

    /** 상세화면 폴더 변경 시트의 대상 장소. */
    val folderAssignForPlace: HomePlace? get() = places.firstOrNull { it.id == folderAssignForPlaceId }

    /** 저장하지 않은 채 닫으려고 하면 경고를 띄워야 하는지 — 원래 폴더와 체크 상태가 다른 경우. */
    val folderAssignHasChanges: Boolean get() = folderAssignForPlace?.folderId != folderAssignSelectedFolderId

    /** 원래도 미분류였던 장소를 체크 안 한 채로 저장하면 더 지울 폴더 배정이 없다 — 이땐 장소 자체를 지운다. */
    val folderAssignWillDeletePlace: Boolean
        get() = folderAssignForPlace?.folderId == null && folderAssignSelectedFolderId == null
}

/** 게시물 없이 카카오 로컬 검색으로 장소를 바로 추가하는 시트의 상태 — 메인 [HomeUiState]와는 별도로 관리한다. */
data class AddPlaceUiState(
    val query: String = "",
    val results: List<PlaceCandidate> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
)
