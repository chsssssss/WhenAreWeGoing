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
}
