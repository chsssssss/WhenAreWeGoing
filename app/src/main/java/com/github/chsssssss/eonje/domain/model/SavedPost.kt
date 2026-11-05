package com.github.chsssssss.eonje.domain.model

/**
 * 공유받아 저장한 인스타그램 게시물. 저장소(Room, 이후 Firestore)와 무관한 순수 모델이다.
 * [thumbnailUrl]은 원격 URL이거나 기기에 받아둔 file:// 경로다.
 */
data class SavedPost(
    val id: String,
    val shortcode: String?,
    val instagramUrl: String,
    val caption: String?,
    val thumbnailUrl: String?,
    val status: ResolveStatus,
    val extractedCount: Int,
    val createdAt: Long,
    /** [status]가 [ResolveStatus.UNRESOLVED]일 때만 값이 있다. */
    val unresolvedReason: UnresolvedReason? = null,
)
