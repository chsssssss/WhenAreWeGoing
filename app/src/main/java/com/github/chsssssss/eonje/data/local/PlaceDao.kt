package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: PlaceEntity)

    @Query("SELECT * FROM places WHERE kakaoPlaceId = :kakaoPlaceId LIMIT 1")
    suspend fun findByKakaoPlaceId(kakaoPlaceId: String): PlaceEntity?

    @Query("SELECT * FROM places WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PlaceEntity?

    @Query("SELECT * FROM places ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaceEntity>>
}
