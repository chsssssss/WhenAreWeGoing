package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.data.local.PlaceEntity
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    suspend fun findByKakaoPlaceId(kakaoPlaceId: String): PlaceEntity?
    suspend fun findById(id: String): PlaceEntity?

    /** Upserts by kakaoPlaceId — an existing place is reused rather than duplicated. Returns the place's id. */
    suspend fun save(place: PlaceEntity): String

    suspend fun linkPostToPlace(postId: String, placeId: String)
    suspend fun postIdsForPlace(placeId: String): List<String>
    fun observeAll(): Flow<List<PlaceEntity>>

    /** folderId가 null이면 미분류로 되돌린다. */
    suspend fun assignFolder(placeId: String, folderId: String?)
    suspend fun assignFolderToPlaces(placeIds: List<String>, folderId: String?)

    /** 장소를 지우고, 게시물과의 연결 정보도 함께 지운다. 게시물 자체는 남는다. */
    suspend fun delete(placeId: String)
}
