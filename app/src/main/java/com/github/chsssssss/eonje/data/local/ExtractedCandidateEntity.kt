package com.github.chsssssss.eonje.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Kakao 검색 후보 하나. 다중 게시물(추출 2건 이상)에서 각 추출 항목별로 최대 5개까지 저장되고,
 * ResolveScreen 다중 모드에서 사용자가 항목별로 확정할 후보를 고른다.
 */
@Entity(
    tableName = "extracted_candidates",
    primaryKeys = ["postId", "extractionIndex", "rank"],
    indices = [Index(value = ["postId"])],
)
data class ExtractedCandidateEntity(
    val postId: String,
    val extractionIndex: Int,
    val extractedName: String,
    val rank: Int,
    val kakaoPlaceId: String,
    val name: String,
    val address: String,
    val category: String?,
    val latitude: Double,
    val longitude: Double,
)
