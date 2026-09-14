package com.github.chsssssss.eonje.ui.resolve

import com.github.chsssssss.eonje.data.local.FolderEntity
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
    // 폴더: 단일/다중 공통 — 이번에 확정하는 장소(들)에 한 번에 적용된다. null이면 미분류.
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: String? = null,
    val showFolderPicker: Boolean = false,
) {
    val selectedFolderName: String? get() = folders.firstOrNull { it.id == selectedFolderId }?.name
}
