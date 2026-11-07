package com.github.chsssssss.eonje.domain.model

/**
 * 저장된 장소. 저장소(Room, 이후 Firestore)와 무관한 순수 모델이다.
 * 메모 기능은 제거돼서 메모 필드는 두지 않는다 — 저장소 구현이 기존 메모 값을 지우지 않고 보존한다.
 */
data class Place(
    val id: String,
    val name: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val kakaoPlaceId: String?,
    val category: String?,
    val status: ResolveStatus,
    val createdAt: Long,
    val resolvedAt: Long?,
    /** null이면 미분류. 폴더는 장소당 하나만 가질 수 있다. */
    val folderId: String? = null,
    /**
     * 이 장소가 나온 게시물의 id(게시물 문서의 placeIds의 역방향). 홈 목록이 게시물 문서를 읽지 않고도 개수·썸네일을 알 수 있게
     * 장소 문서에 둔다. 게시물 본문은 상세 모드에서 [com.github.chsssssss.eonje.domain.repository.PlaceRepository.observePostsForPlace]로 읽는다.
     */
    val postIds: List<String> = emptyList(),
    /** 대표 썸네일(이 기기에 받아둔 file:// 경로). 문서에 저장하지 않고 저장소가 읽을 때 postIds에서 채운다. 없으면 null. */
    val thumbnailUrl: String? = null,
)
