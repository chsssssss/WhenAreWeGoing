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
}
