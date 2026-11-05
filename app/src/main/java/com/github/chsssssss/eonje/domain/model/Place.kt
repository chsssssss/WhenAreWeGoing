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
)
