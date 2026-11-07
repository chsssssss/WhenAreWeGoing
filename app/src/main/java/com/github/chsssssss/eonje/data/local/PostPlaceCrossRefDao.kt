package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PostPlaceCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: PostPlaceCrossRef)

    @Query("SELECT placeId FROM PostPlaceCrossRef WHERE postId = :postId")
    suspend fun placeIdsForPost(postId: String): List<String>

    @Query("SELECT postId FROM PostPlaceCrossRef WHERE placeId = :placeId")
    suspend fun postIdsForPlace(placeId: String): List<String>

    @Query("DELETE FROM PostPlaceCrossRef WHERE placeId = :placeId")
    suspend fun deleteForPlace(placeId: String)

    // 이전용: 게시물 묶음의 연결을 한 번에 읽는다.
    @Query("SELECT * FROM PostPlaceCrossRef WHERE postId IN (:postIds)")
    suspend fun forPosts(postIds: List<String>): List<PostPlaceCrossRef>

    // 이전용: 장소 묶음의 연결을 한 번에 읽는다(장소 문서의 postIds를 채운다).
    @Query("SELECT * FROM PostPlaceCrossRef WHERE placeId IN (:placeIds)")
    suspend fun forPlaces(placeIds: List<String>): List<PostPlaceCrossRef>
}
